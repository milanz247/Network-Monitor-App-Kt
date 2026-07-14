package com.example.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Main Room Database for the Network Monitor application.
 * Note: Uses fallbackToDestructiveMigration for version 1. Real migrations should be added
 * when bumping the version for production.
 */
@Database(
    entities = [DailyDataUsage::class, AppDataUsage::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dataUsageDao(): DataUsageDao
}
