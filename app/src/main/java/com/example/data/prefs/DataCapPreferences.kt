package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.DataCapSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataCapDataStore: DataStore<Preferences> by preferencesDataStore(name = "data_cap_settings")

/**
 * Small, infrequently-written settings - DataStore Preferences rather than a Room table/entity,
 * since this is a single logical settings blob with no relations and no query needs.
 */
@Singleton
class DataCapPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val MONTHLY_CAP_BYTES = longPreferencesKey("monthly_cap_bytes")
        val CAP_ENABLED = booleanPreferencesKey("cap_enabled")
        val CARRIER_SPECIFIC = booleanPreferencesKey("carrier_specific_mode")
        val BILLING_CYCLE_START_DAY = intPreferencesKey("billing_cycle_start_day")
        val PREDICTION_ALERT_DAYS_THRESHOLD = intPreferencesKey("prediction_alert_days_threshold")

        /** Entries shaped "<cycleKey>:<threshold>", e.g. "2026-07-15:80" (Feature 1). */
        val ALERTED_THRESHOLDS = stringSetPreferencesKey("alerted_thresholds")

        /** The single cycleKey the depletion-prediction alert has already fired for (Feature 2). */
        val PREDICTION_ALERTED_CYCLE_KEY = stringSetPreferencesKey("prediction_alerted_cycle_key")
    }

    val settingsFlow: Flow<DataCapSettings> = context.dataCapDataStore.data.map { prefs ->
        DataCapSettings(
            monthlyCapBytes = prefs[Keys.MONTHLY_CAP_BYTES] ?: DEFAULT_CAP_BYTES,
            capEnabled = prefs[Keys.CAP_ENABLED] ?: false,
            carrierSpecificMode = prefs[Keys.CARRIER_SPECIFIC] ?: true,
            billingCycleStartDay = prefs[Keys.BILLING_CYCLE_START_DAY] ?: 1,
            predictionAlertDaysThreshold = prefs[Keys.PREDICTION_ALERT_DAYS_THRESHOLD] ?: DEFAULT_PREDICTION_ALERT_DAYS,
        )
    }

    suspend fun updateSettings(
        monthlyCapBytes: Long? = null,
        capEnabled: Boolean? = null,
        carrierSpecificMode: Boolean? = null,
        billingCycleStartDay: Int? = null,
        predictionAlertDaysThreshold: Int? = null,
    ) {
        context.dataCapDataStore.edit { prefs ->
            monthlyCapBytes?.let { prefs[Keys.MONTHLY_CAP_BYTES] = it }
            capEnabled?.let { prefs[Keys.CAP_ENABLED] = it }
            carrierSpecificMode?.let { prefs[Keys.CARRIER_SPECIFIC] = it }
            billingCycleStartDay?.let { prefs[Keys.BILLING_CYCLE_START_DAY] = it.coerceIn(1, 31) }
            predictionAlertDaysThreshold?.let { prefs[Keys.PREDICTION_ALERT_DAYS_THRESHOLD] = it.coerceAtLeast(1) }
        }
    }

    /** Thresholds (80/95/100) already alerted for [cycleKey] - lets the worker fire each one exactly once per cycle. */
    suspend fun getAlertedThresholds(cycleKey: String): Set<Int> {
        val raw = context.dataCapDataStore.data.first()[Keys.ALERTED_THRESHOLDS] ?: emptySet()
        return raw.filter { it.startsWith("$cycleKey:") }
            .mapNotNull { it.substringAfter(":").toIntOrNull() }
            .toSet()
    }

    suspend fun markThresholdAlerted(cycleKey: String, threshold: Int) {
        context.dataCapDataStore.edit { prefs ->
            val current = prefs[Keys.ALERTED_THRESHOLDS] ?: emptySet()
            // Prune old-cycle entries so this set can't grow forever across months.
            val prunedToCurrentCycle = current.filter { it.startsWith("$cycleKey:") }.toSet()
            prefs[Keys.ALERTED_THRESHOLDS] = prunedToCurrentCycle + "$cycleKey:$threshold"
        }
    }

    suspend fun hasFiredPredictionAlert(cycleKey: String): Boolean {
        val current = context.dataCapDataStore.data.first()[Keys.PREDICTION_ALERTED_CYCLE_KEY] ?: emptySet()
        return cycleKey in current
    }

    suspend fun markPredictionAlerted(cycleKey: String) {
        // Only the current cycle's key is ever meaningful, so this always collapses to a single entry.
        context.dataCapDataStore.edit { prefs -> prefs[Keys.PREDICTION_ALERTED_CYCLE_KEY] = setOf(cycleKey) }
    }

    companion object {
        private const val DEFAULT_CAP_BYTES = 0L // 0 == "no cap configured yet"
        private const val DEFAULT_PREDICTION_ALERT_DAYS = 3
    }
}
