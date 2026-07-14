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
    const val USAGE_SPIKE_ALERTS_CHANNEL_ID = "usage_spike_alerts"
    const val WIFI_REMINDER_CHANNEL_ID = "wifi_reminder_alerts"
    const val RADIO_SCHEDULE_CHANNEL_ID = "radio_schedule_reminders"
    const val SECURITY_ADVISORY_CHANNEL_ID = "security_advisories"

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

        // Phase 2 (#9): a sudden spike is a distinct, actionable signal (possible runaway app or
        // unexpected charge) from the gradual "you may run out" prediction above - default importance.
        manager.createNotificationChannel(
            NotificationChannel(
                USAGE_SPIKE_ALERTS_CHANNEL_ID,
                "Unusual usage spikes",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Alerts when a day's data usage is far above your recent average."
            }
        )

        // Phase 3 (#10): low priority - purely a nudge to open Wi-Fi settings, never blocking.
        manager.createNotificationChannel(
            NotificationChannel(
                WIFI_REMINDER_CHANNEL_ID,
                "Wi-Fi reminders",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Reminds you to switch to Wi-Fi when mobile data is running low."
            }
        )

        // Phase 3 (#11): user explicitly scheduled these reminders, so default importance is
        // appropriate (they asked to be nudged at this exact time) - still distinct in purpose
        // from the Wi-Fi-is-low reminder above (schedule-driven vs usage-driven).
        manager.createNotificationChannel(
            NotificationChannel(
                RADIO_SCHEDULE_CHANNEL_ID,
                "Radio switch reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Reminds you to switch Wi-Fi/mobile data at the times you scheduled."
            }
        )

        // Phase 5 (#19, #20): both untrusted-network and background-usage-heuristic alerts are the
        // same category (a possible security/privacy concern, explicitly framed as a heuristic, not
        // a definitive finding) - one shared channel rather than two near-identical ones.
        manager.createNotificationChannel(
            NotificationChannel(
                SECURITY_ADVISORY_CHANNEL_ID,
                "Security advisories",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Heuristic flags for untrusted Wi-Fi networks and unusual background data usage."
            }
        )
    }
}
