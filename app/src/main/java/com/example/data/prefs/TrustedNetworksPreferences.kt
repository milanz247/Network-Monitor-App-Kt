package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.trustedNetworksDataStore: DataStore<Preferences> by preferencesDataStore(name = "trusted_networks_settings")

/**
 * Phase 5 (#19) - the user-maintained "trusted Wi-Fi" list. An empty list means "no opinion yet" -
 * [com.example.domain.security.RogueWifiDetector] deliberately does not flag anything until the
 * user has added at least one trusted SSID, so a fresh install doesn't immediately warn about every network.
 */
@Singleton
class TrustedNetworksPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val TRUSTED_SSIDS = stringSetPreferencesKey("trusted_ssids")
    }

    val trustedSsidsFlow: Flow<Set<String>> = context.trustedNetworksDataStore.data.map { it[Keys.TRUSTED_SSIDS] ?: emptySet() }

    suspend fun currentTrustedSsids(): Set<String> = trustedSsidsFlow.first()

    suspend fun addTrusted(ssid: String) {
        context.trustedNetworksDataStore.edit { prefs -> prefs[Keys.TRUSTED_SSIDS] = (prefs[Keys.TRUSTED_SSIDS] ?: emptySet()) + ssid }
    }

    suspend fun removeTrusted(ssid: String) {
        context.trustedNetworksDataStore.edit { prefs -> prefs[Keys.TRUSTED_SSIDS] = (prefs[Keys.TRUSTED_SSIDS] ?: emptySet()) - ssid }
    }
}
