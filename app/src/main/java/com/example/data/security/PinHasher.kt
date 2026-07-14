package com.example.data.security

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2-based salted PIN hashing. Only the hash (plus its salt) is ever persisted - see
 * [com.example.data.prefs.AppLockPreferences] - so a leaked prefs file can't be reversed back to
 * the PIN directly, and two users with the same PIN never produce the same stored value.
 */
object PinHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val SALT_LENGTH_BYTES = 16

    fun generateSalt(): ByteArray = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }

    fun hash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        val hashBytes = factory.generateSecret(spec).encoded
        return Base64.encodeToString(hashBytes, Base64.NO_WRAP)
    }

    /** Constant-time comparison so verification timing can't leak how much of the PIN was correct. */
    fun matches(pin: String, salt: ByteArray, expectedHash: String): Boolean {
        val actualHash = hash(pin, salt)
        if (actualHash.length != expectedHash.length) return false
        var diff = 0
        for (i in actualHash.indices) diff = diff or (actualHash[i].code xor expectedHash[i].code)
        return diff == 0
    }
}
