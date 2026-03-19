package com.wisehero.springlabs.labs00

import com.wisehero.springlabs.entity.Transaction
import com.wisehero.springlabs.repository.TransactionRepository
import com.wisehero.springlabs.transaction.dto.TransactionSearchRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@Service
class Lab00DemoDataService(
    private val transactionRepository: TransactionRepository
) {

    @Transactional(readOnly = true)
    fun getRealCount(request: TransactionSearchRequest): Long {
        return transactionRepository.countByConditions(request)
    }

    @Transactional
    fun insertDemoTransactions(count: Int): Int {
        val now = LocalDateTime.now()
        val transactions = (1..count).map { i ->
            Transaction(
                approveDateTime = now.minusMinutes(i.toLong()),
                amount = BigDecimal.valueOf((1000..50000).random().toLong()),
                businessNo = "DEMO-LAB00-${UUID.randomUUID().toString().take(8)}",
                posTransactionNo = "POS-DEMO-${UUID.randomUUID().toString().take(8)}",
                paymentTransactionGuidNo = UUID.randomUUID().toString(),
                spareTransactionGuidNo = UUID.randomUUID().toString(),
                transactionState = listOf("APPROVED", "CANCELLED", "PENDING").random()
            )
        }
        transactionRepository.saveAll(transactions)
        return transactions.size
    }

    @Transactional
    fun deleteDemoTransactions(count: Int): Int {
        val demoTransactions = transactionRepository.findAll()
            .filter { it.businessNo.startsWith("DEMO-LAB00-") }
            .take(count)
        transactionRepository.deleteAll(demoTransactions)
        return demoTransactions.size
    }

    @Transactional
    fun cleanupDemoTransactions(): Long {
        return transactionRepository.deleteByBusinessNoStartingWith("DEMO-LAB00-")
    }

    @Transactional(readOnly = true)
    fun getDemoDataCount(): Long {
        return transactionRepository.countByConditions(
            TransactionSearchRequest(businessNo = "DEMO-LAB00-")
        )
    }
}
