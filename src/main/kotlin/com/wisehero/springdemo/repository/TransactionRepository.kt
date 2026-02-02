package com.wisehero.springdemo.repository

import com.wisehero.springdemo.entity.Transaction
import org.springframework.data.jpa.repository.JpaRepository

interface TransactionRepository : JpaRepository<Transaction, Long>, TransactionRepositoryCustom
