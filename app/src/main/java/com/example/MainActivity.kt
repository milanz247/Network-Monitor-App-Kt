package com.example

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AppUsageItem
import com.example.domain.model.ConnectionState
import com.example.domain.model.SpeedData
import com.example.presentation.MainViewModel
import com.example.presentation.permission.PermissionHelper
import com.example.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check permissions when user returns from settings
        viewModel.checkPermissions()
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val permissionsGranted by viewModel.permissionsGranted.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val speedData by viewModel.realTimeSpeed.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val todayUsage by viewModel.todayUsage.collectAsState()
    val topApps by viewModel.topApps.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("main_scaffold"),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            CustomBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!permissionsGranted) {
                PermissionOnboarding(
                    onCheckAgain = { viewModel.checkPermissions() }
                )
            } else {
                when (selectedTab) {
                    0 -> DashboardTab(
                        isServiceRunning = isServiceRunning,
                        speedData = speedData,
                        connectionState = connectionState,
                        wifiBytes = todayUsage?.wifiBytes ?: 0L,
                        mobileBytes = todayUsage?.mobileBytes ?: 0L,
                        topApps = topApps,
                        onToggleService = {
                            if (isServiceRunning) viewModel.stopSpeedService() else viewModel.startSpeedService()
                        },
                        onRefresh = { viewModel.refreshUsageData() }
                    )
                    1 -> UsageHistoryTab(
                        wifiBytes = todayUsage?.wifiBytes ?: 0L,
                        mobileBytes = todayUsage?.mobileBytes ?: 0L,
                        topApps = topApps,
                        onRefresh = { viewModel.refreshUsageData() }
                    )
                    2 -> SettingsTab(
                        isServiceRunning = isServiceRunning,
                        onToggleService = {
                            if (isServiceRunning) viewModel.stopSpeedService() else viewModel.startSpeedService()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardTab(
    isServiceRunning: Boolean,
    speedData: SpeedData,
    connectionState: ConnectionState,
    wifiBytes: Long,
    mobileBytes: Long,
    topApps: List<AppUsageItem>,
    onToggleService: () -> Unit,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        // Header
        item {
            HeaderSection(
                isServiceRunning = isServiceRunning,
                onToggleService = onToggleService,
                onRefresh = onRefresh
            )
        }

        // Current Speed Card (Clean Minimal Theme: Rich Blue Card)
        item {
            SpeedHeroCard(
                speedData = speedData,
                connectionState = connectionState
            )
        }

        // Row of Stats (Mobile & Wi-Fi Cards)
        item {
            UsageStatsGrid(
                wifiBytes = wifiBytes,
                mobileBytes = mobileBytes
            )
        }

        // Top App Usage List
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Top App Usage",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnBackgroundLight
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "REFRESH",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = BluePrimary,
                    modifier = Modifier
                        .clickable { onRefresh() }
                        .padding(4.dp)
                )
            }
        }

        if (topApps.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No App Usage stats tracked yet.\nTap Refresh or wait for sync.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            items(topApps.take(6)) { app ->
                AppUsageListItem(app = app)
            }
        }
    }
}

@Composable
fun HeaderSection(
    isServiceRunning: Boolean,
    onToggleService: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "ACTIVE MONITORING",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = BluePrimary,
                letterSpacing = 1.5.sp
            )
            Text(
                text = "Network Signal",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnBackgroundLight
            )
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Refresh Button
            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .size(40.dp)
                    .background(BorderLight, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh data",
                    tint = OnBackgroundLight,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Power toggle status icon
            IconButton(
                onClick = onToggleService,
                modifier = Modifier
                    .size(40.dp)
                    .background(if (isServiceRunning) BlueContainer else BorderLight, CircleShape)
                    .testTag("service_toggle_button")
            ) {
                Icon(
                    imageVector = if (isServiceRunning) Icons.Default.PlayArrow else Icons.Default.Close,
                    contentDescription = "Toggle tracker",
                    tint = if (isServiceRunning) BlueOnContainer else OnBackgroundLight,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun SpeedHeroCard(
    speedData: SpeedData,
    connectionState: ConnectionState
) {
    // Elegant Blue Card representing active speed monitoring
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .testTag("speed_hero_card"),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = BluePrimary),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "CURRENT SPEED",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.8f),
                        letterSpacing = 1.sp
                    )
                    
                    // Display Main Speed
                    val speedMbps = (speedData.downloadBps * 8) / 1000000.0
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.1f", speedMbps),
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = (-1).sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Mbps",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
                
                // Active Carrier / connection type badge
                val badgeText = when {
                    connectionState.isWifi -> "Wi-Fi Connected"
                    connectionState.isMobile -> "${connectionState.carrierName ?: "Mobile"}"
                    else -> "No Internet"
                }
                
                Box(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            
            // Speed breakdown
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 0.5.dp, color = Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp))
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Download Speed breakdown
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Download icon",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "DOWNLOAD",
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatBytesSpeed(speedData.downloadBps),
                            fontSize = 13.sp,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Upload Speed breakdown
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Upload icon",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "UPLOAD",
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatBytesSpeed(speedData.uploadBps),
                            fontSize = 13.sp,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UsageStatsGrid(
    wifiBytes: Long,
    mobileBytes: Long
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Mobile Data Card
        UsageStatCard(
            title = "Mobile Data",
            valueText = formatBytes(mobileBytes),
            subtext = "Daily Limit: 2.0 GB",
            progress = (mobileBytes / (2.0 * 1024 * 1024 * 1024)).toFloat().coerceIn(0f, 1f),
            modifier = Modifier.weight(1f).testTag("mobile_usage_card")
        )

        // Wi-Fi Data Card
        UsageStatCard(
            title = "Wi-Fi Data",
            valueText = formatBytes(wifiBytes),
            subtext = "Daily Average: 3.2 GB",
            progress = (wifiBytes / (3.2 * 1024 * 1024 * 1024)).toFloat().coerceIn(0f, 1f),
            modifier = Modifier.weight(1f).testTag("wifi_usage_card")
        )
    }
}

@Composable
fun UsageStatCard(
    title: String,
    valueText: String,
    subtext: String,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title.uppercase(Locale.US),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = valueText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = BluePrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtext,
                fontSize = 10.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            // Linear Progress Indicator
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = BluePrimary,
                trackColor = BorderLight
            )
        }
    }
}

@Composable
fun AppUsageListItem(app: AppUsageItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(width = 0.5.dp, color = BorderLight, shape = RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Simple avatar / placeholder icon matching the Clean Minimalism look
        val isWifiDominant = app.wifiBytes > app.mobileBytes
        val iconColor = if (isWifiDominant) Color(0xFF2196F3) else Color(0xFFFF5722)
        val iconBackground = if (isWifiDominant) Color(0xFFE3F2FD) else Color(0xFFFFEBEE)

        Box(
            modifier = Modifier
                .size(40.dp)
                .background(iconBackground, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isWifiDominant) Icons.Default.Wifi else Icons.Default.Phone,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.appName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnBackgroundLight,
                maxLines = 1
            )
            Text(
                text = if (isWifiDominant) "Wi-Fi Connection" else "Cellular Connection",
                fontSize = 10.sp,
                color = TextSecondary
            )
        }

        Text(
            text = formatBytes(app.wifiBytes + app.mobileBytes),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = OnBackgroundLight
        )
    }
}

@Composable
fun CustomBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        color = Color.White,
        border = BorderStroke(width = 0.5.dp, color = BorderLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(
                icon = Icons.Outlined.Home,
                selectedIcon = Icons.Filled.Home,
                label = "Dashboard",
                isSelected = selectedTab == 0,
                onClick = { onTabSelected(0) }
            )
            BottomNavItem(
                icon = Icons.Outlined.Info,
                selectedIcon = Icons.Filled.Info,
                label = "Usage",
                isSelected = selectedTab == 1,
                onClick = { onTabSelected(1) }
            )
            BottomNavItem(
                icon = Icons.Outlined.Settings,
                selectedIcon = Icons.Filled.Settings,
                label = "Settings",
                isSelected = selectedTab == 2,
                onClick = { onTabSelected(2) }
            )
        }
    }
}

@Composable
fun BottomNavItem(
    icon: ImageVector,
    selectedIcon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color = if (isSelected) BluePrimary else TextSecondary
    
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isSelected) selectedIcon else icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label.uppercase(Locale.US),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun UsageHistoryTab(
    wifiBytes: Long,
    mobileBytes: Long,
    topApps: List<AppUsageItem>,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Column {
                Text(
                    text = "DETAILED STATS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = BluePrimary,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "Usage History",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnBackgroundLight
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BorderLight)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "TOTAL DATA CONSUMED TODAY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = formatBytes(wifiBytes + mobileBytes),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = BluePrimary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "Wi-Fi Data", fontSize = 11.sp, color = TextSecondary)
                            Text(text = formatBytes(wifiBytes), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Column {
                            Text(text = "Mobile Data", fontSize = 11.sp, color = TextSecondary)
                            Text(text = formatBytes(mobileBytes), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "All Applications",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnBackgroundLight
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh list")
                }
            }
        }

        if (topApps.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No recorded app usage stats yet.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }
        } else {
            items(topApps) { app ->
                AppUsageListItem(app = app)
            }
        }
    }
}

@Composable
fun SettingsTab(
    isServiceRunning: Boolean,
    onToggleService: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column {
            Text(
                text = "PREFERENCES",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = BluePrimary,
                letterSpacing = 1.5.sp
            )
            Text(
                text = "Settings",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnBackgroundLight
            )
        }

        // Toggle Speed Service Setting Item
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, BorderLight)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Real-Time Speed Tracking",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnBackgroundLight
                    )
                    Text(
                        text = "Keeps a low-priority foreground notification alive to measure speed.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = isServiceRunning,
                    onCheckedChange = { onToggleService() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = BluePrimary,
                        uncheckedThumbColor = BorderLight,
                        uncheckedTrackColor = Color.White
                    )
                )
            }
        }

        // Information panel
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, BorderLight)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "System Integration Info",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnBackgroundLight
                )
                Text(
                    text = "• Local Storage: Room Database encrypted locally\n• Battery Optimization: Automatically slows down speed tracking updates when screen is Off\n• Permissions: Requires PACKAGE_USAGE_STATS permission for per-app reports",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun PermissionOnboarding(onCheckAgain: () -> Unit) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(BlueContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = BluePrimary,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Permissions Required",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = OnBackgroundLight
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "To track real-time internet speed, connection states, and per-app usage, please grant the following permissions:",
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Guide cards
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            PermissionGuideCard(
                title = "1. Usage Access Permission",
                description = "Required to calculate per-app Wi-Fi/Mobile data usage safely.",
                isGranted = PermissionHelper.hasUsageStatsPermission(context),
                onClick = {
                    context.startActivity(PermissionHelper.getUsageStatsIntent(context))
                }
            )

            PermissionGuideCard(
                title = "2. Notification Permission",
                description = "Required to display the live speed dashboard in your system tray.",
                isGranted = PermissionHelper.hasNotificationPermission(context),
                onClick = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        // Launch settings panel as a fallback or if needed
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onCheckAgain,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("check_permission_button"),
            colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text(
                text = "I HAVE GRANTED ALL",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color.White
            )
        }
    }
}

@Composable
fun PermissionGuideCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (!isGranted) onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, if (isGranted) Color(0xFF4CAF50).copy(alpha = 0.5f) else BorderLight)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnBackgroundLight
                )
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.ArrowForward,
                contentDescription = null,
                tint = if (isGranted) Color(0xFF4CAF50) else BluePrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}

private fun formatBytesSpeed(bytesPerSec: Long): String {
    if (bytesPerSec < 1024) return "$bytesPerSec B/s"
    val kb = bytesPerSec / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB/s", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.1f MB/s", mb)
}
