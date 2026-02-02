package com.wisehero.springdemo.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "transaction",
    indexes = [
        Index(name = "idx_transaction_pos_no", columnList = "pos_transaction_no")
    ]
)
class Transaction(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "approve_date_time")
    val approveDateTime: LocalDateTime,

    @Column(name = "amount", precision = 15, scale = 2)
    val amount: BigDecimal,

    @Column(name = "business_no", length = 20)
    val businessNo: String,

    @Column(name = "pos_transaction_no", length = 50)
    val posTransactionNo: String,

    @Column(name = "payment_transaction_guid_no", length = 100)
    val paymentTransactionGuidNo: String,

    @Column(name = "spare_transaction_guid_no", length = 100)
    val spareTransactionGuidNo: String,

    @Column(name = "transaction_state", length = 20)
    val transactionState: String,

    @Column(name = "pos_cancel_transaction_no", length = 50)
    val posCancelTransactionNo: String? = null,

    @Column(name = "cancel_date_time")
    val cancelDateTime: LocalDateTime? = null,

    @Column(name = "cancel_reason", length = 200)
    val cancelReason: String? = null,

    @Column(name = "cash_receipt_issue_yn")
    val cashReceiptIssueYn: Boolean? = null,

    @Column(name = "cash_receipt_approve_no", length = 50)
    val cashReceiptApproveNo: String? = null,

    @Column(name = "cash_receipt_approve_date_time")
    val cashReceiptApproveDateTime: LocalDateTime? = null,

    @Column(name = "cash_receipt_issue_type", length = 20)
    val cashReceiptIssueType: String? = null,

    @Column(name = "cash_receipt_auth_type", length = 20)
    val cashReceiptAuthType: String? = null,

    @Column(name = "cash_receipt_issue_no", length = 50)
    val cashReceiptIssueNo: String? = null,

    @Column(name = "cash_receipt_cancel_approve_no", length = 50)
    val cashReceiptCancelApproveNo: String? = null,

    @Column(name = "cash_receipt_cancel_date_time")
    val cashReceiptCancelDateTime: LocalDateTime? = null,

    @Column(name = "paper_receipt_print_yn")
    val paperReceiptPrintYn: Boolean? = null
)
