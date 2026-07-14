package com.example.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.backup.BackupRepository
import com.example.data.backup.ImportSummary
import com.example.data.local.ConnectionTypeTransition
import com.example.data.local.DataUsageDao
import com.example.data.local.NetworkDowntimeLog
import com.example.data.prefs.AppBlockPreferences
import com.example.data.prefs.AppBlockSettings
import com.example.data.prefs.CostPreferences
import com.example.data.prefs.CostSettings
import com.example.data.prefs.DiagnosticsPreferences
import com.example.data.prefs.DiagnosticsSettings
import com.example.data.prefs.ExfiltrationHeuristicPreferences
import com.example.data.prefs.ExfiltrationHeuristicSettings
import com.example.data.prefs.RadioSchedulePreferences
import com.example.data.prefs.RadioScheduleSettings
import com.example.data.prefs.SpikeAlertPreferences
import com.example.data.prefs.SpikeAlertSettings
import com.example.data.prefs.TargetRadio
import com.example.data.prefs.TrustedNetworksPreferences
import com.example.data.prefs.WifiReminderPreferences
import com.example.data.prefs.WifiReminderSettings
import com.example.data.repository.DataResetRepository
import com.example.data.repository.DiagnosticsRepository
import com.example.data.repository.DataUsageRepository
import com.example.domain.battery.BatteryDataCorrelationAnalyzer
import com.example.domain.model.BatteryDataCorrelation
import com.example.domain.model.HourlyUsage
import com.example.domain.model.PeakSpeedRecord
import com.example.domain.model.UsageSummaryCardData
import com.example.domain.model.UsageTrendPoint
import com.example.domain.analytics.UsageTrendCalculator
import com.example.domain.model.AppLockSettings
import com.example.domain.model.LockState
import com.example.domain.model.UnlockMethod
import com.example.domain.security.AppLockManager
import com.example.domain.security.BiometricAvailability
import com.example.domain.share.ShareUsageCard
import com.example.domain.util.UsagePeriod
import com.example.domain.vpn.AppBlockVpnManager
import com.example.worker.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Settings-tree ViewModel for everything added in Phases 0-5. Kept separate from [MainViewModel]
 * (which owns Dashboard/History state) since this area's state is large and unrelated to the
 * live-speed/today's-usage state the other tabs need.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLockManager: AppLockManager,
    private val diagnosticsRepository: DiagnosticsRepository,
    private val diagnosticsPreferences: DiagnosticsPreferences,
    private val dataUsageDao: DataUsageDao,
    private val dataUsageRepository: DataUsageRepository,
    private val usageTrendCalculator: UsageTrendCalculator,
    private val spikeAlertPreferences: SpikeAlertPreferences,
    private val wifiReminderPreferences: WifiReminderPreferences,
    private val radioSchedulePreferences: RadioSchedulePreferences,
    private val appBlockPreferences: AppBlockPreferences,
    val appBlockVpnManager: AppBlockVpnManager,
    private val backupRepository: BackupRepository,
    private val dataResetRepository: DataResetRepository,
    private val costPreferences: CostPreferences,
    private val trustedNetworksPreferences: TrustedNetworksPreferences,
    private val exfiltrationHeuristicPreferences: ExfiltrationHeuristicPreferences,
    private val batteryDataCorrelationAnalyzer: BatteryDataCorrelationAnalyzer,
    private val shareUsageCard: ShareUsageCard,
) : ViewModel() {

    // =====================================================================================
    // Phase 0 - App Lock
    // =====================================================================================
    val lockState: StateFlow<LockState> = appLockManager.lockState
    val appLockSettings: StateFlow<AppLockSettings> = appLockManager.settingsFlow

    fun biometricAvailability(): BiometricAvailability = appLockManager.biometricAvailability()
    fun setAppLockEnabled(enabled: Boolean) = appLockManager.setLockEnabled(enabled)
    fun setUnlockMethod(method: UnlockMethod) = appLockManager.setUnlockMethod(method)
    fun setAutoLockDelaySeconds(seconds: Int) = appLockManager.setAutoLockDelaySeconds(seconds)
    fun setPin(pin: String) = appLockManager.setPin(pin)
    fun changePin(oldPin: String, newPin: String): Boolean = appLockManager.changePin(oldPin, newPin)

    // =====================================================================================
    // Phase 1 - Diagnostics
    // =====================================================================================
    val diagnosticsSettings: StateFlow<DiagnosticsSettings> = diagnosticsPreferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_DIAGNOSTICS_SETTINGS)

    private val _peakSpeedRecord = MutableStateFlow<PeakSpeedRecord?>(null)
    val peakSpeedRecord: StateFlow<PeakSpeedRecord?> = _peakSpeedRecord.asStateFlow()

    private val _downtimeLogs = MutableStateFlow<List<NetworkDowntimeLog>>(emptyList())
    val downtimeLogs: StateFlow<List<NetworkDowntimeLog>> = _downtimeLogs.asStateFlow()

    private val _connectionTypeTransitions = MutableStateFlow<List<ConnectionTypeTransition>>(emptyList())
    val connectionTypeTransitions: StateFlow<List<ConnectionTypeTransition>> = _connectionTypeTransitions.asStateFlow()

    fun refreshDiagnostics() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val sevenDaysAgo = now - 7L * 24 * 60 * 60 * 1000
            _peakSpeedRecord.value = diagnosticsRepository.getPeakSpeedRecord(sevenDaysAgo, now)
            _downtimeLogs.value = diagnosticsRepository.getDowntimeLogsForRange(sevenDaysAgo, now).first()
            _connectionTypeTransitions.value = diagnosticsRepository.getConnectionTypeTransitionsForRange(sevenDaysAgo, now).first()
        }
    }

    fun updateDiagnosticsSettings(retentionDays: Int? = null, lowSpeedThresholdBps: Long? = null, lowSpeedSustainedSeconds: Int? = null) {
        viewModelScope.launch {
            diagnosticsPreferences.updateSettings(retentionDays, lowSpeedThresholdBps, lowSpeedSustainedSeconds)
        }
    }

    // =====================================================================================
    // Phase 2 - Trends & spike alerts
    // =====================================================================================
    val spikeAlertSettings: StateFlow<SpikeAlertSettings> = spikeAlertPreferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SpikeAlertSettings(enabled = true, multiplier = 3.0f))

    private val _weeklyTrend = MutableStateFlow<List<UsageTrendPoint>>(emptyList())
    val weeklyTrend: StateFlow<List<UsageTrendPoint>> = _weeklyTrend.asStateFlow()

    private val _monthlyTrend = MutableStateFlow<List<UsageTrendPoint>>(emptyList())
    val monthlyTrend: StateFlow<List<UsageTrendPoint>> = _monthlyTrend.asStateFlow()

    private val _todayHourlyBreakdown = MutableStateFlow<List<HourlyUsage>>(emptyList())
    val todayHourlyBreakdown: StateFlow<List<HourlyUsage>> = _todayHourlyBreakdown.asStateFlow()

    fun refreshTrends() {
        viewModelScope.launch {
            _weeklyTrend.value = usageTrendCalculator.getWeeklyTrend()
            _monthlyTrend.value = usageTrendCalculator.getMonthlyTrend()
            val todayStart = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            _todayHourlyBreakdown.value = dataUsageRepository.getHourlyBreakdownForDate(todayStart)
        }
    }

    fun setSpikeAlertEnabled(enabled: Boolean) = viewModelScope.launch { spikeAlertPreferences.updateSettings(enabled = enabled) }
    fun setSpikeAlertMultiplier(multiplier: Float) = viewModelScope.launch { spikeAlertPreferences.updateSettings(multiplier = multiplier) }

    // =====================================================================================
    // Phase 3a - Wi-Fi reminder + radio schedule
    // =====================================================================================
    val wifiReminderSettings: StateFlow<WifiReminderSettings> = wifiReminderPreferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WifiReminderSettings(enabled = true, thresholdPercent = 90))

    val radioScheduleSettings: StateFlow<RadioScheduleSettings> = radioSchedulePreferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RadioScheduleSettings(false, 9, 0, TargetRadio.WIFI))

    fun setWifiReminderEnabled(enabled: Boolean) = viewModelScope.launch { wifiReminderPreferences.updateSettings(enabled = enabled) }
    fun setWifiReminderThreshold(percent: Int) = viewModelScope.launch { wifiReminderPreferences.updateSettings(thresholdPercent = percent) }

    fun setRadioSchedule(enabled: Boolean, hour: Int, minute: Int, targetRadio: TargetRadio) {
        viewModelScope.launch {
            radioSchedulePreferences.updateSettings(enabled = enabled, hour = hour, minute = minute, targetRadio = targetRadio)
            WorkScheduler.scheduleRadioReminder(context, hour, minute, enabled)
        }
    }

    // =====================================================================================
    // Phase 3b - VPN app blocker
    // =====================================================================================
    val appBlockSettings: StateFlow<AppBlockSettings> = appBlockPreferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppBlockSettings(emptySet(), false, 95))

    fun vpnPrepareIntent() = appBlockVpnManager.prepareIntent()
    fun startAppBlocking() = viewModelScope.launch { appBlockVpnManager.start() }
    fun stopAppBlocking() = appBlockVpnManager.stop()
    fun addBlockedPackage(packageName: String) = viewModelScope.launch { appBlockPreferences.addBlockedPackage(packageName) }
    fun removeBlockedPackage(packageName: String) = viewModelScope.launch { appBlockPreferences.removeBlockedPackage(packageName) }
    fun setAutoBlock(enabled: Boolean? = null, thresholdPercent: Int? = null) =
        viewModelScope.launch { appBlockPreferences.updateAutoBlock(enabled, thresholdPercent) }

    // =====================================================================================
    // Phase 4 - Backup / restore / reset / share
    // =====================================================================================
    private val _lastBackupResult = MutableStateFlow<String?>(null)
    val lastBackupResult: StateFlow<String?> = _lastBackupResult.asStateFlow()

    private val _resetPreviewCount = MutableStateFlow<Int?>(null)
    val resetPreviewCount: StateFlow<Int?> = _resetPreviewCount.asStateFlow()

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val result = backupRepository.exportTo(uri)
            _lastBackupResult.value = result.fold(
                onSuccess = { "Exported $it rows." },
                onFailure = { "Export failed: ${it.message}" },
            )
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val result = backupRepository.importFrom(uri)
            _lastBackupResult.value = result.fold(
                onSuccess = { s: ImportSummary -> "Imported ${s.totalRows} rows." },
                onFailure = { "Import failed: ${it.message}" },
            )
        }
    }

    /** Step 1 of the reset contract - populates [resetPreviewCount], nothing is deleted yet. */
    fun previewResetAll() {
        viewModelScope.launch { _resetPreviewCount.value = dataResetRepository.previewDeleteCount() }
    }

    /** Step 2 - only call after the user has seen [resetPreviewCount] and confirmed. */
    fun confirmResetAll() {
        viewModelScope.launch {
            dataResetRepository.confirmDelete()
            _resetPreviewCount.value = null
        }
    }

    fun clearResetPreview() {
        _resetPreviewCount.value = null
    }

    private val _shareIntent = MutableStateFlow<Intent?>(null)
    /** Set once card generation finishes - the Composable should launch it then call [clearShareIntent]. */
    val shareIntent: StateFlow<Intent?> = _shareIntent.asStateFlow()

    fun generateShareCard() {
        viewModelScope.launch {
            val period = UsagePeriod.thisMonth()
            val rows = dataUsageDao.getDailyDataUsageForRange(period.first, period.last).first()
            val wifiBytes = rows.sumOf { it.wifiBytes }
            val mobileBytes = rows.sumOf { it.mobileBytes }
            val topApp = dataUsageRepository.getTopAppsForPeriod(period.first, period.last, limit = 1).first().firstOrNull()
            val data = UsageSummaryCardData(
                periodLabel = "This month",
                totalBytes = wifiBytes + mobileBytes,
                wifiBytes = wifiBytes,
                mobileBytes = mobileBytes,
                topAppName = topApp?.appName,
                topAppBytes = topApp?.totalBytes ?: 0L,
            )
            _shareIntent.value = shareUsageCard.buildShareIntent(data)
        }
    }

    fun clearShareIntent() {
        _shareIntent.value = null
    }

    // =====================================================================================
    // Phase 5 - Cost, trusted networks, exfiltration heuristic, battery correlation
    // =====================================================================================
    val costSettings: StateFlow<CostSettings> = costPreferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CostSettings("Rs.", 0f, 0f))

    fun updateCostSettings(currencySymbol: String? = null, ratePerGbWifi: Float? = null, ratePerGbMobile: Float? = null) =
        viewModelScope.launch { costPreferences.updateSettings(currencySymbol, ratePerGbWifi, ratePerGbMobile) }

    val trustedSsids: StateFlow<Set<String>> = trustedNetworksPreferences.trustedSsidsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun addTrustedSsid(ssid: String) = viewModelScope.launch { trustedNetworksPreferences.addTrusted(ssid) }
    fun removeTrustedSsid(ssid: String) = viewModelScope.launch { trustedNetworksPreferences.removeTrusted(ssid) }

    val exfiltrationHeuristicSettings: StateFlow<ExfiltrationHeuristicSettings> = exfiltrationHeuristicPreferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExfiltrationHeuristicSettings(true, 20L * 1024 * 1024))

    fun setExfiltrationHeuristicEnabled(enabled: Boolean) =
        viewModelScope.launch { exfiltrationHeuristicPreferences.updateSettings(enabled = enabled) }

    private val _batteryCorrelation = MutableStateFlow<BatteryDataCorrelation?>(null)
    val batteryCorrelation: StateFlow<BatteryDataCorrelation?> = _batteryCorrelation.asStateFlow()

    fun refreshBatteryCorrelation() {
        viewModelScope.launch {
            val today = LocalDate.now()
            _batteryCorrelation.value = batteryDataCorrelationAnalyzer.correlateForRange(today.minusDays(6), today)
        }
    }

    companion object {
        private val DEFAULT_DIAGNOSTICS_SETTINGS = DiagnosticsSettings(
            speedHistoryRetentionDays = DiagnosticsPreferences.DEFAULT_RETENTION_DAYS,
            lowSpeedThresholdBps = DiagnosticsPreferences.DEFAULT_LOW_SPEED_THRESHOLD_BPS,
            lowSpeedSustainedSeconds = DiagnosticsPreferences.DEFAULT_LOW_SPEED_SUSTAINED_SECONDS,
        )
    }
}
