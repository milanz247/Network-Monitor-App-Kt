package com.example.notification

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.domain.util.formatBytesCompact
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Feature 1: fires the 80% / 95% / 100% data-cap-threshold notification. */
class CapAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun notifyThresholdReached(threshold: Int, usedBytes: Long, capBytes: Long) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val title = if (threshold >= 100) "Data cap reached" else "$threshold% of your data cap used"
        val text = "${formatBytesCompact(usedBytes)} of ${formatBytesCompact(capBytes)} used this billing cycle."

        val notification = NotificationCompat.Builder(context, AppNotificationChannels.DATA_CAP_ALERTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        // One notification id per threshold so 80/95/100 alerts each stay visible instead of replacing each other.
        manager.notify(NOTIFICATION_ID_BASE + threshold, notification)
    }

    companion object {
        private const val NOTIFICATION_ID_BASE = 5000
    }
}
