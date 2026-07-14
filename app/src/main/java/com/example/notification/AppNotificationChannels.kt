package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Notification channels for user-facing alerts. Deliberately separate from
 * [com.example.service.NetworkSpeedService]'s silent IMPORTANCE_LOW foreground-service channel -
 * these are things the user should actually notice.
 */
object AppNotificationChannels {
    const val DATA_CAP_ALERTS_CHANNEL_ID = "data_cap_alerts"
    const val PREDICTION_ALERTS_CHANNEL_ID = "usage_prediction_alerts"

    fun ensureChannelsCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        // Actionable, time-sensitive - default importance so it can alert (sound/heads-up eligible).
        manager.createNotificationChannel(
            NotificationChannel(
                DATA_CAP_ALERTS_CHANNEL_ID,
                "Data cap alerts",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Alerts when your monthly data usage crosses 80%, 95% or 100% of your cap."
            }
        )

        // Advisory only - intentionally lower importance than the cap-threshold channel above.
        manager.createNotificationChannel(
            NotificationChannel(
                PREDICTION_ALERTS_CHANNEL_ID,
                "Usage predictions",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "A heads-up when you're projected to run out of data before your billing cycle ends."
            }
        )
    }
}
