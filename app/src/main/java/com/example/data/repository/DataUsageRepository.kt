package com.example.data.repository

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import android.util.Log
import com.example.data.detector.SimInfo
import com.example.data.local.AppDataUsage
import com.example.data.local.DailyDataUsage
import com.example.data.local.DataUsageDao
import com.example.domain.model.AppUsageItem
import com.example.domain.model.AppUsageRanked
import com.example.domain.model.DualSimUsage
import com.example.domain.model.HourlyUsage
import com.example.domain.model.SimUsage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
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
                    // Phase 2 (#8): background-vs-foreground split, read from the same buckets above
                    // rather than a second query - NetworkStats.Bucket.getState() already carries it.
                    var fgWifiBytes = 0L
                    var bgWifiBytes = 0L
                    var fgMobileBytes = 0L
                    var bgMobileBytes = 0L

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
                            val bucketTotal = bucket.rxBytes + bucket.txBytes
                            wifiBytes += bucketTotal
                            if (bucket.state == NetworkStats.Bucket.STATE_FOREGROUND) {
                                fgWifiBytes += bucketTotal
                            } else {
                                bgWifiBytes += bucketTotal
                            }
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
                            val bucketTotal = bucket.rxBytes + bucket.txBytes
                            mobileBytes += bucketTotal
                            if (bucket.state == NetworkStats.Bucket.STATE_FOREGROUND) {
                                fgMobileBytes += bucketTotal
                            } else {
                                bgMobileBytes += bucketTotal
                            }
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
                                mobileBytes = mobileBytes,
                                foregroundWifiBytes = fgWifiBytes,
                                backgroundWifiBytes = bgWifiBytes,
                                foregroundMobileBytes = fgMobileBytes,
                                backgroundMobileBytes = bgMobileBytes,
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
                    mobileBytes = it.mobileBytes,
                    foregroundWifiBytes = it.foregroundWifiBytes,
                    backgroundWifiBytes = it.backgroundWifiBytes,
                    foregroundMobileBytes = it.foregroundMobileBytes,
                    backgroundMobileBytes = it.backgroundMobileBytes,
                )
            }
            dao.insertAppDataUsage(entities)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Phase 2 (#7) - Most Data-Hungry Hour of Day
    // ---------------------------------------------------------------------------------------

    /**
     * Device-wide (not per-app) usage bucketed into the 24 hours of [dayStartMillis]..+24h.
     *
     * [NetworkStatsManager] has no "give me hourly buckets" query - [querySummaryForDevice] always
     * returns one aggregate for the whole requested range. The documented way to get finer
     * granularity is exactly this: issue one query per window and sum. 24 calls is cheap enough to
     * do on-demand (e.g. when a user opens an hourly-breakdown screen) - not worth a new polling
     * job or a stored `HourlyDataUsage` entity just to avoid it.
     */
    suspend fun getHourlyBreakdownForDate(dayStartMillis: Long): List<HourlyUsage> = withContext(Dispatchers.IO) {
        val hourMillis = 60 * 60 * 1000L
        (0 until 24).map { hour ->
            val hourStart = dayStartMillis + hour * hourMillis
            val hourEnd = (hourStart + hourMillis).coerceAtMost(System.currentTimeMillis())
            if (hourEnd <= hourStart) {
                HourlyUsage(hour = hour, wifiBytes = 0L, mobileBytes = 0L)
            } else {
                val wifiBytes = try {
                    val bucket = networkStatsManager.querySummaryForDevice(NetworkCapabilities.TRANSPORT_WIFI, null, hourStart, hourEnd)
                    bucket.rxBytes + bucket.txBytes
                } catch (e: Exception) {
                    0L
                }
                val mobileBytes = try {
                    val bucket = networkStatsManager.querySummaryForDevice(NetworkCapabilities.TRANSPORT_CELLULAR, null, hourStart, hourEnd)
                    bucket.rxBytes + bucket.txBytes
                } catch (e: Exception) {
                    0L
                }
                HourlyUsage(hour = hour, wifiBytes = wifiBytes, mobileBytes = mobileBytes)
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Feature 5 - Dual-SIM
    // ---------------------------------------------------------------------------------------

    /**
     * Best-effort per-SIM mobile usage for [startTime]..[endTime].
     *
     * PLATFORM RESTRICTION: [NetworkStatsManager] only distinguishes cellular usage per-SIM when
     * queried with that SIM's subscriber id (IMSI). Reading the IMSI via
     * [TelephonyManager.getSubscriberId] requires READ_PRIVILEGED_PHONE_STATE (system/carrier-
     * privileged apps only) since Android 10 - a regular app gets a SecurityException, which is
     * the expected, common case here.
     *
     * Fallback: when precise per-SIM attribution isn't available, the combined device-wide mobile
     * total is attributed entirely to the current default-data SIM ([SimInfo.isDefaultData]), and
     * every other SIM is reported as 0 bytes with [SimUsage.isPreciseMeasurement] = false. This is
     * usually a reasonable approximation because Android generally routes cellular *data* traffic
     * through a single active data SIM at a time even in dual-SIM-standby, so the non-default SIM
     * typically carries voice/SMS only and near-zero data regardless.
     */
    suspend fun fetchMobileUsagePerSim(
        startTime: Long,
        endTime: Long,
        activeSims: List<SimInfo>,
    ): List<SimUsage> = withContext(Dispatchers.IO) {
        if (activeSims.isEmpty()) return@withContext emptyList()

        val preciseResults = activeSims.mapNotNull { sim -> tryPreciseSimUsage(sim, startTime, endTime) }
        if (preciseResults.size == activeSims.size) return@withContext preciseResults

        val combinedMobileBytes = try {
            val bucket = networkStatsManager.querySummaryForDevice(
                NetworkCapabilities.TRANSPORT_CELLULAR,
                null,
                startTime,
                endTime,
            )
            bucket.rxBytes + bucket.txBytes
        } catch (e: Exception) {
            Log.w("DataUsageRepo", "Failed to query combined mobile summary for dual-SIM fallback", e)
            0L
        }

        activeSims.map { sim ->
            SimUsage(
                subscriptionId = sim.subscriptionId,
                carrierName = sim.carrierName,
                mobileBytes = if (sim.isDefaultData) combinedMobileBytes else 0L,
                isPreciseMeasurement = false,
            )
        }
    }

    private fun tryPreciseSimUsage(sim: SimInfo, startTime: Long, endTime: Long): SimUsage? {
        return try {
            val subscriberId = telephonyManager.createForSubscriptionId(sim.subscriptionId).subscriberId
                ?: return null
            val bucket = networkStatsManager.querySummaryForDevice(
                NetworkCapabilities.TRANSPORT_CELLULAR,
                subscriberId,
                startTime,
                endTime,
            )
            SimUsage(
                subscriptionId = sim.subscriptionId,
                carrierName = sim.carrierName,
                mobileBytes = bucket.rxBytes + bucket.txBytes,
                isPreciseMeasurement = true,
            )
        } catch (e: SecurityException) {
            null // Expected: READ_PRIVILEGED_PHONE_STATE not held by this (non-privileged) app.
        } catch (e: Exception) {
            Log.w("DataUsageRepo", "Precise per-SIM query failed for subId=${sim.subscriptionId}", e)
            null
        }
    }

    /** Side-by-side Wi-Fi + per-SIM usage for one date, reading whatever buckets [com.example.worker.DailyUsageWorker] wrote. */
    fun getDualSimComparison(date: Long): Flow<DualSimUsage> =
        dao.getDailyDataUsageRowsForDate(date).map { rows ->
            val wifiBytes = rows.firstOrNull { it.subscriptionId == null }?.wifiBytes ?: 0L
            val simRows = rows.filter { it.subscriptionId != null }.sortedBy { it.subscriptionId }
            DualSimUsage(
                sim1 = simRows.getOrNull(0)?.toSimUsage(),
                sim2 = simRows.getOrNull(1)?.toSimUsage(),
                wifiBytes = wifiBytes,
            )
        }

    private fun DailyDataUsage.toSimUsage(): SimUsage? {
        val subId = subscriptionId ?: return null
        return SimUsage(
            subscriptionId = subId,
            carrierName = carrierName,
            mobileBytes = mobileBytes,
            isPreciseMeasurement = true, // Reflects whatever DailyUsageWorker measured/attributed when it wrote this row.
        )
    }

    // ---------------------------------------------------------------------------------------
    // Feature 4 - Per-app usage leaderboard
    // ---------------------------------------------------------------------------------------

    /** Ranked leaderboard for [startDate]..[endDate] (inclusive epoch-day keys, see [com.example.domain.util.UsagePeriod]). */
    fun getTopAppsForPeriod(startDate: Long, endDate: Long, limit: Int = 10): Flow<List<AppUsageRanked>> =
        combine(
            dao.getTopAppsForPeriod(startDate, endDate, limit),
            dao.getTotalAppUsageForPeriod(startDate, endDate),
        ) { topApps, totalBytes ->
            topApps.mapIndexed { index, aggregate ->
                val appTotal = aggregate.wifiBytes + aggregate.mobileBytes
                AppUsageRanked(
                    packageName = aggregate.packageName,
                    appName = aggregate.appName,
                    totalBytes = appTotal,
                    wifiBytes = aggregate.wifiBytes,
                    mobileBytes = aggregate.mobileBytes,
                    rank = index + 1,
                    percentOfTotal = if (totalBytes > 0) (appTotal * 100f / totalBytes) else 0f,
                )
            }
        }

    /**
     * Best-effort launcher icon for a leaderboard row. [AppUsageRanked.packageName]/[AppUsageRanked.appName]
     * are snapshot values already stored in Room at collection time (see [saveAppUsage]), so the
     * leaderboard itself never needs PackageManager and works fine for apps the user has since
     * uninstalled. This is only for callers that additionally want a live icon to display - it
     * returns null (never throws) once the app is gone, so the UI can fall back to a placeholder.
     */
    fun safeGetAppIcon(packageName: String): android.graphics.drawable.Drawable? =
        try {
            packageManager.getApplicationIcon(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
}
