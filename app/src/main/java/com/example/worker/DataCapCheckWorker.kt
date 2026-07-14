package com.example.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.DataUsageDao
import com.example.data.prefs.DataCapPreferences
import com.example.domain.billing.BillingCycleCalculator
import com.example.domain.model.PredictionResult
import com.example.domain.prediction.UsagePredictionEngine
import com.example.domain.util.toStoredDateKey
import com.example.notification.CapAlertNotifier
import com.example.notification.PredictionAlertNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/**
 * One-off worker enqueued by [DailyUsageWorker] right after each aggregation pass. Kept separate
 * from aggregation itself (single responsibility: this worker only checks budgets/alerts, it never
 * touches NetworkStatsManager) and cheap enough to re-run every 4h without its own periodic schedule.
 *
 * Feature 1: fires the 80/95/100% data-cap-threshold notification, at most once per threshold per
 * billing cycle. Feature 2: also runs the depletion prediction and fires a separate, lower-priority
 * notification the first time it crosses into [PredictionResult.WillRunOutOn] within the user's
 * configured N-day warning window.
 */
@HiltWorker
class DataCapCheckWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val dao: DataUsageDao,
    private val preferences: DataCapPreferences,
    private val predictionEngine: UsagePredictionEngine,
    private val capAlertNotifier: CapAlertNotifier,
    private val predictionAlertNotifier: PredictionAlertNotifier,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = preferences.settingsFlow.first()
        if (!settings.capEnabled || settings.monthlyCapBytes <= 0) {
            return@withContext Result.success()
        }

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val cycleStart = BillingCycleCalculator.cycleStart(settings.billingCycleStartDay, today)
        val cycleEnd = BillingCycleCalculator.cycleEnd(settings.billingCycleStartDay, today)
        val cycleKey = BillingCycleCalculator.cycleKey(settings.billingCycleStartDay, today)

        val cycleRows = dao.getDailyDataUsageForRange(
            cycleStart.toStoredDateKey(zone),
            cycleEnd.toStoredDateKey(zone),
        ).first()
        val usedBytes = cycleRows.sumOf { row ->
            if (settings.carrierSpecificMode) row.mobileBytes else row.wifiBytes + row.mobileBytes
        }
        val percentUsed = usedBytes * 100f / settings.monthlyCapBytes

        val alreadyFired = preferences.getAlertedThresholds(cycleKey)
        // Highest first: if usage jumps straight past 95% to 100% between checks, fire only the
        // highest threshold actually reached instead of sending 80/95/100 back-to-back.
        val thresholdToFire = listOf(100, 95, 80).firstOrNull { it !in alreadyFired && percentUsed >= it }
        if (thresholdToFire != null) {
            preferences.markThresholdAlerted(cycleKey, thresholdToFire)
            capAlertNotifier.notifyThresholdReached(thresholdToFire, usedBytes, settings.monthlyCapBytes)
        }

        val prediction = predictionEngine.predictDepletionDate(zone)
        if (prediction is PredictionResult.WillRunOutOn &&
            prediction.daysRemaining < settings.predictionAlertDaysThreshold &&
            !preferences.hasFiredPredictionAlert(cycleKey)
        ) {
            preferences.markPredictionAlerted(cycleKey)
            predictionAlertNotifier.notifyWillRunOut(prediction)
        }

        Result.success()
    }
}
