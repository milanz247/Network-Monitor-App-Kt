package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AppShapes
import com.example.ui.theme.LocalExtendedColors

@Composable
fun SettingsScreen(
    isServiceRunning: Boolean,
    onToggleService: () -> Unit,
) {
    val extended = LocalExtendedColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column {
            Text(
                text = "PREFERENCES",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.card,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, extended.cardBorder),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Real-Time Speed Tracking",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Keeps a low-priority foreground notification alive to measure speed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = extended.textSecondary,
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = isServiceRunning,
                    onCheckedChange = { onToggleService() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = extended.cardBorder,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.card,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, extended.cardBorder),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "System Integration Info",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "• Local storage: Room database, kept on-device only\n" +
                        "• Battery: speed updates slow down automatically when the screen is off\n" +
                        "• Permissions: requires Usage Access to report per-app data usage",
                    style = MaterialTheme.typography.bodySmall,
                    color = extended.textSecondary,
                )
            }
        }
    }
}
