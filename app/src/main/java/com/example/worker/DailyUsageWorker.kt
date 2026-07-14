package com.example.worker

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.NetworkCapabilities
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.DailyDataUsage
import com.example.data.local.DataUsageDao
import com.example.data.repository.DataUsageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Worker that aggregates the daily data usage and per-app usage, saving it to Room.
 * Scheduled to run periodically and at the end of the day.
 */
@HiltWorker
class DailyUsageWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val dao: DataUsageDao,
    private val dataUsageRepository: DataUsageRepository
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

            var totalWifi = 0L
            var totalMobile = 0L

            // Wi-Fi Summary
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

            // Mobile Summary
            try {
                val mobileBucket = networkStatsManager.querySummaryForDevice(
                    NetworkCapabilities.TRANSPORT_CELLULAR,
                    null,
                    startOfDay,
                    endOfDay
                )
                totalMobile = mobileBucket.rxBytes + mobileBucket.txBytes
            } catch (e: Exception) {
                Log.w("DailyUsageWorker", "Failed to query mobile summary", e)
            }

            // Check if record exists for today
            val existing = dao.getDailyDataUsageSync(dateEpochDay)
            val dailyUsage = DailyDataUsage(
                id = existing?.id ?: 0,
                date = dateEpochDay,
                wifiBytes = totalWifi,
                mobileBytes = totalMobile,
                carrierName = existing?.carrierName // Carrier name is updated elsewhere or can be fetched
            )
            
            dao.upsertDailyDataUsage(dailyUsage)

            // Also aggregate per-app usage
            val appUsageList = dataUsageRepository.fetchAppUsageForPeriod(startOfDay, endOfDay)
            dataUsageRepository.saveAppUsage(dateEpochDay, appUsageList)

            // Auto-delete rows older than 60 days
            val sixtyDaysAgo = dateEpochDay - 60
            dao.deleteDailyDataUsageOlderThan(sixtyDaysAgo)
            dao.deleteAppDataUsageOlderThan(sixtyDaysAgo)

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
