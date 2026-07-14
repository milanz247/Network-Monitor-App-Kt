package com.example.di

import android.content.Context
import androidx.room.Room
import com.example.data.local.ALL_MIGRATIONS
import com.example.data.local.AppDatabase
import com.example.data.local.DataUsageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "net_monitor_db"
        )
        .addMigrations(*ALL_MIGRATIONS)
        .build()
    }

    @Provides
    fun provideDataUsageDao(db: AppDatabase): DataUsageDao {
        return db.dataUsageDao()
    }
}
