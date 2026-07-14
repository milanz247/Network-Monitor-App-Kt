package com.example.domain.model

/** Phase 1 (#2) - the day's fastest recorded sample, derived from [com.example.data.local.SpeedHistory] via MAX() - no separate storage. */
data class PeakSpeedRecord(
    val peakDownloadBps: Long,
    val peakDownloadAt: Long?,
    val peakUploadBps: Long,
    val peakUploadAt: Long?,
)
