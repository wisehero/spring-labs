package com.wisehero.springdemo.experiment

import com.wisehero.springdemo.entity.Transaction
import com.wisehero.springdemo.experiment.dto.ExperimentSummary
import com.wisehero.springdemo.experiment.dto.InsertResult
import com.wisehero.springdemo.experiment.dto.RankingEntry
import com.wisehero.springdemo.repository.TransactionRepository
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.*
import kotlin.random.Random

/**
 * ==========================================
 * Lab 03: Bulk Insert 성능 비교 실험
 * ==========================================
 * 
 * 비교 대상:
 * 1. JPA saveAll() - 엔티티 기반, 더티체킹 오버헤드
 * 2. JdbcTemplate batchUpdate() - JDBC 배치 처리
 * 3. Native Bulk Insert - VALUES 절에 여러 row
 * 
 * 테스트 규모: 100건, 1,000건, 10,000건
 */
@Service
class BulkInsertExperimentService(
    private val transactionRepository: TransactionRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val entityManager: EntityManager
) {

    // Self-injection: 프록시를 통한 내부 메서드 호출을 위함
    // Lab 01의 self-invocation 문제 해결
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var self: BulkInsertExperimentService
    
    private val log = LoggerFactory.getLogger(javaClass)
    
    companion object {
        const val TEST_PREFIX = "BT-"  // 20자 제한 맞춤 (business_no column)
        val DEFAULT_TEST_COUNTS = listOf(100, 1000, 10000)
    }
    
    // ==========================================
    // 방법 1: JPA saveAll
    // ==========================================
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun insertWithSaveAll(count: Int): InsertResult {
        log.info("========== JPA saveAll 테스트 시작 (${count}건) ==========")
        
        // 테스트 데이터 생성
        val transactions = generateTestTransactions(count, "SAVEALL")
        
        val start = System.currentTimeMillis()
        
        // saveAll 실행
        transactionRepository.saveAll(transactions)
        
        // flush로 실제 INSERT 강제 실행
        entityManager.flush()
        
        val duration = System.currentTimeMillis() - start
        
        // 1차 캐시 정리
        entityManager.clear()
        
        log.info("✅ JPA saveAll 완료: ${count}건, ${duration}ms")
        log.info("   처리량: ${String.format("%.2f", count * 1000.0 / duration)} records/sec")
        
        return InsertResult.of("JPA saveAll", count, duration)
    }
    
    // ==========================================
    // 방법 2: JdbcTemplate batchUpdate
    // ==========================================
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun insertWithJdbcBatch(count: Int): InsertResult {
        log.info("========== JdbcTemplate batchUpdate 테스트 시작 (${count}건) ==========")
        
        val transactions = generateTestTransactions(count, "JDBC")
        
        val sql = """
            INSERT INTO transaction (
                approve_date_time, amount, business_no, pos_transaction_no,
                payment_transaction_guid_no, spare_transaction_guid_no,
                transaction_state, pos_cancel_transaction_no, cancel_date_time,
                cancel_reason, cash_receipt_issue_yn, cash_receipt_approve_no,
                cash_receipt_approve_date_time, cash_receipt_issue_type,
                cash_receipt_auth_type, cash_receipt_issue_no,
                cash_receipt_cancel_approve_no, cash_receipt_cancel_date_time,
                paper_receipt_print_yn
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        
        val start = System.currentTimeMillis()
        
        jdbcTemplate.batchUpdate(sql, object : BatchPreparedStatementSetter {
            override fun setValues(ps: PreparedStatement, i: Int) {
                val tx = transactions[i]
                ps.setTimestamp(1, Timestamp.valueOf(tx.approveDateTime))
                ps.setBigDecimal(2, tx.amount)
                ps.setString(3, tx.businessNo)
                ps.setString(4, tx.posTransactionNo)
                ps.setString(5, tx.paymentTransactionGuidNo)
                ps.setString(6, tx.spareTransactionGuidNo)
                ps.setString(7, tx.transactionState)
                ps.setString(8, tx.posCancelTransactionNo)
                ps.setTimestamp(9, tx.cancelDateTime?.let { Timestamp.valueOf(it) })
                ps.setString(10, tx.cancelReason)
                ps.setObject(11, tx.cashReceiptIssueYn)
                ps.setString(12, tx.cashReceiptApproveNo)
                ps.setTimestamp(13, tx.cashReceiptApproveDateTime?.let { Timestamp.valueOf(it) })
                ps.setString(14, tx.cashReceiptIssueType)
                ps.setString(15, tx.cashReceiptAuthType)
                ps.setString(16, tx.cashReceiptIssueNo)
                ps.setString(17, tx.cashReceiptCancelApproveNo)
                ps.setTimestamp(18, tx.cashReceiptCancelDateTime?.let { Timestamp.valueOf(it) })
                ps.setObject(19, tx.paperReceiptPrintYn)
            }
            
            override fun getBatchSize() = transactions.size
        })
        
        val duration = System.currentTimeMillis() - start
        
        log.info("✅ JdbcTemplate batchUpdate 완료: ${count}건, ${duration}ms")
        log.info("   처리량: ${String.format("%.2f", count * 1000.0 / duration)} records/sec")
        
        return InsertResult.of("JdbcTemplate batchUpdate", count, duration)
    }
    
    // ==========================================
    // 방법 3: Native Bulk Insert
    // ==========================================
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun insertWithNativeBulk(count: Int): InsertResult {
        log.info("========== Native Bulk Insert 테스트 시작 (${count}건) ==========")
        
        val transactions = generateTestTransactions(count, "NATIVE")
        
        // MySQL max_allowed_packet 고려하여 청크 분할
        val chunkSize = 500
        
        val start = System.currentTimeMillis()
        
        transactions.chunked(chunkSize).forEach { chunk ->
            val values = chunk.joinToString(",\n") { tx ->
                """(
                    '${tx.approveDateTime}',
                    ${tx.amount},
                    '${escapeSql(tx.businessNo)}',
                    '${escapeSql(tx.posTransactionNo)}',
                    '${escapeSql(tx.paymentTransactionGuidNo)}',
                    '${escapeSql(tx.spareTransactionGuidNo)}',
                    '${escapeSql(tx.transactionState)}',
                    ${tx.posCancelTransactionNo?.let { "'${escapeSql(it)}'" } ?: "NULL"},
                    ${tx.cancelDateTime?.let { "'$it'" } ?: "NULL"},
                    ${tx.cancelReason?.let { "'${escapeSql(it)}'" } ?: "NULL"},
                    ${tx.cashReceiptIssueYn},
                    ${tx.cashReceiptApproveNo?.let { "'${escapeSql(it)}'" } ?: "NULL"},
                    ${tx.cashReceiptApproveDateTime?.let { "'$it'" } ?: "NULL"},
                    ${tx.cashReceiptIssueType?.let { "'${escapeSql(it)}'" } ?: "NULL"},
                    ${tx.cashReceiptAuthType?.let { "'${escapeSql(it)}'" } ?: "NULL"},
                    ${tx.cashReceiptIssueNo?.let { "'${escapeSql(it)}'" } ?: "NULL"},
                    ${tx.cashReceiptCancelApproveNo?.let { "'${escapeSql(it)}'" } ?: "NULL"},
                    ${tx.cashReceiptCancelDateTime?.let { "'$it'" } ?: "NULL"},
                    ${tx.paperReceiptPrintYn}
                )""".trimIndent()
            }
            
            val sql = """
                INSERT INTO transaction (
                    approve_date_time, amount, business_no, pos_transaction_no,
                    payment_transaction_guid_no, spare_transaction_guid_no,
                    transaction_state, pos_cancel_transaction_no, cancel_date_time,
                    cancel_reason, cash_receipt_issue_yn, cash_receipt_approve_no,
                    cash_receipt_approve_date_time, cash_receipt_issue_type,
                    cash_receipt_auth_type, cash_receipt_issue_no,
                    cash_receipt_cancel_approve_no, cash_receipt_cancel_date_time,
                    paper_receipt_print_yn
                ) VALUES $values
            """.trimIndent()
            
            entityManager.createNativeQuery(sql).executeUpdate()
        }
        
        val duration = System.currentTimeMillis() - start
        
        log.info("✅ Native Bulk Insert 완료: ${count}건, ${duration}ms")
        log.info("   처리량: ${String.format("%.2f", count * 1000.0 / duration)} records/sec")
        
        return InsertResult.of("Native Bulk Insert", count, duration)
    }
    
    // ==========================================
    // 전체 비교 실행
    // ==========================================
    
    fun compareAll(counts: List<Int> = DEFAULT_TEST_COUNTS): ExperimentSummary {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  🧪 Bulk Insert 성능 비교 실험 시작                         ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")
        
        val results = mutableMapOf<Int, List<InsertResult>>()
        val rankings = mutableMapOf<Int, List<RankingEntry>>()
        
        counts.forEach { count ->
            log.info(">>> ${count}건 테스트 시작")

            // self를 통해 프록시 호출 - @Transactional 적용됨
            // (Lab 01 self-invocation 문제 해결)
            self.cleanupTestData()
            val saveAllResult = self.insertWithSaveAll(count)

            self.cleanupTestData()
            val jdbcResult = self.insertWithJdbcBatch(count)

            self.cleanupTestData()
            val nativeResult = self.insertWithNativeBulk(count)

            val resultList = listOf(saveAllResult, jdbcResult, nativeResult)
            results[count] = resultList

            // 순위 계산
            val sorted = resultList.sortedBy { it.durationMs }
            val fastestTime = sorted.first().durationMs

            rankings[count] = sorted.mapIndexed { index, result ->
                val ratio = if (fastestTime > 0) result.durationMs.toDouble() / fastestTime else 1.0
                RankingEntry(
                    rank = index + 1,
                    method = result.method,
                    durationMs = result.durationMs,
                    throughput = result.throughput,
                    comparedToFirst = if (index == 0) "fastest"
                                      else "${String.format("%.1f", ratio)}x slower"
                )
            }

            // 테스트 데이터 정리
            self.cleanupTestData()

            log.info(">>> ${count}건 테스트 완료\n")
        }
        
        // 결과 출력
        printSummary(rankings)
        
        return ExperimentSummary(
            testCounts = counts,
            results = results,
            rankings = rankings
        )
    }
    
    fun compare(count: Int): List<InsertResult> {
        log.info(">>> ${count}건 비교 테스트")

        // self를 통해 프록시 호출 - @Transactional 적용됨
        self.cleanupTestData()
        val saveAllResult = self.insertWithSaveAll(count)

        self.cleanupTestData()
        val jdbcResult = self.insertWithJdbcBatch(count)

        self.cleanupTestData()
        val nativeResult = self.insertWithNativeBulk(count)

        self.cleanupTestData()

        return listOf(saveAllResult, jdbcResult, nativeResult).sortedBy { it.durationMs }
    }
    
    // ==========================================
    // 유틸리티 메서드
    // ==========================================
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun cleanupTestData(): Int {
        val deleted = entityManager.createQuery(
            "DELETE FROM Transaction t WHERE t.businessNo LIKE :prefix"
        )
            .setParameter("prefix", "$TEST_PREFIX%")
            .executeUpdate()
        
        if (deleted > 0) {
            log.info("🧹 테스트 데이터 ${deleted}건 삭제")
        }
        
        return deleted
    }
    
    private fun generateTestTransactions(count: Int, methodTag: String): List<Transaction> {
        val now = LocalDateTime.now()
        val batchId = UUID.randomUUID().toString().take(8)
        
        return (1..count).map { i ->
            Transaction(
                approveDateTime = now.minusSeconds(i.toLong()),
                amount = BigDecimal(Random.nextDouble(1000.0, 100000.0))
                    .setScale(2, RoundingMode.HALF_UP),
                businessNo = "$TEST_PREFIX$methodTag-$batchId",
                posTransactionNo = "POS-$methodTag-$batchId-$i",
                paymentTransactionGuidNo = "GUID-$methodTag-$batchId-$i",
                spareTransactionGuidNo = "SPARE-$methodTag-$batchId-$i",
                transactionState = "테스트",
                cashReceiptIssueYn = Random.nextBoolean(),
                paperReceiptPrintYn = Random.nextBoolean()
            )
        }
    }
    
    private fun escapeSql(value: String): String {
        return value.replace("'", "''")
    }
    
    private fun printSummary(rankings: Map<Int, List<RankingEntry>>) {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  📊 실험 결과 요약                                          ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        
        rankings.forEach { (count, ranking) ->
            log.info("")
            log.info("[ ${count}건 결과 ]")
            ranking.forEach { entry ->
                val bar = "█".repeat((entry.throughput / 100).toInt().coerceIn(1, 50))
                log.info("  ${entry.rank}위: ${entry.method}")
                log.info("      시간: ${entry.durationMs}ms | 처리량: ${String.format("%.0f", entry.throughput)}/s | ${entry.comparedToFirst}")
                log.info("      $bar")
            }
        }
        
        log.info("")
        log.info("════════════════════════════════════════════════════════════")
    }
}
