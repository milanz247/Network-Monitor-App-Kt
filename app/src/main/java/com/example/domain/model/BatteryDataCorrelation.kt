package com.example.domain.model

/**
 * Phase 5 (#17) - device-wide (not per-app - see BatteryDataCorrelationAnalyzer's class doc for
 * why) battery-vs-data figure for a period. Null fields mean "not enough battery samples yet".
 */
data class BatteryDataCorrelation(
    val totalBytes: Long,
    val batteryDrainPercent: Int?,
    val bytesPerPercentBattery: Long?,
)
