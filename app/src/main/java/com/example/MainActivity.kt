package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.example.presentation.MainViewModel
import com.example.presentation.permission.PermissionHelper
import com.example.ui.components.CustomBottomBar
import com.example.ui.components.PermissionOnboarding
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.UsageHistoryScreen
import com.example.ui.theme.MyApplicationTheme
import dagger.hilt.android.AndroidEntryPoint

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
        // Re-check permissions when user returns from settings or a permission dialog.
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

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.checkPermissions()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("main_scaffold"),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (permissionsGranted) {
                CustomBottomBar(selectedTab = selectedTab, onTabSelected = { selectedTab = it })
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (!permissionsGranted) {
                PermissionOnboarding(
                    hasUsageAccess = PermissionHelper.hasUsageStatsPermission(context),
                    hasNotificationAccess = PermissionHelper.hasNotificationPermission(context),
                    hasPhoneStateAccess = PermissionHelper.hasPhoneStatePermission(context),
                    onGrantUsageAccess = {
                        context.startActivity(PermissionHelper.getUsageStatsIntent(context))
                    },
                    onGrantNotificationAccess = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            runtimePermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                        } else {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        }
                    },
                    onGrantPhoneStateAccess = {
                        runtimePermissionLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE))
                    },
                    onCheckAgain = { viewModel.checkPermissions() },
                )
            } else {
                AnimatedContent(
                    targetState = selectedTab,
                    label = "tab_transition",
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                ) { tab ->
                    when (tab) {
                        0 -> DashboardScreen(
                            isServiceRunning = isServiceRunning,
                            speedData = speedData,
                            connectionState = connectionState,
                            wifiBytes = todayUsage?.wifiBytes ?: 0L,
                            mobileBytes = todayUsage?.mobileBytes ?: 0L,
                            topApps = topApps,
                            onToggleService = {
                                if (isServiceRunning) viewModel.stopSpeedService() else viewModel.startSpeedService()
                            },
                            onRefresh = { viewModel.refreshUsageData() },
                        )
                        1 -> UsageHistoryScreen(
                            wifiBytes = todayUsage?.wifiBytes ?: 0L,
                            mobileBytes = todayUsage?.mobileBytes ?: 0L,
                            topApps = topApps,
                            onRefresh = { viewModel.refreshUsageData() },
                        )
                        else -> SettingsScreen(
                            isServiceRunning = isServiceRunning,
                            onToggleService = {
                                if (isServiceRunning) viewModel.stopSpeedService() else viewModel.startSpeedService()
                            },
                        )
                    }
                }
            }
        }
    }
}
