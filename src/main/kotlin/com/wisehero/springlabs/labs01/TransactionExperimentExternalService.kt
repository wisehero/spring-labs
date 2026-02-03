package com.wisehero.springlabs.labs01

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * 외부 서비스 - 프록시를 통한 호출을 위함
 */
@Service
class TransactionExperimentExternalService {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun methodWithRequiresNew(): Map<String, Any?> {
        val txName = TransactionSynchronizationManager.getCurrentTransactionName()
        val txActive = TransactionSynchronizationManager.isActualTransactionActive()

        log.info("🟢 [EXTERNAL - REQUIRES_NEW] 트랜잭션 이름: $txName")
        log.info("🟢 [EXTERNAL - REQUIRES_NEW] 트랜잭션 활성: $txActive")

        return mapOf(
            "tx_name" to txName,
            "tx_active" to txActive,
            "note" to "외부 호출이므로 새 트랜잭션이 생성됨!"
        )
    }
}
