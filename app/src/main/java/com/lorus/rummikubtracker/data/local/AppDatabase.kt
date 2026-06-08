package com.lorus.rummikubtracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lorus.rummikubtracker.data.local.dao.*
import com.lorus.rummikubtracker.data.local.entity.*

@Database(
    entities = [
        GameEntity::class,
        PlayerEntity::class,
        RoundEntity::class,
        RoundScoreEntity::class,
        GamePlayerEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun playerDao(): PlayerDao
    abstract fun roundDao(): RoundDao
    abstract fun roundScoreDao(): RoundScoreDao
    abstract fun gamePlayerDao(): GamePlayerDao
}
