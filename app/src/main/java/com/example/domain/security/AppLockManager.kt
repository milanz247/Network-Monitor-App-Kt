package com.example.domain.security

import android.content.Context
import com.example.data.prefs.AppLockPreferences
import com.example.domain.model.AppLockSettings
import com.example.domain.model.LockState
import com.example.domain.model.UnlockMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 0 - App Lock. The "LockRepository" the app's navigation graph / top-level composable
 * observes via [lockState] to decide whether to show a lock-screen overlay.
 *
 * Trigger logic (per the brief):
 *  - Cold app start: [lockState] starts as [LockState.Locked] whenever the lock feature is
 *    enabled (see init), so the very first frame after process death is locked.
 *  - Resume after backgrounding: [onAppBackgrounded] stamps a timestamp; [onAppForegrounded]
 *    only re-locks if at least [AppLockSettings.autoLockDelaySeconds] have elapsed since then.
 *    Both are meant to be driven by `ProcessLifecycleOwner` (see [com.example.NetMonitorApp]),
 *    which only fires onStop/onStart when the whole app process has no visible activity left -
 *    unlike an Activity's onPause/onResume, it does NOT fire across a configuration change
 *    (screen rotation) or when a permission/system dialog briefly covers the app, which is
 *    exactly the "don't re-trigger on trivial resume" behavior the brief asks for.
 */
@Singleton
class AppLockManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AppLockPreferences,
) {
    private val _lockState = MutableStateFlow(initialLockState())
    val lockState: StateFlow<LockState> = _lockState.asStateFlow()

    val settingsFlow: StateFlow<AppLockSettings> = preferences.settingsFlow

    private var backgroundedAtMillis: Long? = null

    private fun initialLockState(): LockState =
        if (preferences.settingsFlow.value.lockEnabled) LockState.Locked else LockState.Unlocked

    /** Call when the whole app process leaves the foreground (see class doc). */
    fun onAppBackgrounded() {
        backgroundedAtMillis = System.currentTimeMillis()
    }

    /** Call when the whole app process returns to the foreground (see class doc). */
    fun onAppForegrounded() {
        val settings = preferences.settingsFlow.value
        if (!settings.lockEnabled) {
            _lockState.value = LockState.Unlocked
            return
        }
        val backgroundedAt = backgroundedAtMillis
        if (backgroundedAt == null) {
            // First foregrounding this process (cold start) - already Locked from initialLockState().
            return
        }
        val elapsedSeconds = (System.currentTimeMillis() - backgroundedAt) / 1000
        if (elapsedSeconds >= settings.autoLockDelaySeconds) {
            _lockState.value = LockState.Locked
        }
        backgroundedAtMillis = null
    }

    /** Called after a successful biometric or PIN check. */
    fun unlock() {
        _lockState.value = LockState.Unlocked
    }

    /** Force a re-lock, e.g. from a manual "lock now" Settings action. */
    fun lock() {
        if (preferences.settingsFlow.value.lockEnabled) {
            _lockState.value = LockState.Locked
        }
    }

    fun isLockEnabled(): Boolean = preferences.settingsFlow.value.lockEnabled

    fun setLockEnabled(enabled: Boolean) {
        preferences.setLockEnabled(enabled)
        // Enabling deliberately does NOT lock the current session - the user is standing in
        // Settings mid-configuration (possibly without a PIN set yet), and slamming the lock
        // screen down on them here would be a lockout trap. The lock takes effect on the next
        // cold start / background-timeout, like every system app lock does.
        if (!enabled) {
            _lockState.value = LockState.Unlocked
        }
    }

    fun setUnlockMethod(method: UnlockMethod) = preferences.setUnlockMethod(method)

    fun setAutoLockDelaySeconds(seconds: Int) = preferences.setAutoLockDelaySeconds(seconds)

    fun setPin(pin: String) = preferences.setPin(pin)

    /** Returns false (and leaves the old PIN untouched) if [oldPin] doesn't verify. */
    fun changePin(oldPin: String, newPin: String): Boolean {
        if (!preferences.verifyPin(oldPin)) return false
        preferences.setPin(newPin)
        return true
    }

    fun verifyPin(pin: String): Boolean = preferences.verifyPin(pin)

    fun biometricAvailability(): BiometricAvailability = BiometricAuthenticator.checkAvailability(context)
}
