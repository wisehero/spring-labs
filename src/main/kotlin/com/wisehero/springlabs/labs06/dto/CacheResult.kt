package com.wisehero.springlabs.labs06.dto

data class CacheResult(
    val experimentId: String,
    val experimentName: String,
    val description: String,
    val sqlCountNoCache: Long? = null,
    val sqlCountCached: Long? = null,
    val cacheHitCount: Long? = null,
    val cacheMissCount: Long? = null,
    val cacheHitRate: Double? = null,
    val evictionCount: Long? = null,
    val durationNoCacheMs: Long? = null,
    val durationCachedMs: Long? = null,
    val details: Map<String, Any> = emptyMap(),
    val conclusion: String
) {
    companion object {
        fun cacheVsNoCache(
            sqlCountNoCache: Long,
            sqlCountCached: Long,
            durationNoCacheMs: Long,
            durationCachedMs: Long,
            details: Map<String, Any>
        ): CacheResult {
            return CacheResult(
                experimentId = "6-1",
                experimentName = "캐시 없음 vs @Cacheable",
                description = "같은 Product를 100번 조회하여 캐시 유무에 따른 SQL 실행 횟수와 응답 시간 비교",
                sqlCountNoCache = sqlCountNoCache,
                sqlCountCached = sqlCountCached,
                durationNoCacheMs = durationNoCacheMs,
                durationCachedMs = durationCachedMs,
                details = details,
                conclusion = "캐시 미적용: SQL ${sqlCountNoCache}회 (${durationNoCacheMs}ms), 캐시 적용: SQL ${sqlCountCached}회 (${durationCachedMs}ms). @Cacheable은 첫 조회 이후 프록시가 캐시에서 바로 반환하여 DB 접근을 제거합니다."
            )
        }

        fun cacheStatistics(
            hitCount: Long,
            missCount: Long,
            hitRate: Double,
            evictionCount: Long,
            details: Map<String, Any>
        ): CacheResult {
            return CacheResult(
                experimentId = "6-2",
                experimentName = "Cache Hit/Miss 통계",
                description = "Caffeine recordStats()로 수집한 캐시 적중/미스/퇴거 통계 확인",
                cacheHitCount = hitCount,
                cacheMissCount = missCount,
                cacheHitRate = hitRate,
                evictionCount = evictionCount,
                details = details,
                conclusion = "Hit: ${hitCount}, Miss: ${missCount}, Hit Rate: ${"%.1f".format(hitRate * 100)}%, Eviction: ${evictionCount}. Caffeine의 StatsCounter가 atomic하게 통계를 수집합니다."
            )
        }

        fun ttlExpiration(
            sqlBeforeExpiry: Long,
            sqlAfterExpiry: Long,
            ttlSeconds: Int,
            details: Map<String, Any>
        ): CacheResult {
            return CacheResult(
                experimentId = "6-3",
                experimentName = "TTL 만료와 DB 재조회",
                description = "TTL ${ttlSeconds}초 캐시에서 만료 전/후 조회 시 SQL 발생 패턴 확인",
                sqlCountNoCache = sqlBeforeExpiry,
                sqlCountCached = sqlAfterExpiry,
                details = details,
                conclusion = "만료 전 SQL: ${sqlBeforeExpiry}회 (cache hit), 만료 후 SQL: ${sqlAfterExpiry}회 (cache miss → DB 재조회). Caffeine의 expireAfterWrite는 쓰기 시점 기준으로 TTL을 적용합니다."
            )
        }

        fun sizeEviction(
            insertedCount: Int,
            maxSize: Int,
            evictionCount: Long,
            remainingKeys: Int,
            details: Map<String, Any>
        ): CacheResult {
            return CacheResult(
                experimentId = "6-4",
                experimentName = "Size-based Eviction",
                description = "maxSize=${maxSize} 캐시에 ${insertedCount}개 항목 삽입 후 퇴거(Eviction) 관찰",
                evictionCount = evictionCount,
                details = details,
                conclusion = "${insertedCount}개 삽입, maxSize=${maxSize}, 퇴거=${evictionCount}건, 남은 키=${remainingKeys}개. Caffeine의 Window TinyLFU가 접근 빈도 기반으로 퇴거 대상을 결정합니다."
            )
        }

        fun cacheEvict(
            sqlBeforeEvict: Long,
            sqlAfterEvict: Long,
            details: Map<String, Any>
        ): CacheResult {
            return CacheResult(
                experimentId = "6-5",
                experimentName = "@CacheEvict로 캐시 무효화",
                description = "@CacheEvict 호출 전/후 SQL 발생 패턴으로 캐시 무효화 확인",
                sqlCountNoCache = sqlBeforeEvict,
                sqlCountCached = sqlAfterEvict,
                details = details,
                conclusion = "Evict 전 SQL: ${sqlBeforeEvict}회 (cache hit), Evict 후 SQL: ${sqlAfterEvict}회 (cache miss → DB 재조회). @CacheEvict는 CacheInterceptor가 지정된 키를 캐시에서 제거합니다."
            )
        }

        fun putVsEvict(
            putSqlOnUpdate: Long,
            putSqlOnRead: Long,
            evictSqlOnUpdate: Long,
            evictSqlOnRead: Long,
            details: Map<String, Any>
        ): CacheResult {
            return CacheResult(
                experimentId = "6-6",
                experimentName = "@CachePut vs @CacheEvict",
                description = "업데이트 시 @CachePut(즉시 갱신) vs @CacheEvict(지연 로드) 전략 비교",
                details = details,
                conclusion = "@CachePut: 업데이트 SQL ${putSqlOnUpdate}회 + 재조회 SQL ${putSqlOnRead}회 (즉시 갱신). @CacheEvict: 업데이트 SQL ${evictSqlOnUpdate}회 + 재조회 SQL ${evictSqlOnRead}회 (lazy reload). CachePut은 항상 메서드를 실행하고 결과를 캐시합니다."
            )
        }

        fun cacheStampede(
            threadCount: Int,
            totalSqlCount: Long,
            durationMs: Long,
            details: Map<String, Any>
        ): CacheResult {
            return CacheResult(
                experimentId = "6-7",
                experimentName = "Cache Stampede (Thundering Herd)",
                description = "TTL 만료 직후 ${threadCount}개 스레드가 동시 조회 시 DB 폭주 현상 관찰",
                sqlCountCached = totalSqlCount,
                durationCachedMs = durationMs,
                details = details,
                conclusion = "${threadCount}개 스레드 동시 조회 → SQL ${totalSqlCount}회 발생. TTL 만료 시 캐시가 비어있어 모든 스레드가 DB로 직행합니다. 이는 Connection Pool 고갈로 이어질 수 있습니다."
            )
        }

        fun conditionalCaching(
            cachedIdSqlCount: Long,
            nullIdSqlCount: Long,
            nullIdQueryCount: Int,
            details: Map<String, Any>
        ): CacheResult {
            return CacheResult(
                experimentId = "6-8",
                experimentName = "조건부 캐싱",
                description = "@Cacheable(unless = \"#result == null\")로 null 결과 캐싱 방지 확인",
                sqlCountNoCache = nullIdSqlCount,
                sqlCountCached = cachedIdSqlCount,
                details = details,
                conclusion = "존재하는 ID: SQL ${cachedIdSqlCount}회 (캐시됨), 존재하지 않는 ID: SQL ${nullIdSqlCount}회 (${nullIdQueryCount}회 조회마다 매번 DB 접근). unless 조건으로 null은 캐시하지 않아 Negative Cache를 방지합니다."
            )
        }
    }
}
