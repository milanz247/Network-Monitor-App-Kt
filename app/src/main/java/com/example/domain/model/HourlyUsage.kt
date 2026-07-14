package com.example.domain.model

/** Phase 2 (#7) - device-wide usage for one hour-of-day (0-23) of a given date. */
data class HourlyUsage(
    val hour: Int,
    val wifiBytes: Long,
    val mobileBytes: Long,
) {
    val totalBytes: Long get() = wifiBytes + mobileBytes
}
