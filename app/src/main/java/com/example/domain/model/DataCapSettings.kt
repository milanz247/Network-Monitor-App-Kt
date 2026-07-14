package com.example.domain.model

/** User-configurable data cap settings (Feature 1), persisted via DataStore ([com.example.data.prefs.DataCapPreferences]). */
data class DataCapSettings(
    val monthlyCapBytes: Long,
    val capEnabled: Boolean,
    /** true = only mobile data counts against the cap (the common "SIM plan" case); false = combined Wi-Fi + mobile. */
    val carrierSpecificMode: Boolean,
    /** Day-of-month the billing cycle resets on (1-31); clamped into shorter months. */
    val billingCycleStartDay: Int,
    /** Feature 2: fire the "will run out" notification when projected to deplete within this many days. */
    val predictionAlertDaysThreshold: Int,
)

/** Live month-to-date cap usage for the ViewModel (Feature 1). */
data class CapStatus(
    val usedBytes: Long,
    val capBytes: Long,
    val percentUsed: Float,
    val thresholdsFired: Set<Int>,
)
