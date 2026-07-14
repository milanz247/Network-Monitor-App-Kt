package com.example.domain.model

/** Whether the app-lock screen should currently be shown over the rest of the UI. */
sealed class LockState {
    data object Locked : LockState()
    data object Unlocked : LockState()
}
