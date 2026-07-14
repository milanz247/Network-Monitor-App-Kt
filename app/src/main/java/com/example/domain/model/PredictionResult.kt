package com.example.domain.model

import java.time.LocalDate

/** Output of [com.example.domain.prediction.UsagePredictionEngine.predictDepletionDate] (Feature 2). */
sealed class PredictionResult {

    /** Fewer than 3 tracked days, or no cap configured to project against. */
    data class InsufficientData(val reason: String) : PredictionResult()

    data class WillLastFullCycle(
        val averageDailyBytes: Long,
        val projectedCycleEndUsageBytes: Long,
        val reasoning: String,
    ) : PredictionResult()

    data class WillRunOutOn(
        val date: LocalDate,
        val daysRemaining: Long,
        val averageDailyBytes: Long,
        val reasoning: String,
    ) : PredictionResult()
}
