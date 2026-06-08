package com.lorus.rummikubtracker.counter.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lorus.rummikubtracker.counter.data.local.AppDatabase
import com.lorus.rummikubtracker.counter.data.local.AnalysisResultWithTiles
import com.lorus.rummikubtracker.counter.data.repository.HistoryRepository
import com.lorus.rummikubtracker.counter.model.AnalysisResult
import com.lorus.rummikubtracker.counter.model.DetectedTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryDetailState(
    val bitmap: Bitmap? = null,
    val result: AnalysisResult? = null,
    val isLoading: Boolean = false
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: HistoryRepository by lazy {
        val db = AppDatabase.getInstance(application)
        HistoryRepository(db.analysisDao(), application)
    }

    val allResults: Flow<List<AnalysisResultWithTiles>> = repository.getAllResults()

    private val _detailState = MutableStateFlow(HistoryDetailState())
    val detailState: StateFlow<HistoryDetailState> = _detailState.asStateFlow()

    fun deleteEntry(resultId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteResult(resultId)
        }
    }

    fun loadDetail(resultId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _detailState.update { it.copy(isLoading = true) }

            val entry = repository.getResultWithTiles(resultId)
            if (entry != null) {
                val bitmap = entry.result.imagePath?.let { path ->
                    BitmapFactory.decodeFile(path)
                }

                val tiles = entry.tiles.map { tile ->
                    DetectedTile(
                        number = tile.number,
                        confidence = tile.confidence,
                        isJoker = tile.isJoker,
                        x = tile.x,
                        y = tile.y,
                        width = tile.width,
                        height = tile.height
                    )
                }

                val totalScore = tiles.sumOf {
                    if (it.isJoker) 20 else (it.number ?: 0)
                }

                val result = AnalysisResult(
                    tiles = tiles,
                    totalScore = totalScore,
                    tileCount = entry.result.tileCount,
                    processingTimeMs = entry.result.processingTimeMs
                )

                _detailState.update {
                    it.copy(
                        isLoading = false,
                        bitmap = bitmap,
                        result = result
                    )
                }
            } else {
                _detailState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun clearSelection() {
        _detailState.update { HistoryDetailState() }
    }
}
