package com.wisehero.springdemo.transaction.dto

import java.math.BigDecimal
import java.time.LocalDateTime

data class TransactionSearchRequest(
    val startDateTime: LocalDateTime? = null,
    val endDateTime: LocalDateTime? = null,
    val transactionState: String? = null,
    val businessNo: String? = null,
    val minAmount: BigDecimal? = null,
    val maxAmount: BigDecimal? = null,
    val posTransactionNo: String? = null,
    val cashReceiptIssueYn: Boolean? = null,
    val sortBy: String = "approveDateTime",
    val sortDirection: String = "desc",
    val page: Int = 0,
    val size: Int = 20,
    val totalElements: Long? = null
) {
    fun getValidatedSize(): Int = size.coerceIn(1, 100)
    fun getValidatedPage(): Int = page.coerceAtLeast(0)
    fun needsCountQuery(): Boolean = totalElements == null
}
