package com.wisehero.springlabs.labs04

import com.wisehero.springlabs.entity.Transaction
import com.wisehero.springlabs.labs04.dto.PropagationResult
import com.wisehero.springlabs.repository.TransactionRepository
import jakarta.persistence.EntityManager
import org.hibernate.Session
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.UnexpectedRollbackException
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

/**
 * ==========================================
 * Lab 04: 트랜잭션 전파 실험 - Main Service
 * ==========================================
 *
 * REQUIRED vs REQUIRES_NEW 전파 동작의 모든 흥미로운 차이를 실험합니다.
 *
 * 실험 목록:
 * 4-1: REQUIRED - 외부 트랜잭션 참여 확인
 * 4-2: REQUIRED - 트랜잭션 없을 때 새로 생성
 * 4-3: REQUIRES_NEW - 항상 새 트랜잭션 생성
 * 4-4: REQUIRED inner 예외 시 롤백 전파 트랩 (UnexpectedRollbackException)
 * 4-5: REQUIRES_NEW inner 예외 시 outer 생존
 * 4-6: Outer 실패 시 REQUIRES_NEW inner 데이터 생존
 * 4-7: UnexpectedRollbackException 상세 분석 (3 시나리오)
 * 4-8: DB 커넥션 분리 확인
 * 4-9: 커넥션 풀 고갈 시뮬레이션
 */
@Service
class PropagationExperimentService(
    private val innerService: PropagationExperimentInnerService,
    private val transactionRepository: TransactionRepository,
    private val entityManager: EntityManager,
    private val dataSource: DataSource
) {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val TEST_PREFIX = "PROP-"
    }

    private fun createTestTransaction(businessNo: String): Transaction {
        return Transaction(
            approveDateTime = LocalDateTime.now(),
            amount = BigDecimal("10000"),
            businessNo = businessNo,
            posTransactionNo = UUID.randomUUID().toString().take(20),
            paymentTransactionGuidNo = UUID.randomUUID().toString(),
            spareTransactionGuidNo = UUID.randomUUID().toString(),
            transactionState = "APPROVED"
        )
    }

    @Transactional
    fun experiment4_1_requiredJoinsExisting(): PropagationResult {
        log.info("========== 실험 4-1: REQUIRED 외부 트랜잭션 참여 ==========")

        val outerTxName = TransactionSynchronizationManager.getCurrentTransactionName()
        val outerTxActive = TransactionSynchronizationManager.isActualTransactionActive()
        log.info("[OUTER] tx_name: $outerTxName, active: $outerTxActive")

        val innerResult = innerService.innerWithRequired()
        val innerTxName = innerResult["tx_name"] as? String

        val sameTransaction = outerTxName == innerTxName

        log.info("[결과] 같은 트랜잭션? $sameTransaction")
        log.info("[결과] outer=$outerTxName, inner=$innerTxName")

        return PropagationResult.success(
            experimentId = "4-1",
            experimentName = "REQUIRED - 외부 트랜잭션 존재 시 참여",
            description = "Outer @Transactional(REQUIRED) 내에서 Inner @Transactional(REQUIRED) 호출 시, Inner가 기존 트랜잭션에 참여하는지 확인",
            outerTxName = outerTxName,
            innerTxName = innerTxName,
            sameTransaction = sameTransaction,
            conclusion = if (sameTransaction)
                "REQUIRED는 기존 트랜잭션이 있으면 참여합니다. 두 메서드가 동일한 트랜잭션($outerTxName)을 공유합니다."
            else
                "예상과 다름 - REQUIRED인데 다른 트랜잭션 사용됨. 디버깅 필요.",
            details = mapOf(
                "outer_tx_active" to outerTxActive,
                "inner_tx_active" to (innerResult["tx_active"] ?: false)
            )
        )
    }

    fun experiment4_2_requiredCreatesNew(): PropagationResult {
        log.info("========== 실험 4-2: REQUIRED 트랜잭션 없을 때 새로 생성 ==========")

        val callerTxActive = TransactionSynchronizationManager.isActualTransactionActive()
        val callerTxName = TransactionSynchronizationManager.getCurrentTransactionName()
        log.info("[CALLER - no @Transactional] tx_active: $callerTxActive, tx_name: $callerTxName")

        val innerResult = innerService.innerWithRequired()
        val innerTxName = innerResult["tx_name"] as? String
        val innerTxActive = innerResult["tx_active"] as? Boolean ?: false

        log.info("[결과] caller에 tx 없음: $callerTxActive, inner가 새 tx 생성: $innerTxActive")

        return PropagationResult.success(
            experimentId = "4-2",
            experimentName = "REQUIRED - 트랜잭션 없을 때 새로 생성",
            description = "트랜잭션이 없는 컨텍스트에서 @Transactional(REQUIRED) 메서드 호출 시, 새 트랜잭션을 생성하는지 확인",
            outerTxName = callerTxName,
            innerTxName = innerTxName,
            sameTransaction = false,
            conclusion = if (innerTxActive)
                "REQUIRED는 기존 트랜잭션이 없으면 새로 생성합니다. Inner가 새 트랜잭션($innerTxName)을 시작했습니다."
            else
                "예상과 다름 - REQUIRED인데 트랜잭션이 생성되지 않음. 디버깅 필요.",
            details = mapOf(
                "caller_tx_active" to callerTxActive,
                "caller_tx_name" to (callerTxName ?: "null"),
                "inner_tx_active" to innerTxActive
            )
        )
    }

    @Transactional
    fun experiment4_3_requiresNewAlwaysNew(): PropagationResult {
        log.info("========== 실험 4-3: REQUIRES_NEW 항상 새 트랜잭션 ==========")

        val outerTxName = TransactionSynchronizationManager.getCurrentTransactionName()
        log.info("[OUTER] tx_name: $outerTxName")

        val innerResult = innerService.innerWithRequiresNew()
        val innerTxName = innerResult["tx_name"] as? String

        val sameTransaction = outerTxName == innerTxName

        log.info("[결과] 다른 트랜잭션? ${!sameTransaction}")
        log.info("[결과] outer=$outerTxName, inner=$innerTxName")

        return PropagationResult.success(
            experimentId = "4-3",
            experimentName = "REQUIRES_NEW - 항상 새 트랜잭션 생성",
            description = "Outer @Transactional(REQUIRED) 내에서 Inner @Transactional(REQUIRES_NEW) 호출 시, Inner가 독립적인 새 트랜잭션을 생성하는지 확인",
            outerTxName = outerTxName,
            innerTxName = innerTxName,
            sameTransaction = sameTransaction,
            conclusion = if (!sameTransaction)
                "REQUIRES_NEW는 항상 새 트랜잭션을 생성합니다. Outer($outerTxName)와 Inner($innerTxName)는 독립적인 트랜잭션입니다."
            else
                "예상과 다름 - REQUIRES_NEW인데 같은 트랜잭션 사용됨. 프록시 문제 확인 필요.",
            details = mapOf(
                "outer_tx_active" to TransactionSynchronizationManager.isActualTransactionActive(),
                "inner_tx_active" to (innerResult["tx_active"] ?: false)
            )
        )
    }

    fun experiment4_4_requiredRollbackTrap(): PropagationResult {
        log.info("========== 실험 4-4: REQUIRED 롤백 트랩 (UnexpectedRollbackException) ==========")

        return try {
            innerService.outerWithRequiredCatchingInnerThrow("PROP-4-4")
            PropagationResult.unexpectedSuccess("4-4")
        } catch (e: UnexpectedRollbackException) {
            log.info("[결과] UnexpectedRollbackException 발생! (예상대로)")

            val outerRowExists = transactionRepository.existsByBusinessNo("PROP-4-4-OUTER")
            val innerRowExists = transactionRepository.existsByBusinessNo("PROP-4-4-INNER")
            log.info("[결과] outer 행 존재: $outerRowExists, inner 행 존재: $innerRowExists")

            PropagationResult.rollbackTrap(
                experimentId = "4-4",
                exception = e,
                outerRowExists = outerRowExists,
                innerRowExists = innerRowExists,
                details = mapOf(
                    "trap_explanation" to "REQUIRED로 공유된 트랜잭션에서 inner가 RuntimeException을 던지면, " +
                        "Spring TX 인터셉터가 트랜잭션을 rollback-only로 마킹합니다. " +
                        "outer가 예외를 catch해도, 커밋 시점에 Spring이 rollback-only 플래그를 발견하고 " +
                        "UnexpectedRollbackException을 던집니다. 결과적으로 모든 데이터가 롤백됩니다."
                )
            )
        }
    }

    @Transactional
    fun experiment4_5_requiresNewInnerThrows(): PropagationResult {
        log.info("========== 실험 4-5: REQUIRES_NEW Inner 예외, Outer 생존 ==========")

        val outerTx = createTestTransaction("PROP-4-5-OUTER")
        transactionRepository.save(outerTx)
        log.info("[OUTER] 행 삽입 완료: PROP-4-5-OUTER")

        var innerException: Exception? = null
        try {
            innerService.innerWithRequiresNewAndThrow("PROP-4-5")
        } catch (e: RuntimeException) {
            innerException = e
            log.info("[OUTER] Inner 예외 catch (REQUIRES_NEW이므로 outer에 영향 없음): ${e.message}")
        }

        log.info("[OUTER] 정상 커밋 진행")

        val outerRowExists = transactionRepository.existsByBusinessNo("PROP-4-5-OUTER")
        val innerRowExists = transactionRepository.existsByBusinessNo("PROP-4-5-INNER")

        return PropagationResult.dataExperiment(
            experimentId = "4-5",
            experimentName = "REQUIRES_NEW Inner 예외 - Outer 생존",
            description = "Inner @Transactional(REQUIRES_NEW)가 예외를 던져도, 독립 트랜잭션이므로 Outer는 영향 없이 정상 커밋",
            outerCommitted = outerRowExists,
            innerCommitted = innerRowExists,
            dataVerification = mapOf(
                "outer_row_exists" to outerRowExists,
                "inner_row_exists" to innerRowExists,
                "outer_survived" to (outerRowExists && !innerRowExists)
            ),
            conclusion = "REQUIRES_NEW는 독립 트랜잭션입니다. Inner가 롤백되어도 Outer는 정상 커밋됩니다. " +
                "4-4(REQUIRED)와 비교: REQUIRED에서는 inner 예외가 공유 트랜잭션을 오염시키지만, " +
                "REQUIRES_NEW에서는 완전히 격리됩니다.",
            exceptionOccurred = innerException != null,
            exceptionType = innerException?.let { it::class.simpleName },
            exceptionMessage = innerException?.message
        )
    }

    fun experiment4_6_outerFailsAfterInnerSucceeds(): PropagationResult {
        log.info("========== 실험 4-6: Outer 실패 후 REQUIRES_NEW Inner 생존 ==========")

        return try {
            innerService.outerRequiredThenThrowAfterInnerRequiresNew("PROP-4-6")
            PropagationResult.unexpectedSuccess("4-6")
        } catch (e: RuntimeException) {
            log.info("[결과] Outer RuntimeException 발생: ${e.message}")

            val outerRowExists = transactionRepository.existsByBusinessNo("PROP-4-6-OUTER")
            val innerRowExists = transactionRepository.existsByBusinessNo("PROP-4-6-INNER")
            log.info("[결과] outer 행 존재: $outerRowExists, inner 행 존재: $innerRowExists")

            PropagationResult.dataExperiment(
                experimentId = "4-6",
                experimentName = "Outer 실패 후 REQUIRES_NEW Inner 생존",
                description = "Inner REQUIRES_NEW가 성공적으로 커밋한 후, Outer가 예외를 던지면 Inner 데이터는 생존하는가?",
                outerCommitted = outerRowExists,
                innerCommitted = innerRowExists,
                dataVerification = mapOf(
                    "outer_row_exists" to outerRowExists,
                    "inner_row_exists" to innerRowExists,
                    "inner_survived_outer_rollback" to (innerRowExists && !outerRowExists)
                ),
                conclusion = "REQUIRES_NEW로 생성된 Inner 트랜잭션은 이미 독립적으로 커밋됨. " +
                    "Outer의 롤백과 무관하게 Inner 데이터 생존. " +
                    "이것은 REQUIRES_NEW의 특성이자 위험: 부분 커밋으로 인한 데이터 불일치 가능성.",
                exceptionOccurred = true,
                exceptionType = e::class.simpleName,
                exceptionMessage = e.message
            )
        }
    }

    fun experiment4_7_unexpectedRollbackDeepDive(): PropagationResult {
        log.info("========== 실험 4-7: UnexpectedRollbackException 상세 분석 ==========")

        val scenarioResults = mutableMapOf<String, Any>()

        log.info("--- Scenario A: catch 후 rollback-only 확인 ---")
        val scenarioA = try {
            innerService.outerScenarioA_catchAndCheckRollbackOnly("PROP-4-7A")
        } catch (e: UnexpectedRollbackException) {
            mapOf(
                "exception_type" to (e::class.simpleName ?: "unknown"),
                "exception_message" to (e.message ?: ""),
                "scenario" to "A: UnexpectedRollbackException at commit (expected)"
            )
        }
        scenarioResults["scenario_a"] = scenarioA

        log.info("--- Scenario B: Inner 예외 전파 ---")
        val scenarioB = try {
            innerService.outerScenarioB_letInnerPropagate("PROP-4-7B")
        } catch (e: RuntimeException) {
            mapOf(
                "exception_type" to (e::class.simpleName ?: "unknown"),
                "exception_message" to (e.message ?: ""),
                "is_unexpected_rollback" to (e is UnexpectedRollbackException),
                "scenario" to "B: RuntimeException propagated directly (not wrapped)"
            )
        }
        scenarioResults["scenario_b"] = scenarioB

        log.info("--- Scenario C: setRollbackOnly() 예외 없음 ---")
        val scenarioC = try {
            innerService.outerScenarioC_innerSetsRollbackOnly()
        } catch (e: UnexpectedRollbackException) {
            mapOf(
                "exception_type" to (e::class.simpleName ?: "unknown"),
                "exception_message" to (e.message ?: ""),
                "scenario" to "C: UnexpectedRollbackException even without exception (setRollbackOnly)"
            )
        }
        scenarioResults["scenario_c"] = scenarioC

        return PropagationResult(
            experimentId = "4-7",
            experimentName = "UnexpectedRollbackException 상세 분석",
            description = "공유 트랜잭션(REQUIRED)에서 rollback-only가 마킹되는 3가지 시나리오 비교",
            sameTransaction = true,
            exceptionOccurred = true,
            details = scenarioResults,
            conclusion = "Scenario A: inner 예외를 catch해도 rollback-only 플래그는 이미 설정됨 -> UnexpectedRollbackException. " +
                "Scenario B: inner 예외를 catch하지 않으면 RuntimeException이 그대로 전파됨 (UnexpectedRollbackException이 아님). " +
                "Scenario C: 예외 없이 setRollbackOnly()만 호출해도 커밋 시 UnexpectedRollbackException 발생."
        )
    }

    @Transactional
    fun experiment4_8_connectionSeparation(): PropagationResult {
        log.info("========== 실험 4-8: DB 커넥션 분리 확인 ==========")

        val outerSession = entityManager.unwrap(Session::class.java)
        var outerConnectionId = ""
        outerSession.doWork { connection ->
            outerConnectionId = connection.toString()
        }
        val outerTxName = TransactionSynchronizationManager.getCurrentTransactionName()
        log.info("[OUTER] connection: $outerConnectionId, tx: $outerTxName")

        val requiresNewResult = innerService.innerWithRequiresNewConnectionCheck()
        val requiresNewConnId = requiresNewResult["connection_id"] as? String ?: ""
        log.info("[REQUIRES_NEW] connection: $requiresNewConnId")

        val requiredResult = innerService.innerWithRequiredConnectionCheck()
        val requiredConnId = requiredResult["connection_id"] as? String ?: ""
        log.info("[REQUIRED] connection: $requiredConnId")

        val requiresNewDifferent = outerConnectionId != requiresNewConnId
        val requiredSame = outerConnectionId == requiredConnId

        return PropagationResult.connectionInfo(
            experimentId = "4-8",
            experimentName = "DB 커넥션 분리 확인",
            description = "REQUIRES_NEW가 실제로 별도 DB 커넥션을 사용하는지 확인. REQUIRED(대조군)와 비교.",
            connectionInfo = mapOf(
                "outer_connection" to outerConnectionId,
                "requires_new_connection" to requiresNewConnId,
                "required_connection" to requiredConnId,
                "requires_new_uses_different_connection" to requiresNewDifferent,
                "required_uses_same_connection" to requiredSame
            ),
            conclusion = "REQUIRES_NEW: ${if (requiresNewDifferent) "별도 커넥션 사용 확인" else "같은 커넥션 (예상과 다름)"}. " +
                "REQUIRED(대조군): ${if (requiredSame) "동일 커넥션 사용 확인" else "다른 커넥션 (예상과 다름)"}. " +
                "REQUIRES_NEW는 HikariCP에서 새 커넥션을 가져오며, outer 커넥션은 suspend 상태로 대기합니다. " +
                "동시에 2개 커넥션이 사용되므로 풀 자원에 주의해야 합니다.",
            details = mapOf(
                "outer_tx_name" to (outerTxName ?: "null"),
                "requires_new_tx_name" to (requiresNewResult["tx_name"] ?: "null"),
                "required_tx_name" to (requiredResult["tx_name"] ?: "null")
            )
        )
    }

    fun experiment4_9_connectionPoolExhaustion(): PropagationResult {
        log.info("========== 실험 4-9: 커넥션 풀 고갈 시뮬레이션 ==========")
        log.info("HikariCP 기본 풀 사이즈: 10, connectionTimeout: 30초")
        log.info("REQUIRES_NEW를 11단계 중첩하여 풀 고갈을 유발합니다.")
        log.info("이 실험은 약 30초 소요됩니다.")

        val maxDepth = 11
        val results = mutableListOf<Map<String, Any>>()
        val startTime = System.currentTimeMillis()

        return try {
            innerService.innerWithRequiresNewRecursive(1, maxDepth, results)

            val elapsed = System.currentTimeMillis() - startTime
            PropagationResult.connectionInfo(
                experimentId = "4-9",
                experimentName = "커넥션 풀 고갈 시뮬레이션",
                description = "REQUIRES_NEW를 ${maxDepth}단계 중첩했으나 풀이 고갈되지 않음",
                connectionInfo = mapOf(
                    "max_depth_attempted" to maxDepth,
                    "depth_reached" to results.size,
                    "elapsed_ms" to elapsed,
                    "depth_details" to results
                ),
                conclusion = "예상과 다른 결과: 풀이 고갈되지 않았습니다. 풀 사이즈를 확인하세요."
            )
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            val depthReached = results.size
            log.info("[결과] 깊이 ${depthReached}에서 실패!")
            log.info("[결과] 예외: ${e::class.simpleName}: ${e.message}")
            log.info("[결과] 소요시간: ${elapsed}ms")

            val uniqueConnections = results.map { it["connection_id"] }.distinct().size

            PropagationResult.connectionInfo(
                experimentId = "4-9",
                experimentName = "커넥션 풀 고갈 시뮬레이션",
                description = "REQUIRES_NEW를 ${maxDepth}단계 중첩하여 HikariCP 커넥션 풀 고갈을 시뮬레이션",
                connectionInfo = mapOf(
                    "max_depth_attempted" to maxDepth,
                    "depth_reached" to depthReached,
                    "unique_connections_used" to uniqueConnections,
                    "elapsed_ms" to elapsed,
                    "depth_details" to results
                ),
                exceptionOccurred = true,
                exceptionType = e::class.simpleName,
                exceptionMessage = e.message,
                conclusion = "깊이 ${depthReached}까지 성공 후, 깊이 ${depthReached + 1}에서 커넥션 풀 고갈. " +
                    "HikariCP connectionTimeout(30초) 후 예외 발생. " +
                    "REQUIRES_NEW는 각 호출마다 새 커넥션을 점유하며, outer 커넥션은 suspend 상태로 반환되지 않습니다. " +
                    "중첩 깊이가 풀 사이즈를 초과하면 교착 상태가 발생합니다. " +
                    "실무에서 REQUIRES_NEW를 루프나 재귀 내에서 사용할 때 반드시 주의해야 합니다.",
                details = mapOf(
                    "pool_size_default" to 10,
                    "connection_timeout_ms" to 30000,
                    "recommendation" to "REQUIRES_NEW 중첩 깊이는 항상 커넥션 풀 사이즈 미만으로 유지하세요."
                )
            )
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun cleanupTestData(): Int {
        val deleted = transactionRepository.deleteByBusinessNoStartingWith(TEST_PREFIX)
        if (deleted > 0) {
            log.info("Lab 04 테스트 데이터 ${deleted}건 삭제")
        }
        return deleted.toInt()
    }
}
