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
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.R
import com.example.data.detector.ConnectionTypeMonitor
import com.example.data.detector.SignalStrengthReader
import com.example.data.local.DowntimeReason
import com.example.data.prefs.DiagnosticsPreferences
import com.example.data.prefs.TrustedNetworksPreferences
import com.example.data.repository.DiagnosticsRepository
import com.example.domain.repository.SpeedRepository
import com.example.domain.security.RogueWifiDetector
import com.example.notification.SecurityAdvisoryNotifier
import com.example.widget.WidgetUpdater
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * Foreground service that tracks real-time network speed.
 * Uses a coroutine loop to calculate speed based on TrafficStats deltas.
 *
 * Phase 1 (Speed History & Diagnostics): this same loop also buckets speed into ~90s
 * [com.example.data.local.SpeedHistory] rows, detects sustained low-speed / no-connection spans,
 * and samples signal strength + connection type at a coarse interval - see the constants below
 * for the exact cadence and battery reasoning for each.
 */
@AndroidEntryPoint
class NetworkSpeedService : Service() {

    @Inject
    lateinit var speedRepository: SpeedRepository

    @Inject
    lateinit var diagnosticsRepository: DiagnosticsRepository

    @Inject
    lateinit var diagnosticsPreferences: DiagnosticsPreferences

    @Inject
    lateinit var connectionTypeMonitor: ConnectionTypeMonitor

    @Inject
    lateinit var signalStrengthReader: SignalStrengthReader

    @Inject
    lateinit var trustedNetworksPreferences: TrustedNetworksPreferences

    @Inject
    lateinit var securityAdvisoryNotifier: SecurityAdvisoryNotifier

    private val networkStatsManager by lazy {
        getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
    }
    private val connectivityManager by lazy {
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    private val batteryManager by lazy {
        getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    }
    private val powerManager by lazy {
        getSystemService(Context.POWER_SERVICE) as PowerManager
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

    // ---- Phase 1: speed-history bucket accumulator (#1) ----
    private var bucketStartMs = 0L
    private var bucketSampleCount = 0
    private var bucketSumDownload = 0L
    private var bucketSumUpload = 0L
    private var bucketPeakDownload = 0L
    private var bucketPeakUpload = 0L

    // ---- Phase 1: low-speed downtime tracking (#3) ----
    private var lowSpeedStartMs: Long? = null
    private var lowSpeedLogged = false
    private var noConnectionLogged = false
    private var lowSpeedThresholdBps = DiagnosticsPreferences.DEFAULT_LOW_SPEED_THRESHOLD_BPS
    private var lowSpeedSustainedMs = DiagnosticsPreferences.DEFAULT_LOW_SPEED_SUSTAINED_SECONDS * 1000L

    // ---- Phase 5: rogue Wi-Fi / captive portal dedupe (#19) - avoid renotifying every sample while still connected ----
    private var lastFlaggedUntrustedSsid: String? = null
    private var lastFlaggedCaptivePortal = false

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

        // Phase 1 (#5): starts the (API 30+) TelephonyDisplayInfo listener used to distinguish 5G
        // NSA from plain LTE - see ConnectionTypeMonitor's class doc for the platform limitation
        // below API 30. Cheap: push-based, not polled.
        connectionTypeMonitor.start()
        bucketStartMs = System.currentTimeMillis()

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
            val diagnosticsSettings = diagnosticsPreferences.settingsFlow.first()
            lowSpeedThresholdBps = diagnosticsSettings.lowSpeedThresholdBps
            lowSpeedSustainedMs = diagnosticsSettings.lowSpeedSustainedSeconds * 1000L
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
                delay(computeAdaptivePollingIntervalMs())

                val currentRx = TrafficStats.getTotalRxBytes()
                val currentTx = TrafficStats.getTotalTxBytes()
                val currentTime = System.currentTimeMillis()

                // Phase 1 (#3): polled here rather than via a second NetworkCallback registration,
                // since this loop already ticks at the exact cadence (1s/5s) we'd want to poll at
                // anyway - NetworkDetector's callback-based flow is for the UI's live connection
                // state, this is a separate concern (downtime *logging*).
                if (connectivityManager.activeNetwork == null) {
                    if (!noConnectionLogged) {
                        noConnectionLogged = true
                        serviceScope.launch { diagnosticsRepository.openDowntimeIfNeeded(DowntimeReason.NO_CONNECTION, currentTime) }
                    }
                } else if (noConnectionLogged) {
                    noConnectionLogged = false
                    serviceScope.launch { diagnosticsRepository.closeDowntimeIfOpen(DowntimeReason.NO_CONNECTION, currentTime) }
                }

                val timeDiff = currentTime - lastTimeMs
                if (timeDiff > 0 && currentRx != TrafficStats.UNSUPPORTED.toLong() && currentTx != TrafficStats.UNSUPPORTED.toLong()) {
                    val rxDiff = currentRx - lastRxBytes
                    val txDiff = currentTx - lastTxBytes

                    // Calculate bytes per second
                    val downloadSpeed = (rxDiff * 1000) / timeDiff
                    val uploadSpeed = (txDiff * 1000) / timeDiff

                    speedRepository.updateSpeed(downloadSpeed, uploadSpeed)

                    // ---- Phase 1 (#1): accumulate into the current ~90s speed-history bucket ----
                    bucketSampleCount++
                    bucketSumDownload += downloadSpeed
                    bucketSumUpload += uploadSpeed
                    if (downloadSpeed > bucketPeakDownload) bucketPeakDownload = downloadSpeed
                    if (uploadSpeed > bucketPeakUpload) bucketPeakUpload = uploadSpeed
                    if (currentTime - bucketStartMs >= SPEED_HISTORY_BUCKET_MS && bucketSampleCount > 0) {
                        val avgDown = bucketSumDownload / bucketSampleCount
                        val avgUp = bucketSumUpload / bucketSampleCount
                        val peakDown = bucketPeakDownload
                        val peakUp = bucketPeakUpload
                        val bucketTimestamp = bucketStartMs
                        serviceScope.launch {
                            diagnosticsRepository.recordSpeedSample(bucketTimestamp, avgDown, avgUp, peakDown, peakUp)
                            // Re-read every bucket flush (~90s) so a settings change takes effect
                            // promptly without a long-lived collector running every 1s tick.
                            val diagnosticsSettings = diagnosticsPreferences.settingsFlow.first()
                            lowSpeedThresholdBps = diagnosticsSettings.lowSpeedThresholdBps
                            lowSpeedSustainedMs = diagnosticsSettings.lowSpeedSustainedSeconds * 1000L
                        }
                        bucketStartMs = currentTime
                        bucketSampleCount = 0
                        bucketSumDownload = 0L
                        bucketSumUpload = 0L
                        bucketPeakDownload = 0L
                        bucketPeakUpload = 0L
                    }

                    // ---- Phase 1 (#3): sustained low-speed detection ----
                    if (downloadSpeed < lowSpeedThresholdBps && uploadSpeed < lowSpeedThresholdBps) {
                        val startedAt = lowSpeedStartMs ?: currentTime.also { lowSpeedStartMs = it }
                        if (!lowSpeedLogged && currentTime - startedAt >= lowSpeedSustainedMs) {
                            lowSpeedLogged = true
                            serviceScope.launch { diagnosticsRepository.openDowntimeIfNeeded(DowntimeReason.LOW_SPEED, startedAt) }
                        }
                    } else {
                        if (lowSpeedLogged) {
                            serviceScope.launch { diagnosticsRepository.closeDowntimeIfOpen(DowntimeReason.LOW_SPEED, currentTime) }
                        }
                        lowSpeedStartMs = null
                        lowSpeedLogged = false
                    }

                    // ---- Phase 1 (#4, #5): coarse signal-strength + connection-type sampling ----
                    // Every DIAGNOSTICS_SAMPLE_EVERY_N_TICKS ticks - same reasoning as the widget
                    // push cadence above: a meaningfully coarser interval than the 1s/5s loop tick,
                    // since signal/connection-type rarely needs finer-than-~2min resolution.
                    if (tickCount % DIAGNOSTICS_SAMPLE_EVERY_N_TICKS == 0) {
                        val wifiRssi = signalStrengthReader.readWifiRssi()
                        val cellularLevel = signalStrengthReader.readCellularSignalLevel()
                        val connectionType = connectionTypeMonitor.classify()
                        serviceScope.launch {
                            diagnosticsRepository.recordSignalSample(currentTime, wifiRssi, cellularLevel)
                            diagnosticsRepository.recordConnectionTypeIfChanged(currentTime, connectionType)
                        }

                        // ---- Phase 5 (#17): device-wide battery level sample ----
                        val batteryLevel = try {
                            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                        } catch (e: Exception) {
                            -1
                        }
                        if (batteryLevel in 0..100) {
                            serviceScope.launch { diagnosticsRepository.recordBatteryLevelSample(currentTime, batteryLevel) }
                        }

                        // ---- Phase 5 (#19): rogue Wi-Fi / captive portal check ----
                        if (connectionType == "WIFI") {
                            val ssid = signalStrengthReader.readCurrentSsid()
                            val caps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
                            val isCaptivePortal = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true

                            serviceScope.launch {
                                val trustedSsids = trustedNetworksPreferences.currentTrustedSsids()
                                if (RogueWifiDetector.isUntrusted(ssid, trustedSsids)) {
                                    if (lastFlaggedUntrustedSsid != ssid) {
                                        lastFlaggedUntrustedSsid = ssid
                                        securityAdvisoryNotifier.notifyUntrustedNetwork(ssid!!)
                                    }
                                } else {
                                    lastFlaggedUntrustedSsid = null
                                }
                            }
                            if (isCaptivePortal) {
                                if (!lastFlaggedCaptivePortal) {
                                    lastFlaggedCaptivePortal = true
                                    securityAdvisoryNotifier.notifyCaptivePortal(ssid)
                                }
                            } else {
                                lastFlaggedCaptivePortal = false
                            }
                        } else {
                            lastFlaggedUntrustedSsid = null
                            lastFlaggedCaptivePortal = false
                        }
                    }

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

    /**
     * Phase 5 (#18) - adapts this loop's own polling interval to device state, replacing the old
     * static screen-on/off-only choice:
     *  - charging + screen-on: 1000ms (most responsive, no battery cost to worry about)
     *  - screen-on, not charging: 2000ms (still feels live, halves the wakeups)
     *  - screen-off, not idle: 5000ms (matches the previous behavior)
     *  - Doze (`isDeviceIdleMode`): 10000ms (the OS is already batching background work this
     *    aggressively; matching that intent rather than fighting it)
     */
    private fun computeAdaptivePollingIntervalMs(): Long {
        val isCharging = try {
            batteryManager.isCharging
        } catch (e: Exception) {
            false
        }
        val isIdle = try {
            powerManager.isDeviceIdleMode
        } catch (e: Exception) {
            false
        }
        return when {
            isIdle -> 10_000L
            !isScreenOn -> 5_000L
            isCharging -> 1_000L
            else -> 2_000L
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
        val cleanupScope = CoroutineScope(Dispatchers.IO)
        cleanupScope.launch {
            WidgetUpdater.pushSnapshot(
                context = applicationContext,
                downloadBps = 0L,
                uploadBps = 0L,
                wifiBytesToday = 0L,
                mobileBytesToday = 0L,
                isServiceRunning = false,
            )
            // Phase 1 (#3): we're no longer measuring speed, so a LOW_SPEED span shouldn't be left
            // open indefinitely - a genuine NO_CONNECTION span is left open, since connectivity is
            // independent of whether this service happens to be running.
            if (lowSpeedLogged) {
                diagnosticsRepository.closeDowntimeIfOpen(DowntimeReason.LOW_SPEED, System.currentTimeMillis())
            }
        }
        connectionTypeMonitor.stop()
        super.onDestroy()
        serviceScope.cancel()
        unregisterReceiver(screenStateReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1001

        /** ~20s on-screen / ~100s off-screen at the loop's 1000ms/5000ms tick rate - see the widget push comment above. */
        private const val WIDGET_UPDATE_EVERY_N_TICKS = 20

        /** Phase 1 (#1): flush one SpeedHistory row roughly every 90s, per the brief's "every 1-2 min" guidance. */
        private const val SPEED_HISTORY_BUCKET_MS = 90_000L

        /** Phase 1 (#4, #5): ~90s on-screen / ~450s off-screen at the loop's 1000ms/5000ms tick rate. */
        private const val DIAGNOSTICS_SAMPLE_EVERY_N_TICKS = 90
    }
}
