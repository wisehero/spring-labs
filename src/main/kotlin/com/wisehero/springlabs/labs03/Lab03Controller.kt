package com.wisehero.springlabs.labs03

import com.wisehero.springlabs.common.dto.ApiResponse
import com.wisehero.springlabs.labs03.dto.ExperimentSummary
import com.wisehero.springlabs.labs03.dto.InsertResult
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/experiments")
class Lab03Controller(
    private val bulkInsertExperimentService: BulkInsertExperimentService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 전체 비교 (100, 1000, 10000건)
     * POST /api/v1/experiments/bulk-insert/compare-all
     */
    @PostMapping("/bulk-insert/compare-all")
    fun compareBulkInsertAll(): ResponseEntity<ApiResponse<ExperimentSummary>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 3: Bulk Insert 성능 비교 (전체)                        ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = bulkInsertExperimentService.compareAll()

        return ResponseEntity.ok(ApiResponse.success(result, "Bulk Insert 전체 비교 완료"))
    }

    /**
     * 특정 건수 비교
     * POST /api/v1/experiments/bulk-insert/compare/{count}
     */
    @PostMapping("/bulk-insert/compare/{count}")
    fun compareBulkInsert(@PathVariable count: Int): ResponseEntity<ApiResponse<List<InsertResult>>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 3: Bulk Insert 성능 비교 (${count}건)                   ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val results = bulkInsertExperimentService.compare(count)

        val winner = results.first()
        return ResponseEntity.ok(ApiResponse.success(
            results,
            "🏆 1위: ${winner.method} (${winner.durationMs}ms)"
        ))
    }

    /**
     * 개별 테스트: JPA saveAll
     * POST /api/v1/experiments/bulk-insert/saveall/{count}
     */
    @PostMapping("/bulk-insert/saveall/{count}")
    fun testSaveAll(@PathVariable count: Int): ResponseEntity<ApiResponse<InsertResult>> {
        val result = bulkInsertExperimentService.insertWithSaveAll(count)
        bulkInsertExperimentService.cleanupTestData()
        return ResponseEntity.ok(ApiResponse.success(result, "JPA saveAll 테스트 완료"))
    }

    /**
     * 개별 테스트: JdbcTemplate batchUpdate
     * POST /api/v1/experiments/bulk-insert/jdbc-batch/{count}
     */
    @PostMapping("/bulk-insert/jdbc-batch/{count}")
    fun testJdbcBatch(@PathVariable count: Int): ResponseEntity<ApiResponse<InsertResult>> {
        val result = bulkInsertExperimentService.insertWithJdbcBatch(count)
        bulkInsertExperimentService.cleanupTestData()
        return ResponseEntity.ok(ApiResponse.success(result, "JdbcTemplate batchUpdate 테스트 완료"))
    }

    /**
     * 개별 테스트: Native Bulk Insert
     * POST /api/v1/experiments/bulk-insert/native-bulk/{count}
     */
    @PostMapping("/bulk-insert/native-bulk/{count}")
    fun testNativeBulk(@PathVariable count: Int): ResponseEntity<ApiResponse<InsertResult>> {
        val result = bulkInsertExperimentService.insertWithNativeBulk(count)
        bulkInsertExperimentService.cleanupTestData()
        return ResponseEntity.ok(ApiResponse.success(result, "Native Bulk Insert 테스트 완료"))
    }

    /**
     * 테스트 데이터 정리
     * DELETE /api/v1/experiments/bulk-insert/cleanup
     */
    @DeleteMapping("/bulk-insert/cleanup")
    fun cleanupBulkInsertTestData(): ResponseEntity<ApiResponse<Map<String, Int>>> {
        val deleted = bulkInsertExperimentService.cleanupTestData()
        return ResponseEntity.ok(ApiResponse.success(
            mapOf("deletedCount" to deleted),
            "테스트 데이터 ${deleted}건 삭제"
        ))
    }
}
