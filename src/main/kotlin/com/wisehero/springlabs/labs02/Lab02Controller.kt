package com.wisehero.springlabs.labs02

import com.wisehero.springlabs.common.dto.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/experiments")
class Lab02Controller(
    private val readOnlyExperimentService: ReadOnlyExperimentService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 실험 2-A: readOnly 상태 확인
     * GET /api/v1/experiments/readonly-status
     */
    @GetMapping("/readonly-status")
    fun testReadOnlyStatus(): ResponseEntity<ApiResponse<Map<String, Any?>>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 2-A: readOnly=true 상태 확인                          ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = readOnlyExperimentService.experimentReadOnlyStatus()

        return ResponseEntity.ok(ApiResponse.success(
            result,
            "FlushMode: ${result["hibernate_flush_mode"]}"
        ))
    }

    /**
     * 실험 2-B: readOnly에서 수정 시도
     * GET /api/v1/experiments/readonly-modify/{id}
     */
    @GetMapping("/readonly-modify/{id}")
    fun testReadOnlyModify(@PathVariable id: Long): ResponseEntity<ApiResponse<Map<String, Any?>>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 2-B: readOnly에서 수정 시도                            ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = readOnlyExperimentService.experimentReadOnlyWithModification(id)

        return ResponseEntity.ok(ApiResponse.success(result, "readOnly 수정 테스트"))
    }

    /**
     * 실험 2-C: readOnly 성능 비교
     * GET /api/v1/experiments/readonly-performance
     */
    @GetMapping("/readonly-performance")
    fun testReadOnlyPerformance(): ResponseEntity<ApiResponse<Map<String, Any?>>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 2-C: readOnly 성능 비교                               ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val readOnlyResult = readOnlyExperimentService.experimentReadOnlyPerformance()
        val writableResult = readOnlyExperimentService.experimentWritablePerformance()

        val comparison = mapOf(
            "readOnly_true" to readOnlyResult,
            "readOnly_false" to writableResult,
            "time_difference_ms" to (
                (writableResult["fetch_time_ms"] as Long) - (readOnlyResult["fetch_time_ms"] as Long)
            )
        )

        return ResponseEntity.ok(ApiResponse.success(
            comparison,
            "readOnly 성능 비교 완료"
        ))
    }

    /**
     * 실험 2-D: readOnly에서 persist 시도
     * GET /api/v1/experiments/readonly-persist
     */
    @GetMapping("/readonly-persist")
    fun testReadOnlyPersist(): ResponseEntity<ApiResponse<Map<String, Any?>>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 2-D: readOnly에서 persist 시도                        ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = readOnlyExperimentService.experimentReadOnlyWithPersist()

        return ResponseEntity.ok(ApiResponse.success(
            result,
            "readOnly에서 persist 테스트"
        ))
    }

    /**
     * 실험 2-E: readOnly 메모리 사용량 비교
     * GET /api/v1/experiments/readonly-memory
     */
    @GetMapping("/readonly-memory")
    fun testReadOnlyMemory(): ResponseEntity<ApiResponse<Map<String, Any?>>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 2-E: readOnly 메모리 사용량 비교                       ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val readOnlyResult = readOnlyExperimentService.experimentReadOnlyMemory()
        val writableResult = readOnlyExperimentService.experimentWritableMemory()

        val readOnlyDelta = readOnlyResult["memory_delta_mb"] as Double
        val writableDelta = writableResult["memory_delta_mb"] as Double
        val memorySaved = writableDelta - readOnlyDelta

        val comparison = mapOf(
            "readOnly_true" to readOnlyResult,
            "readOnly_false" to writableResult,
            "memory_saved_mb" to String.format("%.2f", memorySaved).toDouble(),
            "snapshot_overhead_explanation" to "readOnly=false는 더티체킹을 위해 각 엔티티의 스냅샷 복사본을 저장하므로 추가 메모리를 사용합니다."
        )

        return ResponseEntity.ok(ApiResponse.success(
            comparison,
            "readOnly 메모리 비교 완료 (절약: ${String.format("%.2f", memorySaved)}MB)"
        ))
    }
}
