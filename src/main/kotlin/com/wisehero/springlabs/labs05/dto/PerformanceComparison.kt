package com.wisehero.springlabs.labs05.dto

/**
 * Lab 05 실험 5-5: 성능 비교 결과
 */
data class PerformanceComparison(
    val scenario: String,
    val threadCount: Int,
    val optimisticDurationMs: Long,
    val optimisticRetries: Int,
    val optimisticSuccessCount: Int,
    val pessimisticDurationMs: Long,
    val pessimisticSuccessCount: Int,
    val winner: String,
    val speedDifferencePercent: Double
)
