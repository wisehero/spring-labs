package com.wisehero.springlabs.labs06

import com.github.benmanes.caffeine.cache.Cache
import com.wisehero.springlabs.entity.Product
import com.wisehero.springlabs.labs06.dto.CacheResult
import com.wisehero.springlabs.labs06.dto.CachedProduct
import com.wisehero.springlabs.repository.ProductRepository
import jakarta.persistence.EntityManager
import org.hibernate.SessionFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * ==========================================
 * Lab 06: Caffeine Cache 조회 성능 최적화 실험
 * ==========================================
 *
 * Spring Cache 추상화 + Caffeine을 활용한 로컬 캐시의
 * 동작 원리와 성능 최적화를 8개 실험으로 검증합니다.
 *
 * 실험 목록:
 * 6-1: 캐시 없음 vs @Cacheable
 * 6-2: Cache Hit/Miss 통계
 * 6-3: TTL 만료와 DB 재조회
 * 6-4: Size-based Eviction
 * 6-5: @CacheEvict로 캐시 무효화
 * 6-6: @CachePut vs @CacheEvict
 * 6-7: Cache Stampede (Thundering Herd)
 * 6-8: 조건부 캐싱
 */
@Service
class CacheExperimentService(
    private val productRepository: ProductRepository,
    private val entityManager: EntityManager,
    private val cacheManager: CacheManager
) {

    @Lazy @Autowired
    private lateinit var self: CacheExperimentService

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val TEST_PREFIX = "CACHE-"
    }

    // ==========================================
    // 공통 유틸리티
    // ==========================================

    /**
     * 테스트용 Product 엔티티를 생성하고 즉시 DB에 반영한다.
     * saveAndFlush로 영속성 컨텍스트 → DB 동기화까지 보장.
     */
    private fun createTestProduct(name: String, stock: Int = 100): Product {
        return productRepository.saveAndFlush(Product(name = name, stock = stock))
    }

    /**
     * JPA EntityManagerFactory에서 Hibernate SessionFactory를 추출한다.
     * Hibernate Statistics API에 접근하기 위해 필요.
     */
    private fun getSessionFactory(): SessionFactory {
        return entityManager.entityManagerFactory.unwrap(SessionFactory::class.java)
    }

    /**
     * Hibernate Statistics의 모든 카운터(SQL 실행 횟수, 엔티티 로드 수 등)를 0으로 초기화한다.
     * 실험 구간별 SQL 측정 전에 호출하여 측정 구간을 격리한다.
     */
    private fun clearStatistics() {
        getSessionFactory().statistics.clear()
    }

    /**
     * 마지막 clearStatistics() 이후 실행된 SQL PreparedStatement 수를 반환한다.
     * queryExecutionCount는 JPQL/HQL/Criteria만 추적하고 EntityManager.find()는 포함하지 않으므로,
     * em.find() 포함 모든 SQL을 추적하는 prepareStatementCount를 사용한다.
     */
    private fun getQueryCount(): Long {
        return getSessionFactory().statistics.prepareStatementCount
    }

    /**
     * 지정한 Caffeine 캐시의 hit/miss/eviction 등 통계를 반환한다.
     * CacheConfig에서 recordStats()를 활성화해야 동작한다.
     */
    private fun getCaffeineStats(cacheName: String): com.github.benmanes.caffeine.cache.stats.CacheStats {
        val cache = cacheManager.getCache(cacheName) as CaffeineCache
        return (cache.nativeCache as Cache<*, *>).stats()
    }

    /**
     * 지정한 캐시의 모든 엔트리를 제거한다.
     * 실험 간 캐시 상태 격리를 위해 실험 시작 시 호출.
     */
    private fun clearCache(cacheName: String) {
        cacheManager.getCache(cacheName)?.clear()
    }

    /**
     * CacheManager에 등록된 모든 캐시를 일괄 초기화한다.
     * cleanupTestData()에서 테스트 데이터 삭제와 함께 호출.
     */
    private fun clearAllCaches() {
        cacheManager.cacheNames.forEach { cacheManager.getCache(it)?.clear() }
    }

    // ==========================================
    // @Cacheable 메서드들 (AOP 프록시 통과 필수)
    // ==========================================

    @Cacheable(value = ["productCache"], key = "#id")
    @Transactional(readOnly = true)
    fun findProductByIdCached(id: Long): CachedProduct? {
        log.debug("[Cache] productCache MISS → DB 조회: id=$id")
        return productRepository.findById(id).orElse(null)?.let { CachedProduct.from(it) }
    }

    @Transactional(readOnly = true)
    fun findProductByIdNoCache(id: Long): Product? {
        return productRepository.findById(id).orElse(null)
    }

    @Cacheable(value = ["ttlCache"], key = "#id")
    @Transactional(readOnly = true)
    fun findProductByIdWithTtl(id: Long): CachedProduct? {
        log.debug("[Cache] ttlCache MISS → DB 조회: id=$id")
        return productRepository.findById(id).orElse(null)?.let { CachedProduct.from(it) }
    }

    @Cacheable(value = ["smallCache"], key = "#id")
    @Transactional(readOnly = true)
    fun findProductByIdSmallCache(id: Long): CachedProduct? {
        log.debug("[Cache] smallCache MISS → DB 조회: id=$id")
        return productRepository.findById(id).orElse(null)?.let { CachedProduct.from(it) }
    }

    @CacheEvict(value = ["productCache"], key = "#id")
    fun evictProductCache(id: Long) {
        log.info("[Cache] productCache 키 삭제: id=$id")
    }

    @CachePut(value = ["productCache"], key = "#id")
    @Transactional(readOnly = true)
    fun updateProductCacheWithPut(id: Long): CachedProduct? {
        log.debug("[Cache] productCache CachePut → DB 조회 후 캐시 갱신: id=$id")
        return productRepository.findById(id).orElse(null)?.let { CachedProduct.from(it) }
    }

    @Cacheable(value = ["stampedeCache"], key = "#id")
    @Transactional(readOnly = true)
    fun findProductByIdStampedeCache(id: Long): CachedProduct? {
        log.debug("[Cache] stampedeCache MISS → DB 조회: id=$id")
        return productRepository.findById(id).orElse(null)?.let { CachedProduct.from(it) }
    }

    @Cacheable(value = ["conditionalCache"], key = "#id", unless = "#result == null")
    @Transactional(readOnly = true)
    fun findProductByIdConditional(id: Long): CachedProduct? {
        log.debug("[Cache] conditionalCache 조회: id=$id")
        return productRepository.findById(id).orElse(null)?.let { CachedProduct.from(it) }
    }

    // ==========================================
    // 실험 6-1: 캐시 없음 vs @Cacheable
    // ==========================================

    fun experiment6_1_cacheVsNoCache(): CacheResult {
        val product = createTestProduct("${TEST_PREFIX}6-1-CACHE-TEST")
        val productId = product.id!!
        val queryCount = 100

        log.info("[6-1] 상품 생성: id=$productId, 조회 횟수=$queryCount")

        // A: 캐시 없이 100번 조회
        clearStatistics()
        val noCacheStart = System.currentTimeMillis()
        for (i in 1..queryCount) {
            self.findProductByIdNoCache(productId)
        }
        val noCacheDuration = System.currentTimeMillis() - noCacheStart
        val sqlCountNoCache = getQueryCount()
        log.info("[6-1] 캐시 없음: SQL ${sqlCountNoCache}회, ${noCacheDuration}ms")

        // B: @Cacheable로 100번 조회
        clearCache("productCache")
        clearStatistics()
        val cachedStart = System.currentTimeMillis()
        for (i in 1..queryCount) {
            self.findProductByIdCached(productId)
        }
        val cachedDuration = System.currentTimeMillis() - cachedStart
        val sqlCountCached = getQueryCount()
        log.info("[6-1] 캐시 적용: SQL ${sqlCountCached}회, ${cachedDuration}ms")

        val stats = getCaffeineStats("productCache")

        return CacheResult.cacheVsNoCache(
            sqlCountNoCache = sqlCountNoCache,
            sqlCountCached = sqlCountCached,
            durationNoCacheMs = noCacheDuration,
            durationCachedMs = cachedDuration,
            details = mapOf(
                "productId" to productId,
                "queryCount" to queryCount,
                "cacheHitCount" to stats.hitCount(),
                "cacheMissCount" to stats.missCount(),
                "speedupRatio" to if (cachedDuration > 0) "%.1fx".format(noCacheDuration.toDouble() / cachedDuration) else "N/A"
            )
        )
    }

    // ==========================================
    // 실험 6-2: Cache Hit/Miss 통계
    // ==========================================

    fun experiment6_2_cacheStatistics(): CacheResult {
        clearCache("productCache")

        // 5개 상품 생성
        val products = (1..5).map { createTestProduct("${TEST_PREFIX}6-2-STATS-$it") }
        val productIds = products.map { it.id!! }

        log.info("[6-2] 상품 ${products.size}개 생성, 캐시 통계 수집 시작")

        // 1차 조회: 모두 miss
        for (id in productIds) {
            self.findProductByIdCached(id)
        }
        log.info("[6-2] 1차 조회 완료 (모두 miss)")

        // 2차 조회: 모두 hit
        for (id in productIds) {
            self.findProductByIdCached(id)
        }
        log.info("[6-2] 2차 조회 완료 (모두 hit)")

        // 3차 조회: 반복 hit
        for (id in productIds) {
            self.findProductByIdCached(id)
        }
        log.info("[6-2] 3차 조회 완료 (모두 hit)")

        val stats = getCaffeineStats("productCache")
        log.info("[6-2] 통계: hit=${stats.hitCount()}, miss=${stats.missCount()}, hitRate=${stats.hitRate()}, eviction=${stats.evictionCount()}")

        return CacheResult.cacheStatistics(
            hitCount = stats.hitCount(),
            missCount = stats.missCount(),
            hitRate = stats.hitRate(),
            evictionCount = stats.evictionCount(),
            details = mapOf(
                "productIds" to productIds,
                "totalRequests" to stats.requestCount(),
                "loadCount" to stats.loadCount(),
                "averageLoadPenaltyNanos" to stats.averageLoadPenalty(),
                "queryPattern" to "5개 상품 x 3회 조회 = 15회 (miss 5 + hit 10)"
            )
        )
    }

    // ==========================================
    // 실험 6-3: TTL 만료와 DB 재조회
    // ==========================================

    fun experiment6_3_ttlExpiration(): CacheResult {
        clearCache("ttlCache")
        val product = createTestProduct("${TEST_PREFIX}6-3-TTL-TEST")
        val productId = product.id!!
        val ttlSeconds = 3

        log.info("[6-3] 상품 생성: id=$productId, TTL=${ttlSeconds}초")

        // 1. 첫 조회 → cache miss (DB 조회)
        self.findProductByIdWithTtl(productId)
        log.info("[6-3] 첫 조회 완료 (cache miss → DB)")

        // 2. TTL 만료 전 5번 조회 → cache hit (SQL 0)
        clearStatistics()
        for (i in 1..5) {
            self.findProductByIdWithTtl(productId)
        }
        val sqlBeforeExpiry = getQueryCount()
        log.info("[6-3] 만료 전 5회 조회: SQL ${sqlBeforeExpiry}회")

        // 3. TTL 만료 대기 (3.5초)
        log.info("[6-3] TTL 만료 대기 중 (${ttlSeconds + 0.5}초)...")
        Thread.sleep((ttlSeconds * 1000 + 500).toLong())

        // 4. 만료 후 조회 → cache miss (SQL 발생)
        clearStatistics()
        self.findProductByIdWithTtl(productId)
        val sqlAfterExpiry = getQueryCount()
        log.info("[6-3] 만료 후 조회: SQL ${sqlAfterExpiry}회")

        val stats = getCaffeineStats("ttlCache")

        return CacheResult.ttlExpiration(
            sqlBeforeExpiry = sqlBeforeExpiry,
            sqlAfterExpiry = sqlAfterExpiry,
            ttlSeconds = ttlSeconds,
            details = mapOf(
                "productId" to productId,
                "queriesBeforeExpiry" to 5,
                "waitTimeMs" to (ttlSeconds * 1000 + 500),
                "cacheStats" to mapOf(
                    "hitCount" to stats.hitCount(),
                    "missCount" to stats.missCount(),
                    "evictionCount" to stats.evictionCount()
                )
            )
        )
    }

    // ==========================================
    // 실험 6-4: Size-based Eviction
    // ==========================================

    fun experiment6_4_sizeEviction(): CacheResult {
        clearCache("smallCache")
        val maxSize = 5
        val insertCount = 10

        // 10개 상품 생성
        val products = (1..insertCount).map { createTestProduct("${TEST_PREFIX}6-4-SIZE-$it") }
        val productIds = products.map { it.id!! }

        log.info("[6-4] 상품 ${insertCount}개 생성, maxSize=$maxSize 캐시에 삽입")

        // 10개 순차 캐싱
        for (id in productIds) {
            self.findProductByIdSmallCache(id)
        }

        // Caffeine의 비동기 퇴거를 강제 실행
        val smallCacheNative = (cacheManager.getCache("smallCache") as CaffeineCache).nativeCache as Cache<*, *>
        smallCacheNative.cleanUp()

        val stats = getCaffeineStats("smallCache")
        val remainingKeys = smallCacheNative.estimatedSize().toInt()

        log.info("[6-4] 삽입=${insertCount}, 퇴거=${stats.evictionCount()}, 남은 키=${remainingKeys}")

        return CacheResult.sizeEviction(
            insertedCount = insertCount,
            maxSize = maxSize,
            evictionCount = stats.evictionCount(),
            remainingKeys = remainingKeys,
            details = mapOf(
                "productIds" to productIds,
                "evictionPolicy" to "Window TinyLFU (Caffeine 기본)",
                "cacheStats" to mapOf(
                    "hitCount" to stats.hitCount(),
                    "missCount" to stats.missCount(),
                    "evictionCount" to stats.evictionCount(),
                    "estimatedSize" to remainingKeys
                )
            )
        )
    }

    // ==========================================
    // 실험 6-5: @CacheEvict로 캐시 무효화
    // ==========================================

    fun experiment6_5_cacheEvict(): CacheResult {
        clearCache("productCache")
        val product = createTestProduct("${TEST_PREFIX}6-5-EVICT-TEST")
        val productId = product.id!!

        log.info("[6-5] 상품 생성: id=$productId")

        // 1. 캐시 워밍업 (첫 조회 → cache miss)
        self.findProductByIdCached(productId)
        log.info("[6-5] 캐시 워밍업 완료")

        // 2. 캐시 상태에서 5번 조회 (SQL 0)
        clearStatistics()
        for (i in 1..5) {
            self.findProductByIdCached(productId)
        }
        val sqlBeforeEvict = getQueryCount()
        log.info("[6-5] Evict 전 5회 조회: SQL ${sqlBeforeEvict}회")

        // 3. @CacheEvict 호출
        self.evictProductCache(productId)
        log.info("[6-5] @CacheEvict 호출 완료")

        // 4. 캐시 무효화 후 조회 (SQL 발생)
        clearStatistics()
        self.findProductByIdCached(productId)
        val sqlAfterEvict = getQueryCount()
        log.info("[6-5] Evict 후 조회: SQL ${sqlAfterEvict}회")

        return CacheResult.cacheEvict(
            sqlBeforeEvict = sqlBeforeEvict,
            sqlAfterEvict = sqlAfterEvict,
            details = mapOf(
                "productId" to productId,
                "queriesBeforeEvict" to 5,
                "mechanism" to "CacheInterceptor → Cache.evict(key) → Caffeine.invalidate(key)"
            )
        )
    }

    // ==========================================
    // 실험 6-6: @CachePut vs @CacheEvict
    // ==========================================

    fun experiment6_6_putVsEvict(): CacheResult {
        clearCache("productCache")

        // 상품 2개 생성 (하나는 Put용, 하나는 Evict용)
        val productPut = createTestProduct("${TEST_PREFIX}6-6-PUT-TEST")
        val productEvict = createTestProduct("${TEST_PREFIX}6-6-EVICT-TEST")
        val putId = productPut.id!!
        val evictId = productEvict.id!!

        log.info("[6-6] 상품 생성: putId=$putId, evictId=$evictId")

        // 캐시 워밍업
        self.findProductByIdCached(putId)
        self.findProductByIdCached(evictId)
        log.info("[6-6] 캐시 워밍업 완료")

        // === @CachePut 전략 ===
        // 업데이트 시 @CachePut으로 캐시 즉시 갱신
        clearStatistics()
        self.updateProductCacheWithPut(putId)
        val putSqlOnUpdate = getQueryCount()
        log.info("[6-6] @CachePut 업데이트: SQL ${putSqlOnUpdate}회")

        // 재조회 → cache hit (SQL 0)
        clearStatistics()
        self.findProductByIdCached(putId)
        val putSqlOnRead = getQueryCount()
        log.info("[6-6] @CachePut 후 재조회: SQL ${putSqlOnRead}회")

        // === @CacheEvict 전략 ===
        // 업데이트 시 @CacheEvict로 캐시 삭제
        clearStatistics()
        self.evictProductCache(evictId)
        val evictSqlOnUpdate = getQueryCount()
        log.info("[6-6] @CacheEvict 업데이트: SQL ${evictSqlOnUpdate}회")

        // 재조회 → cache miss (SQL 발생)
        clearStatistics()
        self.findProductByIdCached(evictId)
        val evictSqlOnRead = getQueryCount()
        log.info("[6-6] @CacheEvict 후 재조회: SQL ${evictSqlOnRead}회")

        return CacheResult.putVsEvict(
            putSqlOnUpdate = putSqlOnUpdate,
            putSqlOnRead = putSqlOnRead,
            evictSqlOnUpdate = evictSqlOnUpdate,
            evictSqlOnRead = evictSqlOnRead,
            details = mapOf(
                "putProductId" to putId,
                "evictProductId" to evictId,
                "putStrategy" to "@CachePut: 메서드 실행 → 결과를 캐시에 저장 (항상 실행)",
                "evictStrategy" to "@CacheEvict: 캐시에서 키 삭제 → 다음 조회 시 DB 재조회 (lazy)"
            )
        )
    }

    // ==========================================
    // 실험 6-7: Cache Stampede (Thundering Herd)
    // ==========================================

    fun experiment6_7_cacheStampede(): CacheResult {
        clearCache("stampedeCache")
        val product = createTestProduct("${TEST_PREFIX}6-7-STAMPEDE-TEST")
        val productId = product.id!!
        val threadCount = 100

        log.info("[6-7] 상품 생성: id=$productId, threads=$threadCount")

        // 1. 캐시 워밍업 (첫 조회)
        self.findProductByIdStampedeCache(productId)
        log.info("[6-7] 캐시 워밍업 완료 (stampedeCache, TTL=2초)")

        // 2. TTL 만료 대기 (2.5초)
        log.info("[6-7] TTL 만료 대기 중 (2.5초)...")
        Thread.sleep(2500)

        // 3. 동시 조회 → Cache Stampede
        clearStatistics()

        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val startTime = System.currentTimeMillis()

        try {
            for (i in 0 until threadCount) {
                executor.submit {
                    try {
                        startLatch.await()
                        self.findProductByIdStampedeCache(productId)
                    } catch (e: Exception) {
                        log.debug("[Thread-$i] 예외: ${e.message}")
                    } finally {
                        doneLatch.countDown()
                    }
                }
            }

            startLatch.countDown()
            doneLatch.await(30, TimeUnit.SECONDS)
        } finally {
            executor.shutdown()
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        }

        val durationMs = System.currentTimeMillis() - startTime
        val totalSqlCount = getQueryCount()

        log.info("[6-7] Stampede 결과: SQL ${totalSqlCount}회, ${durationMs}ms")

        val stats = getCaffeineStats("stampedeCache")

        return CacheResult.cacheStampede(
            threadCount = threadCount,
            totalSqlCount = totalSqlCount,
            durationMs = durationMs,
            details = mapOf(
                "productId" to productId,
                "ttlSeconds" to 2,
                "waitBeforeStampede" to "2.5초",
                "cacheStats" to mapOf(
                    "hitCount" to stats.hitCount(),
                    "missCount" to stats.missCount()
                ),
                "impact" to "TTL 만료 시 모든 스레드가 동시에 DB 조회 → Connection Pool 고갈 위험"
            )
        )
    }

    // ==========================================
    // 실험 6-8: 조건부 캐싱
    // ==========================================

    fun experiment6_8_conditionalCaching(): CacheResult {
        clearCache("conditionalCache")
        val product = createTestProduct("${TEST_PREFIX}6-8-CONDITIONAL-TEST")
        val productId = product.id!!
        val nonExistentId = 999999L
        val queryCount = 5

        log.info("[6-8] 상품 생성: id=$productId, 존재하지 않는 ID=$nonExistentId")

        // 1. 존재하는 ID로 반복 조회 → 첫 조회만 miss, 이후 hit
        clearStatistics()
        for (i in 1..queryCount) {
            self.findProductByIdConditional(productId)
        }
        val cachedIdSqlCount = getQueryCount()
        log.info("[6-8] 존재하는 ID ${queryCount}회 조회: SQL ${cachedIdSqlCount}회")

        // 2. 존재하지 않는 ID로 반복 조회 → unless="#result == null"이므로 매번 miss
        clearStatistics()
        for (i in 1..queryCount) {
            self.findProductByIdConditional(nonExistentId)
        }
        val nullIdSqlCount = getQueryCount()
        log.info("[6-8] 존재하지 않는 ID ${queryCount}회 조회: SQL ${nullIdSqlCount}회")

        val stats = getCaffeineStats("conditionalCache")

        return CacheResult.conditionalCaching(
            cachedIdSqlCount = cachedIdSqlCount,
            nullIdSqlCount = nullIdSqlCount,
            nullIdQueryCount = queryCount,
            details = mapOf(
                "existingProductId" to productId,
                "nonExistentId" to nonExistentId,
                "queryCount" to queryCount,
                "cacheStats" to mapOf(
                    "hitCount" to stats.hitCount(),
                    "missCount" to stats.missCount()
                ),
                "mechanism" to "@Cacheable(unless = \"#result == null\")는 SpEL로 반환값이 null이면 캐시 저장을 건너뜀"
            )
        )
    }

    // ==========================================
    // 정리
    // ==========================================

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun cleanupTestData(): Int {
        clearAllCaches()
        val deleted = productRepository.deleteByNameStartingWith(TEST_PREFIX)
        if (deleted > 0) {
            log.info("Lab 06 테스트 데이터 ${deleted}건 삭제, 모든 캐시 초기화")
        }
        return deleted.toInt()
    }
}
