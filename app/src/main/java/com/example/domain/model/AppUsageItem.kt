package com.example.domain.model

/**
 * Domain model representing data usage for a single application.
 */
data class AppUsageItem(
    val packageName: String,
    val appName: String,
    val uid: Int,
    val wifiBytes: Long,
    val mobileBytes: Long
)
