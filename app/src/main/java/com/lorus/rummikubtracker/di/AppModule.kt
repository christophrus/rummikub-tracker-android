package com.lorus.rummikubtracker.di

import android.content.Context
import androidx.room.Room
import com.lorus.rummikubtracker.data.local.AppDatabase
import com.lorus.rummikubtracker.data.local.dao.*
import com.lorus.rummikubtracker.data.local.datastore.PreferencesDataStore
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "rummikub_tracker.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideGameDao(database: AppDatabase): GameDao = database.gameDao()

    @Provides
    fun providePlayerDao(database: AppDatabase): PlayerDao = database.playerDao()

    @Provides
    fun provideRoundDao(database: AppDatabase): RoundDao = database.roundDao()

    @Provides
    fun provideRoundScoreDao(database: AppDatabase): RoundScoreDao = database.roundScoreDao()

    @Provides
    fun provideGamePlayerDao(database: AppDatabase): GamePlayerDao = database.gamePlayerDao()

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): PreferencesDataStore {
        return PreferencesDataStore(context)
    }
}
