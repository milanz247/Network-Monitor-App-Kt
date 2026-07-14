package com.example.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.presentation.SettingsViewModel
import com.example.ui.components.SettingsCard
import com.example.ui.components.SettingsSubScreenHeader
import com.example.ui.theme.LocalExtendedColors
import com.example.ui.util.formatBytesSpeed
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm")

private fun Long.formatAsLocalTime(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(TIME_FORMAT)

@Composable
fun DiagnosticsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val extended = LocalExtendedColors.current
    val peak by viewModel.peakSpeedRecord.collectAsState()
    val downtimeLogs by viewModel.downtimeLogs.collectAsState()
    val transitions by viewModel.connectionTypeTransitions.collectAsState()
    val settings by viewModel.diagnosticsSettings.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshDiagnostics() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp),
    ) {
        item { SettingsSubScreenHeader(title = "Speed & Diagnostics", onBack = onBack) }

        item {
            SettingsCard {
                Text("Peak speed (last 7 days)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Download", style = MaterialTheme.typography.bodySmall, color = extended.textSecondary)
                        Text(
                            text = peak?.let { formatBytesSpeed(it.peakDownloadBps) } ?: "-",
                            style = MaterialTheme.typography.titleMedium,
                            color = extended.wifiAccent,
                        )
                    }
                    Column {
                        Text("Upload", style = MaterialTheme.typography.bodySmall, color = extended.textSecondary)
                        Text(
                            text = peak?.let { formatBytesSpeed(it.peakUploadBps) } ?: "-",
                            style = MaterialTheme.typography.titleMedium,
                            color = extended.mobileAccent,
                        )
                    }
                }
            }
        }

        item {
            SettingsCard {
                Text("Low-speed threshold", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = "A downtime span logs when speed stays under this for ${settings.lowSpeedSustainedSeconds}s.",
                    style = MaterialTheme.typography.bodySmall,
                    color = extended.textSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(20_000L, 50_000L, 100_000L, 250_000L).forEach { bps ->
                        FilterChip(
                            selected = settings.lowSpeedThresholdBps == bps,
                            onClick = { viewModel.updateDiagnosticsSettings(lowSpeedThresholdBps = bps) },
                            label = { Text(formatBytesSpeed(bps)) },
                        )
                    }
                }
            }
        }

        item {
            SettingsCard {
                Text("History retention", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = "Diagnostics older than ${settings.speedHistoryRetentionDays} days are auto-deleted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = extended.textSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7, 14, 30).forEach { days ->
                        FilterChip(
                            selected = settings.speedHistoryRetentionDays == days,
                            onClick = { viewModel.updateDiagnosticsSettings(retentionDays = days) },
                            label = { Text("${days}d") },
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Connectivity downtime (last 7 days)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        if (downtimeLogs.isEmpty()) {
            item {
                Text("No downtime recorded.", style = MaterialTheme.typography.bodySmall, color = extended.textSecondary)
            }
        } else {
            items(downtimeLogs) { log ->
                SettingsCard {
                    Text(
                        text = if (log.reason == "NO_CONNECTION") "No connection" else "Low speed",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val endLabel = log.endTime?.formatAsLocalTime() ?: "ongoing"
                    Text(
                        text = "${log.startTime.formatAsLocalTime()} -> $endLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = extended.textSecondary,
                    )
                }
            }
        }

        item {
            Text(
                text = "Connection-type changes (last 7 days)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        if (transitions.isEmpty()) {
            item {
                Text("No transitions recorded.", style = MaterialTheme.typography.bodySmall, color = extended.textSecondary)
            }
        } else {
            items(transitions) { transition ->
                SettingsCard {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(transition.connectionType, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text(transition.timestamp.formatAsLocalTime(), style = MaterialTheme.typography.bodySmall, color = extended.textSecondary)
                    }
                }
            }
        }
    }
}
