package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.NetworkStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.R
import com.example.domain.repository.SpeedRepository
import com.example.widget.WidgetUpdater
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * Foreground service that tracks real-time network speed.
 * Uses a coroutine loop to calculate speed based on TrafficStats deltas.
 */
@AndroidEntryPoint
class NetworkSpeedService : Service() {

    @Inject
    lateinit var speedRepository: SpeedRepository

    private val networkStatsManager by lazy {
        getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var trackingJob: Job? = null

    private var isScreenOn = true
    private var lastRxBytes = 0L
    private var lastTxBytes = 0L
    private var lastTimeMs = 0L

    // Feature 3 (widget): counts loop ticks so the widget is refreshed far less often than the
    // notification. See startSpeedTracking() for the battery-tradeoff reasoning.
    private var tickCount = 0

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

        // Push an immediate "running" snapshot so a pinned widget doesn't sit on stale/zero data
        // for the ~20s until the first throttled update in the tracking loop below.
        serviceScope.launch {
            val (wifiToday, mobileToday) = queryTodayDeviceTotals()
            WidgetUpdater.pushSnapshot(
                context = applicationContext,
                downloadBps = 0L,
                uploadBps = 0L,
                wifiBytesToday = wifiToday,
                mobileBytesToday = mobileToday,
                isServiceRunning = true,
            )
        }
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
            .setSmallIcon(R.drawable.ic_notification)
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

                    // Feature 3 (widget): push far less often than the notification. A Glance/
                    // RemoteViews update is a full IPC + widget-host redraw, much heavier per-call
                    // than a notification update, so doing it every 1s like the notification would
                    // be a real battery cost - especially with several widget instances pinned.
                    // Every WIDGET_UPDATE_EVERY_N_TICKS ticks gives ~20s while the screen is on
                    // (still feels "live" glancing at the home screen) and ~100s while it's off,
                    // which is fine since nobody is looking at a home-screen widget with the
                    // screen off anyway - and it rides the loop's existing screen-off backoff for
                    // free rather than needing its own schedule.
                    tickCount++
                    if (tickCount % WIDGET_UPDATE_EVERY_N_TICKS == 0) {
                        val (wifiToday, mobileToday) = queryTodayDeviceTotals()
                        WidgetUpdater.pushSnapshot(
                            context = applicationContext,
                            downloadBps = downloadSpeed,
                            uploadBps = uploadSpeed,
                            wifiBytesToday = wifiToday,
                            mobileBytesToday = mobileToday,
                            isServiceRunning = true,
                        )
                    }
                }

                lastRxBytes = currentRx
                lastTxBytes = currentTx
                lastTimeMs = currentTime
            }
        }
    }

    /** Cheap, device-wide (not per-app) totals for today - deliberately not the heavier per-app query DataUsageRepository uses. */
    private fun queryTodayDeviceTotals(): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        val now = System.currentTimeMillis()

        val wifiBytes = try {
            val bucket = networkStatsManager.querySummaryForDevice(NetworkCapabilities.TRANSPORT_WIFI, null, startOfDay, now)
            bucket.rxBytes + bucket.txBytes
        } catch (e: Exception) {
            0L
        }
        val mobileBytes = try {
            val bucket = networkStatsManager.querySummaryForDevice(NetworkCapabilities.TRANSPORT_CELLULAR, null, startOfDay, now)
            bucket.rxBytes + bucket.txBytes
        } catch (e: Exception) {
            0L
        }
        return wifiBytes to mobileBytes
    }

    override fun onDestroy() {
        // Let the widget reflect that live tracking has stopped instead of freezing on a stale
        // speed. Uses its own short-lived scope (not serviceScope) since that's cancelled below
        // and a cancelled scope's coroutines never get to run.
        CoroutineScope(Dispatchers.IO).launch {
            WidgetUpdater.pushSnapshot(
                context = applicationContext,
                downloadBps = 0L,
                uploadBps = 0L,
                wifiBytesToday = 0L,
                mobileBytesToday = 0L,
                isServiceRunning = false,
            )
        }
        super.onDestroy()
        serviceScope.cancel()
        unregisterReceiver(screenStateReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1001

        /** ~20s on-screen / ~100s off-screen at the loop's 1000ms/5000ms tick rate - see the widget push comment above. */
        private const val WIDGET_UPDATE_EVERY_N_TICKS = 20
    }
}
