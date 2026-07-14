package com.example.domain.cost

/** Phase 5 (#16) - multiplies existing byte totals by the user's Rs./GB (or any currency) rate. Pure calculation, no new tracking. */
object CostCalculator {
    private const val BYTES_PER_GB = 1_073_741_824.0

    fun estimateCost(bytes: Long, ratePerGb: Float): Double =
        (bytes / BYTES_PER_GB) * ratePerGb

    fun estimateTotalCost(wifiBytes: Long, mobileBytes: Long, ratePerGbWifi: Float, ratePerGbMobile: Float): Double =
        estimateCost(wifiBytes, ratePerGbWifi) + estimateCost(mobileBytes, ratePerGbMobile)
}
