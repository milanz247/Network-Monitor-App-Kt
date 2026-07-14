package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.TrafficStats
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.domain.repository.SpeedRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that tracks real-time network speed.
 * Uses a coroutine loop to calculate speed based on TrafficStats deltas.
 */
@AndroidEntryPoint
class NetworkSpeedService : Service() {

    @Inject
    lateinit var speedRepository: SpeedRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var trackingJob: Job? = null

    private var isScreenOn = true
    private var lastRxBytes = 0L
    private var lastTxBytes = 0L
    private var lastTimeMs = 0L

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenStateReceiver, filter)
        startForegroundServiceWithNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startSpeedTracking()
        return START_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "network_speed_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Network Speed Monitor",
                NotificationManager.IMPORTANCE_LOW // No sound/vibration spam
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = createNotification(0, 0, channelId)
        
        // Use dataSync as foregroundServiceType for API 34+
        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            foregroundServiceType
        )
    }

    private fun createNotification(downloadSpeed: Long, uploadSpeed: Long, channelId: String = "network_speed_channel"): Notification {
        val contentText = "DL: ${formatSpeed(downloadSpeed)} | UL: ${formatSpeed(uploadSpeed)}"
        
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Network Speed")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download) // Placeholder icon
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(downloadSpeed: Long, uploadSpeed: Long) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = createNotification(downloadSpeed, uploadSpeed)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec < 1024) return "$bytesPerSec B/s"
        val kb = bytesPerSec / 1024.0
        if (kb < 1024) return String.format("%.1f KB/s", kb)
        val mb = kb / 1024.0
        return String.format("%.1f MB/s", mb)
    }

    private fun startSpeedTracking() {
        trackingJob?.cancel()
        lastRxBytes = TrafficStats.getTotalRxBytes()
        lastTxBytes = TrafficStats.getTotalTxBytes()
        lastTimeMs = System.currentTimeMillis()

        trackingJob = serviceScope.launch {
            while (isActive) {
                // Battery tradeoff: slow down updates when screen is off.
                // 1000ms when screen is on, 5000ms when screen is off to conserve battery.
                val delayMs = if (isScreenOn) 1000L else 5000L
                delay(delayMs)

                val currentRx = TrafficStats.getTotalRxBytes()
                val currentTx = TrafficStats.getTotalTxBytes()
                val currentTime = System.currentTimeMillis()

                val timeDiff = currentTime - lastTimeMs
                if (timeDiff > 0 && currentRx != TrafficStats.UNSUPPORTED.toLong() && currentTx != TrafficStats.UNSUPPORTED.toLong()) {
                    val rxDiff = currentRx - lastRxBytes
                    val txDiff = currentTx - lastTxBytes

                    // Calculate bytes per second
                    val downloadSpeed = (rxDiff * 1000) / timeDiff
                    val uploadSpeed = (txDiff * 1000) / timeDiff

                    speedRepository.updateSpeed(downloadSpeed, uploadSpeed)

                    // Only update notification every few seconds or if speed changes significantly 
                    // to prevent system throttling of notification updates.
                    updateNotification(downloadSpeed, uploadSpeed)
                }

                lastRxBytes = currentRx
                lastTxBytes = currentTx
                lastTimeMs = currentTime
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        unregisterReceiver(screenStateReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
