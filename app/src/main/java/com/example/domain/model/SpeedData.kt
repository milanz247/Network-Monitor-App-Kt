package com.example.domain.model

/**
 * Domain model representing real-time network speed in bytes per second.
 */
data class SpeedData(
    val downloadBps: Long = 0,
    val uploadBps: Long = 0
)
