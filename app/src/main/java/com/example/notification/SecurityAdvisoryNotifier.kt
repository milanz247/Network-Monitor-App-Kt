package com.example.notification

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Phase 5 (#19, #20) - heuristic security/privacy flags. Both explicitly worded as heuristics, not definitive findings. */
class SecurityAdvisoryNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun notifyUntrustedNetwork(ssid: String) {
        notify(
            NOTIFICATION_ID_UNTRUSTED_NETWORK,
            "Connected to an untrusted Wi-Fi network",
            "\"$ssid\" isn't in your trusted networks list. If this isn't expected, double-check before using it.",
        )
    }

    fun notifyCaptivePortal(ssid: String?) {
        notify(
            NOTIFICATION_ID_CAPTIVE_PORTAL,
            "Captive portal detected",
            "${ssid ?: "This network"} requires a sign-in page before you'll have full internet access.",
        )
    }

    fun notifyPossibleBackgroundExfiltration(appNames: List<String>) {
        val summary = if (appNames.size == 1) appNames.first() else "${appNames.size} apps"
        notify(
            NOTIFICATION_ID_EXFILTRATION,
            "Unusual background data usage",
            "$summary used a meaningful amount of data today almost entirely in the background. " +
                "This is a heuristic, not a definitive finding - review it in the app before assuming anything is wrong.",
        )
    }

    private fun notify(id: Int, title: String, text: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, AppNotificationChannels.SECURITY_ADVISORY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
    }

    companion object {
        private const val NOTIFICATION_ID_UNTRUSTED_NETWORK = 5500
        private const val NOTIFICATION_ID_CAPTIVE_PORTAL = 5501
        private const val NOTIFICATION_ID_EXFILTRATION = 5502
    }
}
