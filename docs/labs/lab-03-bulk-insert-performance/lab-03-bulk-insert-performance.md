# Lab 03: Bulk Insert 성능 비교

## 개요

대량 데이터 Insert 시 JPA saveAll, JdbcTemplate batchUpdate, Native Bulk Insert의 성능을 비교한다.

## 비교 대상

| 방법 | 설명 | 특징 |
|------|------|------|
| **JPA saveAll** | `repository.saveAll(entities)` | 엔티티 기반, 더티체킹, 1차 캐시 |
| **JdbcTemplate batchUpdate** | `jdbcTemplate.batchUpdate(sql, setter)` | JDBC 배치, PreparedStatement 재사용 |
| **Native Bulk Insert** | `INSERT INTO ... VALUES (...),(...),(...)` | 단일 쿼리, 최소 네트워크 왕복 |

## 테스트 환경

- **엔티티**: Transaction (19개 컬럼)
- **DB**: MySQL 8.x (Docker)
- **테스트 규모**: 100건, 1,000건, 10,000건
- **배치 설정**:
  - `rewriteBatchedStatements=true`
  - `hibernate.jdbc.batch_size=50`

---

## 각 방법의 원리

### 1. JPA saveAll

```kotlin
@Transactional
fun insertWithSaveAll(count: Int) {
    val entities = generateEntities(count)
    repository.saveAll(entities)  // 내부적으로 각 엔티티마다 persist 호출
    entityManager.flush()         // 실제 INSERT 실행
}
```

**내부 동작:**
```
saveAll() 호출 (IDENTITY 전략)
├── 엔티티 1: persist() → INSERT 즉시 실행 (ID 획득 필요) → 1차 캐시 저장
├── 엔티티 2: persist() → INSERT 즉시 실행 → 1차 캐시 저장
├── ...
└── 엔티티 N: persist() → INSERT 즉시 실행 → 1차 캐시 저장

※ IDENTITY 전략은 DB가 ID를 생성하므로, Hibernate가 persist 시점에
   INSERT를 바로 실행해야 한다. batch_size 설정이 있어도 배치 묶기가 불가능하다.
```

**장점:**
- 엔티티 라이프사이클 관리
- @PrePersist 등 콜백 동작
- 영속성 컨텍스트 관리

**단점:**
- 메모리 사용량 높음 (1차 캐시 + 스냅샷)
- 더티체킹 오버헤드
- 대량 데이터에 부적합

### 2. JdbcTemplate batchUpdate

```kotlin
fun insertWithJdbcBatch(count: Int) {
    val entities = generateEntities(count)
    
    jdbcTemplate.batchUpdate(sql, object : BatchPreparedStatementSetter {
        override fun setValues(ps: PreparedStatement, i: Int) {
            val tx = entities[i]
            ps.setTimestamp(1, Timestamp.valueOf(tx.approveDateTime))
            ps.setBigDecimal(2, tx.amount)
            // ... 나머지 파라미터
        }
        override fun getBatchSize() = entities.size
    })
}
```

**내부 동작:**
```
batchUpdate() 호출
├── PreparedStatement 생성 (1회)
├── 파라미터 바인딩 (N회)
│   ├── addBatch() - 배치에 추가
│   └── batch_size 도달 시 executeBatch()
└── 최종 executeBatch()
```

**MySQL rewriteBatchedStatements=true 효과:**
```sql
-- 비활성화 시 (개별 쿼리)
INSERT INTO transaction (...) VALUES (?,...);
INSERT INTO transaction (...) VALUES (?,...);
INSERT INTO transaction (...) VALUES (?,...);

-- 활성화 시 (단일 쿼리로 변환!)
INSERT INTO transaction (...) VALUES (?,...),(?,...),(?,...);
```

**장점:**
- PreparedStatement 재사용
- 네트워크 왕복 최소화
- 메모리 효율적

**단점:**
- SQL 직접 작성 필요
- 엔티티 콜백 미동작

### 3. Native Bulk Insert

```kotlin
fun insertWithNativeBulk(count: Int) {
    val entities = generateEntities(count)
    
    entities.chunked(500).forEach { chunk ->
        val values = chunk.joinToString(",") { tx ->
            "('${tx.approveDateTime}', ${tx.amount}, ...)"
        }
        
        entityManager.createNativeQuery(
            "INSERT INTO transaction (...) VALUES $values"
        ).executeUpdate()
    }
}
```

**내부 동작:**
```
네이티브 쿼리 실행
├── SQL 문자열 생성 (VALUES 절에 N개 row)
├── 단일 쿼리로 DB 전송
└── 한 번의 네트워크 왕복으로 완료
```

**장점:**
- 최소 네트워크 왕복
- 가장 빠른 속도
- DB 최적화 활용

**단점:**
- SQL Injection 주의 (문자열 이스케이프 필수)
- `max_allowed_packet` 제한
- 타입 안전성 없음

---

## 테스트 방법

### 1. 애플리케이션 실행

```bash
# Docker로 MySQL 실행
docker-compose up -d

# Spring Boot 실행
./gradlew bootRun
```

### 2. API 호출

```bash
# 전체 비교 (100, 1000, 10000건)
curl -X POST http://localhost:8080/api/v1/experiments/bulk-insert/compare-all

# 특정 건수만 비교
curl -X POST http://localhost:8080/api/v1/experiments/bulk-insert/compare/1000
```

### 3. 결과 확인

콘솔 로그에서 다음 형식으로 결과 출력:
```
╔════════════════════════════════════════════════════════════╗
║  📊 실험 결과 요약                                          ║
╚════════════════════════════════════════════════════════════╝

[ 100건 결과 ]
  1위: Native Bulk Insert
      시간: 45ms | 처리량: 2222/s | fastest
  2위: JdbcTemplate batchUpdate
      시간: 89ms | 처리량: 1124/s | 2.0x slower
  3위: JPA saveAll
      시간: 312ms | 처리량: 321/s | 6.9x slower
```

---

## 예상 결과

> ⚠️ 아래는 일반적인 벤치마크 기반 예상치입니다.
> 실제 결과는 환경에 따라 다를 수 있습니다.

### 성능 비교표

| 건수 | JPA saveAll | JdbcTemplate | Native Bulk | 1위 |
|------|-------------|--------------|-------------|-----|
| 100 | ~300ms | ~90ms | ~45ms | Native |
| 1,000 | ~2,500ms | ~400ms | ~150ms | Native |
| 10,000 | ~25,000ms | ~2,500ms | ~800ms | Native |

### 예상 처리량 (records/second)

```
              100건      1,000건     10,000건
Native     ████████████  ██████████  ████████████
           ~2,200/s      ~6,600/s    ~12,500/s

JdbcTemplate ████████    ████████    ████████
             ~1,100/s    ~2,500/s    ~4,000/s

saveAll    ███           ██          █
           ~320/s        ~400/s      ~400/s
```

### 상대 성능 비교

| 건수 | saveAll 대비 JdbcTemplate | saveAll 대비 Native |
|------|--------------------------|---------------------|
| 100 | **3.3x 빠름** | **6.7x 빠름** |
| 1,000 | **6.3x 빠름** | **16.7x 빠름** |
| 10,000 | **10x 빠름** | **31x 빠름** |

---

## 실제 테스트 결과

> 테스트 환경: MySQL 8.0 (Docker), `rewriteBatchedStatements=true`, `hibernate.jdbc.batch_size=50`

### 테스트 일시
- [x] 2026-02-17

### 100건 결과

| 순위 | 방법 | 시간(ms) | 처리량(/s) | 비고 |
|------|------|----------|-----------|------|
| 1 | JdbcTemplate batchUpdate | 10 | 10,000 | fastest |
| 2 | Native Bulk Insert | 47 | 2,128 | 4.7x slower |
| 3 | JPA saveAll | 237 | 422 | 23.7x slower |

### 1,000건 결과

| 순위 | 방법 | 시간(ms) | 처리량(/s) | 비고 |
|------|------|----------|-----------|------|
| 1 | JdbcTemplate batchUpdate | 34 | 29,412 | fastest |
| 2 | Native Bulk Insert | 250 | 4,000 | 7.4x slower |
| 3 | JPA saveAll | 732 | 1,366 | 21.5x slower |

### 10,000건 결과

| 순위 | 방법 | 시간(ms) | 처리량(/s) | 비고 |
|------|------|----------|-----------|------|
| 1 | JdbcTemplate batchUpdate | 354 | 28,249 | fastest |
| 2 | Native Bulk Insert | 2,751 | 3,635 | 7.8x slower |
| 3 | JPA saveAll | 5,585 | 1,791 | 15.8x slower |

### 예상과 다른 점

예상에서는 Native Bulk Insert가 1위였지만, 실측에서는 **JdbcTemplate batchUpdate가 모든 건수에서 1위**를 차지했다. `rewriteBatchedStatements=true` 옵션이 JdbcTemplate의 개별 INSERT를 multi-row INSERT로 재작성하므로, Native Bulk Insert와 동일한 네트워크 효율을 갖추면서도 PreparedStatement의 타입 안전성과 파라미터 바인딩 이점까지 유지하기 때문이다.

---

## 분석

### 왜 JPA saveAll이 느린가?

1. **1차 캐시 오버헤드**: 모든 엔티티를 메모리에 유지
2. **스냅샷 생성**: 더티체킹을 위한 원본 복사본 생성
3. **더티체킹**: flush 시 모든 엔티티 비교
4. **개별 쿼리 생성**: batch_size 설정해도 Hibernate 레벨 배치

```java
// Hibernate 내부적으로
for (Entity entity : entities) {
    session.persist(entity);     // 1차 캐시 저장
    snapshot.add(copy(entity));  // 스냅샷 저장
}
// flush 시
for (Entity entity : dirtyEntities) {
    generateInsertSQL(entity);   // SQL 생성
}
```

### 왜 JdbcTemplate이 실측에서 가장 빨랐나?

1. **PreparedStatement 재사용**: SQL 파싱/플랜 캐시 활용
2. **배치 실행**: `executeBatch()`로 JDBC 드라이버 레벨 최적화
3. **rewriteBatchedStatements 활성화**: MySQL 드라이버가 multi-row INSERT로 재작성
4. **문자열 조립 비용 없음**: Native 방식 대비 SQL 문자열 생성/이스케이프 오버헤드가 적음

### 왜 Native Bulk가 항상 가장 빠르지 않았나?

1. **문자열 생성 비용**: 대량 row를 문자열로 조합하는 CPU/메모리 오버헤드
2. **chunk 분할 실행**: 구현상 `chunked(500)`으로 여러 쿼리 실행 (완전 단일 쿼리 아님)
3. **이스케이프 처리 비용**: 문자열 컬럼마다 SQL escape 수행 필요

```kotlin
// 현재 구현: 500건 단위로 나눠 multi-row INSERT 실행
entities.chunked(500).forEach { chunk ->
    entityManager.createNativeQuery("INSERT INTO transaction (...) VALUES ...").executeUpdate()
}
```

---

## 언제 무엇을 사용해야 하나?

### JPA saveAll 사용

```
✅ 소량 데이터 (< 100건)
✅ 엔티티 콜백(@PrePersist 등) 필요
✅ 영속성 컨텍스트 관리 필요
✅ 비즈니스 로직과 밀접한 경우
```

### JdbcTemplate batchUpdate 사용

```
✅ 중간 규모 (100 ~ 10,000건)
✅ 타입 안전성 필요
✅ PreparedStatement 파라미터 바인딩
✅ 트랜잭션 관리 필요
```

### Native Bulk Insert 사용

```
✅ 대량 데이터 (> 10,000건)
✅ 최대 성능 필요
✅ 배치 작업, 마이그레이션
⚠️ SQL Injection 주의 필요
⚠️ max_allowed_packet 제한 고려
```

---

## 추가 최적화 팁

### 1. IDENTITY 전략 대신 SEQUENCE/TABLE

```kotlin
// IDENTITY: 배치 INSERT 불가 (각 INSERT 후 ID 조회 필요)
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
val id: Long? = null

// SEQUENCE: 배치 INSERT 가능 (ID 미리 할당)
@Id
@GeneratedValue(strategy = GenerationType.SEQUENCE)
val id: Long? = null
```

### 2. 인덱스 임시 비활성화

```sql
-- 대량 INSERT 전
ALTER TABLE transaction DISABLE KEYS;

-- INSERT 실행
INSERT INTO transaction ...

-- INSERT 후
ALTER TABLE transaction ENABLE KEYS;
```

### 3. LOAD DATA INFILE (최고 성능)

```sql
LOAD DATA INFILE '/path/to/data.csv'
INTO TABLE transaction
FIELDS TERMINATED BY ','
LINES TERMINATED BY '\n';
```

---

## 관련 소스 코드

```
src/main/kotlin/com/wisehero/springlabs/labs03/
├── BulkInsertExperimentService.kt    # 3가지 방식 구현
├── Lab03Controller.kt                # API 엔드포인트
└── dto/
    └── BulkInsertResult.kt           # 결과 DTO
```

## 참고 자료

- [Hibernate Batch Processing](https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html#batch)
- [MySQL rewriteBatchedStatements](https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-connp-props-performance-extensions.html)
- [Vlad Mihalcea: Batch Insert](https://vladmihalcea.com/how-to-batch-insert-and-update-statements-with-hibernate/)
