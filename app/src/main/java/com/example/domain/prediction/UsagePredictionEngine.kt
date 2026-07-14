package com.example.domain.prediction

import com.example.data.local.DataUsageDao
import com.example.data.prefs.DataCapPreferences
import com.example.domain.billing.BillingCycleCalculator
import com.example.domain.model.PredictionResult
import com.example.domain.util.toStoredDateKey
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.ceil

/**
 * Feature 2: "will I run out?" estimate. Uses a rolling 7-day average (not just today) so a single
 * spike or quiet day doesn't swing the projection - see [predictDepletionDate].
 */
class UsagePredictionEngine @Inject constructor(
    private val dao: DataUsageDao,
    private val preferences: DataCapPreferences,
) {
    suspend fun predictDepletionDate(zone: ZoneId = ZoneId.systemDefault()): PredictionResult {
        val settings = preferences.settingsFlow.first()
        if (!settings.capEnabled || settings.monthlyCapBytes <= 0) {
            return PredictionResult.InsufficientData("No data cap is configured yet.")
        }

        val today = LocalDate.now(zone)
        val cycleStart = BillingCycleCalculator.cycleStart(settings.billingCycleStartDay, today)
        val cycleEnd = BillingCycleCalculator.cycleEnd(settings.billingCycleStartDay, today)

        // Rolling window: last 7 days, clipped so it never reads back past the current cycle's start
        // (a fresh cycle shouldn't inherit last month's pace).
        val windowStart = maxOf(today.minusDays(6), cycleStart)
        val windowRows = dao.getDailyDataUsageForRange(
            windowStart.toStoredDateKey(zone),
            today.toStoredDateKey(zone),
        ).first()

        // Grouping by date and summing its buckets handles both a pre-Feature-5 single row per day
        // and the split Wi-Fi/per-SIM rows uniformly. A day where the worker ran but usage was 0 still
        // produces an entry here (zero, not missing) - see DailyUsageWorker, which upserts every day
        // regardless of byte count - so "zero usage days" correctly pull the average down rather than
        // being mistaken for missing history.
        val dailyTotals = windowRows
            .groupBy { it.date }
            .mapValues { (_, bucketsForDay) ->
                bucketsForDay.sumOf { row ->
                    if (settings.carrierSpecificMode) row.mobileBytes else row.wifiBytes + row.mobileBytes
                }
            }
            .toSortedMap()

        if (dailyTotals.size < MIN_DAYS_OF_HISTORY) {
            return PredictionResult.InsufficientData(
                "Only ${dailyTotals.size} day(s) of usage history so far - need at least $MIN_DAYS_OF_HISTORY to estimate."
            )
        }

        val averageDailyBytes = dailyTotals.values.sum() / dailyTotals.size
        val reasoning = describeTrend(dailyTotals.values.toList())

        val cycleToDateRows = dao.getDailyDataUsageForRange(
            cycleStart.toStoredDateKey(zone),
            today.toStoredDateKey(zone),
        ).first()
        val usedSoFar = cycleToDateRows.sumOf { row ->
            if (settings.carrierSpecificMode) row.mobileBytes else row.wifiBytes + row.mobileBytes
        }

        val remainingDaysInCycle = ChronoUnit.DAYS.between(today, cycleEnd) + 1 // inclusive of today
        val projectedCycleEndUsage = usedSoFar + averageDailyBytes * remainingDaysInCycle

        if (averageDailyBytes <= 0 || projectedCycleEndUsage <= settings.monthlyCapBytes) {
            return PredictionResult.WillLastFullCycle(
                averageDailyBytes = averageDailyBytes,
                projectedCycleEndUsageBytes = projectedCycleEndUsage,
                reasoning = reasoning,
            )
        }

        val bytesRemaining = settings.monthlyCapBytes - usedSoFar
        val daysUntilCapReached = ceil(bytesRemaining.toDouble() / averageDailyBytes).toLong().coerceAtLeast(0)

        return PredictionResult.WillRunOutOn(
            date = today.plusDays(daysUntilCapReached),
            daysRemaining = daysUntilCapReached,
            averageDailyBytes = averageDailyBytes,
            reasoning = reasoning,
        )
    }

    /** Compares the first half of the window against the second half for a plain-language trend note. */
    private fun describeTrend(dailyTotalsChronological: List<Long>): String {
        if (dailyTotalsChronological.size < 4) return "steady usage so far"
        val midpoint = dailyTotalsChronological.size / 2
        val earlierAvg = dailyTotalsChronological.take(midpoint).average()
        val recentAvg = dailyTotalsChronological.takeLast(dailyTotalsChronological.size - midpoint).average()
        if (earlierAvg <= 0.0) return "steady usage so far"
        val change = (recentAvg - earlierAvg) / earlierAvg
        return when {
            change > 0.15 -> "usage has been trending up over the last week"
            change < -0.15 -> "usage has been trending down over the last week"
            else -> "usage has been steady over the last week"
        }
    }

    companion object {
        private const val MIN_DAYS_OF_HISTORY = 3
    }
}
