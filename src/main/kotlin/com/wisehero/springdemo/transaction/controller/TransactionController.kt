package com.wisehero.springdemo.transaction.controller

import com.wisehero.springdemo.common.dto.ApiResponse
import com.wisehero.springdemo.common.dto.PageResponse
import com.wisehero.springdemo.transaction.dto.TransactionDetailResponse
import com.wisehero.springdemo.transaction.dto.TransactionListResponse
import com.wisehero.springdemo.transaction.dto.TransactionSearchRequest
import com.wisehero.springdemo.transaction.service.TransactionService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/v1/transactions")
class TransactionController(
    private val transactionService: TransactionService
) {

    @GetMapping
    fun searchTransactions(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startDateTime: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endDateTime: LocalDateTime?,
        @RequestParam(required = false) transactionState: String?,
        @RequestParam(required = false) businessNo: String?,
        @RequestParam(required = false) minAmount: BigDecimal?,
        @RequestParam(required = false) maxAmount: BigDecimal?,
        @RequestParam(required = false) posTransactionNo: String?,
        @RequestParam(required = false) cashReceiptIssueYn: Boolean?,
        @RequestParam(defaultValue = "approveDateTime") sortBy: String,
        @RequestParam(defaultValue = "desc") sortDirection: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) totalElements: Long?
    ): ResponseEntity<ApiResponse<PageResponse<TransactionListResponse>>> {
        val request = TransactionSearchRequest(
            startDateTime = startDateTime,
            endDateTime = endDateTime,
            transactionState = transactionState,
            businessNo = businessNo,
            minAmount = minAmount,
            maxAmount = maxAmount,
            posTransactionNo = posTransactionNo,
            cashReceiptIssueYn = cashReceiptIssueYn,
            sortBy = sortBy,
            sortDirection = sortDirection,
            page = page,
            size = size,
            totalElements = totalElements
        )

        val result = transactionService.searchTransactions(request)
        return ResponseEntity.ok(ApiResponse.success(result, "거래내역 조회 성공"))
    }

    @GetMapping("/{id}")
    fun getTransaction(@PathVariable id: Long): ResponseEntity<ApiResponse<TransactionDetailResponse>> {
        val result = transactionService.getTransaction(id)
        return ResponseEntity.ok(ApiResponse.success(result, "거래내역 상세 조회 성공"))
    }
}
