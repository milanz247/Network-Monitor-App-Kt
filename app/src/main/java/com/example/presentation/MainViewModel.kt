package com.example.presentation

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.detector.NetworkDetector
import com.example.data.local.DailyDataUsage
import com.example.data.local.DataUsageDao
import com.example.data.repository.DataUsageRepository
import com.example.domain.model.AppUsageItem
import com.example.domain.model.ConnectionState
import com.example.domain.model.SpeedData
import com.example.domain.repository.SpeedRepository
import com.example.presentation.permission.PermissionHelper
import com.example.service.NetworkSpeedService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkDetector: NetworkDetector,
    private val speedRepository: SpeedRepository,
    private val dataUsageDao: DataUsageDao,
    private val dataUsageRepository: DataUsageRepository
) : ViewModel() {

    // Permissions State
    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted.asStateFlow()

    // Real-Time Speed Flow
    val realTimeSpeed: StateFlow<SpeedData> = speedRepository.currentSpeed

    // Connection State Flow
    val connectionState: StateFlow<ConnectionState> = networkDetector.observeConnectionState()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ConnectionState()
        )

    // Today's Usage State
    private val _todayUsage = MutableStateFlow<DailyDataUsage?>(null)
    val todayUsage: StateFlow<DailyDataUsage?> = _todayUsage.asStateFlow()

    // Today's Top App Usages
    private val _topApps = MutableStateFlow<List<AppUsageItem>>(emptyList())
    val topApps: StateFlow<List<AppUsageItem>> = _topApps.asStateFlow()

    // Active Service State
    private val _isServiceRunning = MutableStateFlow(true)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    init {
        checkPermissions()
        refreshUsageData()
    }

    fun checkPermissions() {
        val granted = PermissionHelper.areAllPermissionsGranted(context)
        _permissionsGranted.value = granted
        if (granted) {
            startSpeedService()
            refreshUsageData()
        }
    }

    fun startSpeedService() {
        try {
            val intent = Intent(context, NetworkSpeedService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            _isServiceRunning.value = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopSpeedService() {
        try {
            val intent = Intent(context, NetworkSpeedService::class.java)
            context.stopService(intent)
            _isServiceRunning.value = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun refreshUsageData() {
        if (!PermissionHelper.hasUsageStatsPermission(context)) return

        viewModelScope.launch {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startOfDay = calendar.timeInMillis
            val dateEpochDay = startOfDay / 86400000L

            // Get historical app usage from repo
            val currentAppsUsage = dataUsageRepository.fetchAppUsageForPeriod(
                startTime = startOfDay,
                endTime = System.currentTimeMillis()
            ).sortedByDescending { it.wifiBytes + it.mobileBytes }

            _topApps.value = currentAppsUsage

            // Calculate total today summary
            val totalWifi = currentAppsUsage.sumOf { it.wifiBytes }
            val totalMobile = currentAppsUsage.sumOf { it.mobileBytes }

            _todayUsage.value = DailyDataUsage(
                date = dateEpochDay,
                wifiBytes = totalWifi,
                mobileBytes = totalMobile,
                carrierName = connectionState.value.carrierName
            )
        }
    }
}
