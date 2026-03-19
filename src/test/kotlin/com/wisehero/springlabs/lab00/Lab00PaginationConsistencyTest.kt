package com.wisehero.springlabs.lab00

import com.wisehero.springlabs.config.QueryDslConfig
import com.wisehero.springlabs.entity.Transaction
import com.wisehero.springlabs.repository.TransactionRepository
import com.wisehero.springlabs.transaction.dto.TransactionSearchRequest
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Lab 00: 페이지네이션 중 데이터 변경 시 발생하는 문제와 해결책 실험
 *
 * Offset 기반 페이지네이션은 페이지 사이에 데이터가 추가/삭제되면
 * 중복 노출이나 데이터 누락이 발생합니다.
 * Cursor(Keyset) 기반 페이지네이션은 이 문제를 해결합니다.
 */
@DataJpaTest
@Import(QueryDslConfig::class)
@ActiveProfiles("test")
class Lab00PaginationConsistencyTest {

    @Autowired
    lateinit var transactionRepository: TransactionRepository

    @Autowired
    lateinit var entityManager: EntityManager

    private val baseDateTime = LocalDateTime.of(2025, 1, 1, 10, 0, 0)

    /** 테스트마다 삽입된 ID를 추적 */
    private lateinit var insertedIds: List<Long>

    @BeforeEach
    fun setUp() {
        // 10건의 테스트 데이터 삽입
        val saved = (1..10).map { seq ->
            transactionRepository.save(createTransaction(seq))
        }
        entityManager.flush()
        entityManager.clear()
        insertedIds = saved.map { it.id!! }.sorted()
    }

    // ========================================================
    // 실험 1: OFFSET 기반 - 데이터 추가 시 중복 발생
    // ========================================================
    @Test
    @DisplayName("실험 1: OFFSET 기반 - 페이지 사이에 데이터가 추가되면 중복이 발생한다")
    fun offsetPagination_whenDataInserted_duplicateOccurs() {
        val pageSize = 5
        // ID 내림차순 정렬 기준 상위 5개, 하위 5개
        val topIds = insertedIds.sortedDescending().take(5)
        val bottomIds = insertedIds.sortedDescending().drop(5)

        // 1단계: 1페이지 조회 (offset=0, limit=5, ORDER BY id DESC)
        val page1Request = createSearchRequest(page = 0, size = pageSize, sortBy = "id")
        val page1 = transactionRepository.search(page1Request)
        val page1Ids = page1.content.map { it.id }

        println("=== 실험 1: OFFSET 기반 - 데이터 추가 시 중복 ===")
        println("1페이지 결과: $page1Ids")
        assertThat(page1Ids).containsExactlyElementsOf(topIds)

        // 2단계: 페이지 사이에 새 데이터 1건 추가
        val newTransaction = transactionRepository.save(createTransaction(11))
        entityManager.flush()
        entityManager.clear()
        println("새 데이터 추가: ID=${newTransaction.id}")

        // 3단계: 2페이지 조회 (offset=5, limit=5, ORDER BY id DESC)
        // 새 데이터가 맨 앞에 삽입되어 전체 offset이 밀림
        // → 기존 하위 5개 중 첫 번째가 1페이지의 마지막과 겹침
        val page2Request = createSearchRequest(page = 1, size = pageSize, sortBy = "id")
        val page2 = transactionRepository.search(page2Request)
        val page2Ids = page2.content.map { it.id }

        println("데이터 추가 후 2페이지 결과: $page2Ids")

        // 검증: 1페이지의 마지막 ID가 2페이지에도 나타남 → 중복!
        val allIds = page1Ids + page2Ids
        val duplicates = allIds.groupBy { it }.filter { it.value.size > 1 }.keys
        println("중복된 ID: $duplicates")
        println()

        assertThat(duplicates).isNotEmpty
        assertThat(duplicates).contains(topIds.last())
    }

    // ========================================================
    // 실험 2: OFFSET 기반 - 데이터 삭제 시 누락 발생
    // ========================================================
    @Test
    @DisplayName("실험 2: OFFSET 기반 - 페이지 사이에 데이터가 삭제되면 누락이 발생한다")
    fun offsetPagination_whenDataDeleted_skipOccurs() {
        val pageSize = 5
        val sortedDesc = insertedIds.sortedDescending()
        val firstPageLastId = sortedDesc[4]   // 1페이지 마지막 ID
        val secondPageFirstId = sortedDesc[5] // 원래 2페이지 첫 번째 ID
        val deleteTargetId = sortedDesc[0]    // 1페이지 첫 번째 ID (삭제 대상)

        // 1단계: 1페이지 조회
        val page1Request = createSearchRequest(page = 0, size = pageSize, sortBy = "id")
        val page1 = transactionRepository.search(page1Request)
        val page1Ids = page1.content.map { it.id }

        println("=== 실험 2: OFFSET 기반 - 데이터 삭제 시 누락 ===")
        println("1페이지 결과: $page1Ids")
        assertThat(page1Ids).hasSize(5)

        // 2단계: 1페이지의 첫 번째 항목 삭제
        transactionRepository.deleteById(deleteTargetId)
        entityManager.flush()
        entityManager.clear()
        println("삭제된 ID: $deleteTargetId")

        // 3단계: 2페이지 조회 (offset=5)
        // 데이터 1건 삭제로 전체 offset이 당겨짐
        // → 원래 2페이지 첫 번째였던 항목이 건너뛰어짐
        val page2Request = createSearchRequest(page = 1, size = pageSize, sortBy = "id")
        val page2 = transactionRepository.search(page2Request)
        val page2Ids = page2.content.map { it.id }

        println("데이터 삭제 후 2페이지 결과: $page2Ids")

        // 검증: 삭제 후 남은 ID 중 어떤 것이 양쪽 페이지에 모두 안 나타남 → 누락!
        val allSeenIds = (page1Ids + page2Ids).toSet()
        val remainingIds = insertedIds.filter { it != deleteTargetId }
        val missedIds = remainingIds - allSeenIds
        println("누락된 ID: $missedIds")
        println()

        assertThat(missedIds).isNotEmpty
        assertThat(missedIds).contains(secondPageFirstId)
    }

    // ========================================================
    // 실험 3: OFFSET 기반 - totalElements 캐싱 시 빈 마지막 페이지
    // ========================================================
    @Test
    @DisplayName("실험 3: OFFSET 기반 - totalElements 캐싱 후 데이터 삭제 시 실제와 불일치한다")
    fun offsetPagination_staleTotalElements_inconsistency() {
        val pageSize = 5

        // 1단계: 1페이지 조회 → totalElements=10, totalPages=2
        val page1Request = createSearchRequest(page = 0, size = pageSize, sortBy = "id")
        val page1 = transactionRepository.search(page1Request)

        println("=== 실험 3: totalElements 캐싱 시 불일치 ===")
        println("1페이지 totalElements: ${page1.totalElements}, totalPages: ${page1.totalPages}")

        val cachedTotalElements = page1.totalElements
        assertThat(cachedTotalElements).isEqualTo(10L)

        // 2단계: 3건 삭제 → 실제 7건
        val deleteTargets = insertedIds.take(3)
        deleteTargets.forEach { transactionRepository.deleteById(it) }
        entityManager.flush()
        entityManager.clear()
        println("삭제된 ID: $deleteTargets → 실제 남은 데이터: ${10 - deleteTargets.size}건")

        // 3단계: 캐싱된 totalElements=10을 사용하여 2페이지 요청
        val page2Request = createSearchRequest(
            page = 1,
            size = pageSize,
            sortBy = "id",
            totalElements = cachedTotalElements
        )
        val page2 = transactionRepository.search(page2Request)

        println("캐싱된 totalElements=${cachedTotalElements}으로 2페이지 요청")
        println("2페이지 결과: ${page2.content.map { it.id }} (${page2.content.size}건)")
        println("응답의 totalElements: ${page2.totalElements} (실제: 7건)")

        // 검증: 캐싱된 totalElements가 실제와 다름
        assertThat(page2.totalElements)
            .isEqualTo(cachedTotalElements)
            .isNotEqualTo(7L)

        println("totalElements 불일치: 응답=${page2.totalElements}, 실제=7 → UI에 잘못된 페이지 수 표시")
        println()
    }

    // ========================================================
    // 실험 4: CURSOR 기반 - 데이터 추가 시에도 중복 없음
    // ========================================================
    @Test
    @DisplayName("실험 4: CURSOR 기반 - 페이지 사이에 데이터가 추가되어도 중복이 발생하지 않는다")
    fun cursorPagination_whenDataInserted_noDuplicate() {
        val pageSize = 5

        // 1단계: 1페이지 조회 (cursor 없음 → 처음부터)
        val results1 = transactionRepository.searchWithCursor(
            createSearchRequest(), cursorId = null, size = pageSize
        )
        val hasNext1 = results1.size > pageSize
        val page1Items = if (hasNext1) results1.dropLast(1) else results1
        val page1Ids = page1Items.map { it.id }
        val nextCursor = page1Items.last().id

        println("=== 실험 4: CURSOR 기반 - 데이터 추가 시에도 중복 없음 ===")
        println("1페이지 결과: $page1Ids, nextCursor: $nextCursor, hasNext: $hasNext1")
        assertThat(page1Ids).hasSize(5)
        assertThat(hasNext1).isTrue()

        // 2단계: 새 데이터 추가
        val newTransaction = transactionRepository.save(createTransaction(11))
        entityManager.flush()
        entityManager.clear()
        println("새 데이터 추가: ID=${newTransaction.id}")

        // 3단계: 2페이지 조회 (WHERE id < nextCursor ORDER BY id DESC)
        // 새 데이터는 cursor보다 큰 ID이므로 조건에 걸리지 않음
        val results2 = transactionRepository.searchWithCursor(
            createSearchRequest(), cursorId = nextCursor, size = pageSize
        )
        val page2Items = if (results2.size > pageSize) results2.dropLast(1) else results2
        val page2Ids = page2Items.map { it.id }

        println("데이터 추가 후 2페이지 결과: $page2Ids")

        // 검증: 중복 없음!
        val allIds = page1Ids + page2Ids
        val duplicates = allIds.groupBy { it }.filter { it.value.size > 1 }.keys
        println("중복된 ID: $duplicates (비어있어야 정상)")

        assertThat(duplicates).isEmpty()

        // 원래 10건 모두 빠짐없이 조회됨 (새로 추가된 건은 이번 순회에 미포함 - 정상)
        assertThat(allIds.toSet()).containsAll(insertedIds)
        println("원본 데이터 ${insertedIds.size}건 모두 조회 완료, 중복 0건")
        println()
    }

    // ========================================================
    // 실험 5: CURSOR 기반 - 데이터 삭제 시에도 누락 없음
    // ========================================================
    @Test
    @DisplayName("실험 5: CURSOR 기반 - 페이지 사이에 데이터가 삭제되어도 누락이 발생하지 않는다")
    fun cursorPagination_whenDataDeleted_noSkip() {
        val pageSize = 5

        // 1단계: 1페이지 조회
        val results1 = transactionRepository.searchWithCursor(
            createSearchRequest(), cursorId = null, size = pageSize
        )
        val page1Items = if (results1.size > pageSize) results1.dropLast(1) else results1
        val page1Ids = page1Items.map { it.id }
        val nextCursor = page1Items.last().id
        val deleteTargetId = page1Ids.first() // 1페이지 첫 번째(가장 큰 ID) 삭제

        println("=== 실험 5: CURSOR 기반 - 데이터 삭제 시에도 누락 없음 ===")
        println("1페이지 결과: $page1Ids, nextCursor: $nextCursor")

        // 2단계: 1페이지에 있던 항목 삭제
        transactionRepository.deleteById(deleteTargetId!!)
        entityManager.flush()
        entityManager.clear()
        println("삭제된 ID: $deleteTargetId")

        // 3단계: 2페이지 조회 (WHERE id < nextCursor)
        // 삭제된 항목은 cursor보다 큰 ID이므로 2페이지 조회에 영향 없음
        val results2 = transactionRepository.searchWithCursor(
            createSearchRequest(), cursorId = nextCursor, size = pageSize
        )
        val page2Items = if (results2.size > pageSize) results2.dropLast(1) else results2
        val page2Ids = page2Items.map { it.id }

        println("데이터 삭제 후 2페이지 결과: $page2Ids")

        // 검증: 남은 데이터 중 누락 없음
        val allSeenIds = (page1Ids + page2Ids).toSet()
        val remainingIds = insertedIds.filter { it != deleteTargetId }
        val missedIds = remainingIds - allSeenIds
        println("누락된 ID: $missedIds (비어있어야 정상)")
        println()

        assertThat(missedIds).isEmpty()
    }

    // ========================================================
    // Helper Methods
    // ========================================================

    private fun createTransaction(seq: Int): Transaction {
        return Transaction(
            approveDateTime = baseDateTime.plusMinutes(seq.toLong()),
            amount = BigDecimal.valueOf(10000L + seq * 1000L),
            businessNo = "BIZ-${seq.toString().padStart(3, '0')}",
            posTransactionNo = "POS-${seq.toString().padStart(5, '0')}",
            paymentTransactionGuidNo = "PAY-GUID-$seq",
            spareTransactionGuidNo = "SPARE-GUID-$seq",
            transactionState = "거래승인"
        )
    }

    private fun createSearchRequest(
        page: Int = 0,
        size: Int = 20,
        sortBy: String = "id",
        sortDirection: String = "desc",
        totalElements: Long? = null
    ): TransactionSearchRequest {
        return TransactionSearchRequest(
            page = page,
            size = size,
            sortBy = sortBy,
            sortDirection = sortDirection,
            totalElements = totalElements
        )
    }
}
