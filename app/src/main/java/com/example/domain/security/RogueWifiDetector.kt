package com.example.domain.security

/**
 * Phase 5 (#19) - pure classification against the user-maintained trusted-network list, no
 * Android framework dependency - see [com.example.service.NetworkSpeedService] for where the
 * actual SSID/capability reads happen and this gets called.
 */
object RogueWifiDetector {
    /** Only flags when [trustedSsids] is non-empty - see TrustedNetworksPreferences doc for why an empty list never flags. */
    fun isUntrusted(currentSsid: String?, trustedSsids: Set<String>): Boolean {
        if (trustedSsids.isEmpty() || currentSsid.isNullOrBlank()) return false
        return currentSsid !in trustedSsids
    }
}
