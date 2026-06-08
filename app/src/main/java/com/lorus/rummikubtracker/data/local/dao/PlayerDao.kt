package com.lorus.rummikubtracker.data.local.dao

import androidx.room.*
import com.lorus.rummikubtracker.data.local.entity.PlayerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerDao {
    @Query("SELECT * FROM players ORDER BY name ASC")
    fun getAllPlayers(): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players WHERE name = :name")
    suspend fun getPlayerByName(name: String): PlayerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayer(player: PlayerEntity)

    @Update
    suspend fun updatePlayer(player: PlayerEntity)

    @Query("DELETE FROM players WHERE name = :name")
    suspend fun deletePlayerByName(name: String)

    @Query("DELETE FROM players")
    suspend fun deleteAllPlayers()
}
