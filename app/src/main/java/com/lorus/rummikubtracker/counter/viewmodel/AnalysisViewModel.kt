package com.lorus.rummikubtracker.counter.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Matrix
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
    val error: String? = null,
    // Orientation confirmation flow
    val showOrientationConfirm: Boolean = false,
    val orientationConfirmBitmap: Bitmap? = null,
    val orientationCorrectionDegrees: Int = 0,
    val orientationMaxConfidence: Float = 0f
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

    /** Holds the downscaled (unrotated) bitmap while the orientation confirmation dialog is shown. */
    private var pendingSafeBitmap: Bitmap? = null

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
                val orientationResult = orientationDetector.detect(orientationInput)
                val correctionDegrees = orientationDetector.correctionDegrees(orientationResult.degrees)

                // Check if orientation confidence is too low → ask user
                val maxConf = orientationResult.confidences.maxOrNull() ?: 0f
                if (maxConf < 0.9f) {
                    orientedBitmap = if (correctionDegrees != 0) {
                        ImagePreprocessor.rotateBitmap(safeBitmap!!, correctionDegrees)
                    } else safeBitmap
                    pendingSafeBitmap = safeBitmap // keep unrotated for later adjustments
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            showOrientationConfirm = true,
                            orientationConfirmBitmap = orientedBitmap,
                            orientationCorrectionDegrees = correctionDegrees,
                            orientationMaxConfidence = maxConf
                        )
                    }
                    return@launch
                }

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

                // Save to history with original image
                historyRepository.saveResult(result, tiles, orientedBitmap)

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

    /** User confirms the orientation — continue to YOLO detection. */
    fun confirmOrientation() {
        val state = _uiState.value
        val bitmap = state.orientationConfirmBitmap ?: return
        val safeBitmap = pendingSafeBitmap
        pendingSafeBitmap = null
        _uiState.update {
            it.copy(showOrientationConfirm = false, orientationConfirmBitmap = null)
        }
        // Continue the YOLO pipeline with the confirmed bitmap
        continueWithYolo(bitmap, state.orientationCorrectionDegrees, safeBitmap)
    }

    /** User rotates the orientation preview 90° clockwise. */
    fun rotateOrientationRight() {
        val state = _uiState.value
        val currentBmp = state.orientationConfirmBitmap ?: return
        val newCorrection = (state.orientationCorrectionDegrees + 90) % 360
        val rotated = Bitmap.createBitmap(currentBmp, 0, 0, currentBmp.width, currentBmp.height,
            Matrix().apply { postRotate(90f) }, true)
        if (rotated != currentBmp) currentBmp.recycle()
        _uiState.update {
            it.copy(
                orientationConfirmBitmap = rotated,
                orientationCorrectionDegrees = newCorrection
            )
        }
    }

    /** User rotates the orientation preview 90° counter-clockwise. */
    fun rotateOrientationLeft() {
        val state = _uiState.value
        val currentBmp = state.orientationConfirmBitmap ?: return
        val newCorrection = (state.orientationCorrectionDegrees - 90 + 360) % 360
        val rotated = Bitmap.createBitmap(currentBmp, 0, 0, currentBmp.width, currentBmp.height,
            Matrix().apply { postRotate(-90f) }, true)
        if (rotated != currentBmp) currentBmp.recycle()
        _uiState.update {
            it.copy(
                orientationConfirmBitmap = rotated,
                orientationCorrectionDegrees = newCorrection
            )
        }
    }

    /** User cancels orientation confirmation — go back to main menu. */
    fun cancelOrientationConfirmation() {
        pendingSafeBitmap?.recycle()
        pendingSafeBitmap = null
        _uiState.update { it.copy(showOrientationConfirm = false, orientationConfirmBitmap = null) }
    }

    /** Continues the YOLO pipeline after orientation is confirmed. */
    private fun continueWithYolo(orientedBitmap: Bitmap, correctionDegrees: Int, safeBitmap: Bitmap?) {
        val confThreshold = runBlocking { settingsDataStore.confidenceThreshold.first() }
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val startTime = System.currentTimeMillis()

                // Step 2: YOLO tile detection
                val (inputArray, letterboxInfo) = ImagePreprocessor.preprocess(orientedBitmap)
                val rawOutput = detector.detect(inputArray)
                val tiles = NmsProcessor.postProcess(
                    rawOutput, letterboxInfo, orientedBitmap.width, orientedBitmap.height,
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
                    imageWidth = orientedBitmap.width,
                    imageHeight = orientedBitmap.height
                )

                // Save to history with original image
                historyRepository.saveResult(result, tiles, orientedBitmap)

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
            } finally {
                safeBitmap?.let { if (it != orientedBitmap && !it.isRecycled) it.recycle() }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Detectors are singletons shared with ActiveGameViewModel — do NOT close them here
    }
}
