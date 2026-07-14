package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

/**
 * Entity representing daily aggregated data usage.
 *
 * Since v2 (Feature 5 - Dual-SIM), a single [date] can have more than one row: one Wi-Fi
 * bucket ([subscriptionId] == null) plus one mobile bucket per active SIM ([subscriptionId] ==
 * that SIM's subscription id, with [mobileBytes] populated and [wifiBytes] == 0). Readers that
 * want the whole-day total must sum across all rows for the date rather than assuming one row.
 *
 * @param id Unique identifier.
 * @param date Epoch day representing the specific date (e.g., System.currentTimeMillis() / 86400000).
 * @param wifiBytes Total bytes consumed over Wi-Fi for the day (only populated on the Wi-Fi bucket row).
 * @param mobileBytes Total bytes consumed over Mobile Data for the day (only populated on a per-SIM bucket row).
 * @param carrierName Name of the active carrier for this bucket (optional, mobile buckets only).
 * @param subscriptionId Null for the Wi-Fi bucket; the SIM's subscription id for a mobile bucket.
 */
@Entity(
    tableName = "daily_data_usage",
    indices = [Index(value = ["date", "subscriptionId"])]
)
@JsonClass(generateAdapter = true) // Phase 4 (#13) - serialized as-is in manual backup/restore, see BackupRepository.
data class DailyDataUsage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: Long,
    val wifiBytes: Long,
    val mobileBytes: Long,
    val carrierName: String?,
    val subscriptionId: Int? = null,
)

/**
 * Entity representing per-app data usage for a specific day.
 * @param id Unique identifier.
 * @param date Epoch day representing the specific date.
 * @param packageName The package name of the application.
 * @param appName The human-readable name of the app.
 * @param uid The UID of the application.
 * @param wifiBytes Total bytes consumed over Wi-Fi by this app on this date (foreground + background).
 * @param mobileBytes Total bytes consumed over Mobile Data by this app on this date (foreground + background).
 * @param foregroundWifiBytes Since v4 (Phase 2 #8) - the [wifiBytes] subset seen while the app was
 *   in `NetworkStats.Bucket.STATE_FOREGROUND`. Rows written before v4 default this to 0 (see
 *   `MIGRATION_3_4`) - treat a 0/0 split on an old row as "unknown", not "all background".
 * @param backgroundWifiBytes See [foregroundWifiBytes]; `wifiBytes - foregroundWifiBytes` for new rows.
 * @param foregroundMobileBytes See [foregroundWifiBytes], mobile equivalent.
 * @param backgroundMobileBytes See [backgroundWifiBytes], mobile equivalent.
 */
@Entity(
    tableName = "app_data_usage",
    indices = [Index(value = ["date", "packageName"])]
)
@JsonClass(generateAdapter = true) // Phase 4 (#13) - serialized as-is in manual backup/restore, see BackupRepository.
data class AppDataUsage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: Long,
    val packageName: String,
    val appName: String,
    val uid: Int,
    val wifiBytes: Long,
    val mobileBytes: Long,
    // defaultValue must match MIGRATION_3_4's "ADD COLUMN ... DEFAULT 0" exactly - Room validates
    // the on-disk schema's column default against this on every DB open, and a mismatch here
    // (e.g. leaving this annotation off) throws at runtime for any install that migrates through
    // v3->v4 rather than creating the DB fresh at the latest version.
    @ColumnInfo(defaultValue = "0")
    val foregroundWifiBytes: Long = 0,
    @ColumnInfo(defaultValue = "0")
    val backgroundWifiBytes: Long = 0,
    @ColumnInfo(defaultValue = "0")
    val foregroundMobileBytes: Long = 0,
    @ColumnInfo(defaultValue = "0")
    val backgroundMobileBytes: Long = 0,
)

/**
 * Projection returned by [DataUsageDao.getTopAppsForPeriod] (Feature 4). [appName]/[packageName]
 * are the snapshot values captured at collection time (see [AppDataUsage]), so this stays correct
 * even for apps the user has since uninstalled - no PackageManager lookup happens here.
 */
data class AppUsageAggregate(
    val packageName: String,
    val appName: String,
    val wifiBytes: Long,
    val mobileBytes: Long,
)
