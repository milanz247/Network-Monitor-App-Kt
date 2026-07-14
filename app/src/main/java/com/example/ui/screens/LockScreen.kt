package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.domain.model.UnlockMethod
import com.example.domain.security.BiometricAuthenticator
import com.example.ui.theme.LocalExtendedColors

/**
 * Phase 0 - the full-screen gate shown while [com.example.domain.security.AppLockManager]'s state
 * is Locked. Biometric fires automatically on entry when it's the chosen method; PIN entry is
 * always offered as the fallback so a broken sensor can't brick the app.
 */
@Composable
fun LockScreen(
    hasPinConfigured: Boolean,
    unlockMethod: UnlockMethod,
    biometricAvailable: Boolean,
    onVerifyPin: (String) -> Boolean,
    onUnlock: () -> Unit,
) {
    val extended = LocalExtendedColors.current
    val activity = LocalContext.current as? FragmentActivity

    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val canUseBiometric = unlockMethod == UnlockMethod.BIOMETRIC && biometricAvailable && activity != null
    // No PIN set and no usable biometric: there is nothing to verify against, so honestly say so
    // and let the user in rather than bricking the app behind an unanswerable prompt.
    val noCredentialAvailable = !hasPinConfigured && !canUseBiometric

    fun launchBiometric() {
        if (activity == null) return
        BiometricAuthenticator.authenticate(
            activity = activity,
            onSuccess = onUnlock,
            onError = { _, message -> error = message.toString() },
        )
    }

    LaunchedEffect(canUseBiometric) {
        if (canUseBiometric) launchBiometric()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Net Monitor is locked",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (noCredentialAvailable) {
                Text(
                    text = "App lock is on, but no PIN is set and biometrics aren't available. " +
                        "Set a PIN in Settings > App Lock to secure the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = extended.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onUnlock, modifier = Modifier.fillMaxWidth()) {
                    Text("Continue")
                }
            } else {
                if (hasPinConfigured) {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = {
                            pin = it
                            error = null
                        },
                        label = { Text("PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (error != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (onVerifyPin(pin)) {
                                onUnlock()
                            } else {
                                pin = ""
                                error = "Incorrect PIN - try again."
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Unlock")
                    }
                }
                if (canUseBiometric) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { launchBiometric() }) {
                        Text("Use fingerprint / face")
                    }
                }
            }
        }
    }
}
