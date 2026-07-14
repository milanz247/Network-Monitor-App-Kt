package com.example.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.prefs.TargetRadio
import com.example.presentation.SettingsViewModel
import com.example.ui.components.SettingsCard
import com.example.ui.components.SettingsSubScreenHeader
import com.example.ui.components.SettingsToggleRow
import com.example.ui.theme.LocalExtendedColors

@Composable
fun WifiRadioScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val extended = LocalExtendedColors.current
    val wifiReminder by viewModel.wifiReminderSettings.collectAsState()
    val radioSchedule by viewModel.radioScheduleSettings.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp),
    ) {
        item { SettingsSubScreenHeader(title = "Wi-Fi & Radio Reminders", onBack = onBack) }

        item {
            SettingsCard {
                SettingsToggleRow(
                    title = "Wi-Fi low-data reminder",
                    subtitle = "Nudge to switch to Wi-Fi once mobile usage crosses a % of your cap. " +
                        "Android can't auto-connect for you - this just opens Wi-Fi settings.",
                    checked = wifiReminder.enabled,
                    onCheckedChange = { viewModel.setWifiReminderEnabled(it) },
                )
                if (wifiReminder.enabled) {
                    Text(
                        text = "Remind at ${wifiReminder.thresholdPercent}% of your data cap",
                        style = MaterialTheme.typography.bodySmall,
                        color = extended.textSecondary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(75, 90, 95).forEach { percent ->
                            FilterChip(
                                selected = wifiReminder.thresholdPercent == percent,
                                onClick = { viewModel.setWifiReminderThreshold(percent) },
                                label = { Text("$percent%") },
                            )
                        }
                    }
                }
            }
        }

        item {
            SettingsCard {
                SettingsToggleRow(
                    title = "Scheduled radio-switch reminder",
                    subtitle = "A daily notification at a time you choose. Android has no API to silently " +
                        "toggle Wi-Fi/mobile data on a regular app - this only reminds you, via a notification " +
                        "that deep-links to system settings.",
                    checked = radioSchedule.enabled,
                    onCheckedChange = { enabled ->
                        viewModel.setRadioSchedule(enabled, radioSchedule.hour, radioSchedule.minute, radioSchedule.targetRadio)
                    },
                )
                if (radioSchedule.enabled) {
                    Text(
                        text = "Switch to ${if (radioSchedule.targetRadio == TargetRadio.WIFI) "Wi-Fi" else "mobile data"} " +
                            "at ${radioSchedule.hour.toString().padStart(2, '0')}:${radioSchedule.minute.toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.bodySmall,
                        color = extended.textSecondary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TargetRadio.entries.forEach { target ->
                            FilterChip(
                                selected = radioSchedule.targetRadio == target,
                                onClick = {
                                    viewModel.setRadioSchedule(true, radioSchedule.hour, radioSchedule.minute, target)
                                },
                                label = { Text(if (target == TargetRadio.WIFI) "Wi-Fi" else "Mobile") },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        listOf(8, 9, 12, 18, 21).forEach { hour ->
                            FilterChip(
                                selected = radioSchedule.hour == hour,
                                onClick = {
                                    viewModel.setRadioSchedule(true, hour, 0, radioSchedule.targetRadio)
                                },
                                label = { Text("${hour.toString().padStart(2, '0')}:00") },
                            )
                        }
                    }
                }
            }
        }
    }
}
