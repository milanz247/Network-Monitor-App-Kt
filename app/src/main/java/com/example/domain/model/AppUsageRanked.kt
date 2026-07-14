package com.example.domain.model

/** One leaderboard row for a period (Feature 4). [percentOfTotal] is share of ALL apps' usage in that period, not of the data cap. */
data class AppUsageRanked(
    val packageName: String,
    val appName: String,
    val totalBytes: Long,
    val wifiBytes: Long,
    val mobileBytes: Long,
    val rank: Int,
    val percentOfTotal: Float,
)
