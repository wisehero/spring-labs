package com.wisehero.springlabs.labs00

import com.wisehero.springlabs.common.dto.ApiResponse
import com.wisehero.springlabs.transaction.dto.TransactionSearchRequest
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/v1/lab00")
class Lab00DemoDataController(
    private val lab00DemoDataService: Lab00DemoDataService
) {

    @GetMapping("/real-count")
    fun getRealCount(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startDateTime: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endDateTime: LocalDateTime?,
        @RequestParam(required = false) transactionState: String?,
        @RequestParam(required = false) businessNo: String?,
        @RequestParam(required = false) minAmount: BigDecimal?,
        @RequestParam(required = false) maxAmount: BigDecimal?,
        @RequestParam(required = false) posTransactionNo: String?,
        @RequestParam(required = false) cashReceiptIssueYn: Boolean?
    ): ResponseEntity<ApiResponse<Map<String, Long>>> {
        val request = TransactionSearchRequest(
            startDateTime = startDateTime,
            endDateTime = endDateTime,
            transactionState = transactionState,
            businessNo = businessNo,
            minAmount = minAmount,
            maxAmount = maxAmount,
            posTransactionNo = posTransactionNo,
            cashReceiptIssueYn = cashReceiptIssueYn
        )
        val count = lab00DemoDataService.getRealCount(request)
        return ResponseEntity.ok(ApiResponse.success(mapOf("count" to count), "실시간 COUNT 조회 성공"))
    }

    @PostMapping("/demo-data/insert/{count}")
    fun insertDemoData(@PathVariable count: Int): ResponseEntity<ApiResponse<Map<String, Int>>> {
        val inserted = lab00DemoDataService.insertDemoTransactions(count)
        return ResponseEntity.ok(ApiResponse.success(mapOf("inserted" to inserted), "데모 데이터 ${inserted}건 삽입 성공"))
    }

    @DeleteMapping("/demo-data/delete/{count}")
    fun deleteDemoData(@PathVariable count: Int): ResponseEntity<ApiResponse<Map<String, Int>>> {
        val deleted = lab00DemoDataService.deleteDemoTransactions(count)
        return ResponseEntity.ok(ApiResponse.success(mapOf("deleted" to deleted), "데모 데이터 ${deleted}건 삭제 성공"))
    }

    @DeleteMapping("/demo-data/cleanup")
    fun cleanupDemoData(): ResponseEntity<ApiResponse<Map<String, Long>>> {
        val deleted = lab00DemoDataService.cleanupDemoTransactions()
        return ResponseEntity.ok(ApiResponse.success(mapOf("deleted" to deleted), "데모 데이터 전체 정리 완료"))
    }
}
