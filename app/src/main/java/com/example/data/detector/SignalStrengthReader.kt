package com.example.data.detector

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 1 (#4) - one-shot synchronous signal reads, cheap enough to call from a polling loop
 * (see NetworkSpeedService) rather than needing a persistent listener.
 *
 * PLATFORM NOTE: [WifiManager.getConnectionInfo] SSID/BSSID are redacted without
 * `ACCESS_FINE_LOCATION` since API 27, but RSSI itself is not - this app deliberately doesn't
 * request location just for a diagnostic RSSI number. [TelephonyManager.getSignalStrength] is a
 * synchronous getter only from API 28 onward; below that there is no public one-shot API (only
 * the deprecated `PhoneStateListener.onSignalStrengthsChanged` push callback), so
 * [readCellularSignalLevel] returns null on API 26-27 - a genuine platform gap, not a shortcut.
 */
@Singleton
class SignalStrengthReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    fun readWifiRssi(): Int? {
        val info = wifiManager?.connectionInfo ?: return null
        return if (info.rssi != WifiManager.UNKNOWN_RSSI) info.rssi else null
    }

    /**
     * Phase 5 (#19) - current Wi-Fi SSID, or null if not on Wi-Fi / unavailable. Subject to the
     * same API 27+ location-permission redaction as RSSI (see class doc) - without
     * `ACCESS_FINE_LOCATION`, this may return `"<unknown ssid>"`, which callers should treat as null.
     */
    fun readCurrentSsid(): String? {
        val ssid = wifiManager?.connectionInfo?.ssid ?: return null
        val unquoted = ssid.removeSurrounding("\"")
        return if (unquoted.isBlank() || unquoted == "<unknown ssid>") null else unquoted
    }

    /** 0 (none/unknown) .. 4 (great), or null if unavailable (see platform note above). */
    fun readCellularSignalLevel(): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return try {
            telephonyManager?.signalStrength?.level
        } catch (e: SecurityException) {
            null // READ_PHONE_STATE missing.
        }
    }
}
