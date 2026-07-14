package com.example

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.example.domain.security.AppLockManager
import com.example.notification.AppNotificationChannels
import com.example.worker.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NetMonitorApp : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var appLockManager: AppLockManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Ensure daily usage aggregation runs from first install onward, not just after a reboot.
        WorkScheduler.scheduleDailyUsageWorker(this)
        AppNotificationChannels.ensureChannelsCreated(this)

        // Phase 0 (App Lock): ProcessLifecycleOwner only fires onStop/onStart when the whole app
        // process has no visible activity left, so this correctly ignores screen rotations and
        // transient system/permission dialogs - see AppLockManager's class doc. Guarded so a
        // problem here (e.g. an unexpected AppLockManager failure) can never take down app startup.
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    appLockManager.onAppBackgrounded()
                }

                override fun onStart(owner: LifecycleOwner) {
                    appLockManager.onAppForegrounded()
                }
            })
        } catch (e: Exception) {
            android.util.Log.e("NetMonitorApp", "Failed to register app-lock lifecycle observer", e)
        }
    }
}
