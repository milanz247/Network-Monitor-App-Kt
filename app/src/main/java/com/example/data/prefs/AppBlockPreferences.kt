package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appBlockDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_block_settings")

data class AppBlockSettings(
    val blockedPackages: Set<String>,
    val autoBlockOnLowBalance: Boolean,
    val lowBalanceThresholdPercent: Int,
)

/** Phase 3 (#12) - which apps are selected for VPN-based blocking, and the optional auto-trigger threshold. */
@Singleton
class AppBlockPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val BLOCKED_PACKAGES = stringSetPreferencesKey("blocked_packages")
        val AUTO_BLOCK_ON_LOW_BALANCE = booleanPreferencesKey("auto_block_on_low_balance")
        val LOW_BALANCE_THRESHOLD_PERCENT = intPreferencesKey("low_balance_threshold_percent")
    }

    val settingsFlow: Flow<AppBlockSettings> = context.appBlockDataStore.data.map { prefs ->
        AppBlockSettings(
            blockedPackages = prefs[Keys.BLOCKED_PACKAGES] ?: emptySet(),
            autoBlockOnLowBalance = prefs[Keys.AUTO_BLOCK_ON_LOW_BALANCE] ?: false,
            lowBalanceThresholdPercent = prefs[Keys.LOW_BALANCE_THRESHOLD_PERCENT] ?: DEFAULT_THRESHOLD_PERCENT,
        )
    }

    suspend fun currentSettings(): AppBlockSettings = settingsFlow.first()

    suspend fun setBlockedPackages(packages: Set<String>) {
        context.appBlockDataStore.edit { prefs -> prefs[Keys.BLOCKED_PACKAGES] = packages }
    }

    suspend fun addBlockedPackage(packageName: String) {
        context.appBlockDataStore.edit { prefs ->
            prefs[Keys.BLOCKED_PACKAGES] = (prefs[Keys.BLOCKED_PACKAGES] ?: emptySet()) + packageName
        }
    }

    suspend fun removeBlockedPackage(packageName: String) {
        context.appBlockDataStore.edit { prefs ->
            prefs[Keys.BLOCKED_PACKAGES] = (prefs[Keys.BLOCKED_PACKAGES] ?: emptySet()) - packageName
        }
    }

    suspend fun updateAutoBlock(enabled: Boolean? = null, thresholdPercent: Int? = null) {
        context.appBlockDataStore.edit { prefs ->
            enabled?.let { prefs[Keys.AUTO_BLOCK_ON_LOW_BALANCE] = it }
            thresholdPercent?.let { prefs[Keys.LOW_BALANCE_THRESHOLD_PERCENT] = it.coerceIn(1, 100) }
        }
    }

    companion object {
        private const val DEFAULT_THRESHOLD_PERCENT = 95
    }
}
