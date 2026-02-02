package com.wisehero.springlabs.transaction.dto

import com.wisehero.springlabs.entity.Transaction
import java.math.BigDecimal
import java.time.LocalDateTime

data class TransactionListResponse(
    val id: Long,
    val approveDateTime: LocalDateTime,
    val amount: BigDecimal,
    val businessNo: String,
    val posTransactionNo: String,
    val transactionState: String,
    val cashReceiptIssueYn: Boolean?
) {
    companion object {
        fun from(transaction: Transaction): TransactionListResponse {
            return TransactionListResponse(
                id = transaction.id!!,
                approveDateTime = transaction.approveDateTime,
                amount = transaction.amount,
                businessNo = transaction.businessNo,
                posTransactionNo = transaction.posTransactionNo,
                transactionState = transaction.transactionState,
                cashReceiptIssueYn = transaction.cashReceiptIssueYn
            )
        }
    }
}

data class TransactionDetailResponse(
    val id: Long,
    val approveDateTime: LocalDateTime,
    val amount: BigDecimal,
    val businessNo: String,
    val posTransactionNo: String,
    val paymentTransactionGuidNo: String,
    val spareTransactionGuidNo: String,
    val transactionState: String,
    val posCancelTransactionNo: String?,
    val cancelDateTime: LocalDateTime?,
    val cancelReason: String?,
    val cashReceiptIssueYn: Boolean?,
    val cashReceiptApproveNo: String?,
    val cashReceiptApproveDateTime: LocalDateTime?,
    val cashReceiptIssueType: String?,
    val cashReceiptAuthType: String?,
    val cashReceiptIssueNo: String?,
    val cashReceiptCancelApproveNo: String?,
    val cashReceiptCancelDateTime: LocalDateTime?,
    val paperReceiptPrintYn: Boolean?
) {
    companion object {
        fun from(transaction: Transaction): TransactionDetailResponse {
            return TransactionDetailResponse(
                id = transaction.id!!,
                approveDateTime = transaction.approveDateTime,
                amount = transaction.amount,
                businessNo = transaction.businessNo,
                posTransactionNo = transaction.posTransactionNo,
                paymentTransactionGuidNo = transaction.paymentTransactionGuidNo,
                spareTransactionGuidNo = transaction.spareTransactionGuidNo,
                transactionState = transaction.transactionState,
                posCancelTransactionNo = transaction.posCancelTransactionNo,
                cancelDateTime = transaction.cancelDateTime,
                cancelReason = transaction.cancelReason,
                cashReceiptIssueYn = transaction.cashReceiptIssueYn,
                cashReceiptApproveNo = transaction.cashReceiptApproveNo,
                cashReceiptApproveDateTime = transaction.cashReceiptApproveDateTime,
                cashReceiptIssueType = transaction.cashReceiptIssueType,
                cashReceiptAuthType = transaction.cashReceiptAuthType,
                cashReceiptIssueNo = transaction.cashReceiptIssueNo,
                cashReceiptCancelApproveNo = transaction.cashReceiptCancelApproveNo,
                cashReceiptCancelDateTime = transaction.cashReceiptCancelDateTime,
                paperReceiptPrintYn = transaction.paperReceiptPrintYn
            )
        }
    }
}
