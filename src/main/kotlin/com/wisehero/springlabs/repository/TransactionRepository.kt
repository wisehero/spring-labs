package com.wisehero.springlabs.repository

import com.wisehero.springlabs.entity.Transaction
import org.springframework.data.jpa.repository.JpaRepository

interface TransactionRepository : JpaRepository<Transaction, Long>, TransactionRepositoryCustom {

    // Lab 04: 실험 데이터 검증용 - businessNo로 존재 여부 확인
    fun existsByBusinessNo(businessNo: String): Boolean

    // Lab 04: 실험 데이터 정리용 - prefix로 시작하는 businessNo를 가진 행 삭제
    fun deleteByBusinessNoStartingWith(prefix: String): Long
}
