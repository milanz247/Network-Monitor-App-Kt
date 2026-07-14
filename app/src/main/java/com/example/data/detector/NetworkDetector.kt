package com.example.data.detector

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.example.domain.model.ConnectionState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

/**
 * One active SIM as reported by [SubscriptionManager] (Feature 5). This enumeration works on any
 * regular (non-privileged) app - it does not require the IMSI, unlike per-SIM byte attribution.
 */
data class SimInfo(
    val subscriptionId: Int,
    val carrierName: String?,
    val isDefaultData: Boolean,
)

/**
 * Detects the real-time active network connection and fetches carrier information.
 * Uses ConnectivityManager.NetworkCallback to observe changes.
 */
class NetworkDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val subscriptionManager =
        context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    /**
     * Observes the current connection state.
     * Note on VPN: If VPN is active, Android creates a separate virtual network. 
     * In this detector, we consider the underlying transport (Wi-Fi or Cellular) if we can determine it,
     * but usually NetworkCapabilities for VPN will also have the TRANSPORT_VPN capability. 
     * Here, we just check if it has TRANSPORT_WIFI or TRANSPORT_CELLULAR directly.
     * Assumption: VPN usage is billed to the underlying active transport, so we attribute it to whichever
     * transport has internet capability.
     */
    fun observeConnectionState(): Flow<ConnectionState> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                val state = determineState(networkCapabilities)
                trySend(state)
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                trySend(ConnectionState()) // No connection
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        // Send initial state
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        if (caps != null) {
            trySend(determineState(caps))
        } else {
            trySend(ConnectionState())
        }

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    /**
     * Enumerates active SIMs for Feature 5 (Dual-SIM). Requires only READ_PHONE_STATE (already
     * declared in the manifest) - deliberately does NOT touch [TelephonyManager.getSubscriberId],
     * which needs READ_PRIVILEGED_PHONE_STATE and throws SecurityException for regular apps.
     */
    fun getActiveSims(): List<SimInfo> {
        return try {
            val defaultDataSubId = SubscriptionManager.getDefaultDataSubscriptionId()
            subscriptionManager.activeSubscriptionInfoList.orEmpty().map { info ->
                SimInfo(
                    subscriptionId = info.subscriptionId,
                    carrierName = info.carrierName?.toString(),
                    isDefaultData = info.subscriptionId == defaultDataSubId,
                )
            }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    private fun determineState(capabilities: NetworkCapabilities): ConnectionState {
        val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isMobile = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        
        var carrierName: String? = null
        var subId: Int? = null

        if (isMobile) {
            try {
                // Fetch the default data subscription ID which is used for mobile data.
                val defaultDataSubId = SubscriptionManager.getDefaultDataSubscriptionId()
                if (defaultDataSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    val activeSubscriptionInfo = subscriptionManager.activeSubscriptionInfoList?.find {
                        it.subscriptionId == defaultDataSubId
                    }
                    if (activeSubscriptionInfo != null) {
                        subId = activeSubscriptionInfo.subscriptionId
                        carrierName = activeSubscriptionInfo.carrierName?.toString()
                    }
                }
            } catch (e: SecurityException) {
                // READ_PHONE_STATE permission might be missing
            } catch (e: Exception) {
                // Fallback or ignore
            }
            
            // Fallback to TelephonyManager if SubscriptionManager fails to get a name
            if (carrierName.isNullOrBlank()) {
                carrierName = telephonyManager.networkOperatorName
            }
        }

        return ConnectionState(
            isWifi = isWifi,
            isMobile = isMobile,
            carrierName = carrierName,
            subscriptionId = subId
        )
    }
}
