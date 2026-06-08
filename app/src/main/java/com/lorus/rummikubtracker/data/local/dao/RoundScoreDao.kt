package com.lorus.rummikubtracker.data.local.dao

import androidx.room.*
import com.lorus.rummikubtracker.data.local.entity.RoundScoreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoundScoreDao {
    @Query("SELECT * FROM round_scores WHERE roundId = :roundId")
    fun getScoresForRound(roundId: Long): Flow<List<RoundScoreEntity>>

    @Query("SELECT * FROM round_scores WHERE roundId = :roundId")
    suspend fun getScoresForRoundOnce(roundId: Long): List<RoundScoreEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScore(score: RoundScoreEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScores(scores: List<RoundScoreEntity>)

    @Query("DELETE FROM round_scores WHERE roundId = :roundId")
    suspend fun deleteScoresForRound(roundId: Long)
}
