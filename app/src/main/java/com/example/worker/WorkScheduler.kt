package com.example.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Enqueues the periodic [DailyUsageWorker], shared by app startup and [com.example.receiver.BootReceiver]. */
object WorkScheduler {
    private const val UNIQUE_WORK_NAME = "DailyUsageWorker"
    private const val RADIO_REMINDER_WORK_NAME = "RadioScheduleReminderWorker"

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

    /**
     * Phase 3 (#11) - (re)schedules the daily radio-switch reminder for [hour]:[minute]. WorkManager
     * doesn't guarantee exact-time firing (Doze/battery-saver can delay it), which is an accepted
     * tradeoff for a *reminder* notification - call with [enabled] = false to cancel.
     */
    fun scheduleRadioReminder(context: Context, hour: Int, minute: Int, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(RADIO_REMINDER_WORK_NAME)
            return
        }

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        val initialDelayMs = target.timeInMillis - now.timeInMillis

        val request = PeriodicWorkRequestBuilder<RadioScheduleReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .build()

        // REPLACE, not KEEP: this is called every time the user changes the schedule, and the new
        // time must take effect immediately rather than waiting for the previously-scheduled run.
        workManager.enqueueUniquePeriodicWork(
            RADIO_REMINDER_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            request,
        )
    }
}
