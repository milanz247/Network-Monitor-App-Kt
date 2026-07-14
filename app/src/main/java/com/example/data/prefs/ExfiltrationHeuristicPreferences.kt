package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.exfiltrationDataStore: DataStore<Preferences> by preferencesDataStore(name = "exfiltration_heuristic_settings")

data class ExfiltrationHeuristicSettings(val enabled: Boolean, val minBackgroundBytes: Long)

/**
 * Phase 5 (#20) - "meaningful background-only usage" heuristic settings. Deliberately named
 * *heuristic* everywhere (settings, notifier copy) - this flags a pattern, not a diagnosis; a
 * chat app's background sync or a podcast app's pre-download look identical to this heuristic.
 */
@Singleton
class ExfiltrationHeuristicPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val ENABLED = booleanPreferencesKey("exfil_heuristic_enabled")
        val MIN_BACKGROUND_BYTES = longPreferencesKey("exfil_heuristic_min_background_bytes")
        val LAST_ALERTED_DATE_KEY = stringPreferencesKey("exfil_heuristic_last_alerted_date_key")
    }

    val settingsFlow: Flow<ExfiltrationHeuristicSettings> = context.exfiltrationDataStore.data.map { prefs ->
        ExfiltrationHeuristicSettings(
            enabled = prefs[Keys.ENABLED] ?: true,
            minBackgroundBytes = prefs[Keys.MIN_BACKGROUND_BYTES] ?: DEFAULT_MIN_BACKGROUND_BYTES,
        )
    }

    suspend fun updateSettings(enabled: Boolean? = null, minBackgroundBytes: Long? = null) {
        context.exfiltrationDataStore.edit { prefs ->
            enabled?.let { prefs[Keys.ENABLED] = it }
            minBackgroundBytes?.let { prefs[Keys.MIN_BACKGROUND_BYTES] = it.coerceAtLeast(0) }
        }
    }

    suspend fun hasAlertedToday(dateKey: Long): Boolean =
        context.exfiltrationDataStore.data.first()[Keys.LAST_ALERTED_DATE_KEY] == dateKey.toString()

    suspend fun markAlertedToday(dateKey: Long) {
        context.exfiltrationDataStore.edit { prefs -> prefs[Keys.LAST_ALERTED_DATE_KEY] = dateKey.toString() }
    }

    companion object {
        // 20 MB - small enough to catch a genuinely chatty background app, large enough that
        // routine OS/Play Services background chatter for a well-behaved app usually stays under it.
        private const val DEFAULT_MIN_BACKGROUND_BYTES = 20L * 1024 * 1024
    }
}
