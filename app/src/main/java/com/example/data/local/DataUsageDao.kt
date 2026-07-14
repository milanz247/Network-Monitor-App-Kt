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

    @Query("SELECT * FROM daily_data_usage WHERE date = :date LIMIT 1")
    suspend fun getDailyDataUsageSync(date: Long): DailyDataUsage?

    @Query("SELECT * FROM daily_data_usage WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getDailyDataUsageForRange(startDate: Long, endDate: Long): Flow<List<DailyDataUsage>>
    
    @Query("DELETE FROM daily_data_usage WHERE date < :cutoffDate")
    suspend fun deleteDailyDataUsageOlderThan(cutoffDate: Long)

    // App Data Usage Queries

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppDataUsage(usages: List<AppDataUsage>)

    @Query("SELECT * FROM app_data_usage WHERE date = :date ORDER BY (wifiBytes + mobileBytes) DESC")
    fun getTopAppsByUsageForDate(date: Long): Flow<List<AppDataUsage>>

    @Query("DELETE FROM app_data_usage WHERE date < :cutoffDate")
    suspend fun deleteAppDataUsageOlderThan(cutoffDate: Long)
}
