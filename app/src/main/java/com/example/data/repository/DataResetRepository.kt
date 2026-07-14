package com.example.data.repository

import com.example.data.local.DataUsageDao
import com.example.data.local.DiagnosticsDao
import com.example.domain.util.toStoredDateKey
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Phase 4 (#14) - an inclusive calendar-day range to reset, or null (in the repository calls) for "everything". */
data class ResetDateRange(val start: LocalDate, val end: LocalDate)

/**
 * Phase 4 (#14) - wipe all or date-ranged history across every Room table. Deliberately a two-step
 * contract - [previewDeleteCount] reports how many rows *would* be deleted, and only
 * [confirmDelete] (called with that same range) actually deletes - so a caller can never trigger a
 * silent one-shot wipe.
 *
 * [com.example.data.local.DailyDataUsage]/[com.example.data.local.AppDataUsage] key rows by an
 * epoch-*day* integer, while the Phase 1 diagnostics tables key rows by an epoch-*millis*
 * timestamp - two different units. [range] is converted to each unit separately here so callers
 * never have to reason about that distinction themselves.
 */
class DataResetRepository @Inject constructor(
    private val dataUsageDao: DataUsageDao,
    private val diagnosticsDao: DiagnosticsDao,
) {
    suspend fun previewDeleteCount(range: ResetDateRange? = null, zone: ZoneId = ZoneId.systemDefault()): Int {
        val (dayStart, dayEnd) = dayKeyBounds(range, zone)
        val (millisStart, millisEnd) = millisBounds(range, zone)
        return dataUsageDao.countDailyDataUsageInRange(dayStart, dayEnd) +
            dataUsageDao.countAppDataUsageInRange(dayStart, dayEnd) +
            diagnosticsDao.countSpeedHistoryInRange(millisStart, millisEnd) +
            diagnosticsDao.countDowntimeLogsInRange(millisStart, millisEnd) +
            diagnosticsDao.countSignalStrengthInRange(millisStart, millisEnd) +
            diagnosticsDao.countConnectionTypeTransitionsInRange(millisStart, millisEnd) +
            diagnosticsDao.countBatteryLevelInRange(millisStart, millisEnd)
    }

    /** Must be called with the exact same [range] just passed to [previewDeleteCount]. */
    suspend fun confirmDelete(range: ResetDateRange? = null, zone: ZoneId = ZoneId.systemDefault()) {
        val (dayStart, dayEnd) = dayKeyBounds(range, zone)
        val (millisStart, millisEnd) = millisBounds(range, zone)
        dataUsageDao.deleteDailyDataUsageInRange(dayStart, dayEnd)
        dataUsageDao.deleteAppDataUsageInRange(dayStart, dayEnd)
        diagnosticsDao.deleteSpeedHistoryInRange(millisStart, millisEnd)
        diagnosticsDao.deleteDowntimeLogsInRange(millisStart, millisEnd)
        diagnosticsDao.deleteSignalStrengthInRange(millisStart, millisEnd)
        diagnosticsDao.deleteConnectionTypeTransitionsInRange(millisStart, millisEnd)
        diagnosticsDao.deleteBatteryLevelInRange(millisStart, millisEnd)
    }

    private fun dayKeyBounds(range: ResetDateRange?, zone: ZoneId): Pair<Long, Long> =
        if (range == null) Long.MIN_VALUE to Long.MAX_VALUE
        else range.start.toStoredDateKey(zone) to range.end.toStoredDateKey(zone)

    private fun millisBounds(range: ResetDateRange?, zone: ZoneId): Pair<Long, Long> =
        if (range == null) {
            Long.MIN_VALUE to Long.MAX_VALUE
        } else {
            val startMillis = range.start.atStartOfDay(zone).toInstant().toEpochMilli()
            val endMillis = range.end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            startMillis to endMillis
        }
}
