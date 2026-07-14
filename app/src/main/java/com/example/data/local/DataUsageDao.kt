package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DataUsageDao {

    // Daily Data Usage Queries

    @Upsert
    suspend fun upsertDailyDataUsage(usage: DailyDataUsage)

    @Query("SELECT * FROM daily_data_usage WHERE date = :date AND subscriptionId IS NULL LIMIT 1")
    suspend fun getDailyDataUsageSync(date: Long): DailyDataUsage?

    /** Looks up a single bucket row (Feature 5): [subscriptionId] null = Wi-Fi bucket, non-null = that SIM's mobile bucket. */
    @Query("SELECT * FROM daily_data_usage WHERE date = :date AND subscriptionId IS :subscriptionId LIMIT 1")
    suspend fun getDailyDataUsageForBucket(date: Long, subscriptionId: Int?): DailyDataUsage?

    /** All buckets (Wi-Fi + each SIM) recorded for one date (Feature 5). */
    @Query("SELECT * FROM daily_data_usage WHERE date = :date")
    fun getDailyDataUsageRowsForDate(date: Long): Flow<List<DailyDataUsage>>

    @Query("SELECT * FROM daily_data_usage WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getDailyDataUsageForRange(startDate: Long, endDate: Long): Flow<List<DailyDataUsage>>

    @Query("DELETE FROM daily_data_usage WHERE date < :cutoffDate")
    suspend fun deleteDailyDataUsageOlderThan(cutoffDate: Long)

    // App Data Usage Queries

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppDataUsage(usages: List<AppDataUsage>)

    @Query("SELECT * FROM app_data_usage WHERE date = :date ORDER BY (wifiBytes + mobileBytes) DESC")
    fun getTopAppsByUsageForDate(date: Long): Flow<List<AppDataUsage>>

    /**
     * Leaderboard query (Feature 4): ranking is computed in SQL, not Kotlin. Grouped by
     * packageName so an app's usage is summed across every day in the period.
     */
    @Query(
        """
        SELECT packageName, appName, SUM(wifiBytes) AS wifiBytes, SUM(mobileBytes) AS mobileBytes
        FROM app_data_usage
        WHERE date >= :startDate AND date <= :endDate
        GROUP BY packageName
        ORDER BY (SUM(wifiBytes) + SUM(mobileBytes)) DESC
        LIMIT :limit
        """
    )
    fun getTopAppsForPeriod(startDate: Long, endDate: Long, limit: Int): Flow<List<AppUsageAggregate>>

    /** Device-wide total across all apps for the same period, used to compute each app's share (Feature 4). */
    @Query("SELECT COALESCE(SUM(wifiBytes + mobileBytes), 0) FROM app_data_usage WHERE date >= :startDate AND date <= :endDate")
    fun getTotalAppUsageForPeriod(startDate: Long, endDate: Long): Flow<Long>

    @Query("DELETE FROM app_data_usage WHERE date < :cutoffDate")
    suspend fun deleteAppDataUsageOlderThan(cutoffDate: Long)
}
