package com.wisehero.springlabs.labs01

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * ==========================================
 * 실험 1: @Transactional 자기 호출 함정
 * ==========================================
 *
 * Spring AOP 프록시의 한계를 직접 테스트해봅니다.
 *
 * 핵심 개념:
 * - Spring @Transactional은 AOP 프록시를 통해 동작
 * - 같은 클래스 내부에서 호출하면 프록시를 거치지 않음 (this.method())
 * - 따라서 내부 호출 시 @Transactional 설정이 무시됨!
 */
@Service
class TransactionExperimentService {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun experimentSelfInvocation(): Map<String, Any> {
        log.info("========== 실험 1-A: 자기 호출 테스트 시작 ==========")

        val result = mutableMapOf<String, Any>()

        val outerTxName = TransactionSynchronizationManager.getCurrentTransactionName()
        val outerTxActive = TransactionSynchronizationManager.isActualTransactionActive()
        val outerTxReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly()

        log.info("🔵 [OUTER] 트랜잭션 이름: $outerTxName")
        log.info("🔵 [OUTER] 트랜잭션 활성: $outerTxActive")
        log.info("🔵 [OUTER] 읽기전용: $outerTxReadOnly")

        result["outer_tx_name"] = outerTxName ?: "null"
        result["outer_tx_active"] = outerTxActive

        log.info("⚠️ 내부 메서드 호출 (this.innerMethodWithRequiresNew())")
        val innerResult = innerMethodWithRequiresNew()

        result["inner_result"] = innerResult
        result["same_transaction"] = (outerTxName == innerResult["tx_name"])

        log.info("========== 실험 1-A: 결과 ==========")
        log.info("🔴 같은 트랜잭션인가? ${result["same_transaction"]}")
        log.info("💡 REQUIRES_NEW가 무시되었다면 같은 트랜잭션!")

        return result
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun innerMethodWithRequiresNew(): Map<String, Any?> {
        val txName = TransactionSynchronizationManager.getCurrentTransactionName()
        val txActive = TransactionSynchronizationManager.isActualTransactionActive()

        log.info("🟢 [INNER - REQUIRES_NEW] 트랜잭션 이름: $txName")
        log.info("🟢 [INNER - REQUIRES_NEW] 트랜잭션 활성: $txActive")

        return mapOf(
            "tx_name" to txName,
            "tx_active" to txActive,
            "expected" to "새 트랜잭션이어야 하지만... 자기호출이면 같음!"
        )
    }

    @Transactional
    fun experimentExternalCall(externalService: TransactionExperimentExternalService): Map<String, Any> {
        log.info("========== 실험 1-B: 외부 호출 테스트 시작 ==========")

        val result = mutableMapOf<String, Any>()

        val outerTxName = TransactionSynchronizationManager.getCurrentTransactionName()
        log.info("🔵 [OUTER] 트랜잭션 이름: $outerTxName")

        result["outer_tx_name"] = outerTxName ?: "null"

        log.info("✅ 외부 서비스 호출 (externalService.methodWithRequiresNew())")
        val innerResult = externalService.methodWithRequiresNew()

        result["inner_result"] = innerResult
        result["same_transaction"] = (outerTxName == innerResult["tx_name"])

        log.info("========== 실험 1-B: 결과 ==========")
        log.info("🟢 같은 트랜잭션인가? ${result["same_transaction"]}")
        log.info("💡 외부 호출이므로 REQUIRES_NEW가 정상 동작 = 다른 트랜잭션!")

        return result
    }
}
