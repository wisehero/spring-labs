package com.wisehero.springlabs.labs01

import com.wisehero.springlabs.common.dto.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/experiments")
class Lab01Controller(
    private val transactionExperimentService: TransactionExperimentService,
    private val transactionExternalService: TransactionExperimentExternalService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 실험 1-A: 자기 호출 (Self-Invocation) 테스트
     * GET /api/v1/experiments/self-invocation
     */
    @GetMapping("/self-invocation")
    fun testSelfInvocation(): ResponseEntity<ApiResponse<Map<String, Any>>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 1-A: @Transactional 자기 호출 테스트                    ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = transactionExperimentService.experimentSelfInvocation()

        return ResponseEntity.ok(ApiResponse.success(
            result,
            if (result["same_transaction"] == true)
                "⚠️ 자기호출로 인해 REQUIRES_NEW 무시됨!"
            else
                "✅ 다른 트랜잭션 사용됨"
        ))
    }

    /**
     * 실험 1-B: 외부 호출 테스트 (정상 동작 비교)
     * GET /api/v1/experiments/external-call
     */
    @GetMapping("/external-call")
    fun testExternalCall(): ResponseEntity<ApiResponse<Map<String, Any>>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 1-B: 외부 서비스 호출 테스트 (정상 케이스)               ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = transactionExperimentService.experimentExternalCall(transactionExternalService)

        return ResponseEntity.ok(ApiResponse.success(
            result,
            if (result["same_transaction"] == false)
                "✅ 외부 호출로 REQUIRES_NEW 정상 동작!"
            else
                "⚠️ 예상과 다른 결과"
        ))
    }
}
