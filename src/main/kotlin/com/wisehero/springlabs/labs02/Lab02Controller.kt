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
     * 실험 2-B: readOnly에서 persist 후 커밋 결과 검증
     * GET /api/v1/experiments/readonly-modify
     */
    @GetMapping("/readonly-modify")
    fun testReadOnlyModify(): ResponseEntity<ApiResponse<Map<String, Any?>>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 2-B: readOnly에서 persist 후 커밋 결과 검증             ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = readOnlyExperimentService.experimentReadOnlyPersistAndVerify()
        val businessNo = result["run_business_no"] as String

        // readOnly 트랜잭션이 커밋된 후, 별도 트랜잭션에서 실제 DB 반영 여부를 재조회한다.
        val verification = readOnlyExperimentService.verifyNotFlushed(businessNo)
        val actuallyPersisted = verification["actually_persisted"] as Boolean
        val dbFoundCount = verification["db_found_count"] as Int

        val fullResult = result.toMutableMap()
        fullResult["verification"] = verification
        fullResult["conclusion"] = if (!actuallyPersisted) {
            "readOnly=true → FlushMode=MANUAL → 커밋 후 재조회 결과 DB에 0건 → persist한 엔티티가 실제로 DB에 반영되지 않았다"
        } else {
            "예상과 다름! readOnly=true인데도 DB에 ${dbFoundCount}건 반영됨"
        }

        log.info("실험 2-B 검증 완료: actually_persisted=$actuallyPersisted, db_found_count=$dbFoundCount")

        return ResponseEntity.ok(ApiResponse.success(fullResult, "실험 2-B 완료: ${fullResult["conclusion"]}"))
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

        readOnlyExperimentService.warmupQuery()

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
     * 실험 2-D: readOnly에서 명시적 flush 동작 확인
     * GET /api/v1/experiments/readonly-persist
     */
    @GetMapping("/readonly-persist")
    fun testReadOnlyPersist(): ResponseEntity<ApiResponse<Map<String, Any?>>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 2-D: readOnly에서 명시적 flush 동작 확인                ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = readOnlyExperimentService.experimentReadOnlyWithExplicitFlush()
        val businessNo = result["run_business_no"] as String

        // readOnly 트랜잭션 커밋 후, 명시적 flush된 데이터가 실제로 DB에 남아있는지 재조회
        val verification = readOnlyExperimentService.verifyExplicitFlushResult(businessNo)
        val actuallyPersisted = verification["actually_persisted"] as Boolean
        val dbFoundCount = verification["db_found_count"] as Int

        val fullResult = result.toMutableMap()
        fullResult["verification"] = verification
        fullResult["conclusion"] = if (actuallyPersisted) {
            "readOnly=true에서 명시적 flush() 호출 시 INSERT SQL이 실행되고, 커밋 후에도 DB에 ${dbFoundCount}건 남아있다"
        } else {
            "readOnly=true에서 명시적 flush() 호출 시 INSERT SQL은 실행되지만, 커밋 후 DB에 반영되지 않았다 (0건)"
        }

        log.info("실험 2-D 검증 완료: actually_persisted=$actuallyPersisted, db_found_count=$dbFoundCount")

        return ResponseEntity.ok(ApiResponse.success(
            fullResult,
            "실험 2-D 완료: ${fullResult["conclusion"]}"
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

        readOnlyExperimentService.warmupQuery()

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
