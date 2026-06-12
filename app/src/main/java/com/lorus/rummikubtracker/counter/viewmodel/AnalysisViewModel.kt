package com.lorus.rummikubtracker.counter.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lorus.rummikubtracker.counter.data.SettingsDataStore
import com.lorus.rummikubtracker.counter.data.local.AppDatabase
import com.lorus.rummikubtracker.counter.data.repository.HistoryRepository
import com.lorus.rummikubtracker.counter.ml.ImagePreprocessor
import com.lorus.rummikubtracker.counter.ml.NmsProcessor
import com.lorus.rummikubtracker.counter.ml.OrientationDetector
import com.lorus.rummikubtracker.counter.ml.OrientationPreprocessor
import com.lorus.rummikubtracker.counter.ml.YoloDetector
import com.lorus.rummikubtracker.counter.model.AnalysisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

data class AnalysisUiState(
    val isLoading: Boolean = false,
    val result: AnalysisResult? = null,
    val originalBitmap: Bitmap? = null,
    val error: String? = null
)

class AnalysisViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    private val detector: YoloDetector by lazy { YoloDetector.getInstance(application) }
    private val orientationDetector: OrientationDetector by lazy { OrientationDetector.getInstance(application) }
    private val historyRepository: HistoryRepository by lazy {
        val db = AppDatabase.getInstance(application)
        HistoryRepository(db.analysisDao(), application)
    }
    private val settingsDataStore: SettingsDataStore by lazy {
        SettingsDataStore(application)
    }

    fun analyze(bitmap: Bitmap) {
        // Read current confidence threshold from settings
        val confThreshold = runBlocking {
            settingsDataStore.confidenceThreshold.first()
        }
        Log.d("AnalysisViewModel", "Confidence threshold: ${confThreshold * 100}%")

        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isLoading = true, error = null) }

            var safeBitmap: Bitmap? = null
            var orientedBitmap: Bitmap? = null
            try {
                val startTime = System.currentTimeMillis()

                safeBitmap = ImagePreprocessor.downscaleIfNeeded(bitmap, maxDimension = 1280)
                val orientationInput = OrientationPreprocessor.preprocess(safeBitmap!!)
                val detectedDegrees = orientationDetector.detect(orientationInput)
                val correctionDegrees = orientationDetector.correctionDegrees(detectedDegrees)
                orientedBitmap = if (correctionDegrees != 0) {
                    val rotated = ImagePreprocessor.rotateBitmap(safeBitmap!!, correctionDegrees)
                    if (rotated != safeBitmap) safeBitmap!!.recycle()
                    rotated
                } else safeBitmap

                // Step 2: YOLO tile detection
                val (inputArray, letterboxInfo) = ImagePreprocessor.preprocess(orientedBitmap!!)
                val rawOutput = detector.detect(inputArray)
                val tiles = NmsProcessor.postProcess(
                    rawOutput, letterboxInfo, orientedBitmap!!.width, orientedBitmap!!.height,
                    confThreshold = confThreshold
                )
                val elapsed = System.currentTimeMillis() - startTime

                val totalScore = tiles.sumOf {
                    if (it.isJoker) 20 else (it.number ?: 0)
                }

                val result = AnalysisResult(
                    tiles = tiles,
                    totalScore = totalScore,
                    tileCount = tiles.size,
                    processingTimeMs = elapsed,
                    imageWidth = orientedBitmap!!.width,
                    imageHeight = orientedBitmap.height
                )

                // Save to history (scaled down thumbnail to save memory)
                val thumbnail = Bitmap.createScaledBitmap(orientedBitmap!!, 400, 400, true)
                historyRepository.saveResult(result, tiles, thumbnail)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        originalBitmap = orientedBitmap,
                        result = result
                    )
                }
            } catch (e: Exception) {
                Log.e("AnalysisViewModel", "Analysis failed", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Unknown error"
                    )
                }
            }
        }
    }

    fun reset() {
        _uiState.update { AnalysisUiState() }
    }

    override fun onCleared() {
        super.onCleared()
        // Detectors are singletons shared with ActiveGameViewModel — do NOT close them here
    }
}
