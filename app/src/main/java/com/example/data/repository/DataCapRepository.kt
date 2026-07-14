package com.example.data.repository

import com.example.data.local.DataUsageDao
import com.example.data.prefs.DataCapPreferences
import com.example.domain.billing.BillingCycleCalculator
import com.example.domain.model.CapStatus
import com.example.domain.model.DataCapSettings
import com.example.domain.util.toStoredDateKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Feature 1: exposes the user's cap settings and their live month-to-date usage against it. */
@Singleton
class DataCapRepository @Inject constructor(
    private val dao: DataUsageDao,
    private val preferences: DataCapPreferences,
) {
    val settingsFlow: Flow<DataCapSettings> = preferences.settingsFlow

    suspend fun updateSettings(
        monthlyCapBytes: Long? = null,
        capEnabled: Boolean? = null,
        carrierSpecificMode: Boolean? = null,
        billingCycleStartDay: Int? = null,
        predictionAlertDaysThreshold: Int? = null,
    ) = preferences.updateSettings(
        monthlyCapBytes,
        capEnabled,
        carrierSpecificMode,
        billingCycleStartDay,
        predictionAlertDaysThreshold,
    )

    /** Recomputes whenever settings change (e.g. the user edits the cap) or the underlying daily rows change. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeCapStatus(zone: ZoneId = ZoneId.systemDefault()): Flow<CapStatus> =
        preferences.settingsFlow.flatMapLatest { settings ->
            val today = LocalDate.now(zone)
            val cycleStartKey = BillingCycleCalculator.cycleStart(settings.billingCycleStartDay, today).toStoredDateKey(zone)
            val cycleEndKey = BillingCycleCalculator.cycleEnd(settings.billingCycleStartDay, today).toStoredDateKey(zone)
            val cycleKey = BillingCycleCalculator.cycleKey(settings.billingCycleStartDay, today)

            dao.getDailyDataUsageForRange(cycleStartKey, cycleEndKey).map { rows ->
                val usedBytes = rows.sumOf { row ->
                    if (settings.carrierSpecificMode) row.mobileBytes else row.wifiBytes + row.mobileBytes
                }
                val percent = if (settings.capEnabled && settings.monthlyCapBytes > 0) {
                    usedBytes * 100f / settings.monthlyCapBytes
                } else {
                    0f
                }
                CapStatus(
                    usedBytes = usedBytes,
                    capBytes = settings.monthlyCapBytes,
                    percentUsed = percent,
                    thresholdsFired = preferences.getAlertedThresholds(cycleKey),
                )
            }
        }
}
