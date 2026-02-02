package com.wisehero.springlabs.repository

import com.wisehero.springlabs.entity.Transaction
import com.wisehero.springlabs.transaction.dto.TransactionSearchRequest
import org.springframework.data.domain.Page

interface TransactionRepositoryCustom {
    fun search(request: TransactionSearchRequest): Page<Transaction>
}
