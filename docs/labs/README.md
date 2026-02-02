# Spring Labs - 실험실

Spring Framework의 숨겨진 동작들을 직접 테스트하고 검증하는 실험실입니다.

## 실험 목록

| # | 주제 | 난이도 | 상태 |
|---|------|--------|------|
| 0 | [Count Query 최적화 (46% 성능 향상)](./lab-00-count-query-optimization.md) | ⭐ | ✅ 완료 |
| 1 | [@Transactional 자기 호출 함정](./lab-01-transactional-self-invocation.md) | ⭐ | ✅ 완료 |
| 2 | [@Transactional(readOnly=true) 실제 효과](./lab-02-transactional-readonly.md) | ⭐⭐ | ✅ 완료 |
| 3 | [Bulk Insert 성능 비교 (saveAll vs JdbcTemplate vs Native)](./lab-03-bulk-insert-performance.md) | ⭐⭐ | ✅ 완료 |
| 4 | QueryDSL N+1 문제와 해결책 | ⭐⭐⭐ | 📋 예정 |
| 5 | Kotlin data class + JPA 함정 | ⭐⭐ | 📋 예정 |
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
src/main/resources/http/experiment-api.http
```

또는 curl:
```bash
curl http://localhost:8080/api/v1/experiments/all
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
src/main/kotlin/com/wisehero/springdemo/experiment/
├── TransactionExperimentService.kt   # 실험 1: 자기호출
├── ReadOnlyExperimentService.kt      # 실험 2: readOnly
├── BulkInsertExperimentService.kt    # 실험 3: Bulk Insert
├── dto/
│   └── BulkInsertResult.kt           # 실험 3 결과 DTO
└── ExperimentController.kt           # 실험 API
```
