package com.wisehero.springlabs.labs05

import com.wisehero.springlabs.entity.Product
import com.wisehero.springlabs.labs05.dto.LockResult
import com.wisehero.springlabs.labs05.dto.PerformanceComparison
import com.wisehero.springlabs.repository.ProductRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.OptimisticLockException
import org.hibernate.StaleObjectStateException
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.CannotAcquireLockException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.TransactionSystemException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * ==========================================
 * Lab 05: Optimistic Lock vs Pessimistic Lock 실험
 * ==========================================
 *
 * Product 엔티티의 재고(stock) 관리 시나리오로
 * 낙관적 락과 비관적 락을 비교합니다.
 *
 * 실험 목록:
 * 5-1: 락 없음 - Lost Update 발생
 * 5-2: Optimistic Lock (@Version) - 충돌 감지
 * 5-3: Optimistic Lock + Retry - 재시도로 전부 성공
 * 5-4: Pessimistic Lock (SELECT FOR UPDATE) - 순차 처리
 * 5-5: 성능 비교 - Optimistic vs Pessimistic
 * 5-6: 데드락 시나리오 - 역순 잠금
 */
@Service
class LockExperimentService(
    private val productRepository: ProductRepository,
    private val entityManager: EntityManager
) {

    @Lazy @Autowired
    private lateinit var self: LockExperimentService

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val TEST_PREFIX = "LOCK-"
    }

    // ==========================================
    // 공통 유틸리티
    // ==========================================

    private fun createTestProduct(name: String, stock: Int): Product {
        return productRepository.saveAndFlush(Product(name = name, stock = stock))
    }

    /**
     * 동시성 테스트 프레임워크
     * CountDownLatch로 모든 스레드를 동시에 출발시켜 최대 경합을 유도합니다.
     */
    private fun runConcurrentTest(
        threadCount: Int,
        operation: (Int) -> Boolean
    ): Triple<Int, Int, Long> {
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        val startTime = System.currentTimeMillis()

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    startLatch.await() // 모든 스레드가 준비될 때까지 대기
                    val success = operation(i)
                    if (success) successCount.incrementAndGet()
                    else failureCount.incrementAndGet()
                } catch (e: Exception) {
                    failureCount.incrementAndGet()
                    log.debug("[Thread-$i] 예외: ${e::class.simpleName}: ${e.message}")
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown() // 모든 스레드 동시 출발!
        doneLatch.await(60, TimeUnit.SECONDS)
        executor.shutdown()
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            executor.shutdownNow()
        }

        val durationMs = System.currentTimeMillis() - startTime
        return Triple(successCount.get(), failureCount.get(), durationMs)
    }

    // ==========================================
    // 실험 5-1: 락 없음 - Lost Update
    // ==========================================

    fun experiment5_1_noLock(): LockResult {
        val threadCount = 100
        val initialStock = 100
        val product = createTestProduct("${TEST_PREFIX}5-1-NO-LOCK", initialStock)
        val productId = product.id!!

        log.info("[5-1] 상품 생성: id=$productId, stock=$initialStock, threads=$threadCount")
        log.info("[5-1] Native SQL read-modify-write로 Lost Update 유도 (JPA entity lifecycle 우회)")

        val (successCount, _, durationMs) = runConcurrentTest(threadCount) { threadIdx ->
            try {
                self.decrementStockWithoutLock(productId)
                true
            } catch (e: Exception) {
                log.debug("[Thread-$threadIdx] 실패: ${e.message}")
                false
            }
        }

        val finalStock = productRepository.findById(productId).get().stock
        log.info("[5-1] 결과: 초기=$initialStock, 최종=$finalStock, 기대=0, 성공=$successCount")

        return LockResult.lostUpdate(
            initialStock = initialStock,
            finalStock = finalStock,
            threadCount = threadCount,
            successCount = successCount,
            durationMs = durationMs,
            details = mapOf(
                "productId" to productId,
                "productName" to "${TEST_PREFIX}5-1-NO-LOCK",
                "mechanism" to "Native SQL read → 앱에서 계산 → write (version 체크 없음)"
            )
        )
    }

    /**
     * Lost Update를 재현하기 위한 native SQL read-modify-write
     *
     * 의도적으로 JPA entity lifecycle을 우회합니다:
     * 1. SELECT stock FROM product WHERE id = ? (현재 값 읽기)
     * 2. 앱에서 stock - 1 계산
     * 3. UPDATE product SET stock = ? WHERE id = ? (계산된 값 쓰기)
     *
     * 중요: "SET stock = stock - 1"은 SQL 레벨에서 atomic이라 Lost Update가 안 생기므로,
     * 반드시 read → 계산 → write를 분리해야 합니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun decrementStockWithoutLock(productId: Long) {
        // 1. 현재 stock 읽기 (native SQL)
        val currentStock = entityManager.createNativeQuery(
            "SELECT stock FROM product WHERE id = :id"
        ).setParameter("id", productId)
            .singleResult as Number

        // 약간의 지연으로 경합 확률 증가
        Thread.sleep(1)

        // 2. 앱에서 계산
        val newStock = currentStock.toInt() - 1

        // 3. 계산된 값으로 업데이트 (version 체크 없음!)
        entityManager.createNativeQuery(
            "UPDATE product SET stock = :newStock WHERE id = :id"
        ).setParameter("newStock", newStock)
            .setParameter("id", productId)
            .executeUpdate()
    }

    // ==========================================
    // 실험 5-2: Optimistic Lock (@Version)
    // ==========================================

    fun experiment5_2_optimisticLock(): LockResult {
        val threadCount = 100
        val initialStock = 100
        val product = createTestProduct("${TEST_PREFIX}5-2-OPTIMISTIC", initialStock)
        val productId = product.id!!

        log.info("[5-2] 상품 생성: id=$productId, stock=$initialStock, threads=$threadCount")
        log.info("[5-2] JPA @Version으로 Optimistic Lock 충돌 감지")

        val (successCount, failureCount, durationMs) = runConcurrentTest(threadCount) { _ ->
            try {
                self.decrementStockWithOptimisticLock(productId)
                true
            } catch (e: Exception) {
                false
            }
        }

        val finalStock = productRepository.findById(productId).get().stock
        log.info("[5-2] 결과: 성공=$successCount, 실패=$failureCount, 최종 재고=$finalStock")

        return LockResult.optimisticConflict(
            initialStock = initialStock,
            finalStock = finalStock,
            threadCount = threadCount,
            successCount = successCount,
            failureCount = failureCount,
            durationMs = durationMs,
            details = mapOf(
                "productId" to productId,
                "mechanism" to "UPDATE product SET stock=?, version=version+1 WHERE id=? AND version=?",
                "exceptionType" to "ObjectOptimisticLockingFailureException"
            )
        )
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun decrementStockWithOptimisticLock(productId: Long) {
        val product = productRepository.findById(productId)
            .orElseThrow { IllegalArgumentException("Product not found: $productId") }
        product.stock -= 1
        productRepository.saveAndFlush(product)
    }

    // ==========================================
    // 실험 5-3: Optimistic Lock + Retry
    // ==========================================

    fun experiment5_3_optimisticWithRetry(): LockResult {
        val threadCount = 100
        val initialStock = 100
        val product = createTestProduct("${TEST_PREFIX}5-3-OPT-RETRY", initialStock)
        val productId = product.id!!
        val totalRetryCount = AtomicInteger(0)

        log.info("[5-3] 상품 생성: id=$productId, stock=$initialStock, threads=$threadCount")
        log.info("[5-3] Optimistic Lock + Retry (최대 50회)")

        val (successCount, failureCount, durationMs) = runConcurrentTest(threadCount) { threadIdx ->
            var retries = 0
            val maxRetries = 50
            while (retries < maxRetries) {
                try {
                    self.decrementStockWithOptimisticLock(productId)
                    if (retries > 0) {
                        totalRetryCount.addAndGet(retries)
                        log.debug("[Thread-$threadIdx] ${retries}회 재시도 후 성공")
                    }
                    return@runConcurrentTest true
                } catch (e: Exception) {
                    when {
                        e is ObjectOptimisticLockingFailureException ||
                        e is OptimisticLockException ||
                        e is StaleObjectStateException ||
                        (e is TransactionSystemException && e.cause?.cause is StaleObjectStateException) -> {
                            retries++
                            Thread.sleep((1..3).random().toLong()) // 짧은 백오프
                        }
                        else -> throw e
                    }
                }
            }
            totalRetryCount.addAndGet(retries)
            log.warn("[Thread-$threadIdx] ${maxRetries}회 재시도 후에도 실패")
            false
        }

        val finalStock = productRepository.findById(productId).get().stock
        log.info("[5-3] 결과: 성공=$successCount, 실패=$failureCount, 재시도=$totalRetryCount, 최종 재고=$finalStock")

        return LockResult.optimisticRetry(
            initialStock = initialStock,
            finalStock = finalStock,
            threadCount = threadCount,
            successCount = successCount,
            failureCount = failureCount,
            retryCount = totalRetryCount.get(),
            durationMs = durationMs,
            details = mapOf(
                "productId" to productId,
                "maxRetriesPerThread" to 50,
                "retryBackoffMs" to "1-3ms random"
            )
        )
    }

    // ==========================================
    // 실험 5-4: Pessimistic Lock (FOR UPDATE)
    // ==========================================

    fun experiment5_4_pessimisticLock(): LockResult {
        val threadCount = 100
        val initialStock = 100
        val product = createTestProduct("${TEST_PREFIX}5-4-PESSIMISTIC", initialStock)
        val productId = product.id!!

        log.info("[5-4] 상품 생성: id=$productId, stock=$initialStock, threads=$threadCount")
        log.info("[5-4] SELECT FOR UPDATE로 순차 처리")

        val (successCount, failureCount, durationMs) = runConcurrentTest(threadCount) { _ ->
            try {
                self.decrementStockWithPessimisticLock(productId)
                true
            } catch (e: Exception) {
                false
            }
        }

        val finalStock = productRepository.findById(productId).get().stock
        log.info("[5-4] 결과: 성공=$successCount, 실패=$failureCount, 최종 재고=$finalStock")

        return LockResult.pessimistic(
            initialStock = initialStock,
            finalStock = finalStock,
            threadCount = threadCount,
            successCount = successCount,
            failureCount = failureCount,
            durationMs = durationMs,
            details = mapOf(
                "productId" to productId,
                "mechanism" to "SELECT ... FROM product WHERE id = ? FOR UPDATE",
                "lockType" to "InnoDB row-level exclusive lock"
            )
        )
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun decrementStockWithPessimisticLock(productId: Long) {
        val product = productRepository.findByIdWithPessimisticLock(productId)
            ?: throw IllegalArgumentException("Product not found: $productId")
        product.stock -= 1
        productRepository.saveAndFlush(product)
    }

    // ==========================================
    // 실험 5-5: 성능 비교
    // ==========================================

    fun experiment5_5_performanceComparison(): LockResult {
        log.info("[5-5] Optimistic(+Retry) vs Pessimistic 성능 비교")

        // JVM/DB 워밍업 (측정에서 제외)
        log.info("[5-5] 워밍업 실행 중...")
        val warmupProduct = createTestProduct("${TEST_PREFIX}5-5-WARMUP", 10)
        val warmupId = warmupProduct.id!!
        runConcurrentTest(10) { _ ->
            try {
                self.decrementStockWithPessimisticLock(warmupId)
                true
            } catch (e: Exception) { false }
        }
        log.info("[5-5] 워밍업 완료")

        val scenarios = listOf(
            "낮은 경합" to 10,
            "높은 경합" to 100
        )

        val comparisons = mutableListOf<PerformanceComparison>()
        val totalStartTime = System.currentTimeMillis()

        for ((scenario, threadCount) in scenarios) {
            log.info("[5-5] === $scenario ($threadCount 스레드) ===")

            // Optimistic + Retry 테스트
            val optProduct = createTestProduct("${TEST_PREFIX}5-5-OPT-$threadCount", threadCount)
            val optProductId = optProduct.id!!
            val optRetryCount = AtomicInteger(0)

            val (optSuccess, _, optDuration) = runConcurrentTest(threadCount) { _ ->
                var retries = 0
                val maxRetries = 50
                while (retries < maxRetries) {
                    try {
                        self.decrementStockWithOptimisticLock(optProductId)
                        if (retries > 0) optRetryCount.addAndGet(retries)
                        return@runConcurrentTest true
                    } catch (e: Exception) {
                        when {
                            e is ObjectOptimisticLockingFailureException ||
                            e is OptimisticLockException ||
                            e is StaleObjectStateException ||
                            (e is TransactionSystemException && e.cause?.cause is StaleObjectStateException) -> {
                                retries++
                                Thread.sleep((1..3).random().toLong())
                            }
                            else -> throw e
                        }
                    }
                }
                optRetryCount.addAndGet(retries)
                false
            }

            // Pessimistic 테스트
            val pessProduct = createTestProduct("${TEST_PREFIX}5-5-PESS-$threadCount", threadCount)
            val pessProductId = pessProduct.id!!

            val (pessSuccess, _, pessDuration) = runConcurrentTest(threadCount) { _ ->
                try {
                    self.decrementStockWithPessimisticLock(pessProductId)
                    true
                } catch (e: Exception) {
                    false
                }
            }

            val winner = if (optDuration < pessDuration) "Optimistic" else "Pessimistic"
            val slower = maxOf(optDuration, pessDuration).toDouble()
            val faster = minOf(optDuration, pessDuration).toDouble()
            val diffPercent = if (faster > 0) ((slower - faster) / faster * 100) else 0.0

            comparisons.add(
                PerformanceComparison(
                    scenario = scenario,
                    threadCount = threadCount,
                    optimisticDurationMs = optDuration,
                    optimisticRetries = optRetryCount.get(),
                    optimisticSuccessCount = optSuccess,
                    pessimisticDurationMs = pessDuration,
                    pessimisticSuccessCount = pessSuccess,
                    winner = winner,
                    speedDifferencePercent = Math.round(diffPercent * 100.0) / 100.0
                )
            )

            log.info("[5-5] $scenario: Opt=${optDuration}ms(retries=${optRetryCount.get()}), Pess=${pessDuration}ms → $winner 승")
        }

        val totalDuration = System.currentTimeMillis() - totalStartTime

        return LockResult.performance(
            optimisticResult = mapOf(
                "durationMs" to comparisons.map { it.optimisticDurationMs }.average().toLong()
            ),
            pessimisticResult = mapOf(
                "durationMs" to comparisons.map { it.pessimisticDurationMs }.average().toLong()
            ),
            durationMs = totalDuration,
            details = mapOf(
                "comparisons" to comparisons,
                "summary" to comparisons.map {
                    "${it.scenario}(${it.threadCount}t): Opt=${it.optimisticDurationMs}ms vs Pess=${it.pessimisticDurationMs}ms → ${it.winner}"
                }
            )
        )
    }

    // ==========================================
    // 실험 5-6: 데드락 시나리오
    // ==========================================

    fun experiment5_6_deadlock(): LockResult {
        val productA = createTestProduct("${TEST_PREFIX}5-6-DEADLOCK-A", 100)
        val productB = createTestProduct("${TEST_PREFIX}5-6-DEADLOCK-B", 100)
        val productAId = productA.id!!
        val productBId = productB.id!!

        log.info("[5-6] 상품A=$productAId, 상품B=$productBId")
        log.info("[5-6] Thread-0: A→B 순서로 잠금, Thread-1: B→A 순서로 잠금 → 데드락 유도")

        val threadCount = 2
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)
        val deadlockCount = AtomicInteger(0)
        val exceptionMessages = mutableListOf<String>()

        // 두 스레드가 각각 첫 번째 잠금을 획득한 후 동기화하기 위한 래치
        val firstLockAcquiredLatch = CountDownLatch(2)

        val executor = Executors.newFixedThreadPool(2)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(2)
        val startTime = System.currentTimeMillis()

        // Thread-0: 상품A → 상품B 순서로 잠금
        executor.submit {
            try {
                startLatch.await()
                self.lockTwoProductsWithSync(productAId, productBId, firstLockAcquiredLatch)
                successCount.incrementAndGet()
                log.info("[Thread-0] A→B 순서 잠금 성공")
            } catch (e: Exception) {
                failureCount.incrementAndGet()
                val isDeadlock = isDeadlockException(e)
                if (isDeadlock) {
                    deadlockCount.incrementAndGet()
                    log.info("[Thread-0] 데드락 감지! ${e::class.simpleName}: ${e.message?.take(100)}")
                }
                synchronized(exceptionMessages) {
                    exceptionMessages.add("Thread-0: ${e::class.simpleName}: ${e.message?.take(100)}")
                }
            } finally {
                doneLatch.countDown()
            }
        }

        // Thread-1: 상품B → 상품A 순서로 잠금 (역순!)
        executor.submit {
            try {
                startLatch.await()
                self.lockTwoProductsWithSync(productBId, productAId, firstLockAcquiredLatch)
                successCount.incrementAndGet()
                log.info("[Thread-1] B→A 순서 잠금 성공")
            } catch (e: Exception) {
                failureCount.incrementAndGet()
                val isDeadlock = isDeadlockException(e)
                if (isDeadlock) {
                    deadlockCount.incrementAndGet()
                    log.info("[Thread-1] 데드락 감지! ${e::class.simpleName}: ${e.message?.take(100)}")
                }
                synchronized(exceptionMessages) {
                    exceptionMessages.add("Thread-1: ${e::class.simpleName}: ${e.message?.take(100)}")
                }
            } finally {
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        doneLatch.await(30, TimeUnit.SECONDS)
        executor.shutdown()
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            executor.shutdownNow()
        }

        val durationMs = System.currentTimeMillis() - startTime
        val lastException = exceptionMessages.lastOrNull()

        log.info("[5-6] 결과: 성공=${successCount.get()}, 실패=${failureCount.get()}, 데드락=${deadlockCount.get()}")

        return LockResult.deadlock(
            threadCount = threadCount,
            successCount = successCount.get(),
            failureCount = failureCount.get(),
            deadlockCount = deadlockCount.get(),
            durationMs = durationMs,
            exceptionType = if (deadlockCount.get() > 0) "CannotAcquireLockException (Deadlock)" else null,
            exceptionMessage = lastException,
            details = mapOf(
                "productAId" to productAId,
                "productBId" to productBId,
                "thread0_order" to "A($productAId) → B($productBId)",
                "thread1_order" to "B($productBId) → A($productAId)",
                "allExceptions" to exceptionMessages,
                "mysqlDeadlockDetection" to "InnoDB wait-for graph 알고리즘으로 즉시 감지"
            )
        )
    }

    /**
     * 데드락 유도를 위한 두 상품 역순 잠금
     * firstLockAcquiredLatch로 두 스레드가 각각 첫 번째 잠금을 획득한 후
     * 서로 동기화하여 두 번째 잠금 시도 → 확실한 데드락 발생
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun lockTwoProductsWithSync(firstId: Long, secondId: Long, firstLockAcquiredLatch: CountDownLatch) {
        // 1. 첫 번째 상품 잠금 획득
        val first = productRepository.findByIdWithPessimisticLock(firstId)
            ?: throw IllegalArgumentException("Product not found: $firstId")
        first.stock -= 1
        log.info("[데드락] 첫 번째 잠금 획득: productId=$firstId")

        // 2. 상대 스레드도 첫 번째 잠금을 획득할 때까지 대기
        firstLockAcquiredLatch.countDown()
        firstLockAcquiredLatch.await(10, TimeUnit.SECONDS)

        // 3. 두 번째 상품 잠금 시도 → 상대가 보유 중이므로 데드락!
        log.info("[데드락] 두 번째 잠금 시도: productId=$secondId")
        val second = productRepository.findByIdWithPessimisticLock(secondId)
            ?: throw IllegalArgumentException("Product not found: $secondId")
        second.stock -= 1

        productRepository.saveAndFlush(first)
        productRepository.saveAndFlush(second)
    }

    private fun isDeadlockException(e: Exception): Boolean {
        var cause: Throwable? = e
        while (cause != null) {
            val message = cause.message?.lowercase() ?: ""
            if (message.contains("deadlock") || cause is CannotAcquireLockException) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    // ==========================================
    // 정리
    // ==========================================

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun cleanupTestData(): Int {
        val deleted = productRepository.deleteByNameStartingWith(TEST_PREFIX)
        if (deleted > 0) {
            log.info("Lab 05 테스트 데이터 ${deleted}건 삭제")
        }
        return deleted.toInt()
    }
}
