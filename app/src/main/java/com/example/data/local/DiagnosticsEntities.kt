package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

/**
 * Phase 1 (#1) - a periodic speed sample bucket, written roughly every ~90s by
 * [com.example.service.NetworkSpeedService] (not every 1s tick) so this table stays small even
 * with the service running continuously. All four values are in bytes/sec.
 */
@Entity(tableName = "speed_history", indices = [Index(value = ["timestamp"])])
@JsonClass(generateAdapter = true) // Phase 4 (#13) - serialized as-is in manual backup/restore, see BackupRepository.
data class SpeedHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val avgDownloadBps: Long,
    val avgUploadBps: Long,
    val peakDownloadBps: Long,
    val peakUploadBps: Long,
)

/** Phase 1 (#3) - why a [NetworkDowntimeLog] span was opened. */
enum class DowntimeReason { NO_CONNECTION, LOW_SPEED }

/**
 * Phase 1 (#3) - a span of degraded connectivity. [endTime] is null while the span is still open
 * (see [DiagnosticsDao.getOpenDowntimeLog]) and gets filled in the moment connectivity/speed
 * recovers.
 */
@Entity(tableName = "network_downtime_log", indices = [Index(value = ["startTime"])])
@JsonClass(generateAdapter = true) // Phase 4 (#13) - serialized as-is in manual backup/restore, see BackupRepository.
data class NetworkDowntimeLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long?,
    val reason: String, // DowntimeReason.name
)

/**
 * Phase 1 (#4) - Wi-Fi RSSI and/or cellular signal level, sampled at a coarse interval piggy-
 * backed on [com.example.service.NetworkSpeedService]'s existing tick loop. Either field can be
 * null (e.g. no Wi-Fi in range, or no active SIM) - never both, since a sample is only taken when
 * there's an active connection to measure.
 */
@Entity(tableName = "signal_strength_sample", indices = [Index(value = ["timestamp"])])
@JsonClass(generateAdapter = true) // Phase 4 (#13) - serialized as-is in manual backup/restore, see BackupRepository.
data class SignalStrengthSample(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val wifiRssi: Int?,
    /** 0 (none/unknown) .. 4 (great) - see `android.telephony.SignalStrength.getLevel()`. */
    val cellularSignalLevel: Int?,
)

/**
 * Phase 1 (#5) - a timestamped connection-type change. Only written when the type actually
 * differs from the previous sample (see `ConnectionTypeMonitor`), not on every tick.
 */
@Entity(tableName = "connection_type_transition", indices = [Index(value = ["timestamp"])])
@JsonClass(generateAdapter = true) // Phase 4 (#13) - serialized as-is in manual backup/restore, see BackupRepository.
data class ConnectionTypeTransition(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    /** One of "WIFI", "2G", "3G", "4G", "5G", "NONE" - see `ConnectionTypeMonitor.classify()`. */
    val connectionType: String,
)

/**
 * Phase 5 (#17) - device-wide battery level, sampled at the same coarse interval as
 * [SignalStrengthSample] (piggybacked on [com.example.service.NetworkSpeedService]'s tick loop).
 *
 * PLATFORM LIMITATION: this is device-wide, not per-app. True per-app battery attribution needs
 * `BatteryStatsManager`/`BatteryUsageStats`, which require the `android.permission.BATTERY_STATS`
 * signature permission - restricted to system/privileged apps, not obtainable by a normal app at
 * all (not even with user consent). [BatteryManager.BATTERY_PROPERTY_CAPACITY] (used here) is the
 * only battery signal genuinely available to a regular app - see `BatteryDataCorrelationAnalyzer`.
 */
@Entity(tableName = "battery_level_sample", indices = [Index(value = ["timestamp"])])
@JsonClass(generateAdapter = true) // Phase 4 (#13) - serialized as-is in manual backup/restore, see BackupRepository.
data class BatteryLevelSample(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val batteryLevelPercent: Int,
)
