package com.example.vpn

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.domain.vpn.VpnBlockerState
import dagger.hilt.android.AndroidEntryPoint
import java.io.FileInputStream
import javax.inject.Inject

/**
 * Phase 3 (#12) - VPN-based selective per-app network blocking.
 *
 * There is no public Android API to block a specific app's network access directly (that needs
 * Device Owner / enterprise MDM APIs, or root). The standard workaround - and what this does - is
 * a local "kill switch" VPN: [android.net.VpnService.Builder.addAllowedApplication] restricts the
 * VPN tunnel to *only* the apps the user wants blocked (every other app bypasses the VPN entirely
 * and is completely unaffected), and this service then never forwards anything it reads from the
 * tunnel anywhere - it's a black-hole sink, not a real packet relay. Blocked apps' connections
 * simply time out / never resolve. This deliberately does NOT parse IP/TCP/UDP or inspect traffic
 * (that's the out-of-scope Phase 6 domain-level inspector from the master brief) - it only needs
 * to make packets disappear, which reading-and-discarding already achieves.
 *
 * Requires one-time user consent via [VpnService.prepare] (system dialog) - see `AppBlockVpnManager.prepareIntent()`.
 */
@AndroidEntryPoint
class LocalVpnBlockerService : VpnService() {

    @Inject
    lateinit var vpnBlockerState: VpnBlockerState

    private var vpnInterface: ParcelFileDescriptor? = null
    private var sinkThread: Thread? = null
    @Volatile private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            tearDown()
            return START_NOT_STICKY
        }

        val blockedPackages = intent?.getStringArrayListExtra(EXTRA_BLOCKED_PACKAGES).orEmpty()
        if (blockedPackages.isEmpty()) {
            tearDown()
            return START_NOT_STICKY
        }

        establish(blockedPackages)
        return START_STICKY
    }

    private fun establish(blockedPackages: List<String>) {
        tearDownInterfaceOnly() // Re-establish cleanly if the blocked-app set changed while already running.

        val builder = Builder()
            .setSession("Net Monitor - App Block")
            .addAddress(VPN_LOCAL_ADDRESS, 32)
            .addRoute("0.0.0.0", 0)

        var addedAny = false
        for (packageName in blockedPackages) {
            try {
                builder.addAllowedApplication(packageName)
                addedAny = true
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(TAG, "Skipping blocked package no longer installed: $packageName")
            }
        }
        if (!addedAny) {
            tearDown()
            return
        }

        vpnInterface = try {
            builder.establish()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish VPN interface", e)
            null
        }

        val fd = vpnInterface
        if (fd == null) {
            tearDown()
            return
        }

        running = true
        vpnBlockerState.setRunning(true)
        sinkThread = Thread({ sinkLoop(fd) }, "LocalVpnBlockerSink").apply { start() }
    }

    /** Reads and discards every packet from blocked apps - the actual "blocking" mechanism (see class doc). */
    private fun sinkLoop(fd: ParcelFileDescriptor) {
        val input = FileInputStream(fd.fileDescriptor)
        val buffer = ByteArray(32_767)
        try {
            while (running) {
                if (input.read(buffer) < 0) break
                // Deliberately not forwarded anywhere - see class doc.
            }
        } catch (e: Exception) {
            if (running) Log.w(TAG, "VPN sink loop stopped", e)
        }
    }

    private fun tearDownInterfaceOnly() {
        running = false
        sinkThread?.interrupt()
        sinkThread = null
        vpnInterface?.let { runCatching { it.close() } }
        vpnInterface = null
    }

    private fun tearDown() {
        tearDownInterfaceOnly()
        vpnBlockerState.setRunning(false)
        stopSelf()
    }

    override fun onRevoke() {
        // The user revoked VPN permission from system Settings - clean up rather than leaving stale state.
        tearDown()
        super.onRevoke()
    }

    override fun onDestroy() {
        tearDown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LocalVpnBlockerService"
        private const val VPN_LOCAL_ADDRESS = "10.10.10.10"
        const val ACTION_STOP = "com.example.vpn.action.STOP"
        const val EXTRA_BLOCKED_PACKAGES = "com.example.vpn.extra.BLOCKED_PACKAGES"

        fun startIntent(context: Context, blockedPackages: List<String>): Intent =
            Intent(context, LocalVpnBlockerService::class.java).apply {
                putStringArrayListExtra(EXTRA_BLOCKED_PACKAGES, ArrayList(blockedPackages))
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, LocalVpnBlockerService::class.java).apply { action = ACTION_STOP }
    }
}
