package com.example.domain.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Result of a device biometric-capability check, distinct enough for the Settings UI to explain why an option is disabled. */
enum class BiometricAvailability {
    AVAILABLE,
    NO_HARDWARE,
    HARDWARE_UNAVAILABLE,
    NONE_ENROLLED,
    UNSUPPORTED,
}

/**
 * Thin wrapper around [androidx.biometric.BiometricPrompt] (Phase 0). [authenticate] is a hook
 * point for a future lock-screen Composable to call - it isn't invoked anywhere yet since no lock
 * screen UI exists in this phase, and [BiometricPrompt] requires a [FragmentActivity] host (the
 * current `MainActivity` extends plain `ComponentActivity`; hosting the lock screen will need a
 * `FragmentActivity`, or an activity-embedded Fragment, to actually show the OS prompt).
 */
object BiometricAuthenticator {

    fun checkAvailability(context: Context): BiometricAvailability {
        val manager = BiometricManager.from(context)
        return when (manager.canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.HARDWARE_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
            else -> BiometricAvailability.UNSUPPORTED
        }
    }

    fun isAvailable(context: Context): Boolean = checkAvailability(context) == BiometricAvailability.AVAILABLE

    fun authenticate(
        activity: FragmentActivity,
        title: String = "Unlock Net Monitor",
        onSuccess: () -> Unit,
        onError: (errorCode: Int, message: CharSequence) -> Unit,
        onFailed: () -> Unit = {},
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errorCode, errString)
                }

                override fun onAuthenticationFailed() {
                    onFailed()
                }
            },
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setNegativeButtonText("Use PIN instead")
            .build()
        prompt.authenticate(promptInfo)
    }
}
