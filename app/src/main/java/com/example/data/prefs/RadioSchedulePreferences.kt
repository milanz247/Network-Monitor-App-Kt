package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.radioScheduleDataStore: DataStore<Preferences> by preferencesDataStore(name = "radio_schedule_settings")

enum class TargetRadio { WIFI, MOBILE }

data class RadioScheduleSettings(
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    val targetRadio: TargetRadio,
)

/**
 * Phase 3 (#11) - a single daily reminder time to switch radios.
 *
 * PLATFORM LIMITATION: there is no public API to silently toggle Wi-Fi or mobile data on modern
 * (non-rooted, non-Device-Owner) Android - `WifiManager.setWifiEnabled()` has been a no-op for
 * third-party apps since API 29, and there is no public mobile-data toggle at all. This can only
 * ever be a reminder notification deep-linking to system settings, never a silent switch - see
 * [com.example.worker.RadioScheduleReminderWorker].
 */
@Singleton
class RadioSchedulePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val ENABLED = booleanPreferencesKey("radio_schedule_enabled")
        val HOUR = intPreferencesKey("radio_schedule_hour")
        val MINUTE = intPreferencesKey("radio_schedule_minute")
        val TARGET_RADIO = stringPreferencesKey("radio_schedule_target")
    }

    val settingsFlow: Flow<RadioScheduleSettings> = context.radioScheduleDataStore.data.map { prefs ->
        RadioScheduleSettings(
            enabled = prefs[Keys.ENABLED] ?: false,
            hour = prefs[Keys.HOUR] ?: 9,
            minute = prefs[Keys.MINUTE] ?: 0,
            targetRadio = prefs[Keys.TARGET_RADIO]?.let { runCatching { TargetRadio.valueOf(it) }.getOrNull() } ?: TargetRadio.WIFI,
        )
    }

    suspend fun currentSettings(): RadioScheduleSettings = settingsFlow.first()

    suspend fun updateSettings(enabled: Boolean? = null, hour: Int? = null, minute: Int? = null, targetRadio: TargetRadio? = null) {
        context.radioScheduleDataStore.edit { prefs ->
            enabled?.let { prefs[Keys.ENABLED] = it }
            hour?.let { prefs[Keys.HOUR] = it.coerceIn(0, 23) }
            minute?.let { prefs[Keys.MINUTE] = it.coerceIn(0, 59) }
            targetRadio?.let { prefs[Keys.TARGET_RADIO] = it.name }
        }
    }
}
