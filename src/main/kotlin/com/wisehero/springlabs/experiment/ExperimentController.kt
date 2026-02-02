package com.wisehero.springlabs.experiment

import com.wisehero.springlabs.common.dto.ApiResponse
import com.wisehero.springlabs.experiment.dto.ExperimentSummary
import com.wisehero.springlabs.experiment.dto.InsertResult
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
    private val bulkInsertExperimentService: BulkInsertExperimentService
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
}
