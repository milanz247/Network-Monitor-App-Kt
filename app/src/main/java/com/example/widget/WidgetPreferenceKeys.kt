package com.example.widget

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey

/** Keys for the Glance widget's [androidx.glance.state.PreferencesGlanceStateDefinition]-backed state (Feature 3). */
object WidgetPreferenceKeys {
    val DOWNLOAD_BPS = longPreferencesKey("download_bps")
    val UPLOAD_BPS = longPreferencesKey("upload_bps")
    val WIFI_BYTES_TODAY = longPreferencesKey("wifi_bytes_today")
    val MOBILE_BYTES_TODAY = longPreferencesKey("mobile_bytes_today")
    val IS_SERVICE_RUNNING = booleanPreferencesKey("is_service_running")
    val UPDATED_AT_MILLIS = longPreferencesKey("updated_at_millis")
}
