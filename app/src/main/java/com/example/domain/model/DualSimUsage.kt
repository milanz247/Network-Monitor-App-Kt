package com.example.domain.model

/** One SIM's mobile-data usage for a given day (Feature 5). */
data class SimUsage(
    val subscriptionId: Int,
    val carrierName: String?,
    val mobileBytes: Long,
    /** False when this figure is a best-effort attribution rather than a directly-measured value - see [com.example.data.repository.DataUsageRepository.fetchMobileUsagePerSim]. */
    val isPreciseMeasurement: Boolean,
)

/**
 * Side-by-side usage for a single date (Feature 5). [sim2] is null on single-SIM devices - callers
 * must handle that case rather than assuming two SIMs are always present.
 */
data class DualSimUsage(
    val sim1: SimUsage?,
    val sim2: SimUsage?,
    val wifiBytes: Long,
)
