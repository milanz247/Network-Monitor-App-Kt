package com.example.data.prefs

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.data.security.PinHasher
import com.example.domain.model.AppLockSettings
import com.example.domain.model.UnlockMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-lock settings and the salted PIN hash (Phase 0), backed by [EncryptedSharedPreferences]
 * (Jetpack Security Crypto) rather than plain DataStore like [DataCapPreferences] - this file can
 * hold a PIN hash, so it gets AES-256 encryption at rest as a second layer on top of the hash
 * itself never being the plaintext PIN (see [PinHasher]).
 */
@Singleton
class AppLockPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "app_lock_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private object Keys {
        const val LOCK_ENABLED = "lock_enabled"
        const val UNLOCK_METHOD = "unlock_method"
        const val AUTO_LOCK_DELAY_SECONDS = "auto_lock_delay_seconds"
        const val PIN_HASH = "pin_hash"
        const val PIN_SALT = "pin_salt"
    }

    private val _settingsFlow = MutableStateFlow(readSettings())
    /** Cheap in-memory mirror of the encrypted store - avoids hitting disk on every collector. */
    val settingsFlow: StateFlow<AppLockSettings> = _settingsFlow.asStateFlow()

    private fun readSettings(): AppLockSettings = AppLockSettings(
        lockEnabled = prefs.getBoolean(Keys.LOCK_ENABLED, false),
        unlockMethod = prefs.getString(Keys.UNLOCK_METHOD, null)
            ?.let { runCatching { UnlockMethod.valueOf(it) }.getOrNull() }
            ?: UnlockMethod.PIN,
        autoLockDelaySeconds = prefs.getInt(Keys.AUTO_LOCK_DELAY_SECONDS, DEFAULT_AUTO_LOCK_DELAY_SECONDS),
        hasPinConfigured = prefs.contains(Keys.PIN_HASH),
    )

    private fun refresh() {
        _settingsFlow.value = readSettings()
    }

    fun setLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(Keys.LOCK_ENABLED, enabled).apply()
        refresh()
    }

    fun setUnlockMethod(method: UnlockMethod) {
        prefs.edit().putString(Keys.UNLOCK_METHOD, method.name).apply()
        refresh()
    }

    fun setAutoLockDelaySeconds(seconds: Int) {
        prefs.edit().putInt(Keys.AUTO_LOCK_DELAY_SECONDS, seconds.coerceAtLeast(0)).apply()
        refresh()
    }

    fun setPin(pin: String) {
        val salt = PinHasher.generateSalt()
        val hash = PinHasher.hash(pin, salt)
        prefs.edit()
            .putString(Keys.PIN_HASH, hash)
            .putString(Keys.PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .apply()
        refresh()
    }

    fun clearPin() {
        prefs.edit().remove(Keys.PIN_HASH).remove(Keys.PIN_SALT).apply()
        refresh()
    }

    fun verifyPin(pin: String): Boolean {
        val hash = prefs.getString(Keys.PIN_HASH, null) ?: return false
        val saltEncoded = prefs.getString(Keys.PIN_SALT, null) ?: return false
        val salt = Base64.decode(saltEncoded, Base64.NO_WRAP)
        return PinHasher.matches(pin, salt, hash)
    }

    companion object {
        const val DEFAULT_AUTO_LOCK_DELAY_SECONDS = 30
    }
}
