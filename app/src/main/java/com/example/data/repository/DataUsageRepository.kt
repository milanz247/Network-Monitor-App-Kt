package com.example.data.repository

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.data.local.AppDataUsage
import com.example.data.local.DataUsageDao
import com.example.domain.model.AppUsageItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Repository responsible for fetching network stats from the system and saving them to the database.
 */
class DataUsageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DataUsageDao
) {
    private val networkStatsManager =
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
    private val packageManager = context.packageManager

    /**
     * Fetches per-app usage for the given time period directly from the system NetworkStatsManager.
     * Handles Missing permission gracefully by returning an empty list.
     * @param startTime Start of the period (epoch ms).
     * @param endTime End of the period (epoch ms).
     */
    suspend fun fetchAppUsageForPeriod(startTime: Long, endTime: Long): List<AppUsageItem> =
        withContext(Dispatchers.IO) {
            val usageList = mutableListOf<AppUsageItem>()
            try {
                val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

                // Group packages by UID, as NetworkStatsManager queries by UID (shared UIDs handled).
                val appsByUid = installedApps.groupBy { it.uid }

                for ((uid, apps) in appsByUid) {
                    val packageName = apps.first().packageName
                    val appName = apps.first().loadLabel(packageManager).toString()
                    
                    var wifiBytes = 0L
                    var mobileBytes = 0L

                    // Wi-Fi Usage
                    try {
                        val wifiStats = networkStatsManager.queryDetailsForUid(
                            NetworkCapabilities.TRANSPORT_WIFI,
                            null,
                            startTime,
                            endTime,
                            uid
                        )
                        val bucket = NetworkStats.Bucket()
                        while (wifiStats.hasNextBucket()) {
                            wifiStats.getNextBucket(bucket)
                            wifiBytes += bucket.rxBytes + bucket.txBytes
                        }
                        wifiStats.close()
                    } catch (e: Exception) {
                        Log.w("DataUsageRepo", "Failed to get wifi stats for UID $uid", e)
                    }

                    // Mobile Usage
                    try {
                        val mobileStats = networkStatsManager.queryDetailsForUid(
                            NetworkCapabilities.TRANSPORT_CELLULAR,
                            null,
                            startTime,
                            endTime,
                            uid
                        )
                        val bucket = NetworkStats.Bucket()
                        while (mobileStats.hasNextBucket()) {
                            mobileStats.getNextBucket(bucket)
                            mobileBytes += bucket.rxBytes + bucket.txBytes
                        }
                        mobileStats.close()
                    } catch (e: Exception) {
                        Log.w("DataUsageRepo", "Failed to get mobile stats for UID $uid", e)
                    }

                    if (wifiBytes > 0 || mobileBytes > 0) {
                        usageList.add(
                            AppUsageItem(
                                packageName = packageName,
                                appName = appName,
                                uid = uid,
                                wifiBytes = wifiBytes,
                                mobileBytes = mobileBytes
                            )
                        )
                    }
                }
            } catch (e: SecurityException) {
                // The permission PACKAGE_USAGE_STATS was revoked mid-session or wasn't granted
                Log.e("DataUsageRepo", "SecurityException: Usage access denied", e)
            } catch (e: Exception) {
                Log.e("DataUsageRepo", "Exception fetching app usage", e)
            }

            return@withContext usageList
        }

    /**
     * Persists the app usage into the database for a specific date.
     * @param date The epoch day for which the usage is recorded.
     * @param usageList The list of usage items fetched from fetchAppUsageForPeriod.
     */
    suspend fun saveAppUsage(date: Long, usageList: List<AppUsageItem>) {
        withContext(Dispatchers.IO) {
            val entities = usageList.map {
                AppDataUsage(
                    date = date,
                    packageName = it.packageName,
                    appName = it.appName,
                    uid = it.uid,
                    wifiBytes = it.wifiBytes,
                    mobileBytes = it.mobileBytes
                )
            }
            dao.insertAppDataUsage(entities)
        }
    }
}
