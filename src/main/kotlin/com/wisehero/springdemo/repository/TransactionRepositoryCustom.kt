package com.wisehero.springdemo.repository

import com.wisehero.springdemo.entity.Transaction
import com.wisehero.springdemo.transaction.dto.TransactionSearchRequest
import org.springframework.data.domain.Page

interface TransactionRepositoryCustom {
    fun search(request: TransactionSearchRequest): Page<Transaction>
}
