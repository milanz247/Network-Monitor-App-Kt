package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.spikeAlertDataStore: DataStore<Preferences> by preferencesDataStore(name = "spike_alert_settings")

data class SpikeAlertSettings(
    val enabled: Boolean,
    /** Today's total must exceed the 7-day rolling average by this multiple to count as a spike. */
    val multiplier: Float,
)

/** Phase 2 (#9) - simple ratio-based spike detection settings (see UsageSpikeCheckWorker doc for why not a full model). */
@Singleton
class SpikeAlertPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val ENABLED = booleanPreferencesKey("spike_alert_enabled")
        val MULTIPLIER = floatPreferencesKey("spike_alert_multiplier")
        val LAST_ALERTED_DATE_KEY = stringPreferencesKey("spike_alert_last_alerted_date_key")
    }

    val settingsFlow: Flow<SpikeAlertSettings> = context.spikeAlertDataStore.data.map { prefs ->
        SpikeAlertSettings(
            enabled = prefs[Keys.ENABLED] ?: true,
            multiplier = prefs[Keys.MULTIPLIER] ?: DEFAULT_MULTIPLIER,
        )
    }

    suspend fun updateSettings(enabled: Boolean? = null, multiplier: Float? = null) {
        context.spikeAlertDataStore.edit { prefs ->
            enabled?.let { prefs[Keys.ENABLED] = it }
            multiplier?.let { prefs[Keys.MULTIPLIER] = it.coerceAtLeast(1.1f) }
        }
    }

    /** [dateKey] is the epoch-day being checked - lets the worker fire at most once per day even though it re-runs every 4h. */
    suspend fun hasAlertedToday(dateKey: Long): Boolean =
        context.spikeAlertDataStore.data.first()[Keys.LAST_ALERTED_DATE_KEY] == dateKey.toString()

    suspend fun markAlertedToday(dateKey: Long) {
        context.spikeAlertDataStore.edit { prefs -> prefs[Keys.LAST_ALERTED_DATE_KEY] = dateKey.toString() }
    }

    companion object {
        private const val DEFAULT_MULTIPLIER = 3.0f
    }
}
