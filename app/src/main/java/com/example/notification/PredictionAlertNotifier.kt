package com.example.notification

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.domain.model.PredictionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Feature 2: fires the "you may run out of data soon" heads-up - its own, lower-priority channel. */
class PredictionAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun notifyWillRunOut(prediction: PredictionResult.WillRunOutOn) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val formattedDate = prediction.date.format(DateTimeFormatter.ofPattern("MMM d"))

        val notification = NotificationCompat.Builder(context, AppNotificationChannels.PREDICTION_ALERTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("You may run out of data soon")
            .setContentText(
                "At your recent pace, you're projected to hit your cap around $formattedDate " +
                    "(${prediction.daysRemaining} day(s) left) - ${prediction.reasoning}."
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val NOTIFICATION_ID = 5100
    }
}
