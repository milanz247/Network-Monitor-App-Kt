package com.example.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.example.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Phase 3 (#10) - nudges the user to switch to Wi-Fi when mobile data is running low.
 *
 * PLATFORM LIMITATION: there is no public, non-privileged API to scan for/silently connect to a
 * saved Wi-Fi network on Android 10+ - tapping this notification only opens Wi-Fi settings for the
 * user to connect themselves. This is a deliberate design choice, not a missing feature.
 */
class WifiReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun notifyMobileDataLow(percentUsed: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val settingsIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, REQUEST_CODE, settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, AppNotificationChannels.WIFI_REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Mobile data is running low")
            .setContentText("You've used $percentUsed% of your cap on mobile data. Tap to switch to Wi-Fi.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val NOTIFICATION_ID = 5300
        private const val REQUEST_CODE = 5300
    }
}
