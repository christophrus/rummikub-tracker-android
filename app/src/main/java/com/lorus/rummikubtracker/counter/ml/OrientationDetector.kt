package com.lorus.rummikubtracker.counter.ml

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/**
 * Result of orientation detection.
 *
 * @param degrees Predicted orientation in degrees (0, 90, 180, or 270)
 * @param confidences Softmax confidences for each class [0°, 90°, 180°, 270°]
 */
data class OrientationResult(
    val degrees: Int,
    val confidences: List<Float>
)

/**
 * Detects image orientation using a fine-tuned MobileNetV3-Small model.
 *
 * Classes: 0 → 0°, 1 → 90°, 2 → 180°, 3 → 270°
 */
class OrientationDetector private constructor(context: Context) {

    companion object {
        private const val TAG = "OrientationDetector"

        @Volatile
        private var INSTANCE: OrientationDetector? = null

        fun getInstance(context: Context): OrientationDetector {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OrientationDetector(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    /** Maps class index to degrees: 0→0°, 1→90°, 2→180°, 3→270° */
    val orientationDegrees = intArrayOf(0, 90, 180, 270)

    init {
        val modelBytes = context.assets.open("orientation_cnn.onnx").readBytes()
        val options = OrtSession.SessionOptions()
        session = env.createSession(modelBytes, options)
    }

    /**
     * Runs orientation detection on a preprocessed input tensor.
     *
     * @param inputArray Float array of shape [1, 3, 224, 224] in CHW format, ImageNet-normalized
     * @return [OrientationResult] with predicted degrees and per-class softmax confidences
     */
    fun detect(inputArray: FloatArray): OrientationResult {
        val shape = longArrayOf(1, 3, 224, 224)
        val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputArray), shape)

        val results = session.run(mapOf("input" to tensor))

        // Output shape: [1, 4] logits
        @Suppress("UNCHECKED_CAST")
        val rawOutput = (results[0].value as Array<FloatArray>)[0] // [4]

        // Softmax confidences
        val maxLogit = rawOutput.maxOrNull() ?: 0f
        val expSum = rawOutput.map { kotlin.math.exp(it - maxLogit) }.sum()
        val confidences = rawOutput.map { kotlin.math.exp(it - maxLogit) / expSum }
        val predictedClass = rawOutput.indices.maxByOrNull { rawOutput[it] } ?: 0
        val detectedDeg = orientationDegrees[predictedClass]
        val correctionDeg = correctionDegrees(detectedDeg)

        // Debug logging
        Log.d(TAG, "=== Orientation Detection ===")
        orientationDegrees.forEachIndexed { i, deg ->
            Log.d(TAG, "  %3d° : %.4f".format(deg, confidences[i]))
        }
        Log.d(TAG, "Detected: %d° | Correction: %d° | Max confidence: %.2f%%".format(detectedDeg, correctionDeg, confidences[predictedClass] * 100))

        tensor.close()
        results.close()

        return OrientationResult(degrees = detectedDeg, confidences = confidences)
    }

    /**
     * Returns the correction rotation needed to bring the image to 0° orientation.
     * E.g., if the image is at 90°, we need to rotate -90° (i.e., 270°) to correct it.
     */
    fun correctionDegrees(detectedOrientation: Int): Int {
        return (360 - detectedOrientation) % 360
    }

    fun close() {
        session.close()
        env.close()
    }
}
