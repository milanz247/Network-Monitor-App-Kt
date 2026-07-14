package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

/** Back-arrow + title row shared by every Settings sub-screen. */
@Composable
fun SettingsSubScreenHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** The bordered card shell used throughout Settings - matches SettingsScreen's existing style. */
@Composable
fun SettingsCard(content: @Composable Column.() -> Unit) {
    val extended = LocalExtendedColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, extended.cardBorder),
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

/** One title+subtitle+switch row, for use inside a [SettingsCard]. */
@Composable
fun SettingsToggleRow(title: String, subtitle: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val extended = LocalExtendedColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = extended.textSecondary)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = extended.cardBorder,
                uncheckedTrackColor = MaterialTheme.colorScheme.surface,
            ),
        )
    }
}

/** A clickable row that opens a Settings sub-screen - the hub's menu items. */
@Composable
fun SettingsMenuRow(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, LocalExtendedColors.current.cardBorder),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = LocalExtendedColors.current.textSecondary)
            }
        }
    }
}
