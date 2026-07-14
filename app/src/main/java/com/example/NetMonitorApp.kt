package com.example

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.notification.AppNotificationChannels
import com.example.worker.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NetMonitorApp : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Ensure daily usage aggregation runs from first install onward, not just after a reboot.
        WorkScheduler.scheduleDailyUsageWorker(this)
        AppNotificationChannels.ensureChannelsCreated(this)
    }
}
