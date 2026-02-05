package com.wisehero.springlabs.labs06.dto

import com.wisehero.springlabs.entity.Product
import java.io.Serializable
import java.time.LocalDateTime

/**
 * @Cacheable 메서드의 반환 타입으로 사용하는 캐시 전용 DTO.
 *
 * JPA Entity(Product)를 직접 캐싱하면 다음 문제가 발생할 수 있다:
 * 1. OSIV 비활성화 환경에서 detached entity의 lazy 관계 접근 시 LazyInitializationException
 * 2. @Version 필드가 stale해져 merge 시 OptimisticLockException
 * 3. Entity가 Serializable이 아니면 분산 캐시(Redis 등)로 전환 불가
 *
 * Serializable을 구현하여 향후 분산 캐시 전환에도 대응 가능하다.
 */
data class CachedProduct(
    val id: Long,
    val name: String,
    val stock: Int,
    val version: Long?,
    val createdAt: LocalDateTime
) : Serializable {

    companion object {
        private const val serialVersionUID = 1L

        fun from(product: Product): CachedProduct {
            return CachedProduct(
                id = product.id!!,
                name = product.name,
                stock = product.stock,
                version = product.version,
                createdAt = product.createdAt
            )
        }
    }
}
