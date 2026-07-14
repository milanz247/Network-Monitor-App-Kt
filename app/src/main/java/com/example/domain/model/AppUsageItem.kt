package com.example.domain.model

/**
 * Domain model representing data usage for a single application.
 * Phase 2 (#8): [wifiBytes]/[mobileBytes] are foreground+background totals; the four `*ground*`
 * fields are the split, populated from `NetworkStats.Bucket.getState()`.
 */
data class AppUsageItem(
    val packageName: String,
    val appName: String,
    val uid: Int,
    val wifiBytes: Long,
    val mobileBytes: Long,
    val foregroundWifiBytes: Long = 0,
    val backgroundWifiBytes: Long = 0,
    val foregroundMobileBytes: Long = 0,
    val backgroundMobileBytes: Long = 0,
)
