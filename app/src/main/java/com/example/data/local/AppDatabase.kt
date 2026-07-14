package com.example.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Main Room Database for the Network Monitor application.
 * v2 adds [DailyDataUsage.subscriptionId] via [MIGRATION_1_2] - see [ALL_MIGRATIONS].
 */
@Database(
    entities = [DailyDataUsage::class, AppDataUsage::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dataUsageDao(): DataUsageDao
}
