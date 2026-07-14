package com.example.data.detector

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 1 (#5) - classifies the active connection as one of "WIFI" / "2G" / "3G" / "4G" / "5G" / "NONE".
 *
 * PLATFORM NOTE: most real-world "5G" is 5G NSA (non-standalone), where
 * [TelephonyManager.getDataNetworkType] still reports LTE - the only public signal that
 * distinguishes NSA 5G from plain LTE is [TelephonyDisplayInfo.getOverrideNetworkType]. There is
 * no synchronous "current display info" getter; it requires a persistent listener -
 * [PhoneStateListener.onDisplayInfoChanged] (added API 30) superseded by
 * [TelephonyCallback.DisplayInfoListener] (API 31+). Below API 30, this can only detect 5G SA
 * (`dataNetworkType == NETWORK_TYPE_NR`, still rare) - 5G NSA is indistinguishable from 4G on
 * API 26-29 with public, non-privileged APIs, so it's reported as "4G" there. This is a genuine
 * platform limitation, not a shortcut.
 */
@Singleton
class ConnectionTypeMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    // TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE - starts as "no override" until the listener below fires.
    private val overrideNetworkType = MutableStateFlow(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE)

    private var telephonyCallback: TelephonyCallback? = null
    private var legacyPhoneStateListener: PhoneStateListener? = null

    /** Registers the (API 30+) display-info listener; no-op below API 30. Call once, e.g. from NetworkSpeedService.onCreate(). */
    fun start() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                    override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                        overrideNetworkType.value = telephonyDisplayInfo.overrideNetworkType
                    }
                }
                telephonyCallback = callback
                telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java", ReplaceWith(""))
                    override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                        overrideNetworkType.value = telephonyDisplayInfo.overrideNetworkType
                    }
                }
                legacyPhoneStateListener = listener
                @Suppress("DEPRECATION")
                telephonyManager.listen(listener, PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED)
            }
        } catch (e: SecurityException) {
            Log.w("ConnectionTypeMonitor", "READ_PHONE_STATE missing - 5G NSA detection unavailable", e)
        }
    }

    fun stop() {
        telephonyCallback?.let { telephonyManager.unregisterTelephonyCallback(it) }
        legacyPhoneStateListener?.let {
            @Suppress("DEPRECATION")
            telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
        }
        telephonyCallback = null
        legacyPhoneStateListener = null
    }

    /** Synchronous classification for the current moment - cheap enough to call from a polling loop (see NetworkSpeedService). */
    fun classify(): String {
        val activeNetwork = connectivityManager.activeNetwork ?: return "NONE"
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return "NONE"
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "WIFI"
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "NONE"

        val override = overrideNetworkType.value
        if (override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA ||
            override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED
        ) {
            return "5G"
        }

        val dataNetworkType = try {
            telephonyManager.dataNetworkType
        } catch (e: SecurityException) {
            return "4G" // READ_PHONE_STATE missing - fall back to the common case rather than guessing wrong.
        }
        return generationFor(dataNetworkType)
    }

    private fun generationFor(networkType: Int): String = when (networkType) {
        TelephonyManager.NETWORK_TYPE_NR -> "5G"
        TelephonyManager.NETWORK_TYPE_LTE, TelephonyManager.NETWORK_TYPE_LTE_CA, TelephonyManager.NETWORK_TYPE_IWLAN -> "4G"
        TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_EVDO_0,
        TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyManager.NETWORK_TYPE_EHRPD,
        -> "3G"
        TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_CDMA,
        TelephonyManager.NETWORK_TYPE_1xRTT, TelephonyManager.NETWORK_TYPE_IDEN,
        -> "2G"
        else -> "4G" // Unknown network type - default to the common case rather than mislabeling as 2G.
    }
}
