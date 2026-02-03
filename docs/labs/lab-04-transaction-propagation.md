# Lab 04: 트랜잭션 전파 (Transaction Propagation) - REQUIRED vs REQUIRES_NEW

## 개요

Spring의 `@Transactional` 어노테이션에서 가장 많이 사용되는 두 가지 전파 옵션인 **REQUIRED**(기본값)와 **REQUIRES_NEW**의 동작 차이를 9가지 실험을 통해 탐구합니다.

### 왜 중요한가?

- **REQUIRED**: 기본값이므로 대부분의 서비스 메서드가 사용. 기존 트랜잭션이 있으면 참여, 없으면 새로 생성.
- **REQUIRES_NEW**: 항상 새 트랜잭션을 생성. 로깅, 감사, 부분 커밋이 필요한 경우 사용.
- 두 옵션의 차이를 정확히 이해하지 못하면 **UnexpectedRollbackException**, **커넥션 풀 고갈**, **데이터 불일치** 같은 운영 장애로 이어질 수 있습니다.

## 핵심 개념

### REQUIRED (기본값)
```
호출자에 트랜잭션 있음 → 참여 (같은 트랜잭션 공유)
호출자에 트랜잭션 없음 → 새로 생성
```

### REQUIRES_NEW
```
호출자에 트랜잭션 있음 → 기존 트랜잭션 suspend, 새 트랜잭션 생성
호출자에 트랜잭션 없음 → 새로 생성
```

**핵심 차이**: REQUIRED는 트랜잭션을 "공유"하고, REQUIRES_NEW는 항상 "독립"적입니다.

## 실험 목록

| 실험 | 이름 | HTTP Method | 핵심 관찰 포인트 |
|------|------|-------------|-----------------|
| 4-1 | REQUIRED 외부 트랜잭션 참여 | GET | tx name 동일 여부 |
| 4-2 | REQUIRED 새 트랜잭션 생성 | GET | tx 없는 상태에서 생성 |
| 4-3 | REQUIRES_NEW 항상 새 트랜잭션 | GET | tx name 상이 여부 |
| 4-4 | REQUIRED 롤백 트랩 | POST | **UnexpectedRollbackException** |
| 4-5 | REQUIRES_NEW Inner 예외 격리 | POST | outer 생존 여부 |
| 4-6 | Outer 실패 시 Inner 생존 | POST | **REQUIRES_NEW 독립 커밋** |
| 4-7 | UnexpectedRollbackException 심화 | POST | 3가지 시나리오 비교 |
| 4-8 | DB 커넥션 분리 확인 | GET | connection hashCode 비교 |
| 4-9 | 커넥션 풀 고갈 | POST | **약 30초 소요!** |

## 실험 상세

### 실험 4-1: REQUIRED - 외부 트랜잭션 존재 시 참여

**엔드포인트**: `GET /api/v1/experiments/propagation/4-1/required-joins`

**동작**: Outer `@Transactional` → Inner `@Transactional(REQUIRED)` 호출

**예상 결과**: `outerTxName == innerTxName` (같은 트랜잭션 공유)

**의미**: REQUIRED는 기존 트랜잭션이 있으면 자동으로 참여합니다. begin/commit이 한 번만 발생합니다.

---

### 실험 4-2: REQUIRED - 트랜잭션 없을 때 새로 생성

**엔드포인트**: `GET /api/v1/experiments/propagation/4-2/required-creates-new`

**동작**: Controller(no tx) → Service `@Transactional(REQUIRED)` 호출

**예상 결과**: Inner에서 새 트랜잭션이 생성됨 (`tx_active = true`)

**의미**: REQUIRED는 "있으면 참여, 없으면 생성"의 유연한 기본값입니다.

---

### 실험 4-3: REQUIRES_NEW - 항상 새 트랜잭션 생성

**엔드포인트**: `GET /api/v1/experiments/propagation/4-3/requires-new-always-new`

**동작**: Outer `@Transactional` → Inner `@Transactional(REQUIRES_NEW)` 호출

**예상 결과**: `outerTxName != innerTxName` (독립 트랜잭션)

**의미**: REQUIRES_NEW는 기존 트랜잭션을 일시 중단(suspend)하고 완전히 새로운 트랜잭션을 시작합니다.

---

### 실험 4-4: REQUIRED 롤백 전파 트랩 (The Classic Trap!)

**엔드포인트**: `POST /api/v1/experiments/propagation/4-4/required-inner-throws`

**동작**:
1. Outer: 행 A 삽입
2. Inner(REQUIRED, 같은 tx): 행 B 삽입 후 RuntimeException 던짐
3. Outer: 예외를 catch함
4. Outer: 정상 리턴 시도 → **UnexpectedRollbackException 발생!**

**예상 결과**: 행 A, B 모두 롤백. `UnexpectedRollbackException` 발생.

**왜 이런가?**: Inner가 REQUIRED로 같은 트랜잭션에 참여했으므로, Inner의 예외 시 Spring TX 인터셉터가 트랜잭션을 **rollback-only**로 마킹합니다. Outer가 예외를 catch해도 이미 트랜잭션은 오염된 상태. 커밋 시도 시 Spring이 `UnexpectedRollbackException`을 던집니다.

**실무 교훈**: REQUIRED 공유 트랜잭션에서 inner 예외를 catch하는 것은 "트랩"입니다. 예외를 잡아도 트랜잭션은 이미 죽은 상태입니다.

---

### 실험 4-5: REQUIRES_NEW Inner 예외 - Outer 생존

**엔드포인트**: `POST /api/v1/experiments/propagation/4-5/requires-new-inner-throws`

**동작**:
1. Outer: 행 A 삽입
2. Inner(REQUIRES_NEW, 독립 tx): 행 B 삽입 후 RuntimeException 던짐 → Inner만 롤백
3. Outer: 예외를 catch하고 정상 커밋

**예상 결과**: 행 A 커밋 성공, 행 B 롤백. `UnexpectedRollbackException` 없음.

**4-4와의 차이**: REQUIRES_NEW이므로 Inner의 롤백이 Outer에 영향을 주지 않습니다.

---

### 실험 4-6: Outer 실패 후 REQUIRES_NEW Inner 생존

**엔드포인트**: `POST /api/v1/experiments/propagation/4-6/outer-fails-after-inner`

**동작**:
1. Outer: 행 A 삽입
2. Inner(REQUIRES_NEW): 행 B 삽입 → **독립적으로 커밋 완료**
3. Outer: 의도적으로 RuntimeException 던짐 → Outer만 롤백

**예상 결과**: 행 A 롤백, 행 B **생존** (이미 독립 커밋됨).

**실무 교훈**: REQUIRES_NEW의 양날의 검. 부분 커밋이 가능하지만, 데이터 불일치를 야기할 수 있습니다.

---

### 실험 4-7: UnexpectedRollbackException 상세 분석

**엔드포인트**: `POST /api/v1/experiments/propagation/4-7/unexpected-rollback-deep-dive`

3가지 시나리오를 한 번에 실행:

| 시나리오 | 상황 | 결과 |
|----------|------|------|
| A | Inner 예외 catch 후 rollback-only 플래그 확인 | `isRollbackOnly = true`, `UnexpectedRollbackException` |
| B | Inner 예외를 catch하지 않음 | `RuntimeException` 그대로 전파 (NOT UnexpectedRollbackException) |
| C | Inner가 `setRollbackOnly()` 호출, 예외 없음 | `UnexpectedRollbackException` 발생! |

**핵심**: Scenario C가 특히 위험합니다. 예외 없이 `setRollbackOnly()`만 호출해도 Outer 커밋이 실패합니다.

---

### 실험 4-8: DB 커넥션 분리 확인

**엔드포인트**: `GET /api/v1/experiments/propagation/4-8/connection-separation`

**동작**: Outer 커넥션 ID vs Inner(REQUIRES_NEW) 커넥션 ID vs Inner(REQUIRED) 커넥션 ID 비교

**예상 결과**:
- REQUIRES_NEW: 다른 커넥션 (별도 HikariCP 커넥션)
- REQUIRED: 같은 커넥션 (공유)

**의미**: REQUIRES_NEW는 실제로 **2개의 DB 커넥션을 동시에 점유**합니다. Outer 커넥션은 suspend 상태로 반환되지 않습니다.

---

### 실험 4-9: 커넥션 풀 고갈 시뮬레이션

**엔드포인트**: `POST /api/v1/experiments/propagation/4-9/connection-pool-exhaustion`

**WARNING: 이 실험은 약 30초 소요됩니다!**

**동작**: REQUIRES_NEW를 11단계 재귀 중첩 (HikariCP 기본 풀 사이즈 10 초과)

**예상 결과**: 깊이 10까지 성공, 깊이 11에서 커넥션 획득 대기 후 timeout

**Self-injection 필수**: 재귀 호출에 `self.` 사용 (Lab 01 자기 호출 트랩 실전 적용)

**실무 교훈**: REQUIRES_NEW를 루프나 재귀 내에서 사용하면 커넥션 풀을 고갈시킬 수 있습니다.

## 트랜잭션 전파 동작 요약

| 시나리오 | REQUIRED | REQUIRES_NEW |
|----------|----------|-------------|
| 기존 tx 존재 | 참여 (공유) | suspend 후 새 tx 생성 |
| 기존 tx 없음 | 새로 생성 | 새로 생성 |
| Inner 예외 → Outer catch | **rollback-only 트랩!** | Outer 영향 없음 |
| Outer 예외 → Inner 데이터 | 모두 롤백 | Inner 데이터 생존 |
| DB 커넥션 | 공유 (1개) | 분리 (2개 동시 점유) |
| 커넥션 풀 영향 | 없음 | **풀 고갈 위험** |

## 실무 Best Practices

1. **REQUIRED가 기본값인 이유를 이해하세요**: 대부분의 경우 트랜잭션을 공유하는 것이 올바릅니다.
2. **REQUIRES_NEW는 신중하게**: 로깅, 감사 로그, 알림 등 "실패해도 메인 로직에 영향 없어야 하는" 작업에만 사용하세요.
3. **inner 예외를 catch하는 코드를 의심하세요**: REQUIRED 공유 트랜잭션에서 inner 예외 catch는 트랩입니다.
4. **REQUIRES_NEW + 루프 = 위험**: 커넥션 풀 고갈 가능성을 항상 고려하세요.
5. **REQUIRES_NEW의 부분 커밋을 인지하세요**: 데이터 일관성이 깨질 수 있습니다.

## 관련 소스 코드

| 파일 | 설명 |
|------|------|
| `labs04/PropagationExperimentService.kt` | 9개 실험 오케스트레이터 |
| `labs04/PropagationExperimentInnerService.kt` | Inner 서비스 빈 (프록시 기반 호출) |
| `labs04/dto/PropagationResult.kt` | 실험 결과 DTO |
| `labs04/Lab04Controller.kt` | REST 엔드포인트 |
| `repository/TransactionRepository.kt` | 검증용 쿼리 메서드 |

## 선수 학습

- [Lab 01: @Transactional 자기 호출 함정](lab-01-transactional-self-invocation.md) - AOP 프록시와 self-injection 패턴
- [Lab 02: @Transactional(readOnly=true) 실제 효과](lab-02-transactional-readonly.md)
- [Lab 03: Bulk Insert 성능 비교](lab-03-bulk-insert-performance.md) - self-injection 패턴 활용

## 참고 자료

- [Spring Framework - Transaction Propagation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html)
- [UnexpectedRollbackException JavaDoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/UnexpectedRollbackException.html)
- [HikariCP Configuration](https://github.com/brettwooldridge/HikariCP#gear-configuration-knobs-baby)
