package com.example.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pushes a fresh snapshot into every pinned [NetMonitorWidget] instance.
 *
 * Deliberately called directly from [com.example.service.NetworkSpeedService]'s existing polling
 * loop (throttled there - see that class for the battery-tradeoff note) instead of scheduling a
 * WorkManager periodic worker: WorkManager periodic work has an OS-enforced minimum interval of
 * 15 minutes, which is far too coarse for a "live-ish" speed widget updating every 15-30s.
 */
object WidgetUpdater {

    suspend fun pushSnapshot(
        context: Context,
        downloadBps: Long,
        uploadBps: Long,
        wifiBytesToday: Long,
        mobileBytesToday: Long,
        isServiceRunning: Boolean,
    ) = withContext(Dispatchers.IO) {
        val widget = NetMonitorWidget()
        val glanceIds = GlanceAppWidgetManager(context).getGlanceIds(NetMonitorWidget::class.java)
        if (glanceIds.isEmpty()) return@withContext // No widget pinned to the home screen - nothing to do.

        for (glanceId in glanceIds) {
            updateAppWidgetState(context, widget.stateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[WidgetPreferenceKeys.DOWNLOAD_BPS] = downloadBps
                    this[WidgetPreferenceKeys.UPLOAD_BPS] = uploadBps
                    this[WidgetPreferenceKeys.WIFI_BYTES_TODAY] = wifiBytesToday
                    this[WidgetPreferenceKeys.MOBILE_BYTES_TODAY] = mobileBytesToday
                    this[WidgetPreferenceKeys.IS_SERVICE_RUNNING] = isServiceRunning
                    this[WidgetPreferenceKeys.UPDATED_AT_MILLIS] = System.currentTimeMillis()
                }
            }
        }
        widget.updateAll(context)
    }
}
