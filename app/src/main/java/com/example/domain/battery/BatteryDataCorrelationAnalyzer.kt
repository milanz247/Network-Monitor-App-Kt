package com.example.domain.battery

import com.example.data.local.DataUsageDao
import com.example.data.repository.DiagnosticsRepository
import com.example.domain.model.BatteryDataCorrelation
import com.example.domain.util.toStoredDateKey
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Phase 5 (#17) - correlates device-wide battery drain with device-wide data usage over a period.
 *
 * PLATFORM LIMITATION: this is NOT per-app, and can never be with public APIs. Real per-app
 * battery attribution requires `BatteryStatsManager`/`BatteryUsageStats` (API 31+), gated behind
 * the `android.permission.BATTERY_STATS` *signature* permission - a normal app cannot hold it
 * under any circumstance, not even with the user's consent (unlike a runtime permission). Claiming
 * a per-app number here would mean fabricating data this app has no way to measure, so this only
 * ever reports the device-wide figure computed from [com.example.data.local.BatteryLevelSample]
 * (a plain `BatteryManager.BATTERY_PROPERTY_CAPACITY` reading - the one battery signal genuinely
 * available to a regular app).
 */
class BatteryDataCorrelationAnalyzer @Inject constructor(
    private val dataUsageDao: DataUsageDao,
    private val diagnosticsRepository: DiagnosticsRepository,
) {
    suspend fun correlateForRange(
        start: LocalDate,
        end: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): BatteryDataCorrelation {
        val startMillis = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

        val batterySamples = diagnosticsRepository.getBatteryLevelForRange(startMillis, endMillis)
        val dailyRows = dataUsageDao.getDailyDataUsageForRange(
            start.toStoredDateKey(zone),
            end.toStoredDateKey(zone),
        ).first()
        val totalBytes = dailyRows.sumOf { it.wifiBytes + it.mobileBytes }

        if (batterySamples.size < 2) {
            return BatteryDataCorrelation(totalBytes = totalBytes, batteryDrainPercent = null, bytesPerPercentBattery = null)
        }

        val sorted = batterySamples.sortedBy { it.timestamp }
        // Net drop across the whole window - a deliberately simple signal (ignores mid-window
        // charging cycles) matching the coarse, best-effort nature of a device-wide approximation.
        val drop = (sorted.first().batteryLevelPercent - sorted.last().batteryLevelPercent).coerceAtLeast(0)

        return BatteryDataCorrelation(
            totalBytes = totalBytes,
            batteryDrainPercent = drop,
            bytesPerPercentBattery = if (drop > 0) totalBytes / drop else null,
        )
    }
}
