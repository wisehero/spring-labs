package com.wisehero.springlabs.experiment

import com.wisehero.springlabs.entity.Transaction
import com.wisehero.springlabs.repository.TransactionRepository
import jakarta.persistence.EntityManager
import org.hibernate.Session
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.interceptor.TransactionAspectSupport
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

/**
 * ==========================================
 * Lab 04: 트랜잭션 전파 실험 - Inner Service
 * ==========================================
 *
 * PropagationExperimentService에서 호출되는 별도 빈.
 * Spring AOP 프록시를 통한 호출을 보장하여 @Transactional 전파가 정상 동작합니다.
 *
 * 역할:
 * 1. "Inner" 메서드: REQUIRED/REQUIRES_NEW로 호출되어 전파 동작 관찰
 * 2. "Outer Transactional" 래퍼: 4-4, 4-6, 4-7에서 UnexpectedRollbackException을
 *    발생시키는 @Transactional 메서드 (non-transactional wrapper 패턴의 내부 레이어)
 */
@Service
class PropagationExperimentInnerService(
    private val transactionRepository: TransactionRepository,
    private val entityManager: EntityManager
) {

    private val log = LoggerFactory.getLogger(javaClass)

    // ============================================================
    // Self-injection for proxy-based recursive calls (Experiment 4-9)
    // ============================================================
    // WARNING: Without self-injection, this.innerWithRequiresNewRecursive(...)
    // bypasses the Spring proxy. REQUIRES_NEW would be silently IGNORED,
    // and the recursive call would join the existing transaction instead of
    // creating a new one. The pool exhaustion experiment would silently fail.
    //
    // This is the Lab 01 self-invocation trap applied in practice.
    // Same pattern as BulkInsertExperimentService.
    // ============================================================
    @Lazy
    @Autowired
    private lateinit var self: PropagationExperimentInnerService

    // ==========================================
    // 테스트 데이터 생성 헬퍼
    // ==========================================

    /**
     * Transaction 엔티티 생성 헬퍼
     * Transaction은 7개의 non-nullable 필드를 가짐 - 모두 채워야 함
     * BulkInsertExperimentService.generateTestTransactions() 패턴 참조
     */
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

    private fun getCurrentTxInfo(): Map<String, Any?> {
        return mapOf(
            "tx_name" to TransactionSynchronizationManager.getCurrentTransactionName(),
            "tx_active" to TransactionSynchronizationManager.isActualTransactionActive(),
            "tx_read_only" to TransactionSynchronizationManager.isCurrentTransactionReadOnly()
        )
    }

    // ==========================================
    // Pure "Inner" 메서드 - 외부 트랜잭션 컨텍스트에서 호출됨
    // ==========================================

    /**
     * 4-1: REQUIRED inner - 기존 트랜잭션에 참여
     */
    @Transactional(propagation = Propagation.REQUIRED)
    fun innerWithRequired(): Map<String, Any?> {
        val txInfo = getCurrentTxInfo()
        log.info("  [INNER - REQUIRED] tx_name: ${txInfo["tx_name"]}")
        log.info("  [INNER - REQUIRED] tx_active: ${txInfo["tx_active"]}")
        return txInfo
    }

    /**
     * 4-4, 4-7 Scenario A/B: REQUIRED inner가 예외를 던짐
     * 동일한 트랜잭션에 참여한 상태에서 RuntimeException을 던져서
     * 공유 트랜잭션을 rollback-only로 마킹
     */
    @Transactional(propagation = Propagation.REQUIRED)
    fun innerWithRequiredAndThrow(businessNoPrefix: String): Nothing {
        log.info("  [INNER - REQUIRED + THROW] 행 삽입 시도: ${businessNoPrefix}-INNER")
        // GenerationType.IDENTITY: persist() 시 즉시 INSERT 실행됨 - flush() 불필요
        val tx = createTestTransaction("${businessNoPrefix}-INNER")
        transactionRepository.save(tx)
        log.info("  [INNER - REQUIRED + THROW] 행 삽입 완료, RuntimeException 던짐")
        throw RuntimeException("Inner method deliberately throws (REQUIRED, shared tx)")
    }

    /**
     * 4-7 Scenario C: REQUIRED inner가 setRollbackOnly() 호출 후 정상 리턴
     * 예외 없이 트랜잭션을 rollback-only로 마킹
     */
    @Transactional(propagation = Propagation.REQUIRED)
    fun innerWithRequiredAndSetRollbackOnly(): Map<String, Any?> {
        log.info("  [INNER - REQUIRED + setRollbackOnly] 예외 없이 rollback-only 마킹")
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()
        val isRollbackOnly = TransactionAspectSupport.currentTransactionStatus().isRollbackOnly
        log.info("  [INNER - REQUIRED + setRollbackOnly] isRollbackOnly = $isRollbackOnly")
        return mapOf(
            "action" to "setRollbackOnly() called without exception",
            "is_rollback_only" to isRollbackOnly
        )
    }

    /**
     * 4-3: REQUIRES_NEW inner - 항상 새 트랜잭션 생성
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun innerWithRequiresNew(): Map<String, Any?> {
        val txInfo = getCurrentTxInfo()
        log.info("  [INNER - REQUIRES_NEW] tx_name: ${txInfo["tx_name"]}")
        log.info("  [INNER - REQUIRES_NEW] tx_active: ${txInfo["tx_active"]}")
        return txInfo
    }

    /**
     * 4-5: REQUIRES_NEW inner가 예외를 던짐
     * 독립 트랜잭션에서 롤백 - outer에 영향 없음
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun innerWithRequiresNewAndThrow(businessNoPrefix: String): Nothing {
        log.info("  [INNER - REQUIRES_NEW + THROW] 행 삽입 시도: ${businessNoPrefix}-INNER")
        val tx = createTestTransaction("${businessNoPrefix}-INNER")
        transactionRepository.save(tx)
        log.info("  [INNER - REQUIRES_NEW + THROW] 행 삽입 완료, RuntimeException 던짐")
        throw RuntimeException("Inner method deliberately throws (REQUIRES_NEW, independent tx)")
    }

    /**
     * 4-6: REQUIRES_NEW inner가 성공적으로 삽입 후 커밋
     * 독립 트랜잭션으로 커밋 - outer 롤백과 무관하게 생존
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun innerWithRequiresNewAndInsert(businessNoPrefix: String): Map<String, Any?> {
        val txInfo = getCurrentTxInfo()
        log.info("  [INNER - REQUIRES_NEW + INSERT] tx_name: ${txInfo["tx_name"]}")
        val tx = createTestTransaction("${businessNoPrefix}-INNER")
        transactionRepository.save(tx)
        log.info("  [INNER - REQUIRES_NEW + INSERT] 행 삽입 성공: ${businessNoPrefix}-INNER")
        return txInfo + mapOf("inserted_businessNo" to "${businessNoPrefix}-INNER")
    }

    /**
     * 4-8: REQUIRES_NEW 커넥션 확인 - 새 커넥션을 사용하는지 확인
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun innerWithRequiresNewConnectionCheck(): Map<String, Any?> {
        val txInfo = getCurrentTxInfo()
        val session = entityManager.unwrap(Session::class.java)
        var connectionId = ""
        session.doWork { connection ->
            connectionId = connection.toString()
        }
        log.info("  [INNER - REQUIRES_NEW] connection: $connectionId")
        return txInfo + mapOf("connection_id" to connectionId)
    }

    /**
     * 4-8 대조군: REQUIRED 커넥션 확인 - 같은 커넥션을 사용하는지 확인
     */
    @Transactional(propagation = Propagation.REQUIRED)
    fun innerWithRequiredConnectionCheck(): Map<String, Any?> {
        val txInfo = getCurrentTxInfo()
        val session = entityManager.unwrap(Session::class.java)
        var connectionId = ""
        session.doWork { connection ->
            connectionId = connection.toString()
        }
        log.info("  [INNER - REQUIRED] connection: $connectionId")
        return txInfo + mapOf("connection_id" to connectionId)
    }

    /**
     * 4-9: REQUIRES_NEW 재귀 호출 - 커넥션 풀 고갈 테스트
     *
     * WARNING: MUST use self.innerWithRequiresNewRecursive() for recursive call!
     * Direct this.innerWithRequiresNewRecursive() bypasses the proxy and
     * REQUIRES_NEW would be silently ignored. This is the Lab 01 self-invocation
     * trap -- without the proxy, each "recursive" call just joins the existing
     * transaction, no new connection is acquired, and pool exhaustion never occurs.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun innerWithRequiresNewRecursive(
        depth: Int,
        maxDepth: Int,
        results: MutableList<Map<String, Any>>
    ) {
        val txName = TransactionSynchronizationManager.getCurrentTransactionName()
        val session = entityManager.unwrap(Session::class.java)
        var connectionId = ""
        session.doWork { connection ->
            connectionId = connection.toString()
        }

        val depthInfo = mapOf<String, Any>(
            "depth" to depth,
            "tx_name" to (txName ?: "null"),
            "connection_id" to connectionId
        )
        results.add(depthInfo)

        log.info("  [RECURSIVE depth=$depth] tx: $txName, conn: $connectionId")

        if (depth < maxDepth) {
            // MUST use self.xxx() to go through the proxy!
            // Direct this.xxx() would bypass proxy and REQUIRES_NEW would be ignored
            self.innerWithRequiresNewRecursive(depth + 1, maxDepth, results)
        }
    }

    // ==========================================
    // "Outer Transactional" 래퍼 메서드
    // ==========================================
    // Non-transactional wrapper 패턴의 내부 레이어.
    // PropagationExperimentService의 non-transactional 메서드에서 호출됨.
    // 이 메서드들이 @Transactional을 가지므로 UnexpectedRollbackException이
    // 여기서 발생하고, 호출자(non-transactional wrapper)가 catch할 수 있음.

    /**
     * 4-4: Outer tx가 inner REQUIRED의 예외를 catch하는 트랩
     * Inner가 RuntimeException을 던지면 공유 트랜잭션이 rollback-only로 마킹됨.
     * Outer가 catch해도 커밋 시 UnexpectedRollbackException 발생.
     */
    @Transactional
    fun outerWithRequiredCatchingInnerThrow(businessNoPrefix: String): Map<String, Any?> {
        val outerTxName = TransactionSynchronizationManager.getCurrentTransactionName()
        log.info("[OUTER - 4-4] tx_name: $outerTxName")

        // 1. Outer 행 삽입
        // GenerationType.IDENTITY: persist() 시 즉시 INSERT 실행됨 - flush() 불필요
        val outerTx = createTestTransaction("${businessNoPrefix}-OUTER")
        transactionRepository.save(outerTx)
        log.info("[OUTER - 4-4] 행 삽입 완료: ${businessNoPrefix}-OUTER")

        // 2. Inner REQUIRED 호출 - 예외를 catch (THE TRAP!)
        try {
            innerWithRequiredAndThrow(businessNoPrefix)
        } catch (e: RuntimeException) {
            log.info("[OUTER - 4-4] Inner 예외 catch: ${e.message}")
            log.info("[OUTER - 4-4] 하지만 트랜잭션은 이미 rollback-only!")
        }

        // 3. 정상 리턴 시도 -> Spring이 UnexpectedRollbackException을 던짐
        log.info("[OUTER - 4-4] 정상 리턴 시도... (커밋 시 UnexpectedRollbackException 예상)")
        return mapOf("outer_tx_name" to outerTxName)
    }

    /**
     * 4-6: Outer tx가 inner REQUIRES_NEW 성공 후 의도적으로 실패
     * Inner는 독립 트랜잭션으로 이미 커밋됨. Outer만 롤백.
     */
    @Transactional
    fun outerRequiredThenThrowAfterInnerRequiresNew(businessNoPrefix: String): Nothing {
        val outerTxName = TransactionSynchronizationManager.getCurrentTransactionName()
        log.info("[OUTER - 4-6] tx_name: $outerTxName")

        // 1. Outer 행 삽입
        val outerTx = createTestTransaction("${businessNoPrefix}-OUTER")
        transactionRepository.save(outerTx)
        log.info("[OUTER - 4-6] 행 삽입 완료: ${businessNoPrefix}-OUTER")

        // 2. Inner REQUIRES_NEW 호출 - 독립 트랜잭션으로 커밋됨
        val innerResult = innerWithRequiresNewAndInsert(businessNoPrefix)
        log.info("[OUTER - 4-6] Inner REQUIRES_NEW 성공: $innerResult")

        // 3. Outer 의도적으로 실패
        log.info("[OUTER - 4-6] Outer 의도적 실패! Inner 데이터는 이미 커밋됨.")
        throw RuntimeException("Outer deliberately fails after inner REQUIRES_NEW succeeded")
    }

    /**
     * 4-7 Scenario A: Inner 예외 catch 후 rollback-only 플래그 확인
     */
    @Transactional
    fun outerScenarioA_catchAndCheckRollbackOnly(businessNoPrefix: String): Map<String, Any?> {
        val outerTxName = TransactionSynchronizationManager.getCurrentTransactionName()
        log.info("[OUTER - 4-7A] tx_name: $outerTxName")

        var isRollbackOnly = false
        try {
            innerWithRequiredAndThrow(businessNoPrefix)
        } catch (e: RuntimeException) {
            log.info("[OUTER - 4-7A] Inner 예외 catch: ${e.message}")
            isRollbackOnly = TransactionAspectSupport.currentTransactionStatus().isRollbackOnly
            log.info("[OUTER - 4-7A] isRollbackOnly = $isRollbackOnly (catch 후에도 true!)")
        }

        return mapOf(
            "outer_tx_name" to outerTxName,
            "is_rollback_only_after_catch" to isRollbackOnly,
            "scenario" to "A: catch inner exception, check rollback-only flag"
        )
    }

    /**
     * 4-7 Scenario B: Inner 예외를 catch하지 않음 - 그대로 전파
     */
    @Transactional
    fun outerScenarioB_letInnerPropagate(businessNoPrefix: String): Map<String, Any?> {
        val outerTxName = TransactionSynchronizationManager.getCurrentTransactionName()
        log.info("[OUTER - 4-7B] tx_name: $outerTxName")
        log.info("[OUTER - 4-7B] Inner 예외를 catch하지 않음 - 그대로 전파")

        // Inner의 RuntimeException이 그대로 전파됨
        innerWithRequiredAndThrow(businessNoPrefix)

        // 이 코드에 도달하지 않음
        return mapOf("outer_tx_name" to outerTxName)
    }

    /**
     * 4-7 Scenario C: Inner가 setRollbackOnly() 호출, 예외 없이 리턴
     */
    @Transactional
    fun outerScenarioC_innerSetsRollbackOnly(): Map<String, Any?> {
        val outerTxName = TransactionSynchronizationManager.getCurrentTransactionName()
        log.info("[OUTER - 4-7C] tx_name: $outerTxName")

        // Inner가 setRollbackOnly()를 호출하고 정상 리턴
        val innerResult = innerWithRequiredAndSetRollbackOnly()
        log.info("[OUTER - 4-7C] Inner 정상 리턴: $innerResult")

        val isRollbackOnly = TransactionAspectSupport.currentTransactionStatus().isRollbackOnly
        log.info("[OUTER - 4-7C] Outer isRollbackOnly = $isRollbackOnly (Inner가 마킹함)")

        // 정상 리턴 시도 -> Spring이 UnexpectedRollbackException을 던짐
        return mapOf(
            "outer_tx_name" to outerTxName,
            "is_rollback_only" to isRollbackOnly,
            "inner_result" to innerResult,
            "scenario" to "C: inner calls setRollbackOnly(), no exception"
        )
    }
}
