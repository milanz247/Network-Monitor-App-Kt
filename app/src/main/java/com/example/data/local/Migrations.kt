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

/** v2 -> v3: adds the four Phase 1 (Speed History & Diagnostics) tables. No existing data touched. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `speed_history` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, " +
                "`avgDownloadBps` INTEGER NOT NULL, " +
                "`avgUploadBps` INTEGER NOT NULL, " +
                "`peakDownloadBps` INTEGER NOT NULL, " +
                "`peakUploadBps` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_speed_history_timestamp` ON `speed_history` (`timestamp`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `network_downtime_log` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`startTime` INTEGER NOT NULL, " +
                "`endTime` INTEGER, " +
                "`reason` TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_network_downtime_log_startTime` " +
                "ON `network_downtime_log` (`startTime`)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `signal_strength_sample` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, " +
                "`wifiRssi` INTEGER, " +
                "`cellularSignalLevel` INTEGER)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_signal_strength_sample_timestamp` " +
                "ON `signal_strength_sample` (`timestamp`)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `connection_type_transition` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, " +
                "`connectionType` TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_connection_type_transition_timestamp` " +
                "ON `connection_type_transition` (`timestamp`)"
        )
    }
}

/** v3 -> v4: adds the Phase 2 (#8) foreground/background split columns to `app_data_usage`, all defaulting to 0 for existing rows. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_data_usage ADD COLUMN foregroundWifiBytes INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE app_data_usage ADD COLUMN backgroundWifiBytes INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE app_data_usage ADD COLUMN foregroundMobileBytes INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE app_data_usage ADD COLUMN backgroundMobileBytes INTEGER NOT NULL DEFAULT 0")
    }
}

/** v4 -> v5: adds the `battery_level_sample` table (Phase 5 #17). */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `battery_level_sample` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, " +
                "`batteryLevelPercent` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_battery_level_sample_timestamp` " +
                "ON `battery_level_sample` (`timestamp`)"
        )
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
