package com.example.domain.repository

import com.example.domain.model.SpeedData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton repository that holds the real-time speed data updated by the NetworkSpeedService.
 * This allows ViewModels across the application to observe the latest network speed seamlessly.
 */
@Singleton
class SpeedRepository @Inject constructor() {
    private val _currentSpeed = MutableStateFlow(SpeedData())
    val currentSpeed: StateFlow<SpeedData> = _currentSpeed.asStateFlow()

    fun updateSpeed(downloadBps: Long, uploadBps: Long) {
        _currentSpeed.value = SpeedData(downloadBps, uploadBps)
    }
}
