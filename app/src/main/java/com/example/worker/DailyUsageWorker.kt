package com.example.worker

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.NetworkCapabilities
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.detector.NetworkDetector
import com.example.data.local.DailyDataUsage
import com.example.data.local.DataUsageDao
import com.example.data.prefs.DiagnosticsPreferences
import com.example.data.repository.DataUsageRepository
import com.example.data.repository.DiagnosticsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Worker that aggregates the daily data usage and per-app usage, saving it to Room.
 * Scheduled to run periodically and at the end of the day.
 *
 * Feature 5 (Dual-SIM): persists one Wi-Fi bucket row (subscriptionId = null) plus one mobile
 * bucket row per active SIM, instead of a single combined-mobile row - see [DailyDataUsage].
 */
@HiltWorker
class DailyUsageWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val dao: DataUsageDao,
    private val dataUsageRepository: DataUsageRepository,
    private val networkDetector: NetworkDetector,
    private val diagnosticsRepository: DiagnosticsRepository,
    private val diagnosticsPreferences: DiagnosticsPreferences,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val networkStatsManager =
            appContext.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager

        try {
            // Get today's start and end time
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startOfDay = calendar.timeInMillis

            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            val endOfDay = calendar.timeInMillis

            val dateEpochDay = startOfDay / 86400000L

            // Wi-Fi bucket (subscriptionId = null)
            var totalWifi = 0L
            try {
                val wifiBucket = networkStatsManager.querySummaryForDevice(
                    NetworkCapabilities.TRANSPORT_WIFI,
                    null,
                    startOfDay,
                    endOfDay
                )
                totalWifi = wifiBucket.rxBytes + wifiBucket.txBytes
            } catch (e: Exception) {
                Log.w("DailyUsageWorker", "Failed to query wifi summary", e)
            }

            val existingWifiRow = dao.getDailyDataUsageForBucket(dateEpochDay, null)
            dao.upsertDailyDataUsage(
                DailyDataUsage(
                    id = existingWifiRow?.id ?: 0,
                    date = dateEpochDay,
                    wifiBytes = totalWifi,
                    mobileBytes = 0L,
                    carrierName = null,
                    subscriptionId = null,
                )
            )

            // One mobile bucket per active SIM (Feature 5). Falls back to a single best-effort
            // bucket attributed to the default-data SIM when precise per-SIM stats aren't available
            // - see DataUsageRepository.fetchMobileUsagePerSim for the platform restriction.
            val activeSims = networkDetector.getActiveSims()
            val perSimUsage = dataUsageRepository.fetchMobileUsagePerSim(startOfDay, endOfDay, activeSims)
            for (simUsage in perSimUsage) {
                val existingSimRow = dao.getDailyDataUsageForBucket(dateEpochDay, simUsage.subscriptionId)
                dao.upsertDailyDataUsage(
                    DailyDataUsage(
                        id = existingSimRow?.id ?: 0,
                        date = dateEpochDay,
                        wifiBytes = 0L,
                        mobileBytes = simUsage.mobileBytes,
                        carrierName = simUsage.carrierName,
                        subscriptionId = simUsage.subscriptionId,
                    )
                )
            }

            // Also aggregate per-app usage
            val appUsageList = dataUsageRepository.fetchAppUsageForPeriod(startOfDay, endOfDay)
            dataUsageRepository.saveAppUsage(dateEpochDay, appUsageList)

            // Auto-delete rows older than 60 days
            val sixtyDaysAgo = dateEpochDay - 60
            dao.deleteDailyDataUsageOlderThan(sixtyDaysAgo)
            dao.deleteAppDataUsageOlderThan(sixtyDaysAgo)

            // Phase 1: prune diagnostics tables to the user-configured retention window (default
            // 14 days) - piggybacks on this worker's existing 4h schedule rather than adding a new one.
            val retentionDays = diagnosticsPreferences.settingsFlow.first().speedHistoryRetentionDays
            val retentionCutoffMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
            diagnosticsRepository.pruneOlderThan(retentionCutoffMs)

            // Feature 1/2: re-check the data cap and depletion prediction now that fresh totals exist.
            WorkManager.getInstance(appContext).enqueue(
                OneTimeWorkRequestBuilder<DataCapCheckWorker>().build()
            )

            return@withContext Result.success()

        } catch (e: SecurityException) {
            // Permission might be temporarily revoked or user hasn't granted it.
            // Retry later.
            Log.e("DailyUsageWorker", "SecurityException: Usage access denied. Retrying.", e)
            return@withContext Result.retry()
        } catch (e: Exception) {
            // Permanent failure or unexpected crash
            Log.e("DailyUsageWorker", "Worker failed with exception", e)
            return@withContext Result.failure()
        }
    }
}
