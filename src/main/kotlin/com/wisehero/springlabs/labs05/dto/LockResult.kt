package com.wisehero.springlabs.labs05.dto

/**
 * Lab 05: Optimistic Lock vs Pessimistic Lock 실험 결과 DTO
 *
 * 각 실험의 동시성 테스트 결과를 구조화하여 반환합니다.
 */
data class LockResult(
    val experimentId: String,
    val experimentName: String,
    val description: String,
    val initialStock: Int? = null,
    val finalStock: Int? = null,
    val expectedStock: Int? = null,
    val threadCount: Int? = null,
    val successCount: Int? = null,
    val failureCount: Int? = null,
    val retryCount: Int? = null,
    val lostUpdates: Int? = null,
    val durationMs: Long? = null,
    val exceptionOccurred: Boolean = false,
    val exceptionType: String? = null,
    val exceptionMessage: String? = null,
    val details: Map<String, Any> = emptyMap(),
    val conclusion: String
) {
    companion object {
        fun lostUpdate(
            initialStock: Int,
            finalStock: Int,
            threadCount: Int,
            successCount: Int,
            durationMs: Long,
            details: Map<String, Any> = emptyMap()
        ): LockResult {
            val expectedStock = initialStock - threadCount
            val lostUpdates = finalStock - expectedStock
            return LockResult(
                experimentId = "5-1",
                experimentName = "락 없음 - Lost Update 발생",
                description = "동시성 제어 없이 read-modify-write 패턴으로 재고 차감 시 갱신 손실(Lost Update) 발생을 확인",
                initialStock = initialStock,
                finalStock = finalStock,
                expectedStock = expectedStock,
                threadCount = threadCount,
                successCount = successCount,
                lostUpdates = lostUpdates,
                durationMs = durationMs,
                details = details,
                conclusion = "Lost Update ${lostUpdates}건 발생! " +
                    "초기 재고 ${initialStock}에서 ${threadCount}개 스레드가 각 1씩 차감했으나, " +
                    "최종 재고는 ${finalStock} (기대값: ${expectedStock}). " +
                    "동시성 제어 없는 read-modify-write는 갱신 손실이 불가피합니다."
            )
        }

        fun optimisticConflict(
            initialStock: Int,
            finalStock: Int,
            threadCount: Int,
            successCount: Int,
            failureCount: Int,
            durationMs: Long,
            details: Map<String, Any> = emptyMap()
        ) = LockResult(
            experimentId = "5-2",
            experimentName = "Optimistic Lock (@Version) - 충돌 감지",
            description = "JPA @Version을 통한 낙관적 락으로 동시 수정 시 OptimisticLockException 발생을 확인",
            initialStock = initialStock,
            finalStock = finalStock,
            expectedStock = initialStock - successCount,
            threadCount = threadCount,
            successCount = successCount,
            failureCount = failureCount,
            durationMs = durationMs,
            details = details,
            conclusion = "${threadCount}개 스레드 중 ${successCount}건 성공, ${failureCount}건 OptimisticLockException. " +
                "최종 재고 ${finalStock}. @Version이 UPDATE 시 WHERE version = ?로 충돌을 감지합니다. " +
                "Lost Update는 방지되지만, 실패한 요청은 재시도 없이 유실됩니다."
        )

        fun optimisticRetry(
            initialStock: Int,
            finalStock: Int,
            threadCount: Int,
            successCount: Int,
            failureCount: Int,
            retryCount: Int,
            durationMs: Long,
            details: Map<String, Any> = emptyMap()
        ) = LockResult(
            experimentId = "5-3",
            experimentName = "Optimistic Lock + Retry - 재시도로 전부 성공",
            description = "OptimisticLockException 발생 시 재시도 로직을 추가하여 모든 요청이 성공하는지 확인",
            initialStock = initialStock,
            finalStock = finalStock,
            expectedStock = 0,
            threadCount = threadCount,
            successCount = successCount,
            failureCount = failureCount,
            retryCount = retryCount,
            durationMs = durationMs,
            details = details,
            conclusion = "${threadCount}개 스레드 모두 성공 (재시도 ${retryCount}회 발생). " +
                "최종 재고 ${finalStock}. Optimistic Lock + Retry 패턴은 경합이 낮은 환경에서 효과적입니다. " +
                "단, 경합이 심하면 재시도가 폭주할 수 있습니다."
        )

        fun pessimistic(
            initialStock: Int,
            finalStock: Int,
            threadCount: Int,
            successCount: Int,
            failureCount: Int,
            durationMs: Long,
            details: Map<String, Any> = emptyMap()
        ) = LockResult(
            experimentId = "5-4",
            experimentName = "Pessimistic Lock (SELECT FOR UPDATE) - 순차 처리",
            description = "SELECT FOR UPDATE로 행 잠금을 획득하여 순차적으로 재고 차감",
            initialStock = initialStock,
            finalStock = finalStock,
            expectedStock = 0,
            threadCount = threadCount,
            successCount = successCount,
            failureCount = failureCount,
            durationMs = durationMs,
            details = details,
            conclusion = "${threadCount}개 스레드 모두 순차 처리 완료 (${successCount}건 성공, ${failureCount}건 실패). " +
                "최종 재고 ${finalStock}. SELECT FOR UPDATE가 InnoDB row lock을 획득하여 " +
                "다른 트랜잭션을 대기시킵니다. 재시도 없이 정확한 결과를 보장합니다."
        )

        fun performance(
            optimisticResult: Map<String, Any>,
            pessimisticResult: Map<String, Any>,
            durationMs: Long,
            details: Map<String, Any> = emptyMap()
        ): LockResult {
            val optDuration = optimisticResult["durationMs"] as Long
            val pessDuration = pessimisticResult["durationMs"] as Long
            val winner = if (optDuration < pessDuration) "Optimistic" else "Pessimistic"
            return LockResult(
                experimentId = "5-5",
                experimentName = "성능 비교 - Optimistic vs Pessimistic",
                description = "낮은 경합(10t)과 높은 경합(100t)에서 Optimistic Lock(+Retry)과 Pessimistic Lock의 성능을 비교",
                durationMs = durationMs,
                details = details,
                conclusion = "평균 기준: " +
                    "Optimistic(+Retry) ${optDuration}ms vs Pessimistic ${pessDuration}ms. " +
                    "승자: ${winner}. " +
                    "낮은 경합에서는 Optimistic이 유리하고, 높은 경합에서는 Pessimistic이 안정적입니다."
            )
        }

        fun deadlock(
            threadCount: Int,
            successCount: Int,
            failureCount: Int,
            deadlockCount: Int,
            durationMs: Long,
            exceptionType: String? = null,
            exceptionMessage: String? = null,
            details: Map<String, Any> = emptyMap()
        ) = LockResult(
            experimentId = "5-6",
            experimentName = "데드락 시나리오 - 역순 잠금",
            description = "두 상품을 역순으로 SELECT FOR UPDATE하여 MySQL 데드락 감지를 확인",
            threadCount = threadCount,
            successCount = successCount,
            failureCount = failureCount,
            durationMs = durationMs,
            exceptionOccurred = deadlockCount > 0,
            exceptionType = exceptionType,
            exceptionMessage = exceptionMessage,
            details = details,
            conclusion = "데드락 ${deadlockCount}건 발생! " +
                "${threadCount}개 스레드 중 ${successCount}건 성공, ${failureCount}건 실패. " +
                "두 트랜잭션이 서로의 잠금을 대기하면 MySQL InnoDB가 wait-for graph로 " +
                "데드락을 감지하고 하나를 강제 롤백합니다."
        )
    }
}
