package com.wisehero.springlabs.experiment

import com.wisehero.springlabs.entity.Transaction
import com.wisehero.springlabs.repository.TransactionRepository
import jakarta.persistence.EntityManager
import org.hibernate.Session
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * ==========================================
 * 실험 2: @Transactional(readOnly = true) 실제 효과
 * ==========================================
 * 
 * readOnly=true가 실제로 무엇을 하는지 테스트합니다.
 * 
 * 알려진 효과:
 * 1. Hibernate FlushMode가 MANUAL로 변경 → 더티체킹 스킵
 * 2. 일부 DB는 read replica로 라우팅 가능
 * 3. 성능 최적화 힌트로 사용
 * 
 * 하지만 실제로는?
 * - persist()가 막히나? → NO! (flush 시점까지 보류됨)
 * - 읽기만 가능하나? → NO! (FlushMode에 따라 다름)
 */
@Service
class ReadOnlyExperimentService(
    private val transactionRepository: TransactionRepository,
    private val entityManager: EntityManager
) {
    
    private val log = LoggerFactory.getLogger(javaClass)
    
    // ==========================================
    // 실험 2-A: readOnly=true에서 트랜잭션 상태 확인
    // ==========================================
    
    @Transactional(readOnly = true)
    fun experimentReadOnlyStatus(): Map<String, Any?> {
        log.info("========== 실험 2-A: readOnly 상태 확인 ==========")
        
        val result = mutableMapOf<String, Any?>()
        
        // 트랜잭션 정보
        val txName = TransactionSynchronizationManager.getCurrentTransactionName()
        val txActive = TransactionSynchronizationManager.isActualTransactionActive()
        val txReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly()
        
        log.info("📖 트랜잭션 이름: $txName")
        log.info("📖 트랜잭션 활성: $txActive")
        log.info("📖 읽기전용 플래그: $txReadOnly")
        
        result["tx_name"] = txName
        result["tx_active"] = txActive
        result["tx_readonly"] = txReadOnly
        
        // Hibernate Session 정보
        val session = entityManager.unwrap(Session::class.java)
        val flushMode = session.hibernateFlushMode
        val defaultReadOnly = session.isDefaultReadOnly
        
        log.info("🔧 Hibernate FlushMode: $flushMode")
        log.info("🔧 Session DefaultReadOnly: $defaultReadOnly")
        
        result["hibernate_flush_mode"] = flushMode.toString()
        result["session_default_readonly"] = defaultReadOnly
        
        log.info("========== 실험 2-A: 결과 ==========")
        log.info("💡 readOnly=true일 때 FlushMode가 MANUAL이면 더티체킹 스킵!")
        
        return result
    }
    
    // ==========================================
    // 실험 2-B: readOnly=true에서 수정 시도
    // ==========================================
    
    @Transactional(readOnly = true)
    fun experimentReadOnlyWithModification(transactionId: Long): Map<String, Any?> {
        log.info("========== 실험 2-B: readOnly에서 수정 시도 ==========")
        
        val result = mutableMapOf<String, Any?>()
        
        // 기존 엔티티 조회
        val transaction = transactionRepository.findById(transactionId).orElse(null)
        
        if (transaction == null) {
            result["error"] = "Transaction not found: $transactionId"
            return result
        }
        
        val originalAmount = transaction.amount
        log.info("📦 원본 금액: $originalAmount")
        
        result["original_amount"] = originalAmount
        
        // 엔티티 수정 시도 (더티체킹 대상)
        // Transaction이 immutable class라서 새 객체로 테스트해봐야 함
        // 대신 native query로 직접 수정 시도
        
        val session = entityManager.unwrap(Session::class.java)
        val flushModeBefore = session.hibernateFlushMode
        
        log.info("🔧 수정 전 FlushMode: $flushModeBefore")
        result["flush_mode_before"] = flushModeBefore.toString()
        
        // 수동으로 flush 시도
        try {
            log.info("⚠️ 수동 flush 시도...")
            entityManager.flush()
            result["manual_flush"] = "성공 (변경사항 없어서)"
            log.info("✅ flush 성공 (변경사항이 없어서 성공)")
        } catch (e: Exception) {
            result["manual_flush"] = "실패: ${e.message}"
            log.error("❌ flush 실패: ${e.message}")
        }
        
        log.info("========== 실험 2-B: 결과 ==========")
        log.info("💡 readOnly=true여도 flush()는 호출 가능! (변경사항이 없으면)")
        
        return result
    }
    
    // ==========================================
    // 실험 2-C: readOnly=true vs false 성능 비교
    // ==========================================
    
    @Transactional(readOnly = true)
    fun experimentReadOnlyPerformance(): Map<String, Any?> {
        log.info("========== 실험 2-C: readOnly 성능 (readOnly=true) ==========")
        
        val result = mutableMapOf<String, Any?>()
        val startTime = System.currentTimeMillis()
        
        // 대량 조회
        val transactions = transactionRepository.findAll()
        val fetchTime = System.currentTimeMillis() - startTime
        
        result["readOnly"] = true
        result["count"] = transactions.size
        result["fetch_time_ms"] = fetchTime
        
        // Session 상태 확인
        val session = entityManager.unwrap(Session::class.java)
        val stats = session.statistics
        
        log.info("📊 조회 건수: ${transactions.size}")
        log.info("📊 조회 시간: ${fetchTime}ms")
        log.info("📊 FlushMode: ${session.hibernateFlushMode}")
        log.info("📊 Entity Count in Session: ${stats.entityCount}")
        
        result["flush_mode"] = session.hibernateFlushMode.toString()
        result["entity_count_in_session"] = stats.entityCount
        
        return result
    }
    
    @Transactional(readOnly = false)
    fun experimentWritablePerformance(): Map<String, Any?> {
        log.info("========== 실험 2-C: readOnly 성능 (readOnly=false) ==========")
        
        val result = mutableMapOf<String, Any?>()
        val startTime = System.currentTimeMillis()
        
        // 대량 조회
        val transactions = transactionRepository.findAll()
        val fetchTime = System.currentTimeMillis() - startTime
        
        result["readOnly"] = false
        result["count"] = transactions.size
        result["fetch_time_ms"] = fetchTime
        
        // Session 상태 확인
        val session = entityManager.unwrap(Session::class.java)
        val stats = session.statistics
        
        log.info("📊 조회 건수: ${transactions.size}")
        log.info("📊 조회 시간: ${fetchTime}ms")
        log.info("📊 FlushMode: ${session.hibernateFlushMode}")
        log.info("📊 Entity Count in Session: ${stats.entityCount}")
        
        result["flush_mode"] = session.hibernateFlushMode.toString()
        result["entity_count_in_session"] = stats.entityCount
        
        return result
    }
    
    // ==========================================
    // 실험 2-D: readOnly=true에서 새 엔티티 persist 시도
    // ==========================================
    
    @Transactional(readOnly = true)
    fun experimentReadOnlyWithPersist(): Map<String, Any?> {
        log.info("========== 실험 2-D: readOnly에서 persist 시도 ==========")
        
        val result = mutableMapOf<String, Any?>()
        
        val session = entityManager.unwrap(Session::class.java)
        log.info("🔧 FlushMode: ${session.hibernateFlushMode}")
        
        // 새 엔티티 생성
        val newTransaction = Transaction(
            approveDateTime = LocalDateTime.now(),
            amount = BigDecimal("99999.99"),
            businessNo = "TEST-READONLY",
            posTransactionNo = "READONLY-TEST-001",
            paymentTransactionGuidNo = "test-guid-readonly",
            spareTransactionGuidNo = "test-spare-readonly",
            transactionState = "TEST"
        )
        
        try {
            log.info("⚠️ persist 시도...")
            entityManager.persist(newTransaction)
            log.info("✅ persist 호출 성공! (아직 DB에 반영 안됨)")
            result["persist_call"] = "성공"
            
            // persist는 성공하지만 flush가 안되면 DB에 반영 안됨
            log.info("⚠️ flush 시도...")
            entityManager.flush()
            log.info("❓ flush도 성공?!")
            result["flush_call"] = "성공 (예상 외!)"
            result["new_id"] = newTransaction.id
            
        } catch (e: Exception) {
            log.error("❌ 실패: ${e.javaClass.simpleName} - ${e.message}")
            result["error"] = "${e.javaClass.simpleName}: ${e.message}"
        }
        
        log.info("========== 실험 2-D: 결과 ==========")
        log.info("💡 readOnly=true여도 persist() 자체는 예외 없이 호출 가능!")
        log.info("💡 하지만 트랜잭션 커밋 시점에 flush되지 않을 수 있음")

        return result
    }

    // ==========================================
    // 실험 2-E: readOnly=true 메모리 사용량 비교
    // ==========================================

    @Transactional(readOnly = true)
    fun experimentReadOnlyMemory(): Map<String, Any?> {
        log.info("========== 실험 2-E: 메모리 사용량 (readOnly=true) ==========")

        val result = mutableMapOf<String, Any?>()
        val runtime = Runtime.getRuntime()

        // 영속성 컨텍스트 초기화
        entityManager.clear()

        // GC 힌트 후 기준선 측정
        System.gc()
        Thread.sleep(100)
        val memoryBefore = runtime.totalMemory() - runtime.freeMemory()

        // 대량 엔티티 로드
        val transactions = transactionRepository.findAll()

        // 로드 후 메모리 측정
        val memoryAfter = runtime.totalMemory() - runtime.freeMemory()
        val memoryDelta = memoryAfter - memoryBefore

        // Session 상태 확인
        val session = entityManager.unwrap(Session::class.java)
        val stats = session.statistics

        val memoryBeforeMb = String.format("%.2f", memoryBefore / 1024.0 / 1024.0)
        val memoryAfterMb = String.format("%.2f", memoryAfter / 1024.0 / 1024.0)
        val memoryDeltaMb = String.format("%.2f", memoryDelta / 1024.0 / 1024.0)

        log.info("📊 엔티티 수: ${transactions.size}")
        log.info("📊 로드 전 메모리: ${memoryBeforeMb}MB")
        log.info("📊 로드 후 메모리: ${memoryAfterMb}MB")
        log.info("📊 메모리 증가량: ${memoryDeltaMb}MB")
        log.info("📊 FlushMode: ${session.hibernateFlushMode}")
        log.info("📊 Session DefaultReadOnly: ${session.isDefaultReadOnly}")
        log.info("📊 Session Entity Count: ${stats.entityCount}")

        result["readOnly"] = true
        result["entity_count"] = transactions.size
        result["memory_before_mb"] = memoryBeforeMb.toDouble()
        result["memory_after_mb"] = memoryAfterMb.toDouble()
        result["memory_delta_mb"] = memoryDeltaMb.toDouble()
        result["flush_mode"] = session.hibernateFlushMode.toString()
        result["session_default_readonly"] = session.isDefaultReadOnly
        result["entity_count_in_session"] = stats.entityCount

        log.info("💡 readOnly=true: 스냅샷 저장 생략 → 메모리 절약")

        return result
    }

    @Transactional(readOnly = false)
    fun experimentWritableMemory(): Map<String, Any?> {
        log.info("========== 실험 2-E: 메모리 사용량 (readOnly=false) ==========")

        val result = mutableMapOf<String, Any?>()
        val runtime = Runtime.getRuntime()

        // 영속성 컨텍스트 초기화
        entityManager.clear()

        // GC 힌트 후 기준선 측정
        System.gc()
        Thread.sleep(100)
        val memoryBefore = runtime.totalMemory() - runtime.freeMemory()

        // 대량 엔티티 로드
        val transactions = transactionRepository.findAll()

        // 로드 후 메모리 측정
        val memoryAfter = runtime.totalMemory() - runtime.freeMemory()
        val memoryDelta = memoryAfter - memoryBefore

        // Session 상태 확인
        val session = entityManager.unwrap(Session::class.java)
        val stats = session.statistics

        val memoryBeforeMb = String.format("%.2f", memoryBefore / 1024.0 / 1024.0)
        val memoryAfterMb = String.format("%.2f", memoryAfter / 1024.0 / 1024.0)
        val memoryDeltaMb = String.format("%.2f", memoryDelta / 1024.0 / 1024.0)

        log.info("📊 엔티티 수: ${transactions.size}")
        log.info("📊 로드 전 메모리: ${memoryBeforeMb}MB")
        log.info("📊 로드 후 메모리: ${memoryAfterMb}MB")
        log.info("📊 메모리 증가량: ${memoryDeltaMb}MB")
        log.info("📊 FlushMode: ${session.hibernateFlushMode}")
        log.info("📊 Session DefaultReadOnly: ${session.isDefaultReadOnly}")
        log.info("📊 Session Entity Count: ${stats.entityCount}")

        result["readOnly"] = false
        result["entity_count"] = transactions.size
        result["memory_before_mb"] = memoryBeforeMb.toDouble()
        result["memory_after_mb"] = memoryAfterMb.toDouble()
        result["memory_delta_mb"] = memoryDeltaMb.toDouble()
        result["flush_mode"] = session.hibernateFlushMode.toString()
        result["session_default_readonly"] = session.isDefaultReadOnly
        result["entity_count_in_session"] = stats.entityCount

        log.info("💡 readOnly=false: 스냅샷 저장 → 더티체킹용 메모리 추가 사용")

        return result
    }
}
