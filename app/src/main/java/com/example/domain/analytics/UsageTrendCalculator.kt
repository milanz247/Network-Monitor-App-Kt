package com.example.domain.analytics

import com.example.data.local.DataUsageDao
import com.example.domain.model.UsageTrendPoint
import com.example.domain.util.toStoredDateKey
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/**
 * Phase 2 (#6) - weekly/monthly usage trend.
 *
 * APPROACH: one small range query per period (like Phase 2 (#7)'s hourly breakdown), rather than
 * fetching one large range and grouping by date in SQL or Kotlin. [com.example.data.local.DailyDataUsage.date]
 * is an opaque epoch-day *key* (`localMidnightUtcMillis / 86_400_000`, see its class doc) with no
 * safe, general inverse back to [LocalDate] - the offset can shift the key by a day depending on
 * the zone, so naively reconstructing a [LocalDate] from a stored key to decide "which week/month
 * is this row in" is a correctness trap. Reusing the already-correct *forward* conversion
 * ([toStoredDateKey], the same one every other query in this codebase already uses) per period
 * sidesteps that entirely, at the cost of N small queries instead of one big one - N is small
 * (weeks/months requested), so this is cheap.
 */
class UsageTrendCalculator @Inject constructor(
    private val dao: DataUsageDao,
) {
    suspend fun getWeeklyTrend(weeksBack: Int = 8, zone: ZoneId = ZoneId.systemDefault()): List<UsageTrendPoint> {
        val today = LocalDate.now(zone)
        val thisWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (weeksBack - 1 downTo 0).map { weeksAgo ->
            val weekStart = thisWeekStart.minusWeeks(weeksAgo.toLong())
            val weekEnd = weekStart.plusDays(6).let { if (it.isAfter(today)) today else it }
            val (wifiBytes, mobileBytes) = sumRange(weekStart, weekEnd, zone)
            UsageTrendPoint(
                periodLabel = weekStart.format(DateTimeFormatter.ofPattern("MMM d")),
                periodStart = weekStart.toStoredDateKey(zone),
                wifiBytes = wifiBytes,
                mobileBytes = mobileBytes,
            )
        }
    }

    suspend fun getMonthlyTrend(monthsBack: Int = 6, zone: ZoneId = ZoneId.systemDefault()): List<UsageTrendPoint> {
        val today = LocalDate.now(zone)
        val thisMonth = YearMonth.from(today)
        return (monthsBack - 1 downTo 0).map { monthsAgo ->
            val month = thisMonth.minusMonths(monthsAgo.toLong())
            val monthStart = month.atDay(1)
            val monthEnd = month.atEndOfMonth().let { if (it.isAfter(today)) today else it }
            val (wifiBytes, mobileBytes) = sumRange(monthStart, monthEnd, zone)
            UsageTrendPoint(
                periodLabel = month.format(DateTimeFormatter.ofPattern("MMM yyyy")),
                periodStart = monthStart.toStoredDateKey(zone),
                wifiBytes = wifiBytes,
                mobileBytes = mobileBytes,
            )
        }
    }

    private suspend fun sumRange(start: LocalDate, end: LocalDate, zone: ZoneId): Pair<Long, Long> {
        val rows = dao.getDailyDataUsageForRange(start.toStoredDateKey(zone), end.toStoredDateKey(zone)).first()
        return rows.sumOf { it.wifiBytes } to rows.sumOf { it.mobileBytes }
    }
}
