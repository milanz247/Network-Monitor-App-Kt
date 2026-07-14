package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AppShapes
import com.example.ui.theme.LocalExtendedColors
import com.example.ui.util.formatBytes
import java.util.Locale

@Composable
fun UsageStatsGrid(
    wifiBytes: Long,
    mobileBytes: Long,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UsageStatCard(
            title = "Mobile Data",
            valueText = formatBytes(mobileBytes),
            subtext = "Daily budget: 2.0 GB",
            progress = (mobileBytes / (2.0 * 1024 * 1024 * 1024)).toFloat().coerceIn(0f, 1f),
            accent = LocalExtendedColors.current.mobileAccent,
            modifier = Modifier.weight(1f).testTag("mobile_usage_card"),
        )

        UsageStatCard(
            title = "Wi-Fi Data",
            valueText = formatBytes(wifiBytes),
            subtext = "Daily average: 3.2 GB",
            progress = (wifiBytes / (3.2 * 1024 * 1024 * 1024)).toFloat().coerceIn(0f, 1f),
            accent = LocalExtendedColors.current.wifiAccent,
            modifier = Modifier.weight(1f).testTag("wifi_usage_card"),
        )
    }
}

@Composable
fun UsageStatCard(
    title: String,
    valueText: String,
    subtext: String,
    progress: Float,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 600),
        label = "usage_progress",
    )

    Card(
        modifier = modifier,
        shape = AppShapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, LocalExtendedColors.current.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = title.uppercase(Locale.US),
                style = MaterialTheme.typography.labelMedium,
                color = LocalExtendedColors.current.textMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = valueText,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtext,
                style = MaterialTheme.typography.bodySmall,
                color = LocalExtendedColors.current.textSecondary,
            )
            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = accent,
                trackColor = LocalExtendedColors.current.cardBorder,
            )
        }
    }
}
