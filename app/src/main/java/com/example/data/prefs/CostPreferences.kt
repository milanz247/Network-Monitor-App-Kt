package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.costDataStore: DataStore<Preferences> by preferencesDataStore(name = "cost_settings")

/** Phase 5 (#16) - user-set cost rates. A rate of 0 means "not configured" - see CostCalculator. */
data class CostSettings(
    val currencySymbol: String,
    val ratePerGbWifi: Float,
    val ratePerGbMobile: Float,
)

/** Phase 5 (#16) - pure calculation on existing byte totals, no new tracking - see CostCalculator. */
@Singleton
class CostPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val CURRENCY_SYMBOL = stringPreferencesKey("cost_currency_symbol")
        val RATE_PER_GB_WIFI = floatPreferencesKey("cost_rate_per_gb_wifi")
        val RATE_PER_GB_MOBILE = floatPreferencesKey("cost_rate_per_gb_mobile")
    }

    val settingsFlow: Flow<CostSettings> = context.costDataStore.data.map { prefs ->
        CostSettings(
            currencySymbol = prefs[Keys.CURRENCY_SYMBOL] ?: "Rs.",
            ratePerGbWifi = prefs[Keys.RATE_PER_GB_WIFI] ?: 0f,
            ratePerGbMobile = prefs[Keys.RATE_PER_GB_MOBILE] ?: 0f,
        )
    }

    suspend fun updateSettings(currencySymbol: String? = null, ratePerGbWifi: Float? = null, ratePerGbMobile: Float? = null) {
        context.costDataStore.edit { prefs ->
            currencySymbol?.let { prefs[Keys.CURRENCY_SYMBOL] = it }
            ratePerGbWifi?.let { prefs[Keys.RATE_PER_GB_WIFI] = it.coerceAtLeast(0f) }
            ratePerGbMobile?.let { prefs[Keys.RATE_PER_GB_MOBILE] = it.coerceAtLeast(0f) }
        }
    }
}
