package com.example.domain.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 3 (#12) - shared running-state between [com.example.vpn.LocalVpnBlockerService] (which
 * writes it) and [AppBlockVpnManager] / any ViewModel (which reads it) - mirrors the
 * [com.example.domain.repository.SpeedRepository] pattern already used for the speed service.
 */
@Singleton
class VpnBlockerState @Inject constructor() {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    fun setRunning(running: Boolean) {
        _isRunning.value = running
    }
}
