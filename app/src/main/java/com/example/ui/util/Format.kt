package com.example.ui.util

import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}

fun formatBytesSpeed(bytesPerSec: Long): String {
    if (bytesPerSec < 1024) return "$bytesPerSec B/s"
    val kb = bytesPerSec / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB/s", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.1f MB/s", mb)
}
