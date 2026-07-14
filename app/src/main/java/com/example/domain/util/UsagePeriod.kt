package com.example.domain.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Converts a calendar date into the same "epoch day" key that [com.example.worker.DailyUsageWorker]
 * already stores in [com.example.data.local.DailyDataUsage.date] (local midnight instant / ms-per-day).
 * Keeping one definition avoids the two call sites drifting apart on DST-boundary days.
 */
fun LocalDate.toStoredDateKey(zone: ZoneId = ZoneId.systemDefault()): Long =
    atStartOfDay(zone).toInstant().toEpochMilli() / 86_400_000L

/** Inclusive [start, end] date-key ranges for the Feature 4 leaderboard period filter - no magic numbers, no Calendar. */
object UsagePeriod {

    fun today(zone: ZoneId = ZoneId.systemDefault()): LongRange {
        val key = LocalDate.now(zone).toStoredDateKey(zone)
        return key..key
    }

    fun thisWeek(zone: ZoneId = ZoneId.systemDefault()): LongRange {
        val today = LocalDate.now(zone)
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return weekStart.toStoredDateKey(zone)..today.toStoredDateKey(zone)
    }

    fun thisMonth(zone: ZoneId = ZoneId.systemDefault()): LongRange {
        val today = LocalDate.now(zone)
        val monthStart = today.withDayOfMonth(1)
        return monthStart.toStoredDateKey(zone)..today.toStoredDateKey(zone)
    }
}
