package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.presentation.SettingsViewModel
import com.example.ui.screens.settings.AdvancedSettingsScreen
import com.example.ui.screens.settings.AppBlockerScreen
import com.example.ui.screens.settings.AppLockSettingsScreen
import com.example.ui.screens.settings.BackupResetScreen
import com.example.ui.screens.settings.DiagnosticsScreen
import com.example.ui.screens.settings.SettingsRoute
import com.example.ui.screens.settings.TrendsAlertsScreen
import com.example.ui.screens.settings.WifiRadioScreen
import com.example.ui.components.SettingsMenuRow
import com.example.ui.theme.AppShapes
import com.example.ui.theme.LocalExtendedColors

@Composable
fun SettingsScreen(
    isServiceRunning: Boolean,
    onToggleService: () -> Unit,
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    var route by rememberSaveable { mutableStateOf(SettingsRoute.HUB) }

    AnimatedContent(
        targetState = route,
        label = "settings_route_transition",
        transitionSpec = { fadeIn() togetherWith fadeOut() },
    ) { currentRoute ->
        when (currentRoute) {
            SettingsRoute.HUB -> SettingsHub(
                isServiceRunning = isServiceRunning,
                onToggleService = onToggleService,
                onNavigate = { route = it },
            )
            SettingsRoute.APP_LOCK -> AppLockSettingsScreen(viewModel = viewModel, onBack = { route = SettingsRoute.HUB })
            SettingsRoute.DIAGNOSTICS -> DiagnosticsScreen(viewModel = viewModel, onBack = { route = SettingsRoute.HUB })
            SettingsRoute.TRENDS_ALERTS -> TrendsAlertsScreen(viewModel = viewModel, onBack = { route = SettingsRoute.HUB })
            SettingsRoute.WIFI_RADIO -> WifiRadioScreen(viewModel = viewModel, onBack = { route = SettingsRoute.HUB })
            SettingsRoute.APP_BLOCKER -> AppBlockerScreen(viewModel = viewModel, onBack = { route = SettingsRoute.HUB })
            SettingsRoute.BACKUP_RESET -> BackupResetScreen(viewModel = viewModel, onBack = { route = SettingsRoute.HUB })
            SettingsRoute.ADVANCED -> AdvancedSettingsScreen(viewModel = viewModel, onBack = { route = SettingsRoute.HUB })
        }
    }
}

private data class MenuEntry(
    val route: SettingsRoute,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

@Composable
private fun SettingsHub(
    isServiceRunning: Boolean,
    onToggleService: () -> Unit,
    onNavigate: (SettingsRoute) -> Unit,
) {
    val extended = LocalExtendedColors.current

    val menuItems = listOf(
        MenuEntry(SettingsRoute.APP_LOCK, "App Lock", "Biometric or PIN gate for the app", Icons.Outlined.Lock),
        MenuEntry(SettingsRoute.DIAGNOSTICS, "Speed & Diagnostics", "Peak speed, downtime log, connection history", Icons.Outlined.Speed),
        MenuEntry(SettingsRoute.TRENDS_ALERTS, "Trends & Alerts", "Weekly/monthly trends, spike alerts", Icons.Outlined.BarChart),
        MenuEntry(SettingsRoute.WIFI_RADIO, "Wi-Fi & Radio Reminders", "Low-data nudges, scheduled switch reminders", Icons.Outlined.Wifi),
        MenuEntry(SettingsRoute.APP_BLOCKER, "App Data Blocker", "VPN-based per-app network block", Icons.Outlined.Block),
        MenuEntry(SettingsRoute.BACKUP_RESET, "Backup, Reset & Share", "Export/import, clear history, share summary", Icons.Outlined.Save),
        MenuEntry(SettingsRoute.ADVANCED, "Advanced", "Cost tracking, trusted networks, security heuristics", Icons.Outlined.Tune),
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp),
    ) {
        item {
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
        }

        item {
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
        }

        items(menuItems) { entry ->
            SettingsMenuRow(
                title = entry.title,
                subtitle = entry.subtitle,
                icon = entry.icon,
                onClick = { onNavigate(entry.route) },
            )
        }

        item {
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
}
