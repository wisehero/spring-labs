# Lab 05: Optimistic Lock vs Pessimistic Lock

## 개요

데이터베이스 동시성 제어의 두 가지 핵심 전략인 **낙관적 락(Optimistic Lock)**과 **비관적 락(Pessimistic Lock)**을 Product 엔티티의 재고(stock) 관리 시나리오로 비교 실험합니다.

### 왜 중요한가?

- 재고 차감, 좌석 예약, 포인트 사용 등 **동시 수정이 빈번한 도메인**에서 반드시 이해해야 하는 개념
- 잘못된 동시성 제어는 **Lost Update(갱신 손실)**, **데이터 불일치**, **데드락** 등을 유발
- 두 전략의 **트레이드오프**를 이해하고 상황에 맞게 선택하는 것이 핵심

## 핵심 개념

### 낙관적 락 (Optimistic Lock)

> "충돌은 거의 없을 것이다" → 충돌 시 감지하고 처리

| 항목 | 설명 |
|------|------|
| 원리 | `@Version` 컬럼으로 읽은 시점의 버전과 쓰는 시점의 버전을 비교 |
| SQL | `UPDATE ... SET version = version + 1 WHERE id = ? AND version = ?` |
| 충돌 시 | `OptimisticLockException` 발생 → 애플리케이션에서 재시도 |
| 장점 | DB 락 없음 → 높은 처리량 |
| 단점 | 충돌 시 재시도 비용, 경합 심하면 성능 저하 |

### 비관적 락 (Pessimistic Lock)

> "충돌이 발생할 것이다" → 미리 잠그고 작업

| 항목 | 설명 |
|------|------|
| 원리 | `SELECT ... FOR UPDATE`로 행 잠금 획득 후 작업 |
| SQL | `SELECT ... FROM product WHERE id = ? FOR UPDATE` |
| 충돌 시 | 다른 트랜잭션은 잠금 해제까지 **대기(blocking)** |
| 장점 | 항상 정확한 결과, 재시도 불필요 |
| 단점 | 대기로 인한 처리량 감소, 데드락 가능성 |

### 동작 원리 상세

#### JPA @Version 메커니즘

```
1. Entity 조회: SELECT id, name, stock, version FROM product WHERE id = 1
   → version = 3 로 읽음

2. Entity 수정: product.stock -= 1

3. Hibernate flush (커밋 시점):
   → UPDATE product SET stock = 99, version = 4
     WHERE id = 1 AND version = 3
                       ^^^^^^^^^^^^
                       이 조건이 핵심!

4-a. version = 3 이 맞으면: 1 row updated → 성공
4-b. version ≠ 3 이면: 0 rows updated → StaleObjectStateException 발생
     → Spring이 ObjectOptimisticLockingFailureException으로 래핑
```

**Hibernate 내부 흐름:**
```
EntityManager.flush()
  → DefaultFlushEntityEventListener
    → EntityUpdateAction
      → 생성된 SQL에 version 조건 추가
      → executeUpdate() 후 affected rows 확인
      → 0 rows → StaleObjectStateException 던짐
  → Spring TX: ObjectOptimisticLockingFailureException으로 변환
```

#### SELECT FOR UPDATE 메커니즘

```
트랜잭션 A:
  BEGIN;
  SELECT * FROM product WHERE id = 1 FOR UPDATE;  ← row lock 획득
  UPDATE product SET stock = 99 WHERE id = 1;
  COMMIT;  ← lock 해제

트랜잭션 B (A가 잠금 보유 중):
  BEGIN;
  SELECT * FROM product WHERE id = 1 FOR UPDATE;  ← 대기(blocking)...
  ... A가 COMMIT하면 진행 ...
  → 최신 데이터(stock=99)를 읽게 됨
```

**InnoDB 내부:**
```
1. FOR UPDATE → InnoDB에 X-lock (exclusive lock) 요청
2. index record lock 또는 next-key lock 획득
3. 다른 트랜잭션의 S-lock/X-lock 요청은 대기 큐에 진입
4. COMMIT/ROLLBACK 시 lock 해제 → 대기 중인 트랜잭션 깨움
```

## 실험 목록

| 실험 | 이름 | Method | 핵심 관찰 포인트 |
|------|------|--------|-----------------|
| 5-1 | 락 없음 - Lost Update | POST | 갱신 손실 건수 확인 |
| 5-2 | Optimistic Lock (@Version) | POST | OptimisticLockException 발생 확인 |
| 5-3 | Optimistic Lock + Retry | POST | 재시도로 전부 성공하는지 확인 |
| 5-4 | Pessimistic Lock (FOR UPDATE) | POST | 재시도 없이 순차 처리 확인 |
| 5-5 | 성능 비교 | POST | 경합 수준별 두 전략 비교 |
| 5-6 | 데드락 시나리오 | POST | MySQL 데드락 감지 확인 |

## 실험 상세

### 실험 5-1: 락 없음 - Lost Update 발생

- **엔드포인트**: `POST /api/v1/experiments/lock/5-1/no-lock-lost-update`
- **동작**: 100개 스레드가 동시에 재고 1씩 차감 (동시성 제어 없음)
- **내부 동작 원리**:

Native SQL로 JPA entity lifecycle을 우회하여 read-modify-write 패턴을 사용합니다:

```
Thread-A: SELECT stock FROM product WHERE id = 1  → 100
Thread-B: SELECT stock FROM product WHERE id = 1  → 100 (같은 값!)
Thread-A: UPDATE product SET stock = 99 WHERE id = 1  → OK
Thread-B: UPDATE product SET stock = 99 WHERE id = 1  → OK (A의 변경 덮어씀!)
```

> **중요**: `SET stock = stock - 1`은 SQL 레벨에서 atomic operation이라 Lost Update가 발생하지 않습니다. 그래서 의도적으로 read → 앱에서 계산 → write를 분리합니다.

- **예상 결과**: `finalStock > 0` (Lost Update로 인해 일부 차감이 유실됨)
- **의미**: 동시성 제어 없이는 read-modify-write 패턴에서 갱신 손실이 불가피

### 실험 5-2: Optimistic Lock (@Version) - 충돌 감지

- **엔드포인트**: `POST /api/v1/experiments/lock/5-2/optimistic-lock`
- **동작**: 100개 스레드가 JPA @Version 기반으로 동시 차감
- **내부 동작 원리**:

```
Thread-A: findById(1) → Product(stock=100, version=0)
Thread-B: findById(1) → Product(stock=100, version=0)

Thread-A: stock=99, flush → UPDATE ... SET stock=99, version=1 WHERE id=1 AND version=0
  → 1 row affected ✅

Thread-B: stock=99, flush → UPDATE ... SET stock=99, version=1 WHERE id=1 AND version=0
  → 0 rows affected ❌ → OptimisticLockException!
```

각 스레드가 `REQUIRES_NEW` 트랜잭션에서 독립 실행되므로, version 충돌이 감지됩니다.

- **예상 결과**: 다수의 `failureCount`, Lost Update 없음
- **의미**: @Version이 충돌을 감지하지만, 재시도 없이는 실패한 요청이 유실됨

### 실험 5-3: Optimistic Lock + Retry

- **엔드포인트**: `POST /api/v1/experiments/lock/5-3/optimistic-retry`
- **동작**: OptimisticLockException 시 최대 50회 재시도
- **내부 동작 원리**:

```kotlin
while (retries < maxRetries) {
    try {
        // 새 트랜잭션에서 시도 (REQUIRES_NEW)
        self.decrementStockWithOptimisticLock(productId)
        break  // 성공!
    } catch (e: ObjectOptimisticLockingFailureException) {
        retries++
        Thread.sleep(random(1..3))  // 짧은 백오프
        // 재시도 → 새 트랜잭션이 최신 version을 읽어옴
    }
}
```

**핵심**: 재시도는 반드시 **새 트랜잭션**에서 해야 합니다. 같은 트랜잭션의 EntityManager는 이미 stale 상태이므로 `clear()` 없이는 같은 version을 읽게 됩니다. `REQUIRES_NEW`가 새 EntityManager/PersistenceContext를 사용하므로 자연스럽게 해결됩니다.

- **예상 결과**: `finalStock = 0`, `retryCount > 0`
- **의미**: Optimistic Lock + Retry는 경합이 적을 때 효과적이며, 경합이 심하면 재시도가 폭주할 수 있음

### 실험 5-4: Pessimistic Lock (SELECT FOR UPDATE)

- **엔드포인트**: `POST /api/v1/experiments/lock/5-4/pessimistic-lock`
- **동작**: SELECT FOR UPDATE로 행 잠금 후 순차 처리
- **내부 동작 원리**:

```
Thread-A: SELECT * FROM product WHERE id = 1 FOR UPDATE → lock 획득, stock=100
Thread-B: SELECT * FROM product WHERE id = 1 FOR UPDATE → 대기...
Thread-C: SELECT * FROM product WHERE id = 1 FOR UPDATE → 대기...

Thread-A: UPDATE stock = 99, COMMIT → lock 해제
Thread-B: (깨어남) stock=99 읽음, UPDATE stock = 98, COMMIT → lock 해제
Thread-C: (깨어남) stock=98 읽음, UPDATE stock = 97, COMMIT → lock 해제
```

**JPA에서의 사용**:
```kotlin
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Product p WHERE p.id = :id")
fun findByIdWithPessimisticLock(@Param("id") id: Long): Product?
```

Hibernate가 `LockModeType.PESSIMISTIC_WRITE`를 `FOR UPDATE`로 변환합니다.

- **예상 결과**: `finalStock = 0`, `failureCount = 0`, 재시도 없음
- **의미**: 비관적 락은 항상 정확하지만 처리량이 제한됨 (직렬화)

### 실험 5-5: 성능 비교

- **엔드포인트**: `POST /api/v1/experiments/lock/5-5/performance-comparison`
- **동작**: 10 스레드(낮은 경합) vs 100 스레드(높은 경합)로 두 전략 비교
- **내부 동작 원리**:

| 시나리오 | Optimistic + Retry | Pessimistic |
|---------|-------------------|-------------|
| 낮은 경합 (10t) | 재시도 적음 → 빠름 | 잠금 대기 짧음 |
| 높은 경합 (100t) | 재시도 폭주 → 느림 | 잠금 대기 길지만 안정적 |

- **예상 결과**: 낮은 경합에서는 Optimistic 유리, 높은 경합에서는 Pessimistic 유리 (환경에 따라 다름)
- **의미**: 경합 수준에 따라 최적 전략이 다름

### 실험 5-6: 데드락 시나리오

- **엔드포인트**: `POST /api/v1/experiments/lock/5-6/deadlock`
- **동작**: 두 스레드가 두 상품을 역순으로 잠금
- **내부 동작 원리**:

```
Thread-0: LOCK(A) → sleep → LOCK(B)
Thread-1: LOCK(B) → sleep → LOCK(A)

시간 흐름:
t=0: Thread-0이 A 잠금 획득
t=0: Thread-1이 B 잠금 획득
t=50ms: Thread-0이 B 잠금 요청 → Thread-1이 보유 중이므로 대기
t=50ms: Thread-1이 A 잠금 요청 → Thread-0이 보유 중이므로 대기
→ 교착 상태(Deadlock)!
```

**MySQL InnoDB 데드락 감지:**
```
1. InnoDB는 wait-for graph (대기 그래프)를 유지
2. 새로운 잠금 요청이 대기 상태가 되면 그래프에 추가
3. 그래프에서 cycle 감지 시 → 데드락 판정
4. 비용이 적은 트랜잭션을 victim으로 선택하여 강제 롤백
5. victim 트랜잭션은 "Deadlock found when trying to get lock" 에러 수신
6. 살아남은 트랜잭션은 정상 진행
```

`innodb_deadlock_detect = ON` (기본값)이면 즉시 감지합니다. `innodb_lock_wait_timeout`(기본 50초)까지 기다리지 않습니다.

- **예상 결과**: 데드락 1건 이상 발생, 하나의 트랜잭션이 강제 롤백
- **의미**: 비관적 락 사용 시 잠금 순서를 일관되게 유지해야 데드락을 예방할 수 있음

## 정리

### 전략 선택 가이드

| 기준 | Optimistic Lock | Pessimistic Lock |
|------|----------------|-----------------|
| **경합 빈도** | 낮음 (대부분 성공) | 높음 (충돌 빈번) |
| **재시도 비용** | 감수 가능 | 재시도 불가/비쌈 |
| **처리량 요구** | 높은 처리량 필요 | 정확성 우선 |
| **적합한 케이스** | 게시글 수정, 프로필 업데이트 | 재고 차감, 좌석 예약, 포인트 사용 |
| **데드락 위험** | 없음 | 잠금 순서 미준수 시 발생 |

### Best Practices

1. **Optimistic Lock 사용 시**
   - 반드시 **재시도 로직**을 구현할 것 (재시도 없으면 5-2처럼 요청 유실)
   - 재시도는 **새 트랜잭션**에서 수행 (`REQUIRES_NEW` 또는 트랜잭션 밖에서)
   - 재시도 횟수 제한 + **exponential backoff** 적용 권장
   - 경합이 높아지면 Pessimistic으로 전환 고려

2. **Pessimistic Lock 사용 시**
   - **잠금 순서를 항상 일관**되게 유지 (ex: ID 오름차순)
   - `@QueryHints`로 **lock timeout** 설정 권장
   - 트랜잭션을 **짧게** 유지 (잠금 보유 시간 최소화)
   - 불필요한 `SELECT FOR UPDATE` 남발 금지

3. **공통**
   - MySQL `SET stock = stock - 1`은 atomic이므로, 단순 증감은 **UPDATE 쿼리 하나**로 처리 가능 (락 불필요)
   - `@Version`과 `FOR UPDATE`를 **동시에 사용**할 수 있음 (이중 보호)
   - 분산 환경에서는 DB 락만으로 부족할 수 있음 → Redis 분산 락 등 고려

### 참고 자료

- [JPA Spec - Optimistic Locking](https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1#a2297)
- [Hibernate - Locking](https://docs.jboss.org/hibernate/orm/6.4/userguide/html_single/Hibernate_User_Guide.html#locking)
- [MySQL InnoDB Locking](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking.html)
- [MySQL InnoDB Deadlock Detection](https://dev.mysql.com/doc/refman/8.0/en/innodb-deadlock-detection.html)
