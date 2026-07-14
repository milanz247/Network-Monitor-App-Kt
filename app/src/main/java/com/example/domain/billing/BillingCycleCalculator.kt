package com.example.domain.billing

import java.time.LocalDate

/** Computes billing-cycle boundaries for a configurable start day (Feature 1) - not just the calendar month. */
object BillingCycleCalculator {

    fun cycleStart(startDay: Int, reference: LocalDate = LocalDate.now()): LocalDate {
        val clampedDayThisMonth = startDay.coerceIn(1, reference.lengthOfMonth())
        return if (reference.dayOfMonth >= clampedDayThisMonth) {
            reference.withDayOfMonth(clampedDayThisMonth)
        } else {
            val previousMonth = reference.minusMonths(1)
            previousMonth.withDayOfMonth(startDay.coerceIn(1, previousMonth.lengthOfMonth()))
        }
    }

    fun cycleEnd(startDay: Int, reference: LocalDate = LocalDate.now()): LocalDate =
        cycleStart(startDay, reference).plusMonths(1).minusDays(1)

    /** Stable per-cycle identifier: a new value naturally appears the moment the cycle rolls over, so
     * "already alerted" tracking resets itself without any extra bookkeeping. */
    fun cycleKey(startDay: Int, reference: LocalDate = LocalDate.now()): String =
        cycleStart(startDay, reference).toString()
}
