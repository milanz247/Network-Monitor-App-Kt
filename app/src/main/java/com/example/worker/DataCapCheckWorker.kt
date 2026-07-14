package com.example.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import android.net.VpnService
import com.example.data.local.DataUsageDao
import com.example.data.prefs.AppBlockPreferences
import com.example.data.prefs.DataCapPreferences
import com.example.data.prefs.ExfiltrationHeuristicPreferences
import com.example.data.prefs.SpikeAlertPreferences
import com.example.data.prefs.WifiReminderPreferences
import com.example.domain.billing.BillingCycleCalculator
import com.example.domain.model.PredictionResult
import com.example.domain.prediction.UsagePredictionEngine
import com.example.domain.util.toStoredDateKey
import com.example.domain.vpn.AppBlockVpnManager
import com.example.notification.CapAlertNotifier
import com.example.notification.PredictionAlertNotifier
import com.example.notification.SecurityAdvisoryNotifier
import com.example.notification.UsageSpikeAlertNotifier
import com.example.notification.WifiReminderNotifier
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
 * configured N-day warning window. Phase 2 (#9): also runs the "today is an unusual spike" check.
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
    private val spikeAlertPreferences: SpikeAlertPreferences,
    private val usageSpikeAlertNotifier: UsageSpikeAlertNotifier,
    private val wifiReminderPreferences: WifiReminderPreferences,
    private val wifiReminderNotifier: WifiReminderNotifier,
    private val appBlockPreferences: AppBlockPreferences,
    private val appBlockVpnManager: AppBlockVpnManager,
    private val exfiltrationHeuristicPreferences: ExfiltrationHeuristicPreferences,
    private val securityAdvisoryNotifier: SecurityAdvisoryNotifier,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        checkUsageSpike(today, zone)
        checkExfiltrationHeuristic(today, zone)

        val settings = preferences.settingsFlow.first()
        if (!settings.capEnabled || settings.monthlyCapBytes <= 0) {
            return@withContext Result.success()
        }

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

        checkWifiReminder(cycleKey, percentUsed)
        checkAutoBlock(percentUsed)

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

    /**
     * Phase 2 (#9): flags a day whose usage is far above its own recent baseline. Deliberately a
     * "basic ratio" check (today's total vs the mean of the last 7 complete days), not mean+stddev
     * or any learned model - the brief is explicit this should stay a simple statistical check, not
     * a full anomaly-detection model (that's out of scope, see the master brief's Phase 6 note).
     */
    private suspend fun checkUsageSpike(today: LocalDate, zone: ZoneId) {
        val spikeSettings = spikeAlertPreferences.settingsFlow.first()
        if (!spikeSettings.enabled) return

        val todayKey = today.toStoredDateKey(zone)
        if (spikeAlertPreferences.hasAlertedToday(todayKey)) return

        val baselineStart = today.minusDays(7)
        val baselineEnd = today.minusDays(1)
        val baselineRows = dao.getDailyDataUsageForRange(
            baselineStart.toStoredDateKey(zone),
            baselineEnd.toStoredDateKey(zone),
        ).first()
        val baselineByDay = baselineRows.groupBy { it.date }.mapValues { (_, rows) -> rows.sumOf { it.wifiBytes + it.mobileBytes } }
        if (baselineByDay.size < MIN_BASELINE_DAYS) return // Not enough history yet to call anything "unusual".

        val averageBytes = baselineByDay.values.sum() / baselineByDay.size
        if (averageBytes <= 0L) return

        val todayRows = dao.getDailyDataUsageForRange(todayKey, todayKey).first()
        val todayBytes = todayRows.sumOf { it.wifiBytes + it.mobileBytes }

        if (todayBytes >= averageBytes * spikeSettings.multiplier) {
            spikeAlertPreferences.markAlertedToday(todayKey)
            usageSpikeAlertNotifier.notifySpike(todayBytes, averageBytes, spikeSettings.multiplier)
        }
    }

    /**
     * Phase 3 (#10): reminds the user to switch to Wi-Fi once mobile-data usage crosses a
     * configurable % of the cap - but only if they're actually on mobile data right now (no point
     * nagging someone who's already on Wi-Fi). See [WifiReminderNotifier] for the Android 10+
     * scanning/auto-connect limitation this works around.
     */
    private suspend fun checkWifiReminder(cycleKey: String, percentUsed: Float) {
        val reminderSettings = wifiReminderPreferences.settingsFlow.first()
        if (!reminderSettings.enabled || percentUsed < reminderSettings.thresholdPercent) return
        if (wifiReminderPreferences.hasRemindedThisCycle(cycleKey)) return

        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        val isOnMobile = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true &&
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI).not()
        if (!isOnMobile) return

        wifiReminderPreferences.markRemindedThisCycle(cycleKey)
        wifiReminderNotifier.notifyMobileDataLow(percentUsed.toInt())
    }

    /**
     * Phase 3 (#12): auto-starts VPN-based app blocking once usage crosses the user's configured
     * threshold - but only if VPN consent was already granted (a background worker can't launch
     * the system consent dialog, which requires an Activity). If consent hasn't been granted yet,
     * this silently no-ops; blocking only auto-triggers after the user has started it manually at
     * least once from the app. This is a genuine platform constraint, not an oversight.
     */
    private suspend fun checkAutoBlock(percentUsed: Float) {
        val blockSettings = appBlockPreferences.currentSettings()
        if (!blockSettings.autoBlockOnLowBalance || blockSettings.blockedPackages.isEmpty()) return
        if (percentUsed < blockSettings.lowBalanceThresholdPercent) return
        if (appBlockVpnManager.isRunning.value) return
        if (VpnService.prepare(appContext) != null) return // Consent not yet granted - see doc above.

        appBlockVpnManager.start()
    }

    /**
     * Phase 5 (#20): flags apps with meaningful data usage that was almost entirely in the
     * background today. `NetworkStats.Bucket.STATE_FOREGROUND` (see the Phase 2 (#8) split this
     * reads) already requires the app's UI to have been visible - which itself requires the screen
     * to have been on - so "foreground bytes stayed at 0" already captures the screen-off-timing
     * signal the brief describes, without a separate correlation pass.
     *
     * Framed explicitly as a heuristic everywhere (settings, notification copy) - a legitimate
     * background-sync or pre-download app looks identical to this check. This is NOT a spyware
     * detector; it's a prompt to go look, not an accusation.
     */
    private suspend fun checkExfiltrationHeuristic(today: LocalDate, zone: ZoneId) {
        val settings = exfiltrationHeuristicPreferences.settingsFlow.first()
        if (!settings.enabled) return

        val todayKey = today.toStoredDateKey(zone)
        if (exfiltrationHeuristicPreferences.hasAlertedToday(todayKey)) return

        val todayRows = dao.getTopAppsByUsageForDate(todayKey).first()
        val flaggedAppNames = todayRows
            .filter { row ->
                val backgroundBytes = row.backgroundWifiBytes + row.backgroundMobileBytes
                val foregroundBytes = row.foregroundWifiBytes + row.foregroundMobileBytes
                backgroundBytes >= settings.minBackgroundBytes && foregroundBytes == 0L
            }
            .map { it.appName }

        if (flaggedAppNames.isNotEmpty()) {
            exfiltrationHeuristicPreferences.markAlertedToday(todayKey)
            securityAdvisoryNotifier.notifyPossibleBackgroundExfiltration(flaggedAppNames)
        }
    }

    companion object {
        private const val MIN_BASELINE_DAYS = 3
    }
}
