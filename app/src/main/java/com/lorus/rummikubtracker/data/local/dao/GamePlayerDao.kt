package com.lorus.rummikubtracker.data.local.dao

import androidx.room.*
import com.lorus.rummikubtracker.data.local.entity.GamePlayerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GamePlayerDao {
    @Query("SELECT * FROM game_players WHERE gameId = :gameId ORDER BY playerOrder ASC")
    fun getPlayersForGame(gameId: Long): Flow<List<GamePlayerEntity>>

    @Query("SELECT * FROM game_players WHERE gameId = :gameId ORDER BY playerOrder ASC")
    suspend fun getPlayersForGameOnce(gameId: Long): List<GamePlayerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGamePlayer(gamePlayer: GamePlayerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGamePlayers(gamePlayers: List<GamePlayerEntity>)

    @Update
    suspend fun updateGamePlayer(gamePlayer: GamePlayerEntity)

    @Query("DELETE FROM game_players WHERE gameId = :gameId")
    suspend fun deletePlayersForGame(gameId: Long)

    @Query("UPDATE game_players SET playerOrder = :newOrder WHERE gameId = :gameId AND playerName = :playerName")
    suspend fun updatePlayerOrder(gameId: Long, playerName: String, newOrder: Int)

    @Query("UPDATE game_players SET extensionsUsed = extensionsUsed + 1 WHERE gameId = :gameId AND playerName = :playerName AND extensionsUsed < maxExtensions")
    suspend fun incrementExtensionsUsed(gameId: Long, playerName: String): Int

    @Query("UPDATE game_players SET extensionsUsed = 0 WHERE gameId = :gameId")
    suspend fun resetAllExtensions(gameId: Long)
}
