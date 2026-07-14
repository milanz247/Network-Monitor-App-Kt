package com.example.domain.model

/**
 * Represents the current connection state of the device.
 * @param isWifi True if the active connection is Wi-Fi.
 * @param isMobile True if the active connection is Mobile Data.
 * @param carrierName Name of the active carrier (if mobile data is active).
 * @param subscriptionId The subscription ID for the active mobile data SIM (if applicable).
 */
data class ConnectionState(
    val isWifi: Boolean = false,
    val isMobile: Boolean = false,
    val carrierName: String? = null,
    val subscriptionId: Int? = null
)
