# Lab 02: @Transactional(readOnly=true) 실제 효과

## 개요

`@Transactional(readOnly = true)`가 실제로 무엇을 하는지, 어떤 최적화가 적용되는지 실험합니다.

## 핵심 개념

### readOnly=true가 하는 일

| 레이어 | 효과 | 설명 |
|--------|------|------|
| **Spring** | 트랜잭션 힌트 설정 | `TransactionDefinition.isReadOnly()` = true |
| **Hibernate** | FlushMode 변경 | `AUTO` → `MANUAL` |
| **Hibernate** | 더티체킹 스킵 | 스냅샷 비교 생략 가능 |
| **JDBC** | Connection 힌트 | `connection.setReadOnly(true)` |
| **DB** | Read Replica 라우팅 | 일부 드라이버/프록시에서 지원 |

### 흔한 오해

| 오해 | 실제 |
|------|------|
| "persist() 호출 불가" | ❌ 호출 가능 (flush 시점에 문제) |
| "수정하면 예외 발생" | ❌ 예외 없음 (flush 안 될 수 있음) |
| "DB 레벨 읽기 전용" | ❌ DB마다 다름, 보장 안 됨 |

## 실험 코드

### 위치
```
src/main/kotlin/com/wisehero/springlabs/labs02/ReadOnlyExperimentService.kt
```

### 실험 A: readOnly 상태 확인

```kotlin
@Transactional(readOnly = true)
fun experimentReadOnlyStatus(): Map<String, Any?> {
    // Spring 레벨
    val txReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly()
    
    // Hibernate 레벨
    val session = entityManager.unwrap(Session::class.java)
    val flushMode = session.hibernateFlushMode
    val defaultReadOnly = session.isDefaultReadOnly
    
    return mapOf(
        "tx_readonly" to txReadOnly,           // true
        "hibernate_flush_mode" to flushMode,   // MANUAL
        "session_default_readonly" to defaultReadOnly  // true
    )
}
```

### 실험 B: readOnly에서 수정 시도

```kotlin
@Transactional(readOnly = true)
fun experimentReadOnlyWithModification(transactionId: Long): Map<String, Any?> {
    val result = mutableMapOf<String, Any?>()
    val transaction = transactionRepository.findById(transactionId).orElse(null)
        ?: return mapOf("error" to "Transaction not found")
    val originalAmount = transaction.amount
    result["original_amount"] = originalAmount
    
    // readOnly 트랜잭션에서 JPQL UPDATE 시도
    try {
        val updated = entityManager.createQuery(
            "UPDATE Transaction t SET t.amount = :newAmount WHERE t.id = :id"
        )
            .setParameter("newAmount", BigDecimal("99999.99"))
            .setParameter("id", transactionId)
            .executeUpdate()
        
        result["jpql_update"] = "성공 (${updated}건)"
    } catch (e: Exception) {
        result["jpql_update"] = "실패: ${e.javaClass.simpleName}"
    }

    entityManager.clear()
    val dbAmount = transactionRepository.findById(transactionId).orElse(null)?.amount
    result["db_amount_after"] = dbAmount
    result["amount_changed"] = dbAmount != originalAmount
    return result
}
```

### 실험 C: readOnly 성능 비교

```kotlin
@Transactional(readOnly = true)
fun experimentReadOnlyPerformance(): Map<String, Any?> {
    val startTime = System.currentTimeMillis()
    val transactions = transactionRepository.findAll()
    val fetchTime = System.currentTimeMillis() - startTime
    
    return mapOf(
        "readOnly" to true,
        "count" to transactions.size,
        "fetch_time_ms" to fetchTime,
        "flush_mode" to session.hibernateFlushMode  // MANUAL
    )
}

@Transactional(readOnly = false)
fun experimentWritablePerformance(): Map<String, Any?> {
    // 동일한 로직, readOnly=false
    // flush_mode는 AUTO
}
```

### 실험 D: readOnly에서 persist 시도

```kotlin
@Transactional(readOnly = true)
fun experimentReadOnlyWithPersist(): Map<String, Any?> {
    val newTransaction = Transaction(
        approveDateTime = LocalDateTime.now(),
        amount = BigDecimal("99999.99"),
        // ...
    )
    
    try {
        entityManager.persist(newTransaction)  // ✅ 성공!
        entityManager.flush()  // ❓ 결과는?
    } catch (e: Exception) {
        // 어떤 예외가 발생하나?
    }
}
```

### 실험 E: readOnly 메모리 사용량 비교

```kotlin
@Transactional(readOnly = true)
fun experimentReadOnlyMemory(): Map<String, Any?> {
    val runtime = Runtime.getRuntime()

    entityManager.clear()
    System.gc()
    Thread.sleep(100)
    val memoryBefore = runtime.totalMemory() - runtime.freeMemory()

    val transactions = transactionRepository.findAll()

    val memoryAfter = runtime.totalMemory() - runtime.freeMemory()
    val memoryDelta = memoryAfter - memoryBefore

    return mapOf(
        "readOnly" to true,
        "entity_count" to transactions.size,
        "memory_before_mb" to memoryBefore / 1024.0 / 1024.0,
        "memory_after_mb" to memoryAfter / 1024.0 / 1024.0,
        "memory_delta_mb" to memoryDelta / 1024.0 / 1024.0
    )
}

@Transactional(readOnly = false)
fun experimentWritableMemory(): Map<String, Any?> {
    // 동일한 로직, readOnly=false
    // 스냅샷 저장으로 인한 추가 메모리 사용
}
```

## 테스트 방법

### API 호출

```bash
# 실험 A: 상태 확인
curl http://localhost:8080/api/v1/experiments/readonly-status

# 실험 B: 수정 시도
curl http://localhost:8080/api/v1/experiments/readonly-modify

# 실험 C: 성능 비교
curl http://localhost:8080/api/v1/experiments/readonly-performance

# 실험 D: persist 시도
curl http://localhost:8080/api/v1/experiments/readonly-persist

# 실험 E: 메모리 비교
curl http://localhost:8080/api/v1/experiments/readonly-memory
```

### 예상 결과

**실험 A (상태 확인):**
```json
{
  "data": {
    "tx_readonly": true,
    "hibernate_flush_mode": "MANUAL",
    "session_default_readonly": true
  }
}
```

**실험 C (성능 비교):**
```json
{
  "data": {
    "readOnly_true": {
      "flush_mode": "MANUAL",
      "fetch_time_ms": 45
    },
    "readOnly_false": {
      "flush_mode": "AUTO",
      "fetch_time_ms": 52
    },
    "time_difference_ms": 7
  }
}
```

**실험 E (메모리 비교):**
```json
{
  "data": {
    "readOnly_true": {
      "memory_delta_mb": 12.45,
      "flush_mode": "MANUAL",
      "entity_count": 10000
    },
    "readOnly_false": {
      "memory_delta_mb": 18.72,
      "flush_mode": "AUTO",
      "entity_count": 10000
    },
    "memory_saved_mb": 6.27,
    "snapshot_overhead_explanation": "readOnly=false는 더티체킹을 위해 각 엔티티의 스냅샷 복사본을 저장하므로 추가 메모리를 사용합니다."
  }
}
```

## FlushMode 상세

### FlushMode.AUTO (기본값, readOnly=false)

```
1. 쿼리 실행 전 자동 flush
2. 트랜잭션 커밋 전 자동 flush
3. 더티체킹 수행 (스냅샷 비교)
```

### FlushMode.MANUAL (readOnly=true)

```
1. 자동 flush 안 함
2. 명시적 flush() 호출해야 반영
3. 커밋 시에도 자동 flush 안 함!
```

### 성능 영향

```
[Entity 조회]
    ↓
[1차 캐시 저장] ← 여기까지는 동일
    ↓
[스냅샷 저장] ← readOnly=true면 스킵 가능!
    ↓
[더티체킹] ← readOnly=true면 스킵!
    ↓
[flush] ← readOnly=true면 스킵!
```

### 메모리 영향

```
[Entity 조회]
    ↓
[1차 캐시 저장] ← 양쪽 모두 동일
    ↓
[스냅샷 복사본 저장] ← readOnly=true면 스킵! (메모리 절약 핵심)
    ↓
    readOnly=false: 엔티티 수 × 엔티티 크기만큼 추가 힙 메모리 사용
    readOnly=true:  스냅샷 없음 → 절반 가까이 메모리 절약 가능
```

> ⚠️ **주의**: `System.gc()`는 JVM에 대한 힌트일 뿐 보장이 아닙니다.
> 정밀한 메모리 측정이 필요하면 VisualVM, JFR, 또는 `-verbose:gc` 옵션을 사용하세요.

## 주의사항

### 1. readOnly=true여도 수정 가능!

```kotlin
@Transactional(readOnly = true)
fun danger() {
    val entity = repository.findById(1).get()
    entity.name = "Modified"  // 변경됨!
    
    // 트랜잭션 종료 시:
    // - FlushMode=MANUAL이므로 자동 flush 안 됨
    // - 하지만 명시적 flush() 하면 반영될 수 있음!
}
```

### 2. Native Query는 flush 트리거할 수 있음

```kotlin
@Transactional(readOnly = true)
fun nativeQueryDanger() {
    val entity = repository.findById(1).get()
    entity.name = "Modified"
    
    // Native query 실행 시 Hibernate가 flush할 수 있음!
    entityManager.createNativeQuery("SELECT 1").resultList
}
```

### 3. DB별 동작 차이

| DB | readOnly 지원 | 효과 |
|----|--------------|------|
| MySQL | ⚠️ 제한적 | 힌트만 전달, 강제 안 됨 |
| PostgreSQL | ✅ | `SET TRANSACTION READ ONLY` |
| Oracle | ✅ | 읽기 전용 트랜잭션 |

## Best Practices

### 1. 조회 전용 서비스에 적용

```kotlin
@Service
@Transactional(readOnly = true)  // 클래스 레벨 기본값
class QueryService {
    
    fun findAll() = repository.findAll()
    
    fun findById(id: Long) = repository.findById(id)
    
    @Transactional  // 쓰기 메서드만 오버라이드
    fun save(entity: Entity) = repository.save(entity)
}
```

### 2. 성능 최적화 체크리스트

```kotlin
@Transactional(readOnly = true)
fun optimizedQuery(): List<Entity> {
    // ✅ 대량 조회 시 readOnly 사용
    // ✅ DTO 프로젝션 사용 (엔티티 대신)
    // ✅ 불필요한 연관관계 로딩 방지
    // ✅ 페이징 적용
}
```

### 3. Read Replica 라우팅 (고급)

```kotlin
// DataSource 라우팅 설정 필요
@Transactional(readOnly = true)  // → Read Replica로 라우팅
fun queryFromReplica() { }

@Transactional(readOnly = false)  // → Primary로 라우팅
fun writeToMaster() { }
```

## 결론

| 설정 | FlushMode | 더티체킹 | 자동 flush | 메모리 | 성능 |
|------|-----------|---------|-----------|--------|------|
| `readOnly=false` | AUTO | ✅ 수행 | ✅ | 스냅샷 저장 (추가 사용) | 기본 |
| `readOnly=true` | MANUAL | ❌ 스킵 | ❌ | 스냅샷 생략 (절약) | 최적화 |

**핵심 포인트:**
- `readOnly=true`는 **힌트**일 뿐, 강제가 아님
- 주요 효과는 **FlushMode 변경**과 **더티체킹 스킵**
- 조회 전용 로직에 적극 활용하면 성능 향상
- 대량 조회 시 **메모리 절약** 효과 (스냅샷 복사본 생략)

## 참고 자료

- [Hibernate FlushMode Documentation](https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html#flushing)
- [Spring @Transactional readOnly](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/annotation/Transactional.html#readOnly())
- [Vlad Mihalcea: Read-Only Transactions](https://vladmihalcea.com/read-only-transactions/)
