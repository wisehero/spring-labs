package com.wisehero.springlabs.labs05

import com.wisehero.springlabs.common.dto.ApiResponse
import com.wisehero.springlabs.labs05.dto.LockResult
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/experiments")
class Lab05Controller(
    private val lockExperimentService: LockExperimentService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 실험 5-1: 락 없음 - Lost Update 발생
     * POST /api/v1/experiments/lock/5-1/no-lock-lost-update
     */
    @PostMapping("/lock/5-1/no-lock-lost-update")
    fun testNoLockLostUpdate(): ResponseEntity<ApiResponse<LockResult>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 5-1: 락 없음 - Lost Update 발생                       ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = lockExperimentService.experiment5_1_noLock()
        lockExperimentService.cleanupTestData()
        return ResponseEntity.ok(ApiResponse.success(result, "실험 5-1 완료: ${result.conclusion}"))
    }

    /**
     * 실험 5-2: Optimistic Lock (@Version) - 충돌 감지
     * POST /api/v1/experiments/lock/5-2/optimistic-lock
     */
    @PostMapping("/lock/5-2/optimistic-lock")
    fun testOptimisticLock(): ResponseEntity<ApiResponse<LockResult>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 5-2: Optimistic Lock (@Version) - 충돌 감지           ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = lockExperimentService.experiment5_2_optimisticLock()
        lockExperimentService.cleanupTestData()
        return ResponseEntity.ok(ApiResponse.success(result, "실험 5-2 완료: ${result.conclusion}"))
    }

    /**
     * 실험 5-3: Optimistic Lock + Retry - 재시도로 전부 성공
     * POST /api/v1/experiments/lock/5-3/optimistic-retry
     */
    @PostMapping("/lock/5-3/optimistic-retry")
    fun testOptimisticRetry(): ResponseEntity<ApiResponse<LockResult>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 5-3: Optimistic Lock + Retry - 재시도로 전부 성공       ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = lockExperimentService.experiment5_3_optimisticWithRetry()
        lockExperimentService.cleanupTestData()
        return ResponseEntity.ok(ApiResponse.success(result, "실험 5-3 완료: ${result.conclusion}"))
    }

    /**
     * 실험 5-4: Pessimistic Lock (SELECT FOR UPDATE) - 순차 처리
     * POST /api/v1/experiments/lock/5-4/pessimistic-lock
     */
    @PostMapping("/lock/5-4/pessimistic-lock")
    fun testPessimisticLock(): ResponseEntity<ApiResponse<LockResult>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 5-4: Pessimistic Lock (SELECT FOR UPDATE) - 순차 처리  ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = lockExperimentService.experiment5_4_pessimisticLock()
        lockExperimentService.cleanupTestData()
        return ResponseEntity.ok(ApiResponse.success(result, "실험 5-4 완료: ${result.conclusion}"))
    }

    /**
     * 실험 5-5: 성능 비교 - Optimistic vs Pessimistic
     * POST /api/v1/experiments/lock/5-5/performance-comparison
     */
    @PostMapping("/lock/5-5/performance-comparison")
    fun testPerformanceComparison(): ResponseEntity<ApiResponse<LockResult>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 5-5: 성능 비교 - Optimistic vs Pessimistic            ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = lockExperimentService.experiment5_5_performanceComparison()
        lockExperimentService.cleanupTestData()
        return ResponseEntity.ok(ApiResponse.success(result, "실험 5-5 완료: ${result.conclusion}"))
    }

    /**
     * 실험 5-6: 데드락 시나리오 - 역순 잠금
     * POST /api/v1/experiments/lock/5-6/deadlock
     */
    @PostMapping("/lock/5-6/deadlock")
    fun testDeadlock(): ResponseEntity<ApiResponse<LockResult>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 5-6: 데드락 시나리오 - 역순 잠금                        ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = lockExperimentService.experiment5_6_deadlock()
        lockExperimentService.cleanupTestData()
        return ResponseEntity.ok(ApiResponse.success(result, "실험 5-6 완료: ${result.conclusion}"))
    }

    /**
     * Lab 05 테스트 데이터 정리
     * DELETE /api/v1/experiments/lock/cleanup
     */
    @DeleteMapping("/lock/cleanup")
    fun cleanupLockTestData(): ResponseEntity<ApiResponse<Map<String, Int>>> {
        val deleted = lockExperimentService.cleanupTestData()
        return ResponseEntity.ok(ApiResponse.success(
            mapOf("deletedCount" to deleted),
            "Lab 05 테스트 데이터 ${deleted}건 삭제"
        ))
    }
}
