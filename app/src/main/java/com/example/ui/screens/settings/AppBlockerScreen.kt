package com.example.ui.screens.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
fun AppBlockerScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val extended = LocalExtendedColors.current
    val blockSettings by viewModel.appBlockSettings.collectAsState()
    val isRunning by viewModel.appBlockVpnManager.isRunning.collectAsState()
    var packageInput by remember { mutableStateOf("") }

    val vpnConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.startAppBlocking()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp),
    ) {
        item { SettingsSubScreenHeader(title = "App Data Blocker", onBack = onBack) }

        item {
            SettingsCard {
                Text(
                    text = "Blocks selected apps' network access via a local VPN tunnel. Android has no " +
                        "public API to block one app's network access directly (that needs Device Owner or " +
                        "root) - this app instead routes only the apps you pick into a tunnel that goes " +
                        "nowhere, leaving every other app untouched. Requires one-time VPN permission.",
                    style = MaterialTheme.typography.bodySmall,
                    color = extended.textSecondary,
                )
            }
        }

        item {
            SettingsCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = if (isRunning) "Blocking is ON" else "Blocking is OFF",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Button(
                        onClick = {
                            if (isRunning) {
                                viewModel.stopAppBlocking()
                            } else {
                                val prepareIntent = viewModel.vpnPrepareIntent()
                                if (prepareIntent != null) vpnConsentLauncher.launch(prepareIntent) else viewModel.startAppBlocking()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text(if (isRunning) "Stop" else "Start")
                    }
                }
            }
        }

        item {
            SettingsCard {
                Text("Blocked apps (by package name)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = packageInput,
                        onValueChange = { packageInput = it },
                        label = { Text("e.g. com.example.app") },
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            if (packageInput.isNotBlank()) {
                                viewModel.addBlockedPackage(packageInput.trim())
                                packageInput = ""
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    ) { Text("Add") }
                }
            }
        }

        if (blockSettings.blockedPackages.isEmpty()) {
            item {
                Text("No apps blocked yet.", style = MaterialTheme.typography.bodySmall, color = extended.textSecondary)
            }
        } else {
            items(blockSettings.blockedPackages.toList()) { packageName ->
                SettingsCard {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(packageName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        IconButton(onClick = { viewModel.removeBlockedPackage(packageName) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = extended.textSecondary)
                        }
                    }
                }
            }
        }

        item {
            SettingsCard {
                SettingsToggleRow(
                    title = "Auto-block on low balance",
                    subtitle = "Automatically start blocking once usage crosses a % of your cap - only if " +
                        "you've granted VPN permission at least once already (a background check can't show the consent dialog).",
                    checked = blockSettings.autoBlockOnLowBalance,
                    onCheckedChange = { viewModel.setAutoBlock(enabled = it) },
                )
                if (blockSettings.autoBlockOnLowBalance) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                        listOf(90, 95, 100).forEach { percent ->
                            FilterChip(
                                selected = blockSettings.lowBalanceThresholdPercent == percent,
                                onClick = { viewModel.setAutoBlock(thresholdPercent = percent) },
                                label = { Text("$percent%") },
                            )
                        }
                    }
                }
            }
        }
    }
}
