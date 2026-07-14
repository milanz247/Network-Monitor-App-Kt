package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalExtendedColors
import java.util.Locale

@Composable
fun CustomBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(width = 1.dp, color = LocalExtendedColors.current.cardBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomNavItem(
                icon = Icons.Outlined.Home,
                selectedIcon = Icons.Filled.Home,
                label = "Dashboard",
                isSelected = selectedTab == 0,
                onClick = { onTabSelected(0) },
            )
            BottomNavItem(
                icon = Icons.Outlined.Info,
                selectedIcon = Icons.Filled.Info,
                label = "Usage",
                isSelected = selectedTab == 1,
                onClick = { onTabSelected(1) },
            )
            BottomNavItem(
                icon = Icons.Outlined.Settings,
                selectedIcon = Icons.Filled.Settings,
                label = "Settings",
                isSelected = selectedTab == 2,
                onClick = { onTabSelected(2) },
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    selectedIcon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val color = if (isSelected) MaterialTheme.colorScheme.primary else LocalExtendedColors.current.textSecondary

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (isSelected) selectedIcon else icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label.uppercase(Locale.US),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}
