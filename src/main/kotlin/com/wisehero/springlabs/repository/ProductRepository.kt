package com.wisehero.springlabs.repository

import com.wisehero.springlabs.entity.Product
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductRepository : JpaRepository<Product, Long> {

    // Lab 05: 비관적 락으로 조회 (SELECT ... FOR UPDATE)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    fun findByIdWithPessimisticLock(@Param("id") id: Long): Product?

    // Lab 05: 테스트 데이터 정리용
    fun deleteByNameStartingWith(prefix: String): Long
}
