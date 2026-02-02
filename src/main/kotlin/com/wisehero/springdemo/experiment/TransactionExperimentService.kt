package com.wisehero.springdemo.experiment

import com.wisehero.springdemo.entity.Transaction
import com.wisehero.springdemo.repository.TransactionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.math.BigDecimal
import java.time.LocalDateTime

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
class TransactionExperimentService(
    private val transactionRepository: TransactionRepository
) {
    
    private val log = LoggerFactory.getLogger(javaClass)
    
    // ==========================================
    // 실험 1-A: 자기 호출 시 REQUIRES_NEW가 무시되는 문제
    // ==========================================
    
    @Transactional
    fun experimentSelfInvocation(): Map<String, Any> {
        log.info("========== 실험 1-A: 자기 호출 테스트 시작 ==========")
        
        val result = mutableMapOf<String, Any>()
        
        // 현재 트랜잭션 정보 출력
        val outerTxName = TransactionSynchronizationManager.getCurrentTransactionName()
        val outerTxActive = TransactionSynchronizationManager.isActualTransactionActive()
        val outerTxReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly()
        
        log.info("🔵 [OUTER] 트랜잭션 이름: $outerTxName")
        log.info("🔵 [OUTER] 트랜잭션 활성: $outerTxActive")
        log.info("🔵 [OUTER] 읽기전용: $outerTxReadOnly")
        
        result["outer_tx_name"] = outerTxName ?: "null"
        result["outer_tx_active"] = outerTxActive
        
        // 내부 메서드 호출 (자기 호출 - 프록시 우회!)
        log.info("⚠️ 내부 메서드 호출 (this.innerMethodWithRequiresNew())")
        val innerResult = innerMethodWithRequiresNew()
        
        result["inner_result"] = innerResult
        result["same_transaction"] = (outerTxName == innerResult["tx_name"])
        
        log.info("========== 실험 1-A: 결과 ==========")
        log.info("🔴 같은 트랜잭션인가? ${result["same_transaction"]}")
        log.info("💡 REQUIRES_NEW가 무시되었다면 같은 트랜잭션!")
        
        return result
    }
    
    /**
     * REQUIRES_NEW: 항상 새 트랜잭션을 시작해야 함
     * 하지만 자기 호출 시에는... 무시됨!
     */
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
    
    // ==========================================
    // 실험 1-B: 외부 호출 시 정상 동작 비교
    // ==========================================
    
    @Transactional
    fun experimentExternalCall(externalService: TransactionExperimentExternalService): Map<String, Any> {
        log.info("========== 실험 1-B: 외부 호출 테스트 시작 ==========")
        
        val result = mutableMapOf<String, Any>()
        
        val outerTxName = TransactionSynchronizationManager.getCurrentTransactionName()
        log.info("🔵 [OUTER] 트랜잭션 이름: $outerTxName")
        
        result["outer_tx_name"] = outerTxName ?: "null"
        
        // 외부 서비스 호출 (프록시를 통해 호출됨!)
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
