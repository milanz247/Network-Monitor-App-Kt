package com.example.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.domain.model.UnlockMethod
import com.example.domain.security.BiometricAvailability
import com.example.presentation.SettingsViewModel
import com.example.ui.components.SettingsCard
import com.example.ui.components.SettingsSubScreenHeader
import com.example.ui.components.SettingsToggleRow
import com.example.ui.theme.LocalExtendedColors

@Composable
fun AppLockSettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.appLockSettings.collectAsState()
    val extended = LocalExtendedColors.current
    val biometricAvailability = remember { viewModel.biometricAvailability() }

    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var oldPin by remember { mutableStateOf("") }
    var pinMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp),
    ) {
        item { SettingsSubScreenHeader(title = "App Lock", onBack = onBack) }

        item {
            SettingsCard {
                SettingsToggleRow(
                    title = "Require unlock on open",
                    subtitle = "Locks on cold start and after being backgrounded for a while.",
                    checked = settings.lockEnabled,
                    onCheckedChange = { viewModel.setAppLockEnabled(it) },
                )
            }
        }

        if (settings.lockEnabled) {
            item {
                SettingsCard {
                    Text("Unlock method", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.unlockMethod == UnlockMethod.PIN,
                            onClick = { viewModel.setUnlockMethod(UnlockMethod.PIN) },
                            label = { Text("PIN") },
                        )
                        FilterChip(
                            selected = settings.unlockMethod == UnlockMethod.BIOMETRIC,
                            onClick = { viewModel.setUnlockMethod(UnlockMethod.BIOMETRIC) },
                            enabled = biometricAvailability == BiometricAvailability.AVAILABLE,
                            label = { Text("Biometric") },
                        )
                    }
                    if (biometricAvailability != BiometricAvailability.AVAILABLE) {
                        Text(
                            text = biometricUnavailableReason(biometricAvailability),
                            style = MaterialTheme.typography.bodySmall,
                            color = extended.textSecondary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            item {
                SettingsCard {
                    Text(
                        text = if (settings.hasPinConfigured) "Change PIN" else "Set a PIN",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (settings.hasPinConfigured) {
                            OutlinedTextField(
                                value = oldPin,
                                onValueChange = { oldPin = it },
                                label = { Text("Current PIN") },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        OutlinedTextField(
                            value = newPin,
                            onValueChange = { newPin = it },
                            label = { Text("New PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = confirmPin,
                            onValueChange = { confirmPin = it },
                            label = { Text("Confirm PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (pinMessage != null) {
                            Text(text = pinMessage!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Button(
                            onClick = {
                                when {
                                    newPin.length < 4 -> pinMessage = "PIN must be at least 4 digits."
                                    newPin != confirmPin -> pinMessage = "PINs don't match."
                                    settings.hasPinConfigured && !viewModel.changePin(oldPin, newPin) ->
                                        pinMessage = "Current PIN is incorrect."
                                    else -> {
                                        if (!settings.hasPinConfigured) viewModel.setPin(newPin)
                                        pinMessage = "PIN saved."
                                        newPin = ""; confirmPin = ""; oldPin = ""
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Save PIN")
                        }
                    }
                }
            }

            item {
                SettingsCard {
                    Text("Auto-lock delay", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = "Re-lock after being in the background for ${settings.autoLockDelaySeconds}s.",
                        style = MaterialTheme.typography.bodySmall,
                        color = extended.textSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 60, 300).forEach { seconds ->
                            FilterChip(
                                selected = settings.autoLockDelaySeconds == seconds,
                                onClick = { viewModel.setAutoLockDelaySeconds(seconds) },
                                label = { Text(if (seconds < 60) "${seconds}s" else "${seconds / 60}m") },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun biometricUnavailableReason(availability: BiometricAvailability): String = when (availability) {
    BiometricAvailability.NO_HARDWARE -> "This device has no biometric hardware."
    BiometricAvailability.HARDWARE_UNAVAILABLE -> "Biometric hardware is currently unavailable."
    BiometricAvailability.NONE_ENROLLED -> "No fingerprint/face is enrolled - add one in system Settings first."
    BiometricAvailability.UNSUPPORTED -> "Biometric unlock isn't supported on this device."
    BiometricAvailability.AVAILABLE -> ""
}
