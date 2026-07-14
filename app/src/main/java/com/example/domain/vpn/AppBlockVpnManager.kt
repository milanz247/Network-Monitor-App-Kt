package com.example.domain.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.example.data.prefs.AppBlockPreferences
import com.example.vpn.LocalVpnBlockerService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 3 (#12) - the ViewModel-facing entry point for VPN-based app blocking. See
 * [com.example.vpn.LocalVpnBlockerService] for how blocking actually works and its platform limitations.
 */
@Singleton
class AppBlockVpnManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AppBlockPreferences,
    vpnBlockerState: VpnBlockerState,
) {
    val isRunning: StateFlow<Boolean> = vpnBlockerState.isRunning

    /** Null if VPN consent was already granted; otherwise an Intent the caller must launch (e.g. via `ActivityResultContracts.StartActivityForResult`). */
    fun prepareIntent(): Intent? = VpnService.prepare(context)

    /** Call only after [prepareIntent] returned null, or the consent activity it launched returned RESULT_OK. No-op if no apps are selected. */
    suspend fun start() {
        val blocked = preferences.currentSettings().blockedPackages
        if (blocked.isEmpty()) return
        context.startService(LocalVpnBlockerService.startIntent(context, blocked.toList()))
    }

    fun stop() {
        context.startService(LocalVpnBlockerService.stopIntent(context))
    }

    suspend fun setBlockedPackages(packages: Set<String>) {
        preferences.setBlockedPackages(packages)
        if (isRunning.value) start() // Live-update the running tunnel's allow-list.
    }
}
