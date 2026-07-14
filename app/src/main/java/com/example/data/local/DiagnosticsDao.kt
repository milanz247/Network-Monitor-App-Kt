package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Phase 1 - Speed History & Diagnostics. Kept separate from [DataUsageDao] (different table family). */
@Dao
interface DiagnosticsDao {

    // ---- Speed History (#1, #2 - peak is a MAX() query over this table, no separate storage) ----

    @Insert
    suspend fun insertSpeedHistory(sample: SpeedHistory)

    @Query("SELECT * FROM speed_history WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    fun getSpeedHistoryForRange(startTime: Long, endTime: Long): Flow<List<SpeedHistory>>

    @Query("SELECT * FROM speed_history WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY peakDownloadBps DESC LIMIT 1")
    suspend fun getRowWithMaxDownloadPeak(startTime: Long, endTime: Long): SpeedHistory?

    @Query("SELECT * FROM speed_history WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY peakUploadBps DESC LIMIT 1")
    suspend fun getRowWithMaxUploadPeak(startTime: Long, endTime: Long): SpeedHistory?

    @Query("DELETE FROM speed_history WHERE timestamp < :cutoffTime")
    suspend fun deleteSpeedHistoryOlderThan(cutoffTime: Long)

    /** Phase 4 (#13) - full-table read for backup export. */
    @Query("SELECT * FROM speed_history")
    suspend fun getAllSpeedHistory(): List<SpeedHistory>

    /** Phase 4 (#13) - bulk insert for backup restore. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSpeedHistory(rows: List<SpeedHistory>)

    /** Phase 4 (#14) - preview/confirm delete contract, see DataResetRepository. */
    @Query("SELECT COUNT(*) FROM speed_history WHERE timestamp >= :startTime AND timestamp <= :endTime")
    suspend fun countSpeedHistoryInRange(startTime: Long, endTime: Long): Int

    @Query("DELETE FROM speed_history WHERE timestamp >= :startTime AND timestamp <= :endTime")
    suspend fun deleteSpeedHistoryInRange(startTime: Long, endTime: Long)

    // ---- Network Downtime / Low-Speed Log (#3) ----

    @Insert
    suspend fun insertDowntimeLog(log: NetworkDowntimeLog): Long

    @Query("SELECT * FROM network_downtime_log WHERE reason = :reason AND endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun getOpenDowntimeLog(reason: String): NetworkDowntimeLog?

    @Query("UPDATE network_downtime_log SET endTime = :endTime WHERE id = :id")
    suspend fun closeDowntimeLog(id: Long, endTime: Long)

    @Query("SELECT * FROM network_downtime_log WHERE startTime >= :startTime AND startTime <= :endTime ORDER BY startTime DESC")
    fun getDowntimeLogsForRange(startTime: Long, endTime: Long): Flow<List<NetworkDowntimeLog>>

    @Query("DELETE FROM network_downtime_log WHERE startTime < :cutoffTime")
    suspend fun deleteDowntimeLogsOlderThan(cutoffTime: Long)

    /** Phase 4 (#13) - full-table read for backup export. */
    @Query("SELECT * FROM network_downtime_log")
    suspend fun getAllDowntimeLogs(): List<NetworkDowntimeLog>

    /** Phase 4 (#13) - bulk insert for backup restore. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllDowntimeLogs(rows: List<NetworkDowntimeLog>)

    /** Phase 4 (#14) - preview/confirm delete contract, see DataResetRepository. */
    @Query("SELECT COUNT(*) FROM network_downtime_log WHERE startTime >= :startTime AND startTime <= :endTime")
    suspend fun countDowntimeLogsInRange(startTime: Long, endTime: Long): Int

    @Query("DELETE FROM network_downtime_log WHERE startTime >= :startTime AND startTime <= :endTime")
    suspend fun deleteDowntimeLogsInRange(startTime: Long, endTime: Long)

    // ---- Signal Strength History (#4) ----

    @Insert
    suspend fun insertSignalStrengthSample(sample: SignalStrengthSample)

    @Query("SELECT * FROM signal_strength_sample WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    fun getSignalStrengthForRange(startTime: Long, endTime: Long): Flow<List<SignalStrengthSample>>

    @Query("DELETE FROM signal_strength_sample WHERE timestamp < :cutoffTime")
    suspend fun deleteSignalStrengthOlderThan(cutoffTime: Long)

    /** Phase 4 (#13) - full-table read for backup export. */
    @Query("SELECT * FROM signal_strength_sample")
    suspend fun getAllSignalStrengthSamples(): List<SignalStrengthSample>

    /** Phase 4 (#13) - bulk insert for backup restore. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSignalStrengthSamples(rows: List<SignalStrengthSample>)

    /** Phase 4 (#14) - preview/confirm delete contract, see DataResetRepository. */
    @Query("SELECT COUNT(*) FROM signal_strength_sample WHERE timestamp >= :startTime AND timestamp <= :endTime")
    suspend fun countSignalStrengthInRange(startTime: Long, endTime: Long): Int

    @Query("DELETE FROM signal_strength_sample WHERE timestamp >= :startTime AND timestamp <= :endTime")
    suspend fun deleteSignalStrengthInRange(startTime: Long, endTime: Long)

    // ---- Connection Type Transitions (#5) ----

    @Insert
    suspend fun insertConnectionTypeTransition(transition: ConnectionTypeTransition)

    @Query("SELECT * FROM connection_type_transition ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestConnectionTypeTransition(): ConnectionTypeTransition?

    @Query("SELECT * FROM connection_type_transition WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    fun getConnectionTypeTransitionsForRange(startTime: Long, endTime: Long): Flow<List<ConnectionTypeTransition>>

    @Query("DELETE FROM connection_type_transition WHERE timestamp < :cutoffTime")
    suspend fun deleteConnectionTypeTransitionsOlderThan(cutoffTime: Long)

    /** Phase 4 (#13) - full-table read for backup export. */
    @Query("SELECT * FROM connection_type_transition")
    suspend fun getAllConnectionTypeTransitions(): List<ConnectionTypeTransition>

    /** Phase 4 (#13) - bulk insert for backup restore. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllConnectionTypeTransitions(rows: List<ConnectionTypeTransition>)

    /** Phase 4 (#14) - preview/confirm delete contract, see DataResetRepository. */
    @Query("SELECT COUNT(*) FROM connection_type_transition WHERE timestamp >= :startTime AND timestamp <= :endTime")
    suspend fun countConnectionTypeTransitionsInRange(startTime: Long, endTime: Long): Int

    @Query("DELETE FROM connection_type_transition WHERE timestamp >= :startTime AND timestamp <= :endTime")
    suspend fun deleteConnectionTypeTransitionsInRange(startTime: Long, endTime: Long)

    // ---- Battery Level History (Phase 5 #17) ----

    @Insert
    suspend fun insertBatteryLevelSample(sample: BatteryLevelSample)

    @Query("SELECT * FROM battery_level_sample WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    suspend fun getBatteryLevelForRange(startTime: Long, endTime: Long): List<BatteryLevelSample>

    @Query("DELETE FROM battery_level_sample WHERE timestamp < :cutoffTime")
    suspend fun deleteBatteryLevelOlderThan(cutoffTime: Long)

    /** Phase 4 (#13) - full-table read for backup export. */
    @Query("SELECT * FROM battery_level_sample")
    suspend fun getAllBatteryLevelSamples(): List<BatteryLevelSample>

    /** Phase 4 (#13) - bulk insert for backup restore. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllBatteryLevelSamples(rows: List<BatteryLevelSample>)

    /** Phase 4 (#14) - preview/confirm delete contract, see DataResetRepository. */
    @Query("SELECT COUNT(*) FROM battery_level_sample WHERE timestamp >= :startTime AND timestamp <= :endTime")
    suspend fun countBatteryLevelInRange(startTime: Long, endTime: Long): Int

    @Query("DELETE FROM battery_level_sample WHERE timestamp >= :startTime AND timestamp <= :endTime")
    suspend fun deleteBatteryLevelInRange(startTime: Long, endTime: Long)
}
