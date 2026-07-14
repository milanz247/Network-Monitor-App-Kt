package com.example.data.backup

import com.example.data.local.AppDataUsage
import com.example.data.local.BatteryLevelSample
import com.example.data.local.ConnectionTypeTransition
import com.example.data.local.DailyDataUsage
import com.example.data.local.NetworkDowntimeLog
import com.example.data.local.SignalStrengthSample
import com.example.data.local.SpeedHistory
import com.squareup.moshi.JsonClass

/**
 * Phase 4 (#13) - the full on-device export. [backupFormatVersion] is this JSON schema's own
 * version, deliberately independent of Room's [com.example.data.local.AppDatabase] version - a
 * backup file needs to stay readable across app updates even as the *live* DB migrates, so it gets
 * its own, much slower-moving version number rather than being tied to Room's.
 */
@JsonClass(generateAdapter = true)
data class BackupPayload(
    val backupFormatVersion: Int = BackupRepository.BACKUP_FORMAT_VERSION,
    val exportedAtMillis: Long,
    val dailyDataUsage: List<DailyDataUsage>,
    val appDataUsage: List<AppDataUsage>,
    val speedHistory: List<SpeedHistory>,
    val networkDowntimeLog: List<NetworkDowntimeLog>,
    val signalStrengthSample: List<SignalStrengthSample>,
    val connectionTypeTransition: List<ConnectionTypeTransition>,
    val batteryLevelSample: List<BatteryLevelSample> = emptyList(),
)

data class ImportSummary(
    val dailyDataUsageRows: Int,
    val appDataUsageRows: Int,
    val speedHistoryRows: Int,
    val networkDowntimeLogRows: Int,
    val signalStrengthSampleRows: Int,
    val connectionTypeTransitionRows: Int,
    val batteryLevelSampleRows: Int = 0,
) {
    val totalRows: Int
        get() = dailyDataUsageRows + appDataUsageRows + speedHistoryRows +
            networkDowntimeLogRows + signalStrengthSampleRows + connectionTypeTransitionRows + batteryLevelSampleRows
}
