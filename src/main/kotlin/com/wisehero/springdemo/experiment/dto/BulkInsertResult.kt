package com.wisehero.springdemo.experiment.dto

import java.time.LocalDateTime

/**
 * 단일 Insert 실험 결과
 */
data class InsertResult(
    val method: String,
    val count: Int,
    val durationMs: Long,
    val throughput: Double,      // records/second
    val avgPerRecordMs: Double   // ms/record
) {
    companion object {
        fun of(method: String, count: Int, durationMs: Long): InsertResult {
            return InsertResult(
                method = method,
                count = count,
                durationMs = durationMs,
                throughput = if (durationMs > 0) count * 1000.0 / durationMs else 0.0,
                avgPerRecordMs = if (count > 0) durationMs.toDouble() / count else 0.0
            )
        }
    }
}

/**
 * 전체 실험 요약
 */
data class ExperimentSummary(
    val testDate: LocalDateTime = LocalDateTime.now(),
    val entityType: String = "Transaction",
    val testCounts: List<Int>,
    val results: Map<Int, List<InsertResult>>,   // count -> results for each method
    val rankings: Map<Int, List<RankingEntry>>   // count -> sorted by speed
)

data class RankingEntry(
    val rank: Int,
    val method: String,
    val durationMs: Long,
    val throughput: Double,
    val comparedToFirst: String  // e.g., "2.5x slower"
)
