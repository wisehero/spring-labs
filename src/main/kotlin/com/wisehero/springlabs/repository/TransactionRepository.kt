package com.wisehero.springlabs.repository

import com.wisehero.springlabs.entity.Transaction
import org.springframework.data.jpa.repository.JpaRepository

interface TransactionRepository : JpaRepository<Transaction, Long>, TransactionRepositoryCustom
