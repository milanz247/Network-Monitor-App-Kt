package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AppUsageItem
import com.example.ui.components.AppUsageListItem
import com.example.ui.theme.AppShapes
import com.example.ui.theme.LocalExtendedColors
import com.example.ui.util.formatBytes

@Composable
fun UsageHistoryScreen(
    wifiBytes: Long,
    mobileBytes: Long,
    topApps: List<AppUsageItem>,
    onRefresh: () -> Unit,
) {
    val extended = LocalExtendedColors.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
    ) {
        item {
            Column {
                Text(
                    text = "DETAILED STATS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Usage History",
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
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "TOTAL DATA CONSUMED TODAY",
                        style = MaterialTheme.typography.labelMedium,
                        color = extended.textMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = formatBytes(wifiBytes + mobileBytes),
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 32.sp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(text = "Wi-Fi Data", style = MaterialTheme.typography.bodySmall, color = extended.textSecondary)
                            Text(text = formatBytes(wifiBytes), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column {
                            Text(text = "Mobile Data", style = MaterialTheme.typography.bodySmall, color = extended.textSecondary)
                            Text(text = formatBytes(mobileBytes), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "All Applications",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh list",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }

        if (topApps.isEmpty()) {
            item { EmptyUsageState(text = "No recorded app usage stats yet.") }
        } else {
            items(topApps) { app -> AppUsageListItem(app = app) }
        }
    }
}
