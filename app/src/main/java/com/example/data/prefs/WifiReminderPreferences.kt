package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.wifiReminderDataStore: DataStore<Preferences> by preferencesDataStore(name = "wifi_reminder_settings")

data class WifiReminderSettings(
    val enabled: Boolean,
    /** Remind once mobile-data usage in the current billing cycle crosses this % of the data cap. */
    val thresholdPercent: Int,
)

/**
 * Phase 3 (#10) - "your mobile data is getting low, consider Wi-Fi" reminder settings.
 *
 * PLATFORM LIMITATION: Android 10+ restricts non-system apps from scanning for or silently
 * connecting to Wi-Fi networks. This feature can only ever be a notification prompting the user
 * to open Wi-Fi settings themselves - see [com.example.notification.WifiReminderNotifier].
 */
@Singleton
class WifiReminderPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val ENABLED = booleanPreferencesKey("wifi_reminder_enabled")
        val THRESHOLD_PERCENT = intPreferencesKey("wifi_reminder_threshold_percent")
        val LAST_REMINDED_CYCLE_KEY = stringPreferencesKey("wifi_reminder_last_cycle_key")
    }

    val settingsFlow: Flow<WifiReminderSettings> = context.wifiReminderDataStore.data.map { prefs ->
        WifiReminderSettings(
            enabled = prefs[Keys.ENABLED] ?: true,
            thresholdPercent = prefs[Keys.THRESHOLD_PERCENT] ?: DEFAULT_THRESHOLD_PERCENT,
        )
    }

    suspend fun updateSettings(enabled: Boolean? = null, thresholdPercent: Int? = null) {
        context.wifiReminderDataStore.edit { prefs ->
            enabled?.let { prefs[Keys.ENABLED] = it }
            thresholdPercent?.let { prefs[Keys.THRESHOLD_PERCENT] = it.coerceIn(1, 100) }
        }
    }

    /** [cycleKey] from [com.example.domain.billing.BillingCycleCalculator.cycleKey] - one reminder per billing cycle. */
    suspend fun hasRemindedThisCycle(cycleKey: String): Boolean =
        context.wifiReminderDataStore.data.first()[Keys.LAST_REMINDED_CYCLE_KEY] == cycleKey

    suspend fun markRemindedThisCycle(cycleKey: String) {
        context.wifiReminderDataStore.edit { prefs -> prefs[Keys.LAST_REMINDED_CYCLE_KEY] = cycleKey }
    }

    companion object {
        private const val DEFAULT_THRESHOLD_PERCENT = 90
    }
}
