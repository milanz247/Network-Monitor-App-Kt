package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.service.NetworkSpeedService
import com.example.worker.WorkScheduler

/**
 * Receiver that restarts the NetworkSpeedService and re-enqueues WorkManager jobs
 * on device boot and app update.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            
            // Restart Foreground Service
            val serviceIntent = Intent(context, NetworkSpeedService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            // Re-enqueue WorkManager jobs
            WorkScheduler.scheduleDailyUsageWorker(context)
        }
    }
}
