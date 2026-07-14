package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing daily aggregated data usage.
 * @param id Unique identifier.
 * @param date Epoch day representing the specific date (e.g., System.currentTimeMillis() / 86400000).
 * @param wifiBytes Total bytes consumed over Wi-Fi for the day.
 * @param mobileBytes Total bytes consumed over Mobile Data for the day.
 * @param carrierName Name of the active carrier on that day (optional, if mobile data used).
 */
@Entity(
    tableName = "daily_data_usage",
    indices = [Index(value = ["date"])]
)
data class DailyDataUsage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: Long,
    val wifiBytes: Long,
    val mobileBytes: Long,
    val carrierName: String?
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
