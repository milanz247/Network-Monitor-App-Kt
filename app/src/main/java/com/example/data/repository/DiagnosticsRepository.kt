package com.example.data.repository

import com.example.data.local.BatteryLevelSample
import com.example.data.local.ConnectionTypeTransition
import com.example.data.local.DiagnosticsDao
import com.example.data.local.DowntimeReason
import com.example.data.local.NetworkDowntimeLog
import com.example.data.local.SignalStrengthSample
import com.example.data.local.SpeedHistory
import com.example.domain.model.PeakSpeedRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Phase 1 - Speed History & Diagnostics (#1-#5). All writes go through here, never the DAO directly, so cleanup/dedup rules live in one place. */
class DiagnosticsRepository @Inject constructor(
    private val dao: DiagnosticsDao,
) {
    // ---- #1 Speed History ----

    suspend fun recordSpeedSample(
        timestamp: Long,
        avgDownloadBps: Long,
        avgUploadBps: Long,
        peakDownloadBps: Long,
        peakUploadBps: Long,
    ) = withContext(Dispatchers.IO) {
        dao.insertSpeedHistory(
            SpeedHistory(
                timestamp = timestamp,
                avgDownloadBps = avgDownloadBps,
                avgUploadBps = avgUploadBps,
                peakDownloadBps = peakDownloadBps,
                peakUploadBps = peakUploadBps,
            )
        )
    }

    fun getSpeedHistoryForRange(startTime: Long, endTime: Long): Flow<List<SpeedHistory>> =
        dao.getSpeedHistoryForRange(startTime, endTime)

    // ---- #2 Peak Speed Record (derived, no separate storage) ----

    suspend fun getPeakSpeedRecord(startTime: Long, endTime: Long): PeakSpeedRecord = withContext(Dispatchers.IO) {
        val downloadRow = dao.getRowWithMaxDownloadPeak(startTime, endTime)
        val uploadRow = dao.getRowWithMaxUploadPeak(startTime, endTime)
        PeakSpeedRecord(
            peakDownloadBps = downloadRow?.peakDownloadBps ?: 0L,
            peakDownloadAt = downloadRow?.timestamp,
            peakUploadBps = uploadRow?.peakUploadBps ?: 0L,
            peakUploadAt = uploadRow?.timestamp,
        )
    }

    // ---- #3 Network Downtime / Low-Speed Log ----

    /** No-op if a span for [reason] is already open - callers can call this on every tick without worrying about duplicates. */
    suspend fun openDowntimeIfNeeded(reason: DowntimeReason, timestamp: Long) = withContext(Dispatchers.IO) {
        val existing = dao.getOpenDowntimeLog(reason.name)
        if (existing == null) {
            dao.insertDowntimeLog(NetworkDowntimeLog(startTime = timestamp, endTime = null, reason = reason.name))
        }
    }

    /** No-op if there's no open span for [reason]. */
    suspend fun closeDowntimeIfOpen(reason: DowntimeReason, timestamp: Long) = withContext(Dispatchers.IO) {
        val open = dao.getOpenDowntimeLog(reason.name) ?: return@withContext
        dao.closeDowntimeLog(open.id, timestamp)
    }

    fun getDowntimeLogsForRange(startTime: Long, endTime: Long): Flow<List<NetworkDowntimeLog>> =
        dao.getDowntimeLogsForRange(startTime, endTime)

    // ---- #4 Signal Strength History ----

    suspend fun recordSignalSample(timestamp: Long, wifiRssi: Int?, cellularSignalLevel: Int?) =
        withContext(Dispatchers.IO) {
            if (wifiRssi == null && cellularSignalLevel == null) return@withContext
            dao.insertSignalStrengthSample(
                SignalStrengthSample(timestamp = timestamp, wifiRssi = wifiRssi, cellularSignalLevel = cellularSignalLevel)
            )
        }

    fun getSignalStrengthForRange(startTime: Long, endTime: Long): Flow<List<SignalStrengthSample>> =
        dao.getSignalStrengthForRange(startTime, endTime)

    // ---- #5 Connection Type Transitions ----

    /** Only writes a row when [connectionType] differs from the last recorded one. */
    suspend fun recordConnectionTypeIfChanged(timestamp: Long, connectionType: String) = withContext(Dispatchers.IO) {
        val latest = dao.getLatestConnectionTypeTransition()
        if (latest?.connectionType != connectionType) {
            dao.insertConnectionTypeTransition(ConnectionTypeTransition(timestamp = timestamp, connectionType = connectionType))
        }
    }

    fun getConnectionTypeTransitionsForRange(startTime: Long, endTime: Long): Flow<List<ConnectionTypeTransition>> =
        dao.getConnectionTypeTransitionsForRange(startTime, endTime)

    // ---- Battery Level History (Phase 5 #17) ----

    suspend fun recordBatteryLevelSample(timestamp: Long, batteryLevelPercent: Int) = withContext(Dispatchers.IO) {
        dao.insertBatteryLevelSample(BatteryLevelSample(timestamp = timestamp, batteryLevelPercent = batteryLevelPercent))
    }

    suspend fun getBatteryLevelForRange(startTime: Long, endTime: Long): List<BatteryLevelSample> =
        withContext(Dispatchers.IO) { dao.getBatteryLevelForRange(startTime, endTime) }

    // ---- Retention cleanup (called from DailyUsageWorker's existing cleanup pass, see WorkScheduler) ----

    suspend fun pruneOlderThan(cutoffTimestamp: Long) = withContext(Dispatchers.IO) {
        dao.deleteSpeedHistoryOlderThan(cutoffTimestamp)
        dao.deleteDowntimeLogsOlderThan(cutoffTimestamp)
        dao.deleteSignalStrengthOlderThan(cutoffTimestamp)
        dao.deleteConnectionTypeTransitionsOlderThan(cutoffTimestamp)
        dao.deleteBatteryLevelOlderThan(cutoffTimestamp)
    }
}
