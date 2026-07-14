package com.example.domain.model

/** Phase 4 (#15) - the data a shareable summary card renders; kept separate from the Canvas drawing code (see UsageSummaryCardGenerator). */
data class UsageSummaryCardData(
    val periodLabel: String,
    val totalBytes: Long,
    val wifiBytes: Long,
    val mobileBytes: Long,
    val topAppName: String?,
    val topAppBytes: Long,
)
