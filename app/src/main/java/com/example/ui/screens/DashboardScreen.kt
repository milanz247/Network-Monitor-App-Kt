package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.model.AppUsageItem
import com.example.domain.model.ConnectionState
import com.example.domain.model.SpeedData
import com.example.ui.components.AppUsageListItem
import com.example.ui.components.SpeedHeroCard
import com.example.ui.components.UsageStatsGrid
import com.example.ui.theme.LocalExtendedColors

@Composable
fun DashboardScreen(
    isServiceRunning: Boolean,
    speedData: SpeedData,
    connectionState: ConnectionState,
    wifiBytes: Long,
    mobileBytes: Long,
    topApps: List<AppUsageItem>,
    onToggleService: () -> Unit,
    onRefresh: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
    ) {
        item {
            DashboardHeader(
                isServiceRunning = isServiceRunning,
                onToggleService = onToggleService,
                onRefresh = onRefresh,
            )
        }

        item {
            SpeedHeroCard(speedData = speedData, connectionState = connectionState)
        }

        item {
            UsageStatsGrid(wifiBytes = wifiBytes, mobileBytes = mobileBytes)
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Top App Usage",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "REFRESH",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { onRefresh() }
                        .padding(4.dp),
                )
            }
        }

        if (topApps.isEmpty()) {
            item { EmptyUsageState(text = "No app usage stats tracked yet.\nTap refresh or wait for sync.") }
        } else {
            items(topApps.take(6)) { app -> AppUsageListItem(app = app) }
        }
    }
}

@Composable
private fun DashboardHeader(
    isServiceRunning: Boolean,
    onToggleService: () -> Unit,
    onRefresh: () -> Unit,
) {
    val extended = LocalExtendedColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "ACTIVE MONITORING",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Network Signal",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .size(40.dp)
                    .background(extended.cardBorder, CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh data",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp),
                )
            }

            IconButton(
                onClick = onToggleService,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (isServiceRunning) MaterialTheme.colorScheme.primaryContainer else extended.cardBorder,
                        CircleShape,
                    )
                    .testTag("service_toggle_button"),
            ) {
                Icon(
                    imageVector = if (isServiceRunning) Icons.Default.PlayArrow else Icons.Default.Close,
                    contentDescription = "Toggle tracker",
                    tint = if (isServiceRunning) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
fun EmptyUsageState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = LocalExtendedColors.current.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
