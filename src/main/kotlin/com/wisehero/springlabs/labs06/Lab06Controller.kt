package com.wisehero.springlabs.labs06

import com.wisehero.springlabs.common.dto.ApiResponse
import com.wisehero.springlabs.labs06.dto.CacheResult
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/experiments")
class Lab06Controller(
    private val cacheExperimentService: CacheExperimentService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 실험 6-1: 캐시 없음 vs @Cacheable
     * POST /api/v1/experiments/cache/6-1/cache-vs-no-cache
     */
    @PostMapping("/cache/6-1/cache-vs-no-cache")
    fun testCacheVsNoCache(): ResponseEntity<ApiResponse<CacheResult>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 6-1: 캐시 없음 vs @Cacheable                          ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = cacheExperimentService.experiment6_1_cacheVsNoCache()
        cacheExperimentService.cleanupTestData()
        return ResponseEntity.ok(ApiResponse.success(result, "실험 6-1 완료: ${result.conclusion}"))
    }

    /**
     * 실험 6-2: Cache Hit/Miss 통계
     * POST /api/v1/experiments/cache/6-2/cache-statistics
     */
    @PostMapping("/cache/6-2/cache-statistics")
    fun testCacheStatistics(): ResponseEntity<ApiResponse<CacheResult>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 6-2: Cache Hit/Miss 통계                              ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = cacheExperimentService.experiment6_2_cacheStatistics()
        cacheExperimentService.cleanupTestData()
        return ResponseEntity.ok(ApiResponse.success(result, "실험 6-2 완료: ${result.conclusion}"))
    }

    /**
     * 실험 6-3: TTL 만료와 DB 재조회
     * POST /api/v1/experiments/cache/6-3/ttl-expiration
     */
    @PostMapping("/cache/6-3/ttl-expiration")
    fun testTtlExpiration(): ResponseEntity<ApiResponse<CacheResult>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 6-3: TTL 만료와 DB 재조회                               ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")
        log.warn("[주의] 이 실험은 TTL 만료 대기를 위해 약 3.5초간 요청 스레드를 블로킹합니다.")

        val result = cacheExperimentService.experiment6_3_ttlExpiration()
        cacheExperimentService.cleanupTestData()
        return ResponseEntity.ok(ApiResponse.success(result, "실험 6-3 완료: ${result.conclusion}"))
    }

    /**
     * 실험 6-4: Size-based Eviction
     * POST /api/v1/experiments/cache/6-4/size-eviction
     */
    @PostMapping("/cache/6-4/size-eviction")
    fun testSizeEviction(): ResponseEntity<ApiResponse<CacheResult>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 6-4: Size-based Eviction                             ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = cacheExperimentService.experiment6_4_sizeEviction()
        cacheExperimentService.cleanupTestData()
        return ResponseEntity.ok(ApiResponse.success(result, "실험 6-4 완료: ${result.conclusion}"))
    }

    /**
     * 실험 6-5: @CacheEvict로 캐시 무효화
     * POST /api/v1/experiments/cache/6-5/cache-evict
     */
    @PostMapping("/cache/6-5/cache-evict")
    fun testCacheEvict(): ResponseEntity<ApiResponse<CacheResult>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 6-5: @CacheEvict로 캐시 무효화                         ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = cacheExperimentService.experiment6_5_cacheEvict()
        cacheExperimentService.cleanupTestData()
        return ResponseEntity.ok(ApiResponse.success(result, "실험 6-5 완료: ${result.conclusion}"))
    }

    /**
     * 실험 6-6: @CachePut vs @CacheEvict
     * POST /api/v1/experiments/cache/6-6/put-vs-evict
     */
    @PostMapping("/cache/6-6/put-vs-evict")
    fun testPutVsEvict(): ResponseEntity<ApiResponse<CacheResult>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 6-6: @CachePut vs @CacheEvict                        ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = cacheExperimentService.experiment6_6_putVsEvict()
        cacheExperimentService.cleanupTestData()
        return ResponseEntity.ok(ApiResponse.success(result, "실험 6-6 완료: ${result.conclusion}"))
    }

    /**
     * 실험 6-7: Cache Stampede (Thundering Herd)
     * POST /api/v1/experiments/cache/6-7/cache-stampede
     */
    @PostMapping("/cache/6-7/cache-stampede")
    fun testCacheStampede(): ResponseEntity<ApiResponse<CacheResult>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 6-7: Cache Stampede (Thundering Herd)                 ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")
        log.warn("[주의] 이 실험은 TTL 만료 대기(2.5초) + 100개 스레드 동시 조회를 수행합니다.")

        val result = cacheExperimentService.experiment6_7_cacheStampede()
        cacheExperimentService.cleanupTestData()
        return ResponseEntity.ok(ApiResponse.success(result, "실험 6-7 완료: ${result.conclusion}"))
    }

    /**
     * 실험 6-8: 조건부 캐싱
     * POST /api/v1/experiments/cache/6-8/conditional-caching
     */
    @PostMapping("/cache/6-8/conditional-caching")
    fun testConditionalCaching(): ResponseEntity<ApiResponse<CacheResult>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 6-8: 조건부 캐싱                                       ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = cacheExperimentService.experiment6_8_conditionalCaching()
        cacheExperimentService.cleanupTestData()
        return ResponseEntity.ok(ApiResponse.success(result, "실험 6-8 완료: ${result.conclusion}"))
    }

    /**
     * Lab 06 테스트 데이터 정리
     * DELETE /api/v1/experiments/cache/cleanup
     */
    @DeleteMapping("/cache/cleanup")
    fun cleanupCacheTestData(): ResponseEntity<ApiResponse<Map<String, Int>>> {
        val deleted = cacheExperimentService.cleanupTestData()
        return ResponseEntity.ok(ApiResponse.success(
            mapOf("deletedCount" to deleted),
            "Lab 06 테스트 데이터 ${deleted}건 삭제"
        ))
    }
}
