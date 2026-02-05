# Lab 06: Caffeine Cache 조회 성능 최적화

## 개요

Spring Cache 추상화 + Caffeine을 활용한 로컬 캐시의 동작 원리와 성능 최적화를 8개 실험으로 검증한다.

### 왜 캐시가 중요한가?

- **DB 부하 감소**: 반복 조회 쿼리를 메모리에서 즉시 반환
- **응답 시간 단축**: Network I/O + SQL 파싱 + 디스크 I/O를 제거
- **Connection Pool 보호**: 불필요한 DB 커넥션 점유 방지
- **실무 필수**: 상품 목록, 카테고리, 설정 값 등 읽기 비율이 높은 데이터에 필수

---

## 핵심 개념

### Spring Cache 추상화 계층 구조

```
@Cacheable / @CacheEvict / @CachePut  (어노테이션)
         ↓
   CacheInterceptor                    (AOP Advice)
         ↓
   CacheAspectSupport.execute()        (캐시 로직 핵심)
         ↓
   CacheManager → Cache               (추상화 인터페이스)
         ↓
   CaffeineCache → Caffeine Cache      (구현체)
```

### @Cacheable 내부 동작 흐름

```
1. 클라이언트 → 프록시(CGLIB) 호출
2. CacheInterceptor.invoke()
3. CacheAspectSupport.execute()
   3-1. 캐시 키 생성 (KeyGenerator 또는 SpEL)
   3-2. Cache.get(key) 호출
   3-3. Cache HIT → 캐시 값 즉시 반환 (메서드 실행 안 함!)
   3-4. Cache MISS → 실제 메서드 실행 → 결과를 Cache.put(key, value)
4. 결과 반환
```

**핵심 포인트**: Cache HIT 시 **메서드 본문이 실행되지 않는다**. 따라서 DB 접근이 완전히 차단된다.

### Caffeine Window TinyLFU 알고리즘

Caffeine은 최신 퇴거 알고리즘인 **Window TinyLFU**를 사용한다:

```
새 항목 → [Admission Window (1%)] → [Probationary Segment] → [Protected Segment]
                                           ↓                        ↓
                                    접근 빈도 낮음 → 퇴거        접근 빈도 높음 → 유지
```

| 구역 | 비율 | 역할 |
|------|------|------|
| Admission Window | ~1% | 새 항목의 임시 저장소 |
| Probationary Segment | ~20% | 접근 빈도를 관찰하는 대기 구역 |
| Protected Segment | ~80% | 자주 접근되는 핫 데이터 |

- **TinyLFU**: 4-bit CountMinSketch로 접근 빈도를 O(1) 공간에 추적
- **퇴거 결정**: 새 항목 vs 퇴거 후보의 빈도를 비교하여 결정
- **장점**: LRU보다 높은 hit rate, LFU보다 낮은 메모리 오버헤드

### 캐시 어노테이션 비교

| 어노테이션 | 메서드 실행 | 캐시 동작 | 사용 시점 |
|-----------|-----------|----------|----------|
| `@Cacheable` | Cache MISS일 때만 | 결과를 캐시에 저장 | 조회 |
| `@CachePut` | **항상 실행** | 결과를 캐시에 덮어쓰기 | 업데이트 후 즉시 갱신 |
| `@CacheEvict` | 선택적 | 캐시에서 키 삭제 | 업데이트/삭제 후 무효화 |

---

## 캐시 설정 (`CacheConfig.kt`)

| 캐시 이름 | 용도 | maxSize | TTL | recordStats |
|-----------|------|---------|-----|-------------|
| `productCache` | 실험 6-1, 6-2, 6-5, 6-6 | 1000 | 10분 | O |
| `ttlCache` | 실험 6-3: TTL 만료 | - | 3초 | O |
| `smallCache` | 실험 6-4: Size Eviction | 5 | - | O |
| `stampedeCache` | 실험 6-7: Stampede | - | 2초 | O |
| `conditionalCache` | 실험 6-8: 조건부 | 100 | 5분 | O |

`SimpleCacheManager`로 각 `CaffeineCache` 인스턴스를 개별 등록하여, 캐시별 독립적인 정책을 적용한다.

---

## 실험 목록

| 실험 | 이름 | Method | 핵심 관찰 포인트 |
|------|------|--------|-----------------|
| 6-1 | 캐시 없음 vs @Cacheable | POST | SQL 100회 → 1회 감소 |
| 6-2 | Cache Hit/Miss 통계 | POST | hitCount, missCount, hitRate |
| 6-3 | TTL 만료와 DB 재조회 | POST | 만료 전 SQL 0, 만료 후 SQL 1 |
| 6-4 | Size-based Eviction | POST | Window TinyLFU 퇴거 관찰 |
| 6-5 | @CacheEvict로 캐시 무효화 | POST | evict 후 cache miss 발생 |
| 6-6 | @CachePut vs @CacheEvict | POST | 즉시 갱신 vs 지연 로드 |
| 6-7 | Cache Stampede | POST | TTL 만료 후 동시 DB 폭주 |
| 6-8 | 조건부 캐싱 | POST | null 결과 캐싱 방지 |

---

## 실험 상세

### 실험 6-1: 캐시 없음 vs @Cacheable

- **엔드포인트**: `POST /api/v1/experiments/cache/6-1/cache-vs-no-cache`
- **동작**: 같은 Product를 100번 조회하여 캐시 유무에 따른 SQL 횟수 비교

**내부 동작 원리**:

```
[캐시 없음]
Client → Proxy → Service.findProductByIdNoCache()
                      ↓
                EntityManager.find() → SQL SELECT 실행 (매번!)
                      ↓
                100회 반복 → SQL 100회

[캐시 적용]
Client → Proxy → CacheInterceptor
                      ↓
              1회차: Cache MISS → Service 실행 → SQL 1회 → Cache.put()
              2회차: Cache HIT → 캐시에서 즉시 반환 (Service 실행 안 함!)
              ...
              100회차: Cache HIT → 캐시에서 즉시 반환
                      ↓
                100회 반복 → SQL 1회
```

- **예상 결과**: 캐시 미적용 SQL 100회, 캐시 적용 SQL 1회
- **의미**: `@Cacheable`의 AOP 프록시가 메서드 실행 자체를 차단하여 DB 접근을 근본적으로 제거

### 실험 6-2: Cache Hit/Miss 통계

- **엔드포인트**: `POST /api/v1/experiments/cache/6-2/cache-statistics`
- **동작**: 5개 상품을 3회씩 조회하여 Caffeine 통계 수집

**내부 동작 원리**:

```
Caffeine.newBuilder().recordStats() 활성화 시:
  → ConcurrentStatsCounter 인스턴스 생성
  → 모든 캐시 연산에 atomic counter 업데이트

get() 호출 시:
  HIT  → statsCounter.recordHits(1)    // AtomicLongArray 증가
  MISS → statsCounter.recordMisses(1)  // AtomicLongArray 증가

stats() 호출 시:
  → CacheStats 스냅샷 반환 (hitCount, missCount, loadTime, evictionCount, ...)
```

- **예상 결과**: 1차 조회 miss 5, 2~3차 조회 hit 10, hitRate ≈ 66.7%
- **의미**: `recordStats()`는 lock-free atomic counter 기반이라 성능 오버헤드가 극히 작음

### 실험 6-3: TTL 만료와 DB 재조회

- **엔드포인트**: `POST /api/v1/experiments/cache/6-3/ttl-expiration`
- **동작**: TTL 3초 캐시에서 만료 전/후 SQL 발생 패턴 확인

**내부 동작 원리**:

```
expireAfterWrite(3, SECONDS) 설정 시:
  → 각 엔트리에 writeTime 기록
  → get() 시점에 (currentTime - writeTime > TTL) 체크

TTL 만료 후 get() 호출:
  1. BoundedLocalCache.afterRead() → expireEntries()
  2. 만료된 엔트리 발견 → 즉시 제거 (lazy expiration)
  3. Cache MISS 반환 → CacheAspectSupport가 메서드 실행
  4. DB 조회 → 새 값 캐시에 저장 (writeTime 갱신)
```

**주의**: Caffeine의 만료는 **lazy** 방식이다. 백그라운드 스레드가 아니라 **다음 접근 시점**에 만료를 확인한다.
(단, `Caffeine.scheduler()`를 설정하면 능동적 만료도 가능)

- **예상 결과**: 만료 전 SQL 0회, 3.5초 대기 후 SQL 1회
- **의미**: TTL 만료는 캐시 freshness와 DB 부하의 트레이드오프

### 실험 6-4: Size-based Eviction

- **엔드포인트**: `POST /api/v1/experiments/cache/6-4/size-eviction`
- **동작**: maxSize=5 캐시에 10개 항목 삽입 후 퇴거 관찰

**내부 동작 원리**:

```
maximumSize(5) 설정 시:
  → Window TinyLFU 퇴거 정책 활성화
  → Admission Window(~1%) + Main Cache(~99%) 구조

10개 순차 삽입 시:
  1~5번째: 정상 삽입 (maxSize 미도달)
  6번째~: eviction 발생
    → TinyLFU가 새 항목의 빈도 vs 퇴거 후보의 빈도 비교
    → 빈도가 낮은 쪽을 퇴거

주의: Caffeine의 eviction은 비동기!
  → cleanUp() 호출로 강제 동기 퇴거 가능
  → estimatedSize()는 정확한 값이 아닌 추정치
```

- **예상 결과**: 10개 삽입 후 evictionCount ≈ 5, remainingKeys ≈ 5
- **의미**: Caffeine의 Window TinyLFU는 접근 빈도 기반으로 퇴거하여 LRU보다 높은 hit rate 보장

### 실험 6-5: @CacheEvict로 캐시 무효화

- **엔드포인트**: `POST /api/v1/experiments/cache/6-5/cache-evict`
- **동작**: 캐시 워밍업 → evict → 재조회로 무효화 확인

**내부 동작 원리**:

```
@CacheEvict(value = "productCache", key = "#id") 호출 시:
  1. CacheInterceptor.invoke()
  2. CacheAspectSupport.execute()
     → processCacheEvicts() 호출
     → Cache.evict(key) → CaffeineCache.evict(key)
       → com.github.benmanes.caffeine.cache.Cache.invalidate(key)
  3. (beforeInvocation=false일 때) 메서드 실행 후 evict

이후 @Cacheable 호출 시:
  → Cache.get(key) → null (evict됨) → MISS
  → 메서드 실행 → DB 조회 → Cache.put(key, value)
```

- **예상 결과**: evict 전 SQL 0회, evict 후 SQL 1회
- **의미**: `@CacheEvict`는 stale data를 방지하는 가장 기본적인 캐시 무효화 전략

### 실험 6-6: @CachePut vs @CacheEvict

- **엔드포인트**: `POST /api/v1/experiments/cache/6-6/put-vs-evict`
- **동작**: 업데이트 시 두 캐시 갱신 전략의 SQL 횟수 비교

**내부 동작 원리**:

```
[@CachePut 전략]
  업데이트 호출 → 메서드 항상 실행 (SQL 1) → 결과를 캐시에 덮어쓰기
  재조회 → Cache HIT (SQL 0) → 즉시 반환
  총 SQL: 1회

[@CacheEvict 전략]
  업데이트 호출 → 캐시에서 키 삭제 (SQL 0)
  재조회 → Cache MISS (SQL 1) → DB 조회 → 캐시 저장
  총 SQL: 1회

비교:
  CachePut  = 업데이트 시점에 DB 1회 → 이후 읽기 무료
  CacheEvict = 업데이트 시 DB 0회 → 첫 읽기 시 DB 1회 (lazy)
```

| 전략 | 업데이트 시 SQL | 재조회 시 SQL | 장점 | 단점 |
|------|----------------|-------------|------|------|
| @CachePut | 1 (항상 실행) | 0 | 즉시 일관성 | 불필요한 실행 가능 |
| @CacheEvict | 0 | 1 (lazy) | 단순함 | 첫 읽기 지연 |

- **의미**: 읽기 빈도가 높으면 `@CachePut`, 업데이트 빈도가 높으면 `@CacheEvict`가 유리

### 실험 6-7: Cache Stampede (Thundering Herd)

- **엔드포인트**: `POST /api/v1/experiments/cache/6-7/cache-stampede`
- **동작**: TTL 만료 후 100 스레드 동시 조회로 DB 폭주 관찰

**내부 동작 원리**:

```
TTL 만료 시점의 동시 요청 시나리오:

  Thread-1 → Cache.get(key) → MISS → DB 조회 시작...
  Thread-2 → Cache.get(key) → MISS → DB 조회 시작...  (Thread-1 아직 진행 중)
  Thread-3 → Cache.get(key) → MISS → DB 조회 시작...
  ...
  Thread-100 → Cache.get(key) → MISS → DB 조회 시작...

  → 100개 스레드 모두 동시에 같은 SQL 실행!
  → Connection Pool 100개 커넥션 동시 점유
  → DB CPU/IO 급증
```

**Cache Stampede의 위험성**:
- HikariCP의 `maximum-pool-size`를 초과하면 ConnectionTimeoutException 발생
- MySQL의 `max_connections`에 도달하면 "Too many connections" 에러
- 대규모 트래픽 환경에서 DB 장애의 주요 원인

**해결 방법** (실험에서는 문제만 관찰):
1. **Lock-based**: 첫 번째 스레드만 DB 조회, 나머지 대기 (`Caffeine.build(key -> loadFromDB)`)
2. **Probabilistic Early Expiration**: TTL 만료 전에 확률적으로 갱신
3. **Background Refresh**: 별도 스레드가 TTL 만료 전에 미리 갱신

- **예상 결과**: SQL ~100회 (100 스레드 모두 DB 직행)
- **의미**: 단순 `@Cacheable`만으로는 Stampede를 방지할 수 없음

### 실험 6-8: 조건부 캐싱

- **엔드포인트**: `POST /api/v1/experiments/cache/6-8/conditional-caching`
- **동작**: `unless = "#result == null"`로 null 결과 캐싱 방지

**내부 동작 원리**:

```
@Cacheable(unless = "#result == null") 처리 흐름:

  1. CacheAspectSupport.execute()
  2. Cache.get(key) → MISS
  3. 메서드 실행 → 결과 획득 (result)
  4. unless 조건 평가 (SpEL):
     → SpelExpressionParser가 "#result == null" 파싱
     → StandardEvaluationContext에 result 변수 바인딩
     → Expression.getValue() → true/false

  결과가 null이 아닌 경우:
     unless = false → Cache.put(key, value) → 캐시 저장
  결과가 null인 경우:
     unless = true → 캐시 저장 건너뜀!
```

**Negative Caching 문제**:
- `unless` 없이 null을 캐시하면, 존재하지 않는 ID 조회가 영구적으로 캐시됨
- 나중에 해당 ID의 데이터가 생성되어도 캐시된 null이 반환됨
- `unless = "#result == null"`로 이 문제를 방지

- **예상 결과**: 존재하는 ID → SQL 1회 (이후 hit), 존재하지 않는 ID → SQL 5회 (매번 miss)
- **의미**: 조건부 캐싱으로 Negative Cache를 방지하되, 존재하지 않는 키가 많으면 DB 부하 주의

---

## 정리

### Best Practices

| 항목 | 권장사항 |
|------|---------|
| 캐시 대상 | 읽기 빈도 높고 변경 빈도 낮은 데이터 (상품, 카테고리, 설정) |
| TTL 설정 | 데이터 특성에 맞게 (설정값: 길게, 재고: 짧게 또는 evict) |
| maxSize | 메모리 예산 내에서 hit rate 모니터링하며 조정 |
| 무효화 전략 | 쓰기 시 `@CacheEvict`, 읽기 우선이면 `@CachePut` |
| Stampede 방지 | `Caffeine.build(CacheLoader)` 또는 분산 락 사용 |
| Negative Cache | `unless = "#result == null"` 필수 |
| 모니터링 | `recordStats()` + Micrometer로 hit rate/eviction 추적 |
| Self-invocation | 같은 클래스 내 호출은 `@Lazy self` 패턴 필수 |

### Self-invocation 함정

```kotlin
// ❌ 캐시 동작 안 함 (프록시를 통하지 않음)
fun someMethod() {
    val result = findProductByIdCached(id)  // this.findProductByIdCached()
}

// ✅ 캐시 정상 동작 (프록시를 통해 호출)
@Lazy @Autowired private lateinit var self: CacheExperimentService
fun someMethod() {
    val result = self.findProductByIdCached(id)  // proxy.findProductByIdCached()
}
```

Spring AOP는 **CGLIB 프록시**를 통해 동작하므로, `this.method()`로 호출하면 프록시를 우회하여 `@Cacheable`이 적용되지 않는다.

### 실무 적용 가이드

1. **단일 서버**: Caffeine (로컬 캐시) → 가장 빠르고 간단
2. **다중 서버**: Redis (분산 캐시) → 서버 간 일관성 보장
3. **하이브리드**: Caffeine (L1) + Redis (L2) → 최적의 성능과 일관성

### 참고 자료

- [Spring Cache Abstraction 공식 문서](https://docs.spring.io/spring-framework/reference/integration/cache.html)
- [Caffeine GitHub](https://github.com/ben-manes/caffeine)
- [Caffeine Wiki - Design](https://github.com/ben-manes/caffeine/wiki/Design)
- [Window TinyLFU 논문](https://arxiv.org/abs/1512.00727)
