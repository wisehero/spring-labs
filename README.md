# Spring Labs - 실험실

Spring Framework의 숨겨진 동작들을 직접 테스트하고 검증하는 실험실입니다.

## 실험 목록

| # | 주제 | 난이도 | 상태 |
|---|------|--------|------|
| 0 | [Count Query 최적화 (46% 성능 향상)](docs/labs/lab-00-count-query-optimization.md) | ⭐ | ✅ 완료 |
| 1 | [@Transactional 자기 호출 함정](docs/labs/lab-01-transactional-self-invocation.md) | ⭐ | ✅ 완료 |
| 2 | [@Transactional(readOnly=true) 실제 효과](docs/labs/lab-02-transactional-readonly.md) | ⭐⭐ | ✅ 완료 |
| 3 | [Bulk Insert 성능 비교 (saveAll vs JdbcTemplate vs Native)](docs/labs/lab-03-bulk-insert-performance.md) | ⭐⭐ | ✅ 완료 |
| 4 | [트랜잭션 전파 REQUIRED vs REQUIRES_NEW](docs/labs/lab-04-transaction-propagation.md) | ⭐⭐⭐ | ✅ 완료 |
| 5 | [Optimistic Lock vs Pessimistic Lock](docs/labs/lab-05-optimistic-pessimistic-lock.md) | ⭐⭐⭐ | ✅ 완료 |
| 6 | Bean 순환 참조 해결 방법들 | ⭐⭐ | 📋 예정 |

## 실험 환경

- **프레임워크**: Spring Boot 3.x
- **언어**: Kotlin
- **ORM**: JPA + Hibernate + QueryDSL
- **DB**: MySQL 8.x (Docker)
- **로깅**: p6spy

## 실험 실행 방법

### 1. 애플리케이션 실행
```bash
# Docker로 MySQL 실행
docker-compose up -d

# Spring Boot 실행
./gradlew bootRun
```

### 2. API 호출
IntelliJ HTTP Client 사용:
```
src/main/resources/http/
├── lab01-self-invocation.http
├── lab02-readonly.http
├── lab03-bulk-insert.http
├── lab04-propagation.http
└── lab05-lock.http
```

또는 curl:
```bash
curl http://localhost:8080/api/v1/experiments/self-invocation
```

### 3. 로그 확인
콘솔에서 트랜잭션 로그 확인 (TRACE 레벨 활성화됨)

## 로깅 설정

`application.properties`에서 다음 로깅이 활성화되어 있습니다:

```properties
# Spring Transaction
logging.level.org.springframework.transaction=TRACE
logging.level.org.springframework.transaction.interceptor=TRACE

# Hibernate Transaction
logging.level.org.hibernate.engine.transaction.internal.TransactionImpl=DEBUG
logging.level.org.hibernate.resource.transaction=DEBUG

# SQL
logging.level.org.hibernate.SQL=DEBUG

# AOP Proxy
logging.level.org.springframework.aop=DEBUG
```

## 관련 소스 코드

```
src/main/kotlin/com/wisehero/springlabs/
├── labs01/                                        # Lab 01: 자기호출
│   ├── Lab01Controller.kt
│   ├── TransactionExperimentService.kt
│   └── TransactionExperimentExternalService.kt
├── labs02/                                        # Lab 02: readOnly
│   ├── Lab02Controller.kt
│   └── ReadOnlyExperimentService.kt
├── labs03/                                        # Lab 03: Bulk Insert
│   ├── Lab03Controller.kt
│   ├── BulkInsertExperimentService.kt
│   └── dto/BulkInsertResult.kt
├── labs04/                                        # Lab 04: Transaction Propagation
│   ├── Lab04Controller.kt
│   ├── PropagationExperimentService.kt
│   ├── PropagationExperimentInnerService.kt
│   └── dto/PropagationResult.kt
└── labs05/                                        # Lab 05: Lock
    ├── Lab05Controller.kt
    ├── LockExperimentService.kt
    └── dto/
        ├── LockResult.kt
        └── PerformanceComparison.kt
```
