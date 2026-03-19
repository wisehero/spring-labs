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
import java.util.UUID

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

    @Transactional(readOnly = true)
    fun experimentReadOnlyPersistAndVerify(): Map<String, Any?> {
        log.info("========== 실험 2-B: readOnly에서 persist 후 커밋 결과 검증 ==========")

        val result = mutableMapOf<String, Any?>()
        val runId = UUID.randomUUID().toString().substring(0, 8)
        val businessNo = "READONLY-2B-$runId"

        val session = entityManager.unwrap(Session::class.java)
        val flushMode = session.hibernateFlushMode

        log.info("FlushMode: $flushMode")
        log.info("이번 실행 식별자: $businessNo")
        result["flush_mode"] = flushMode.toString()
        result["session_default_readonly"] = session.isDefaultReadOnly
        result["run_business_no"] = businessNo

        // readOnly=true 트랜잭션에서 새 엔티티를 persist한다.
        // FlushMode=MANUAL이므로 트랜잭션 커밋 시 자동 flush가 발생하지 않아야 한다.
        val newTx = Transaction(
            approveDateTime = LocalDateTime.now(),
            amount = BigDecimal("99999.99"),
            businessNo = businessNo,
            posTransactionNo = "readonly-pos-2b-$runId",
            paymentTransactionGuidNo = "readonly-pay-2b-$runId",
            spareTransactionGuidNo = "readonly-spare-2b-$runId",
            transactionState = "테스트"
        )

        log.info("persist() 호출...")
        entityManager.persist(newTx)
        result["persist_result"] = "성공 (예외 없음 — 1차 캐시에 저장됨)"
        log.info("persist() 성공 — 엔티티가 1차 캐시에 저장됨")

        // 이 메서드가 리턴되면 @Transactional이 트랜잭션을 커밋한다.
        // FlushMode=MANUAL이면 커밋 시점에도 자동 flush가 발생하지 않으므로
        // persist된 엔티티는 DB에 반영되지 않아야 한다.
        // → 실제 검증은 컨트롤러에서 이 트랜잭션 커밋 후 재조회로 수행한다.

        log.info("========== 실험 2-B: 트랜잭션 커밋 대기 — 커밋 후 재조회로 결과 검증 예정 ==========")

        return result
    }

    /**
     * 실험 2-B 검증용: 커밋 완료 후 별도 트랜잭션에서 DB 반영 여부를 재조회한다.
     */
    @Transactional(readOnly = true)
    fun verifyNotFlushed(businessNo: String): Map<String, Any?> {
        val found = transactionRepository.findAll()
            .filter { it.businessNo == businessNo }

        val persisted = found.isNotEmpty()

        if (persisted) {
            log.info("검증 결과: DB에 반영됨! (found ${found.size}건, businessNo=$businessNo)")
        } else {
            log.info("검증 결과: DB에 반영되지 않음 (0건, businessNo=$businessNo)")
        }

        return mapOf(
            "db_found_count" to found.size,
            "actually_persisted" to persisted
        )
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
    fun experimentReadOnlyWithExplicitFlush(): Map<String, Any?> {
        log.info("========== 실험 2-D: readOnly에서 명시적 flush 동작 확인 ==========")

        val result = mutableMapOf<String, Any?>()
        val runId = UUID.randomUUID().toString().substring(0, 8)
        val businessNo = "READONLY-2D-$runId"

        val session = entityManager.unwrap(Session::class.java)
        val flushMode = session.hibernateFlushMode
        log.info("FlushMode: $flushMode")
        log.info("이번 실행 식별자: $businessNo")
        result["flush_mode"] = flushMode.toString()
        result["run_business_no"] = businessNo

        val newTransaction = Transaction(
            approveDateTime = LocalDateTime.now(),
            amount = BigDecimal("99999.99"),
            businessNo = businessNo,
            posTransactionNo = "READONLY-TEST-2D-$runId",
            paymentTransactionGuidNo = "test-guid-readonly-2d-$runId",
            spareTransactionGuidNo = "test-spare-readonly-2d-$runId",
            transactionState = "TEST"
        )

        try {
            log.info("persist() 호출...")
            entityManager.persist(newTransaction)
            log.info("persist() 성공 — 1차 캐시에 저장됨")
            result["persist_call"] = "성공"

            // 핵심: FlushMode=MANUAL이어도 명시적 flush()는 차단되는가?
            log.info("명시적 flush() 호출...")
            entityManager.flush()
            log.info("flush() 성공 — INSERT SQL이 DB로 전송됨")
            result["explicit_flush_call"] = "성공"
            result["new_id"] = newTransaction.id

        } catch (e: Exception) {
            log.error("실패: ${e.javaClass.simpleName} - ${e.message}")
            result["error"] = "${e.javaClass.simpleName}: ${e.message}"
        }

        // 이 메서드가 리턴된 후 커밋/롤백 결과는 컨트롤러에서 재조회로 검증한다.

        return result
    }

    /**
     * 실험 2-D 검증용: 커밋 완료 후 별도 트랜잭션에서 명시적 flush된 데이터의 DB 잔존 여부를 확인한다.
     */
    @Transactional(readOnly = true)
    fun verifyExplicitFlushResult(businessNo: String): Map<String, Any?> {
        val found = transactionRepository.findAll()
            .filter { it.businessNo == businessNo }

        val persisted = found.isNotEmpty()

        if (persisted) {
            log.info("검증 결과: 명시적 flush 데이터가 DB에 남아있음 (${found.size}건, businessNo=$businessNo)")
        } else {
            log.info("검증 결과: 명시적 flush 데이터가 DB에 없음 (businessNo=$businessNo)")
        }

        return mapOf(
            "db_found_count" to found.size,
            "actually_persisted" to persisted
        )
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

        // @Transactional(readOnly=true/false)가 자동 설정한 세션 상태를 그대로 관찰한다.
        // 수동으로 session.isDefaultReadOnly를 조작하지 않는다 — 어노테이션의 효과를 측정하는 것이 실험 목적이다.
        log.info("session_default_readonly (어노테이션에 의해 설정됨): ${session.isDefaultReadOnly}")
        log.info("FlushMode (어노테이션에 의해 설정됨): ${session.hibernateFlushMode}")

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
