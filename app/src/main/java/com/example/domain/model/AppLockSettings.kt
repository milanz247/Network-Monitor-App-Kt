package com.example.domain.model

/** Which factor unlocks the app when the lock screen is showing. */
enum class UnlockMethod { BIOMETRIC, PIN }

/**
 * User-configurable app-lock settings (Phase 0). [hasPinConfigured] reflects whether a PIN hash
 * currently exists in [com.example.data.prefs.AppLockPreferences] - callers can offer BIOMETRIC
 * only when the device supports it, but PIN always needs one to be set first via `setPin`.
 */
data class AppLockSettings(
    val lockEnabled: Boolean,
    val unlockMethod: UnlockMethod,
    val autoLockDelaySeconds: Int,
    val hasPinConfigured: Boolean,
)
