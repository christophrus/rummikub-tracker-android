package org.lorus.rummiq.counter.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalysisDao {

    @Insert
    suspend fun insertResult(result: AnalysisResultEntity): Long

    @Insert
    suspend fun insertTiles(tiles: List<DetectedTileEntity>)

    @Transaction
    @Query("SELECT * FROM analysis_results ORDER BY timestamp DESC")
    fun getAllResultsWithTiles(): Flow<List<AnalysisResultWithTiles>>

    @Transaction
    @Query("SELECT * FROM analysis_results WHERE id = :resultId")
    suspend fun getResultWithTiles(resultId: Long): AnalysisResultWithTiles?

    @Query("DELETE FROM analysis_results WHERE id = :resultId")
    suspend fun deleteResult(resultId: Long)

    @Query("DELETE FROM analysis_results")
    suspend fun deleteAll()
}
