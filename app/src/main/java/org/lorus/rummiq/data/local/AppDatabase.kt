package org.lorus.rummiq.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import org.lorus.rummiq.data.local.dao.*
import org.lorus.rummiq.data.local.entity.*

@Database(
    entities = [
        GameEntity::class,
        PlayerEntity::class,
        RoundEntity::class,
        RoundScoreEntity::class,
        GamePlayerEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun playerDao(): PlayerDao
    abstract fun roundDao(): RoundDao
    abstract fun roundScoreDao(): RoundScoreDao
    abstract fun gamePlayerDao(): GamePlayerDao
}
