package com.lorus.rummikubtracker.data.local.dao

import androidx.room.*
import com.lorus.rummikubtracker.data.local.entity.RoundEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoundDao {
    @Query("SELECT * FROM rounds WHERE gameId = :gameId ORDER BY roundNumber ASC")
    fun getRoundsForGame(gameId: Long): Flow<List<RoundEntity>>

    @Query("SELECT * FROM rounds WHERE gameId = :gameId ORDER BY roundNumber ASC")
    suspend fun getRoundsForGameOnce(gameId: Long): List<RoundEntity>

    @Query("SELECT COUNT(*) FROM rounds WHERE gameId = :gameId")
    suspend fun getRoundCount(gameId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRound(round: RoundEntity): Long

    @Update
    suspend fun updateRound(round: RoundEntity)

    @Query("DELETE FROM rounds WHERE gameId = :gameId")
    suspend fun deleteRoundsForGame(gameId: Long)
}
