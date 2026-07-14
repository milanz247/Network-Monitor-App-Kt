package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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
 * @param wifiBytes Bytes consumed over Wi-Fi by this app on this date.
 * @param mobileBytes Bytes consumed over Mobile Data by this app on this date.
 */
@Entity(
    tableName = "app_data_usage",
    indices = [Index(value = ["date", "packageName"])]
)
data class AppDataUsage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: Long,
    val packageName: String,
    val appName: String,
    val uid: Int,
    val wifiBytes: Long,
    val mobileBytes: Long
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
