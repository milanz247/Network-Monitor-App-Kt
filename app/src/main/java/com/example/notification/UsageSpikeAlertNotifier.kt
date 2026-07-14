package com.example.notification

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.domain.util.formatBytesCompact
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Phase 2 (#9): "today's usage is far above your recent average" - a simple ratio check, not a definitive anomaly diagnosis. */
class UsageSpikeAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun notifySpike(todayBytes: Long, averageBytes: Long, multiplier: Float) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, AppNotificationChannels.USAGE_SPIKE_ALERTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Unusual data usage today")
            .setContentText(
                "${formatBytesCompact(todayBytes)} used so far today - that's over " +
                    "${String.format("%.1f", multiplier)}x your recent daily average of ${formatBytesCompact(averageBytes)}."
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val NOTIFICATION_ID = 5200
    }
}
