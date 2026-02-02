package com.wisehero.springlabs.experiment

import com.wisehero.springlabs.common.dto.ApiResponse
import com.wisehero.springlabs.experiment.dto.ExperimentSummary
import com.wisehero.springlabs.experiment.dto.InsertResult
import com.wisehero.springlabs.experiment.dto.PropagationResult
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * ==========================================
 * Spring Transaction 실험 컨트롤러
 * ==========================================
 * 
 * 실험 1: @Transactional 자기 호출 함정
 * 실험 2: @Transactional(readOnly = true) 실제 효과
 * 
 * 테스트 방법:
 * 1. 애플리케이션 실행
 * 2. 각 엔드포인트 호출
 * 3. 콘솔 로그 확인!
 */
@RestController
@RequestMapping("/api/v1/experiments")
class ExperimentController(
    private val transactionExperimentService: TransactionExperimentService,
    private val transactionExternalService: TransactionExperimentExternalService,
    private val readOnlyExperimentService: ReadOnlyExperimentService,
    private val bulkInsertExperimentService: BulkInsertExperimentService,
    private val propagationExperimentService: PropagationExperimentService
) {
    
    private val log = LoggerFactory.getLogger(javaClass)
    
    /**
     * ==========================================
     * 실험 1-A: 자기 호출 (Self-Invocation) 테스트
     * ==========================================
     * 
     * GET /api/v1/experiments/self-invocation
     * 
     * 예상 결과: REQUIRES_NEW가 무시되어 같은 트랜잭션 사용
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
     * ==========================================
     * 실험 1-B: 외부 호출 테스트 (정상 동작 비교)
     * ==========================================
     * 
     * GET /api/v1/experiments/external-call
     * 
     * 예상 결과: REQUIRES_NEW가 정상 동작하여 다른 트랜잭션 사용
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
    
    /**
     * ==========================================
     * 실험 2-A: readOnly 상태 확인
     * ==========================================
     * 
     * GET /api/v1/experiments/readonly-status
     * 
     * 확인할 것: FlushMode, DefaultReadOnly 등
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
     * ==========================================
     * 실험 2-B: readOnly에서 수정 시도
     * ==========================================
     * 
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
     * ==========================================
     * 실험 2-C: readOnly 성능 비교
     * ==========================================
     * 
     * GET /api/v1/experiments/readonly-performance
     * 
     * readOnly=true vs readOnly=false 성능 차이 확인
     */
    @GetMapping("/readonly-performance")
    fun testReadOnlyPerformance(): ResponseEntity<ApiResponse<Map<String, Any?>>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 2-C: readOnly 성능 비교                               ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")
        
        // readOnly=true 테스트
        val readOnlyResult = readOnlyExperimentService.experimentReadOnlyPerformance()
        
        // readOnly=false 테스트
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
     * ==========================================
     * 실험 2-D: readOnly에서 persist 시도
     * ==========================================
     * 
     * GET /api/v1/experiments/readonly-persist
     * 
     * ⚠️ 주의: 실제로 데이터가 저장될 수 있음!
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
     * ==========================================
     * 실험 2-E: readOnly 메모리 사용량 비교
     * ==========================================
     *
     * GET /api/v1/experiments/readonly-memory
     *
     * readOnly=true vs readOnly=false 메모리 사용량 차이 확인
     */
    @GetMapping("/readonly-memory")
    fun testReadOnlyMemory(): ResponseEntity<ApiResponse<Map<String, Any?>>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 2-E: readOnly 메모리 사용량 비교                       ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        // readOnly=true 메모리 측정
        val readOnlyResult = readOnlyExperimentService.experimentReadOnlyMemory()

        // readOnly=false 메모리 측정
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

    /**
     * ==========================================
     * 모든 실험 한 번에 실행
     * ==========================================
     *
     * GET /api/v1/experiments/all
     */
    @GetMapping("/all")
    fun runAllExperiments(): ResponseEntity<ApiResponse<Map<String, Any?>>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  🧪 모든 Spring Transaction 실험 실행                       ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")
        
        val results = mutableMapOf<String, Any?>()
        
        // 실험 1-A
        log.info(">>> 실험 1-A 시작")
        results["experiment_1a_self_invocation"] = transactionExperimentService.experimentSelfInvocation()
        
        // 실험 1-B
        log.info(">>> 실험 1-B 시작")
        results["experiment_1b_external_call"] = transactionExperimentService.experimentExternalCall(transactionExternalService)
        
        // 실험 2-A
        log.info(">>> 실험 2-A 시작")
        results["experiment_2a_readonly_status"] = readOnlyExperimentService.experimentReadOnlyStatus()
        
        // 실험 2-C
        log.info(">>> 실험 2-C 시작")
        results["experiment_2c_readonly_performance"] = mapOf(
            "readOnly_true" to readOnlyExperimentService.experimentReadOnlyPerformance(),
            "readOnly_false" to readOnlyExperimentService.experimentWritablePerformance()
        )

        // 실험 2-E
        log.info(">>> 실험 2-E 시작")
        results["experiment_2e_readonly_memory"] = mapOf(
            "readOnly_true" to readOnlyExperimentService.experimentReadOnlyMemory(),
            "readOnly_false" to readOnlyExperimentService.experimentWritableMemory()
        )

        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  🎉 모든 실험 완료! 로그를 확인하세요                        ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")
        
        return ResponseEntity.ok(ApiResponse.success(results, "모든 실험 완료"))
    }
    
    // ==========================================
    // 실험 3: Bulk Insert 성능 비교
    // ==========================================
    
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

    // ==========================================
    // 실험 4: Transaction Propagation (REQUIRED vs REQUIRES_NEW)
    // ==========================================

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
     *
     * 핵심: inner가 예외를 던지고 outer가 catch해도, 공유 트랜잭션은 이미 rollback-only.
     * 커밋 시 UnexpectedRollbackException 발생!
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
     *
     * WARNING: 이 엔드포인트는 약 30초 소요됩니다!
     * HikariCP 풀 사이즈(10)를 초과하는 REQUIRES_NEW 중첩으로 connectionTimeout 대기 발생.
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
