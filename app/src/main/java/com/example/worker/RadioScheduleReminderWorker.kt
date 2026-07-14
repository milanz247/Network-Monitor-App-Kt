package com.example.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.prefs.RadioSchedulePreferences
import com.example.notification.RadioScheduleReminderNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 3 (#11) - fires the user's scheduled "switch radios" reminder. Re-reads settings on every
 * run (rather than baking the target radio into the WorkRequest) so a settings change takes effect
 * on the very next scheduled firing without needing to reschedule - see [WorkScheduler.scheduleRadioReminder].
 */
@HiltWorker
class RadioScheduleReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val preferences: RadioSchedulePreferences,
    private val notifier: RadioScheduleReminderNotifier,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = preferences.currentSettings()
        if (settings.enabled) {
            notifier.notifyScheduledSwitch(settings.targetRadio)
        }
        Result.success()
    }
}
