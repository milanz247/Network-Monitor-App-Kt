package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.domain.model.AppUsageItem
import com.example.ui.theme.LocalExtendedColors
import com.example.ui.util.formatBytes

@Composable
fun AppUsageListItem(app: AppUsageItem) {
    val extended = LocalExtendedColors.current
    val isWifiDominant = app.wifiBytes > app.mobileBytes
    val iconColor = if (isWifiDominant) extended.wifiAccent else extended.mobileAccent
    val iconBackground = iconColor.copy(alpha = 0.12f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(width = 1.dp, color = extended.cardBorder, shape = RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(iconBackground, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isWifiDominant) Icons.Default.Wifi else Icons.Default.Phone,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.appName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = if (isWifiDominant) "Wi-Fi connection" else "Cellular connection",
                style = MaterialTheme.typography.bodySmall,
                color = extended.textSecondary,
            )
        }

        Text(
            text = formatBytes(app.wifiBytes + app.mobileBytes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
