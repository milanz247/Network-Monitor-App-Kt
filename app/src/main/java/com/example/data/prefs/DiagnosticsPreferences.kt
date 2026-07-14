package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.diagnosticsDataStore: DataStore<Preferences> by preferencesDataStore(name = "diagnostics_settings")

data class DiagnosticsSettings(
    val speedHistoryRetentionDays: Int,
    val lowSpeedThresholdBps: Long,
    val lowSpeedSustainedSeconds: Int,
)

/** Phase 1 - retention window and low-speed detection thresholds, all user-tunable. */
@Singleton
class DiagnosticsPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val RETENTION_DAYS = intPreferencesKey("speed_history_retention_days")
        val LOW_SPEED_THRESHOLD_BPS = longPreferencesKey("low_speed_threshold_bps")
        val LOW_SPEED_SUSTAINED_SECONDS = intPreferencesKey("low_speed_sustained_seconds")
    }

    val settingsFlow: Flow<DiagnosticsSettings> = context.diagnosticsDataStore.data.map { prefs ->
        DiagnosticsSettings(
            speedHistoryRetentionDays = prefs[Keys.RETENTION_DAYS] ?: DEFAULT_RETENTION_DAYS,
            lowSpeedThresholdBps = prefs[Keys.LOW_SPEED_THRESHOLD_BPS] ?: DEFAULT_LOW_SPEED_THRESHOLD_BPS,
            lowSpeedSustainedSeconds = prefs[Keys.LOW_SPEED_SUSTAINED_SECONDS] ?: DEFAULT_LOW_SPEED_SUSTAINED_SECONDS,
        )
    }

    suspend fun updateSettings(
        speedHistoryRetentionDays: Int? = null,
        lowSpeedThresholdBps: Long? = null,
        lowSpeedSustainedSeconds: Int? = null,
    ) {
        context.diagnosticsDataStore.edit { prefs ->
            speedHistoryRetentionDays?.let { prefs[Keys.RETENTION_DAYS] = it.coerceIn(1, 90) }
            lowSpeedThresholdBps?.let { prefs[Keys.LOW_SPEED_THRESHOLD_BPS] = it.coerceAtLeast(0) }
            lowSpeedSustainedSeconds?.let { prefs[Keys.LOW_SPEED_SUSTAINED_SECONDS] = it.coerceAtLeast(1) }
        }
    }

    companion object {
        const val DEFAULT_RETENTION_DAYS = 14
        // 50 KB/s - well below any usable streaming/browsing speed, generous enough to avoid false positives.
        const val DEFAULT_LOW_SPEED_THRESHOLD_BPS = 50_000L
        const val DEFAULT_LOW_SPEED_SUSTAINED_SECONDS = 15
    }
}
