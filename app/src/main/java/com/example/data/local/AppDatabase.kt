package com.example.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Main Room Database for the Network Monitor application.
 * v2 adds [DailyDataUsage.subscriptionId] via [MIGRATION_1_2].
 * v3 adds the Phase 1 diagnostics tables via [MIGRATION_2_3].
 * v4 adds the Phase 2 foreground/background split columns via [MIGRATION_3_4].
 * v5 adds [BatteryLevelSample] (Phase 5 #17) via [MIGRATION_4_5] - see [ALL_MIGRATIONS].
 */
@Database(
    entities = [
        DailyDataUsage::class,
        AppDataUsage::class,
        SpeedHistory::class,
        NetworkDowntimeLog::class,
        SignalStrengthSample::class,
        ConnectionTypeTransition::class,
        BatteryLevelSample::class,
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dataUsageDao(): DataUsageDao
    abstract fun diagnosticsDao(): DiagnosticsDao
}
