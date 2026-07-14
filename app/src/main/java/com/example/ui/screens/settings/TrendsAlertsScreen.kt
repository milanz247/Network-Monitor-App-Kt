package com.example.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.domain.model.UsageTrendPoint
import com.example.presentation.SettingsViewModel
import com.example.ui.components.SettingsCard
import com.example.ui.components.SettingsSubScreenHeader
import com.example.ui.components.SettingsToggleRow
import com.example.ui.theme.LocalExtendedColors
import com.example.ui.util.formatBytes

@Composable
fun TrendsAlertsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val extended = LocalExtendedColors.current
    val weeklyTrend by viewModel.weeklyTrend.collectAsState()
    val monthlyTrend by viewModel.monthlyTrend.collectAsState()
    val hourlyBreakdown by viewModel.todayHourlyBreakdown.collectAsState()
    val spikeSettings by viewModel.spikeAlertSettings.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshTrends() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp),
    ) {
        item { SettingsSubScreenHeader(title = "Trends & Alerts", onBack = onBack) }

        item {
            SettingsCard {
                SettingsToggleRow(
                    title = "Unusual usage spike alert",
                    subtitle = "Notify when a day's usage is far above your recent average.",
                    checked = spikeSettings.enabled,
                    onCheckedChange = { viewModel.setSpikeAlertEnabled(it) },
                )
                if (spikeSettings.enabled) {
                    Text(
                        text = "Threshold: ${spikeSettings.multiplier}x your 7-day average",
                        style = MaterialTheme.typography.bodySmall,
                        color = extended.textSecondary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2f, 3f, 5f).forEach { multiplier ->
                            FilterChip(
                                selected = spikeSettings.multiplier == multiplier,
                                onClick = { viewModel.setSpikeAlertMultiplier(multiplier) },
                                label = { Text("${multiplier.toInt()}x") },
                            )
                        }
                    }
                }
            }
        }

        item {
            Text("Busiest hours today", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
        }
        item {
            SettingsCard {
                val busiest = hourlyBreakdown.sortedByDescending { it.totalBytes }.take(5).filter { it.totalBytes > 0 }
                if (busiest.isEmpty()) {
                    Text("No usage recorded yet today.", style = MaterialTheme.typography.bodySmall, color = extended.textSecondary)
                } else {
                    busiest.forEach { hourly ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${hourly.hour.toString().padStart(2, '0')}:00",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(formatBytes(hourly.totalBytes), style = MaterialTheme.typography.bodyMedium, color = extended.textSecondary)
                        }
                    }
                }
            }
        }

        item {
            Text("Weekly trend", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
        }
        item { TrendList(points = weeklyTrend) }

        item {
            Text("Monthly trend", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
        }
        item { TrendList(points = monthlyTrend) }
    }
}

@Composable
private fun TrendList(points: List<UsageTrendPoint>) {
    val extended = LocalExtendedColors.current
    SettingsCard {
        if (points.isEmpty() || points.all { it.totalBytes == 0L }) {
            Text("Not enough history yet.", style = MaterialTheme.typography.bodySmall, color = extended.textSecondary)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                points.forEach { point ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(point.periodLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(formatBytes(point.totalBytes), style = MaterialTheme.typography.bodyMedium, color = extended.textSecondary)
                    }
                }
            }
        }
    }
}
