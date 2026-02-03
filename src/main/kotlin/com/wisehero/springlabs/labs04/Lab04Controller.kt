package com.wisehero.springlabs.labs04

import com.wisehero.springlabs.common.dto.ApiResponse
import com.wisehero.springlabs.labs04.dto.PropagationResult
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/experiments")
class Lab04Controller(
    private val propagationExperimentService: PropagationExperimentService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 실험 4-1: REQUIRED - 외부 트랜잭션 존재 시 참여
     * GET /api/v1/experiments/propagation/4-1/required-joins
     */
    @GetMapping("/propagation/4-1/required-joins")
    fun testRequiredJoins(): ResponseEntity<ApiResponse<PropagationResult>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 4-1: REQUIRED - 외부 트랜잭션 참여 확인                  ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = propagationExperimentService.experiment4_1_requiredJoinsExisting()
        return ResponseEntity.ok(ApiResponse.success(result, "실험 4-1 완료: ${result.conclusion}"))
    }

    /**
     * 실험 4-2: REQUIRED - 트랜잭션 없을 때 새로 생성
     * GET /api/v1/experiments/propagation/4-2/required-creates-new
     */
    @GetMapping("/propagation/4-2/required-creates-new")
    fun testRequiredCreatesNew(): ResponseEntity<ApiResponse<PropagationResult>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 4-2: REQUIRED - 트랜잭션 없을 때 새로 생성               ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = propagationExperimentService.experiment4_2_requiredCreatesNew()
        return ResponseEntity.ok(ApiResponse.success(result, "실험 4-2 완료: ${result.conclusion}"))
    }

    /**
     * 실험 4-3: REQUIRES_NEW - 항상 새 트랜잭션 생성
     * GET /api/v1/experiments/propagation/4-3/requires-new-always-new
     */
    @GetMapping("/propagation/4-3/requires-new-always-new")
    fun testRequiresNewAlwaysNew(): ResponseEntity<ApiResponse<PropagationResult>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 4-3: REQUIRES_NEW - 항상 새 트랜잭션 생성               ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = propagationExperimentService.experiment4_3_requiresNewAlwaysNew()
        return ResponseEntity.ok(ApiResponse.success(result, "실험 4-3 완료: ${result.conclusion}"))
    }

    /**
     * 실험 4-4: REQUIRED inner 예외 - 롤백 전파 트랩
     * POST /api/v1/experiments/propagation/4-4/required-inner-throws
     */
    @PostMapping("/propagation/4-4/required-inner-throws")
    fun testRequiredInnerThrows(): ResponseEntity<ApiResponse<PropagationResult>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 4-4: REQUIRED 롤백 트랩 (UnexpectedRollbackException)  ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = propagationExperimentService.experiment4_4_requiredRollbackTrap()
        propagationExperimentService.cleanupTestData()
        return ResponseEntity.ok(ApiResponse.success(result, "실험 4-4 완료"))
    }

    /**
     * 실험 4-5: REQUIRES_NEW inner 예외 - outer 생존
     * POST /api/v1/experiments/propagation/4-5/requires-new-inner-throws
     */
    @PostMapping("/propagation/4-5/requires-new-inner-throws")
    fun testRequiresNewInnerThrows(): ResponseEntity<ApiResponse<PropagationResult>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 4-5: REQUIRES_NEW Inner 예외 - Outer 생존              ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = propagationExperimentService.experiment4_5_requiresNewInnerThrows()
        propagationExperimentService.cleanupTestData()
        return ResponseEntity.ok(ApiResponse.success(result, "실험 4-5 완료"))
    }

    /**
     * 실험 4-6: Outer 실패 후 REQUIRES_NEW inner 생존
     * POST /api/v1/experiments/propagation/4-6/outer-fails-after-inner
     */
    @PostMapping("/propagation/4-6/outer-fails-after-inner")
    fun testOuterFailsAfterInner(): ResponseEntity<ApiResponse<PropagationResult>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 4-6: Outer 실패 후 REQUIRES_NEW Inner 생존             ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = propagationExperimentService.experiment4_6_outerFailsAfterInnerSucceeds()
        propagationExperimentService.cleanupTestData()
        return ResponseEntity.ok(ApiResponse.success(result, "실험 4-6 완료"))
    }

    /**
     * 실험 4-7: UnexpectedRollbackException 상세 분석 (3 시나리오)
     * POST /api/v1/experiments/propagation/4-7/unexpected-rollback-deep-dive
     */
    @PostMapping("/propagation/4-7/unexpected-rollback-deep-dive")
    fun testUnexpectedRollbackDeepDive(): ResponseEntity<ApiResponse<PropagationResult>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 4-7: UnexpectedRollbackException 상세 분석             ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = propagationExperimentService.experiment4_7_unexpectedRollbackDeepDive()
        propagationExperimentService.cleanupTestData()
        return ResponseEntity.ok(ApiResponse.success(result, "실험 4-7 완료"))
    }

    /**
     * 실험 4-8: DB 커넥션 분리 확인
     * GET /api/v1/experiments/propagation/4-8/connection-separation
     */
    @GetMapping("/propagation/4-8/connection-separation")
    fun testConnectionSeparation(): ResponseEntity<ApiResponse<PropagationResult>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 4-8: DB 커넥션 분리 확인                                ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = propagationExperimentService.experiment4_8_connectionSeparation()
        return ResponseEntity.ok(ApiResponse.success(result, "실험 4-8 완료"))
    }

    /**
     * 실험 4-9: 커넥션 풀 고갈 시뮬레이션
     * POST /api/v1/experiments/propagation/4-9/connection-pool-exhaustion
     */
    @PostMapping("/propagation/4-9/connection-pool-exhaustion")
    fun testConnectionPoolExhaustion(): ResponseEntity<ApiResponse<PropagationResult>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 4-9: 커넥션 풀 고갈 시뮬레이션 (약 30초 소요)            ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = propagationExperimentService.experiment4_9_connectionPoolExhaustion()
        return ResponseEntity.ok(ApiResponse.success(result, "실험 4-9 완료"))
    }

    /**
     * Lab 04 테스트 데이터 정리
     * DELETE /api/v1/experiments/propagation/cleanup
     */
    @DeleteMapping("/propagation/cleanup")
    fun cleanupPropagationTestData(): ResponseEntity<ApiResponse<Map<String, Int>>> {
        val deleted = propagationExperimentService.cleanupTestData()
        return ResponseEntity.ok(ApiResponse.success(
            mapOf("deletedCount" to deleted),
            "Lab 04 테스트 데이터 ${deleted}건 삭제"
        ))
    }
}
