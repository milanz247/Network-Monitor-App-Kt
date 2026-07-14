package com.example.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Enqueues the periodic [DailyUsageWorker], shared by app startup and [com.example.receiver.BootReceiver]. */
object WorkScheduler {
    private const val UNIQUE_WORK_NAME = "DailyUsageWorker"

    fun scheduleDailyUsageWorker(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .build()

        val dailyWorkRequest = PeriodicWorkRequestBuilder<DailyUsageWorker>(4, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            dailyWorkRequest,
        )
    }
}
