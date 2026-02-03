package com.wisehero.springlabs.labs02

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

    @Transactional(readOnly = true)
    fun experimentReadOnlyStatus(): Map<String, Any?> {
        log.info("========== 실험 2-A: readOnly 상태 확인 ==========")

        val result = mutableMapOf<String, Any?>()

        val txName = TransactionSynchronizationManager.getCurrentTransactionName()
        val txActive = TransactionSynchronizationManager.isActualTransactionActive()
        val txReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly()

        log.info("📖 트랜잭션 이름: $txName")
        log.info("📖 트랜잭션 활성: $txActive")
        log.info("📖 읽기전용 플래그: $txReadOnly")

        result["tx_name"] = txName
        result["tx_active"] = txActive
        result["tx_readonly"] = txReadOnly

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

    @Transactional
    fun setupTestTransaction(): Long {
        val tx = Transaction(
            approveDateTime = LocalDateTime.now(),
            amount = BigDecimal("50000"),
            businessNo = "READONLY-TEST",
            posTransactionNo = "READONLY-POS-001",
            paymentTransactionGuidNo = "readonly-guid-001",
            spareTransactionGuidNo = "readonly-spare-001",
            transactionState = "APPROVED"
        )
        return transactionRepository.save(tx).id!!
    }

    @Transactional(readOnly = true)
    fun experimentReadOnlyWithModification(transactionId: Long): Map<String, Any?> {
        log.info("========== 실험 2-B: readOnly에서 수정 시도 ==========")

        val result = mutableMapOf<String, Any?>()

        val transaction = transactionRepository.findById(transactionId).orElse(null)

        if (transaction == null) {
            result["error"] = "Transaction not found: $transactionId"
            return result
        }

        val originalAmount = transaction.amount
        log.info("원본 금액: $originalAmount")

        result["original_amount"] = originalAmount

        val session = entityManager.unwrap(Session::class.java)
        val flushModeBefore = session.hibernateFlushMode

        log.info("수정 전 FlushMode: $flushModeBefore")
        result["flush_mode_before"] = flushModeBefore.toString()

        try {
            log.info("JPQL UPDATE 시도 (readOnly 트랜잭션에서)...")
            val updated = entityManager.createQuery(
                "UPDATE Transaction t SET t.amount = :newAmount WHERE t.id = :id"
            )
                .setParameter("newAmount", BigDecimal("99999.99"))
                .setParameter("id", transactionId)
                .executeUpdate()
            result["jpql_update"] = "성공 (${updated}건)"
            log.info("JPQL UPDATE 성공: ${updated}건")
        } catch (e: Exception) {
            result["jpql_update"] = "실패: ${e.javaClass.simpleName}"
            log.info("JPQL UPDATE 실패: ${e.javaClass.simpleName} - ${e.message}")
        }

        entityManager.clear()
        val dbAmount = transactionRepository.findById(transactionId).orElse(null)?.amount
        result["db_amount_after"] = dbAmount
        result["amount_changed"] = dbAmount != originalAmount
        log.info("DB 재조회 금액: $dbAmount (원본: $originalAmount)")

        log.info("========== 실험 2-B: 결과 ==========")
        log.info("readOnly=true -> FlushMode=MANUAL, JPQL UPDATE 시도 시 TransactionRequiredException 또는 무시")

        return result
    }

    @Transactional(readOnly = true)
    fun warmupQuery() {
        log.info("[워밍업] 캐시 워밍업 쿼리 실행...")
        transactionRepository.findAll()
        log.info("[워밍업] 완료")
    }

    @Transactional(readOnly = true)
    fun experimentReadOnlyPerformance(): Map<String, Any?> {
        log.info("========== 실험 2-C: readOnly 성능 (readOnly=true) ==========")

        val result = mutableMapOf<String, Any?>()
        val startTime = System.currentTimeMillis()

        val transactions = transactionRepository.findAll()
        val fetchTime = System.currentTimeMillis() - startTime

        result["readOnly"] = true
        result["count"] = transactions.size
        result["fetch_time_ms"] = fetchTime

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

        val transactions = transactionRepository.findAll()
        val fetchTime = System.currentTimeMillis() - startTime

        result["readOnly"] = false
        result["count"] = transactions.size
        result["fetch_time_ms"] = fetchTime

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

    @Transactional(readOnly = true)
    fun experimentReadOnlyWithPersist(): Map<String, Any?> {
        log.info("========== 실험 2-D: readOnly에서 persist 시도 ==========")

        val result = mutableMapOf<String, Any?>()

        val session = entityManager.unwrap(Session::class.java)
        log.info("🔧 FlushMode: ${session.hibernateFlushMode}")

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

    @Transactional(readOnly = true)
    fun experimentReadOnlyMemory(): Map<String, Any?> {
        log.info("========== 실험 2-E: 메모리 사용량 (readOnly=true) ==========")
        return measureMemory(readOnly = true)
    }

    @Transactional(readOnly = false)
    fun experimentWritableMemory(): Map<String, Any?> {
        log.info("========== 실험 2-E: 메모리 사용량 (readOnly=false) ==========")
        return measureMemory(readOnly = false)
    }

    private fun measureMemory(readOnly: Boolean): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        val runtime = Runtime.getRuntime()

        val session = entityManager.unwrap(Session::class.java)

        // 명시적으로 session.defaultReadOnly 설정
        // Spring Boot 4 / Hibernate 7에서 @Transactional(readOnly=true)가 자동 설정하지 않으며,
        // OSIV로 인해 이전 트랜잭션의 세션 상태가 유지될 수 있으므로 양쪽 모두 명시적으로 설정
        session.isDefaultReadOnly = readOnly

        log.info("session_default_readonly: ${session.isDefaultReadOnly}")
        log.info("FlushMode: ${session.hibernateFlushMode}")

        // GC 2회 + 대기로 측정 안정화
        System.gc()
        System.gc()
        Thread.sleep(200)
        val memoryBefore = runtime.totalMemory() - runtime.freeMemory()

        val transactions = transactionRepository.findAll()

        // findAll 직후 측정 (GC 없이)
        val memoryAfter = runtime.totalMemory() - runtime.freeMemory()
        val memoryDelta = memoryAfter - memoryBefore

        val stats = session.statistics

        // 엔티티 단위 readOnly 상태 확인 (첫 번째 엔티티)
        val firstEntityReadOnly = if (transactions.isNotEmpty()) {
            session.isReadOnly(transactions.first())
        } else null

        val memoryBeforeMb = String.format("%.2f", memoryBefore / 1024.0 / 1024.0)
        val memoryAfterMb = String.format("%.2f", memoryAfter / 1024.0 / 1024.0)
        val memoryDeltaMb = String.format("%.2f", memoryDelta / 1024.0 / 1024.0)

        log.info("엔티티 수: ${transactions.size}")
        log.info("로드 전 메모리: ${memoryBeforeMb}MB")
        log.info("로드 후 메모리: ${memoryAfterMb}MB")
        log.info("메모리 증가량: ${memoryDeltaMb}MB")
        log.info("FlushMode: ${session.hibernateFlushMode}")
        log.info("Session DefaultReadOnly: ${session.isDefaultReadOnly}")
        log.info("Entity ReadOnly: $firstEntityReadOnly")
        log.info("Session Entity Count: ${stats.entityCount}")

        result["readOnly"] = readOnly
        result["entity_count"] = transactions.size
        result["memory_before_mb"] = memoryBeforeMb.toDouble()
        result["memory_after_mb"] = memoryAfterMb.toDouble()
        result["memory_delta_mb"] = memoryDeltaMb.toDouble()
        result["flush_mode"] = session.hibernateFlushMode.toString()
        result["session_default_readonly"] = session.isDefaultReadOnly
        result["entity_readonly"] = firstEntityReadOnly
        result["entity_count_in_session"] = stats.entityCount

        return result
    }
}
