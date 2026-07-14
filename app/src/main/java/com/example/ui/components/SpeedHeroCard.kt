package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.ConnectionState
import com.example.domain.model.SpeedData
import com.example.ui.theme.AppShapes
import com.example.ui.theme.LocalExtendedColors
import com.example.ui.util.formatBytesSpeed
import java.util.Locale

@Composable
fun SpeedHeroCard(
    speedData: SpeedData,
    connectionState: ConnectionState,
) {
    val extended = LocalExtendedColors.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .testTag("speed_hero_card"),
        shape = AppShapes.hero,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(extended.heroGradient)
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LiveDot()
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CURRENT SPEED",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }

                    val speedMbps = (speedData.downloadBps * 8) / 1_000_000.0
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.1f", speedMbps),
                            style = MaterialTheme.typography.displaySmall,
                            color = Color.White,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Mbps",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.9f),
                        )
                    }
                }

                val badgeText = when {
                    connectionState.isWifi -> "Wi-Fi Connected"
                    connectionState.isMobile -> connectionState.carrierName ?: "Mobile"
                    else -> "No Internet"
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.16f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .padding(vertical = 14.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SpeedBreakdown(
                    icon = Icons.Filled.KeyboardArrowDown,
                    label = "DOWNLOAD",
                    value = formatBytesSpeed(speedData.downloadBps),
                    modifier = Modifier.weight(1f),
                )
                SpeedBreakdown(
                    icon = Icons.Filled.KeyboardArrowUp,
                    label = "UPLOAD",
                    value = formatBytesSpeed(speedData.uploadBps),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RowScope.SpeedBreakdown(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = "$label icon",
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun LiveDot() {
    val transition = rememberInfiniteTransition(label = "live_dot")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "live_dot_alpha",
    )
    Column(
        modifier = Modifier
            .size(6.dp)
            .background(Color(0xFF4ADE80).copy(alpha = alpha), CircleShape)
    ) {}
}
