package com.lorus.rummikubtracker.data.local.dao

import androidx.room.*
import com.lorus.rummikubtracker.data.local.entity.GameEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games WHERE status = 'active' LIMIT 1")
    suspend fun getActiveGame(): GameEntity?

    @Query("SELECT * FROM games WHERE status = 'active' LIMIT 1")
    fun getActiveGameFlow(): Flow<GameEntity?>

    @Query("SELECT * FROM games WHERE status = 'completed' ORDER BY endTime DESC")
    fun getCompletedGames(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id = :gameId")
    suspend fun getGameById(gameId: Long): GameEntity?

    @Query("SELECT * FROM games WHERE id = :gameId")
    fun getGameByIdFlow(gameId: Long): Flow<GameEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGame(game: GameEntity): Long

    @Update
    suspend fun updateGame(game: GameEntity)

    @Delete
    suspend fun deleteGame(game: GameEntity)

    @Query("DELETE FROM games WHERE id = :gameId")
    suspend fun deleteGameById(gameId: Long)
}
