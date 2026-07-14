package com.example.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.domain.util.formatBytesCompact
import com.example.domain.util.formatBytesSpeedCompact

/**
 * Home-screen widget (Feature 3): current speed + today's Wi-Fi/mobile totals. Built with Jetpack
 * Glance (Kotlin-first Composable widget UI) rather than legacy RemoteViews XML - Glance itself
 * still compiles down to RemoteViews, so it's fully compatible with this app's minSdk 26.
 *
 * Note: exact ColorProvider/TextStyle constructor shapes have shifted slightly across Glance 1.0.x
 * -> 1.1.x; adjust to match whatever `glance` version ends up pinned in libs.versions.toml if the
 * compiler flags a signature mismatch.
 */
class NetMonitorWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            WidgetContent(prefs)
        }
    }

    @Composable
    private fun WidgetContent(prefs: Preferences) {
        val downloadBps = prefs[WidgetPreferenceKeys.DOWNLOAD_BPS] ?: 0L
        val uploadBps = prefs[WidgetPreferenceKeys.UPLOAD_BPS] ?: 0L
        val wifiBytes = prefs[WidgetPreferenceKeys.WIFI_BYTES_TODAY] ?: 0L
        val mobileBytes = prefs[WidgetPreferenceKeys.MOBILE_BYTES_TODAY] ?: 0L
        val isRunning = prefs[WidgetPreferenceKeys.IS_SERVICE_RUNNING] ?: false

        val background = ColorProvider(Color(0xFF15171F))
        val labelColor = ColorProvider(Color(0xFF9AA0B4))
        val valueColor = ColorProvider(Color(0xFFE8EAF2))
        val accentColor = ColorProvider(Color(0xFF6E9BFF))

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(background)
                .padding(12.dp),
        ) {
            Text(
                text = "NET MONITOR",
                style = TextStyle(color = labelColor, fontWeight = FontWeight.Bold),
            )

            Spacer(modifier = GlanceModifier.height(6.dp))

            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    text = if (isRunning) "↓ ${formatBytesSpeedCompact(downloadBps)}" else "↓ --",
                    style = TextStyle(color = accentColor, fontWeight = FontWeight.Bold),
                )
                Spacer(modifier = GlanceModifier.width(12.dp))
                Text(
                    text = if (isRunning) "↑ ${formatBytesSpeedCompact(uploadBps)}" else "↑ --",
                    style = TextStyle(color = valueColor, fontWeight = FontWeight.Medium),
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            Text(
                text = "Today: ${formatBytesCompact(wifiBytes + mobileBytes)}",
                style = TextStyle(color = labelColor),
            )
            Text(
                text = "Wi-Fi ${formatBytesCompact(wifiBytes)} · Mobile ${formatBytesCompact(mobileBytes)}",
                style = TextStyle(color = valueColor),
            )
        }
    }
}
