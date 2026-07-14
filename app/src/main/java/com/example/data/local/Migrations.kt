package com.example.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2: adds [DailyDataUsage.subscriptionId] to support one Wi-Fi bucket plus one bucket
 * per active SIM per day (Feature 5 - Dual-SIM). Existing rows all predate multi-SIM tracking,
 * so they're left with subscriptionId = NULL, which the read path already treats as "Wi-Fi bucket"
 * - historically correct, since every pre-migration row stored a combined wifi+mobile total that
 * the app will simply keep reading as-is until the next aggregation cycle writes split buckets.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE daily_data_usage ADD COLUMN subscriptionId INTEGER DEFAULT NULL")
        db.execSQL("DROP INDEX IF EXISTS index_daily_data_usage_date")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_daily_data_usage_date_subscriptionId " +
                "ON daily_data_usage(date, subscriptionId)"
        )
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
