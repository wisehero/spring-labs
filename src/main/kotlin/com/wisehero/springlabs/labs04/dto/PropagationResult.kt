package com.wisehero.springlabs.labs04.dto

/**
 * Lab 04: 트랜잭션 전파(Propagation) 실험 결과 DTO
 *
 * 각 실험의 관찰 결과를 구조화하여 반환합니다.
 */
data class PropagationResult(
    val experimentId: String,
    val experimentName: String,
    val description: String,
    val outerTxName: String? = null,
    val innerTxName: String? = null,
    val sameTransaction: Boolean? = null,
    val outerCommitted: Boolean? = null,
    val innerCommitted: Boolean? = null,
    val exceptionOccurred: Boolean = false,
    val exceptionType: String? = null,
    val exceptionMessage: String? = null,
    val dataVerification: Map<String, Any> = emptyMap(),
    val connectionInfo: Map<String, Any> = emptyMap(),
    val details: Map<String, Any> = emptyMap(),
    val conclusion: String
) {
    companion object {
        fun success(
            experimentId: String,
            experimentName: String,
            description: String,
            outerTxName: String?,
            innerTxName: String?,
            sameTransaction: Boolean?,
            conclusion: String,
            details: Map<String, Any> = emptyMap()
        ) = PropagationResult(
            experimentId = experimentId,
            experimentName = experimentName,
            description = description,
            outerTxName = outerTxName,
            innerTxName = innerTxName,
            sameTransaction = sameTransaction,
            conclusion = conclusion,
            details = details
        )

        fun rollbackTrap(
            experimentId: String,
            exception: Exception,
            outerRowExists: Boolean,
            innerRowExists: Boolean,
            details: Map<String, Any> = emptyMap()
        ) = PropagationResult(
            experimentId = experimentId,
            experimentName = "롤백 전파 트랩 - REQUIRED",
            description = "Inner 예외 catch 후에도 공유 트랜잭션이 rollback-only로 마킹되어 UnexpectedRollbackException 발생",
            sameTransaction = true,
            outerCommitted = false,
            innerCommitted = false,
            exceptionOccurred = true,
            exceptionType = exception::class.simpleName,
            exceptionMessage = exception.message,
            dataVerification = mapOf(
                "outer_row_exists" to outerRowExists,
                "inner_row_exists" to innerRowExists,
                "both_rolled_back" to (!outerRowExists && !innerRowExists)
            ),
            conclusion = "REQUIRED로 공유된 트랜잭션에서 inner가 예외를 던지면, outer가 catch해도 트랜잭션은 이미 rollback-only. 커밋 시 UnexpectedRollbackException 발생.",
            details = details
        )

        fun unexpectedSuccess(experimentId: String) = PropagationResult(
            experimentId = experimentId,
            experimentName = "예상치 못한 성공",
            description = "UnexpectedRollbackException이 발생할 것으로 예상했으나 정상 완료됨",
            conclusion = "예상과 다른 결과 - 디버깅 필요"
        )

        fun dataExperiment(
            experimentId: String,
            experimentName: String,
            description: String,
            outerCommitted: Boolean,
            innerCommitted: Boolean,
            dataVerification: Map<String, Any>,
            conclusion: String,
            exceptionOccurred: Boolean = false,
            exceptionType: String? = null,
            exceptionMessage: String? = null,
            details: Map<String, Any> = emptyMap()
        ) = PropagationResult(
            experimentId = experimentId,
            experimentName = experimentName,
            description = description,
            sameTransaction = false,
            outerCommitted = outerCommitted,
            innerCommitted = innerCommitted,
            exceptionOccurred = exceptionOccurred,
            exceptionType = exceptionType,
            exceptionMessage = exceptionMessage,
            dataVerification = dataVerification,
            conclusion = conclusion,
            details = details
        )

        fun connectionInfo(
            experimentId: String,
            experimentName: String,
            description: String,
            connectionInfo: Map<String, Any>,
            conclusion: String,
            exceptionOccurred: Boolean = false,
            exceptionType: String? = null,
            exceptionMessage: String? = null,
            details: Map<String, Any> = emptyMap()
        ) = PropagationResult(
            experimentId = experimentId,
            experimentName = experimentName,
            description = description,
            exceptionOccurred = exceptionOccurred,
            exceptionType = exceptionType,
            exceptionMessage = exceptionMessage,
            connectionInfo = connectionInfo,
            conclusion = conclusion,
            details = details
        )
    }
}
