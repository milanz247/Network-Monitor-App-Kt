package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AppShapes
import com.example.ui.theme.LocalExtendedColors

@Composable
fun PermissionOnboarding(
    hasUsageAccess: Boolean,
    hasNotificationAccess: Boolean,
    hasPhoneStateAccess: Boolean,
    onGrantUsageAccess: () -> Unit,
    onGrantNotificationAccess: () -> Unit,
    onGrantPhoneStateAccess: () -> Unit,
    onCheckAgain: () -> Unit,
) {
    val extended = LocalExtendedColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(28.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Permissions Required",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "To track real-time internet speed, connection states, and per-app usage, please grant the following permissions:",
            style = MaterialTheme.typography.bodyMedium,
            color = extended.textSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(28.dp))

        Column(
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            PermissionGuideCard(
                title = "1. Usage Access",
                description = "Required to calculate per-app Wi-Fi/Mobile data usage safely.",
                isGranted = hasUsageAccess,
                onClick = onGrantUsageAccess,
            )

            PermissionGuideCard(
                title = "2. Notifications",
                description = "Required to display the live speed dashboard in your system tray.",
                isGranted = hasNotificationAccess,
                onClick = onGrantNotificationAccess,
            )

            PermissionGuideCard(
                title = "3. Phone State",
                description = "Required to show your carrier name alongside mobile data usage.",
                isGranted = hasPhoneStateAccess,
                onClick = onGrantPhoneStateAccess,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onCheckAgain,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("check_permission_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = AppShapes.card,
        ) {
            Text(
                text = "I HAVE GRANTED ALL",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun PermissionGuideCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit,
) {
    val extended = LocalExtendedColors.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isGranted) { onClick() },
        shape = AppShapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (isGranted) extended.success.copy(alpha = 0.5f) else extended.cardBorder),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = extended.textSecondary,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.ArrowForward,
                contentDescription = null,
                tint = if (isGranted) extended.success else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
