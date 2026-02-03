package com.wisehero.springlabs.labs04

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

    @Lazy
    @Autowired
    private lateinit var self: PropagationExperimentInnerService

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

    @Transactional(propagation = Propagation.REQUIRED)
    fun innerWithRequired(): Map<String, Any?> {
        val txInfo = getCurrentTxInfo()
        log.info("  [INNER - REQUIRED] tx_name: ${txInfo["tx_name"]}")
        log.info("  [INNER - REQUIRED] tx_active: ${txInfo["tx_active"]}")
        return txInfo
    }

    @Transactional(propagation = Propagation.REQUIRED)
    fun innerWithRequiredAndThrow(businessNoPrefix: String): Nothing {
        log.info("  [INNER - REQUIRED + THROW] 행 삽입 시도: ${businessNoPrefix}-INNER")
        val tx = createTestTransaction("${businessNoPrefix}-INNER")
        transactionRepository.save(tx)
        log.info("  [INNER - REQUIRED + THROW] 행 삽입 완료, RuntimeException 던짐")
        throw RuntimeException("Inner method deliberately throws (REQUIRED, shared tx)")
    }

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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun innerWithRequiresNew(): Map<String, Any?> {
        val txInfo = getCurrentTxInfo()
        log.info("  [INNER - REQUIRES_NEW] tx_name: ${txInfo["tx_name"]}")
        log.info("  [INNER - REQUIRES_NEW] tx_active: ${txInfo["tx_active"]}")
        return txInfo
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun innerWithRequiresNewAndThrow(businessNoPrefix: String): Nothing {
        log.info("  [INNER - REQUIRES_NEW + THROW] 행 삽입 시도: ${businessNoPrefix}-INNER")
        val tx = createTestTransaction("${businessNoPrefix}-INNER")
        transactionRepository.save(tx)
        log.info("  [INNER - REQUIRES_NEW + THROW] 행 삽입 완료, RuntimeException 던짐")
        throw RuntimeException("Inner method deliberately throws (REQUIRES_NEW, independent tx)")
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun innerWithRequiresNewAndInsert(businessNoPrefix: String): Map<String, Any?> {
        val txInfo = getCurrentTxInfo()
        log.info("  [INNER - REQUIRES_NEW + INSERT] tx_name: ${txInfo["tx_name"]}")
        val tx = createTestTransaction("${businessNoPrefix}-INNER")
        transactionRepository.save(tx)
        log.info("  [INNER - REQUIRES_NEW + INSERT] 행 삽입 성공: ${businessNoPrefix}-INNER")
        return txInfo + mapOf("inserted_businessNo" to "${businessNoPrefix}-INNER")
    }

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
            self.innerWithRequiresNewRecursive(depth + 1, maxDepth, results)
        }
    }

    @Transactional
    fun outerWithRequiredCatchingInnerThrow(businessNoPrefix: String): Map<String, Any?> {
        val outerTxName = TransactionSynchronizationManager.getCurrentTransactionName()
        log.info("[OUTER - 4-4] tx_name: $outerTxName")

        val outerTx = createTestTransaction("${businessNoPrefix}-OUTER")
        transactionRepository.save(outerTx)
        log.info("[OUTER - 4-4] 행 삽입 완료: ${businessNoPrefix}-OUTER")

        try {
            self.innerWithRequiredAndThrow(businessNoPrefix)
        } catch (e: RuntimeException) {
            log.info("[OUTER - 4-4] Inner 예외 catch: ${e.message}")
            log.info("[OUTER - 4-4] 하지만 트랜잭션은 이미 rollback-only!")
        }

        log.info("[OUTER - 4-4] 정상 리턴 시도... (커밋 시 UnexpectedRollbackException 예상)")
        return mapOf("outer_tx_name" to outerTxName)
    }

    @Transactional
    fun outerRequiredThenThrowAfterInnerRequiresNew(businessNoPrefix: String): Nothing {
        val outerTxName = TransactionSynchronizationManager.getCurrentTransactionName()
        log.info("[OUTER - 4-6] tx_name: $outerTxName")

        val outerTx = createTestTransaction("${businessNoPrefix}-OUTER")
        transactionRepository.save(outerTx)
        log.info("[OUTER - 4-6] 행 삽입 완료: ${businessNoPrefix}-OUTER")

        val innerResult = self.innerWithRequiresNewAndInsert(businessNoPrefix)
        log.info("[OUTER - 4-6] Inner REQUIRES_NEW 성공: $innerResult")

        log.info("[OUTER - 4-6] Outer 의도적 실패! Inner 데이터는 이미 커밋됨.")
        throw RuntimeException("Outer deliberately fails after inner REQUIRES_NEW succeeded")
    }

    @Transactional
    fun outerScenarioA_catchAndCheckRollbackOnly(businessNoPrefix: String): Map<String, Any?> {
        val outerTxName = TransactionSynchronizationManager.getCurrentTransactionName()
        log.info("[OUTER - 4-7A] tx_name: $outerTxName")

        var isRollbackOnly = false
        try {
            self.innerWithRequiredAndThrow(businessNoPrefix)
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

    @Transactional
    fun outerScenarioB_letInnerPropagate(businessNoPrefix: String): Map<String, Any?> {
        val outerTxName = TransactionSynchronizationManager.getCurrentTransactionName()
        log.info("[OUTER - 4-7B] tx_name: $outerTxName")
        log.info("[OUTER - 4-7B] Inner 예외를 catch하지 않음 - 그대로 전파")

        self.innerWithRequiredAndThrow(businessNoPrefix)

        return mapOf("outer_tx_name" to outerTxName)
    }

    @Transactional
    fun outerScenarioC_innerSetsRollbackOnly(): Map<String, Any?> {
        val outerTxName = TransactionSynchronizationManager.getCurrentTransactionName()
        log.info("[OUTER - 4-7C] tx_name: $outerTxName")

        val innerResult = self.innerWithRequiredAndSetRollbackOnly()
        log.info("[OUTER - 4-7C] Inner 정상 리턴: $innerResult")

        val isRollbackOnly = TransactionAspectSupport.currentTransactionStatus().isRollbackOnly
        log.info("[OUTER - 4-7C] Outer isRollbackOnly = $isRollbackOnly (Inner가 마킹함)")

        return mapOf(
            "outer_tx_name" to outerTxName,
            "is_rollback_only" to isRollbackOnly,
            "inner_result" to innerResult,
            "scenario" to "C: inner calls setRollbackOnly(), no exception"
        )
    }
}
