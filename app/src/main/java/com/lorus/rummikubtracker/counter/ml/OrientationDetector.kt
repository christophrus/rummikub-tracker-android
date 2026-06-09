package com.lorus.rummikubtracker.counter.ml

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/**
 * Detects image orientation using a fine-tuned ResNet-18 model.
 *
 * Classes: 0 → 0°, 1 → 90°, 2 → 180°, 3 → 270°
 */
class OrientationDetector private constructor(context: Context) {

    companion object {
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
     * @return The predicted orientation in degrees (0, 90, 180, or 270)
     */
    fun detect(inputArray: FloatArray): Int {
        val shape = longArrayOf(1, 3, 224, 224)
        val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputArray), shape)

        val results = session.run(mapOf("input" to tensor))

        // Output shape: [1, 4] logits
        @Suppress("UNCHECKED_CAST")
        val rawOutput = (results[0].value as Array<FloatArray>)[0] // [4]

        tensor.close()
        results.close()

        // Argmax over the 4 classes
        val predictedClass = rawOutput.indices.maxByOrNull { rawOutput[it] } ?: 0
        return orientationDegrees[predictedClass]
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
