package com.example.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.prefs.TargetRadio
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Phase 3 (#11) - the user's scheduled "switch radios now" reminder. See RadioSchedulePreferences for the platform limitation this works around. */
class RadioScheduleReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun notifyScheduledSwitch(targetRadio: TargetRadio) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val settingsAction = if (targetRadio == TargetRadio.WIFI) Settings.ACTION_WIFI_SETTINGS else Settings.ACTION_WIRELESS_SETTINGS
        val pendingIntent = PendingIntent.getActivity(
            context, REQUEST_CODE, Intent(settingsAction).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val label = if (targetRadio == TargetRadio.WIFI) "Wi-Fi" else "mobile data"
        val notification = NotificationCompat.Builder(context, AppNotificationChannels.RADIO_SCHEDULE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Scheduled reminder")
            .setContentText("It's time to switch to $label, as you scheduled. Tap to open settings.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val NOTIFICATION_ID = 5400
        private const val REQUEST_CODE = 5400
    }
}
