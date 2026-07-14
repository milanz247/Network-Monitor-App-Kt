package com.example.domain.model

/** Phase 2 (#6) - one point in a weekly/monthly usage trend (see `UsageTrendCalculator`). */
data class UsageTrendPoint(
    val periodLabel: String,
    val periodStart: Long,
    val wifiBytes: Long,
    val mobileBytes: Long,
) {
    val totalBytes: Long get() = wifiBytes + mobileBytes
}
