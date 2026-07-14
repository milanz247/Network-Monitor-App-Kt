package com.example.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.presentation.SettingsViewModel
import com.example.ui.components.SettingsCard
import com.example.ui.components.SettingsSubScreenHeader
import com.example.ui.components.SettingsToggleRow
import com.example.ui.theme.LocalExtendedColors
import com.example.ui.util.formatBytes

@Composable
fun AdvancedSettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val extended = LocalExtendedColors.current
    val costSettings by viewModel.costSettings.collectAsState()
    val trustedSsids by viewModel.trustedSsids.collectAsState()
    val exfilSettings by viewModel.exfiltrationHeuristicSettings.collectAsState()
    val batteryCorrelation by viewModel.batteryCorrelation.collectAsState()

    var wifiRate by remember(costSettings.ratePerGbWifi) { mutableStateOf(costSettings.ratePerGbWifi.toString()) }
    var mobileRate by remember(costSettings.ratePerGbMobile) { mutableStateOf(costSettings.ratePerGbMobile.toString()) }
    var ssidInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.refreshBatteryCorrelation() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp),
    ) {
        item { SettingsSubScreenHeader(title = "Advanced", onBack = onBack) }

        item {
            SettingsCard {
                Text("Cost tracking", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = "Pure calculation on your existing usage totals - set a rate per GB to estimate cost.",
                    style = MaterialTheme.typography.bodySmall,
                    color = extended.textSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = wifiRate,
                        onValueChange = { wifiRate = it },
                        label = { Text("${costSettings.currencySymbol}/GB Wi-Fi") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = mobileRate,
                        onValueChange = { mobileRate = it },
                        label = { Text("${costSettings.currencySymbol}/GB Mobile") },
                        modifier = Modifier.weight(1f),
                    )
                }
                Button(
                    onClick = {
                        viewModel.updateCostSettings(
                            ratePerGbWifi = wifiRate.toFloatOrNull() ?: costSettings.ratePerGbWifi,
                            ratePerGbMobile = mobileRate.toFloatOrNull() ?: costSettings.ratePerGbMobile,
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) { Text("Save rates") }
            }
        }

        item {
            SettingsCard {
                Text("Battery vs data (last 7 days)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = "Device-wide only - true per-app battery attribution needs a system-signature " +
                        "permission no regular app can hold, so this can't be broken down per app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = extended.textSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                val correlation = batteryCorrelation
                if (correlation == null) {
                    Text("Loading...", style = MaterialTheme.typography.bodySmall, color = extended.textSecondary)
                } else {
                    Text("Data used: ${formatBytes(correlation.totalBytes)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = if (correlation.batteryDrainPercent != null) {
                            "Battery drop: ${correlation.batteryDrainPercent}% (${formatBytes(correlation.bytesPerPercentBattery ?: 0L)} per 1%)"
                        } else {
                            "Not enough battery samples yet."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = extended.textSecondary,
                    )
                }
            }
        }

        item {
            SettingsCard {
                SettingsToggleRow(
                    title = "Background-usage heuristic alert",
                    subtitle = "Flags apps using meaningful data almost entirely in the background. A heuristic, not a definitive finding.",
                    checked = exfilSettings.enabled,
                    onCheckedChange = { viewModel.setExfiltrationHeuristicEnabled(it) },
                )
            }
        }

        item {
            SettingsCard {
                Text("Trusted Wi-Fi networks", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = "Connecting to a Wi-Fi network not on this list triggers an advisory notification. Empty list = no checking.",
                    style = MaterialTheme.typography.bodySmall,
                    color = extended.textSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = ssidInput,
                        onValueChange = { ssidInput = it },
                        label = { Text("SSID") },
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            if (ssidInput.isNotBlank()) {
                                viewModel.addTrustedSsid(ssidInput.trim())
                                ssidInput = ""
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    ) { Text("Add") }
                }
            }
        }

        items(trustedSsids.toList()) { ssid ->
            SettingsCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(ssid, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    IconButton(onClick = { viewModel.removeTrustedSsid(ssid) }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = extended.textSecondary)
                    }
                }
            }
        }
    }
}
