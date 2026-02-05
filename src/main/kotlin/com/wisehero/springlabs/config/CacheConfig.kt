package com.wisehero.springlabs.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.cache.support.SimpleCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

/**
 * Spring Cache 설정
 * Caffeine 캐시를 사용하여 여러 용도의 캐시 인스턴스를 제공한다.
 */
@Configuration
@EnableCaching
class CacheConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun cacheManager(): CacheManager {
        val cacheManager = SimpleCacheManager()

        // 5개의 Caffeine 캐시 인스턴스 생성
        val caches = listOf(
            // 1. productCache - 일반적인 제품 데이터 캐싱용 (크기 1000, 10분 TTL)
            CaffeineCache(
                "productCache",
                Caffeine.newBuilder()
                    .maximumSize(1000)
                    .expireAfterWrite(10, TimeUnit.MINUTES)
                    .recordStats()
                    .build(),
                true
            ),

            // 2. ttlCache - 짧은 TTL 실험용 (3초 TTL)
            CaffeineCache(
                "ttlCache",
                Caffeine.newBuilder()
                    .expireAfterWrite(3, TimeUnit.SECONDS)
                    .recordStats()
                    .build(),
                true
            ),

            // 3. smallCache - 캐시 eviction 실험용 (최대 5개)
            CaffeineCache(
                "smallCache",
                Caffeine.newBuilder()
                    .maximumSize(5)
                    .recordStats()
                    .build(),
                true
            ),

            // 4. stampedeCache - Cache Stampede 실험용 (2초 TTL)
            CaffeineCache(
                "stampedeCache",
                Caffeine.newBuilder()
                    .expireAfterWrite(2, TimeUnit.SECONDS)
                    .recordStats()
                    .build(),
                true
            ),

            // 5. conditionalCache - 조건부 캐싱 실험용 (크기 100, 5분 TTL)
            CaffeineCache(
                "conditionalCache",
                Caffeine.newBuilder()
                    .maximumSize(100)
                    .expireAfterWrite(5, TimeUnit.MINUTES)
                    .recordStats()
                    .build(),
                true
            )
        )

        cacheManager.setCaches(caches)

        val cacheNames = caches.joinToString(", ") { it.name }
        log.info("CacheConfig 초기화 완료 - 등록된 캐시: [$cacheNames]")

        return cacheManager
    }
}
