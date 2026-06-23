package org.lorus.rummiq.counter.ml

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

class YoloDetector private constructor(context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: YoloDetector? = null

        fun getInstance(context: Context): YoloDetector {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: YoloDetector(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val modelBytes = context.assets.open("RummiQ_yolo.onnx").readBytes()
        val options = OrtSession.SessionOptions()
        session = env.createSession(modelBytes, options)
    }

    /**
     * Runs YOLO inference on a preprocessed input tensor.
     *
     * @param inputArray Float array of shape [1, 3, 1280, 1280] in CHW format, normalized to 0..1
     * @return Output array [300, 6] where each row is [x1, y1, x2, y2, confidence, class_id]
     *         with coordinates normalized to [0, 1] on the 1280x1280 letterbox.
     */
    fun detect(inputArray: FloatArray): Array<FloatArray> {
        val shape = longArrayOf(1, 3, 1280, 1280)
        val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputArray), shape)

        val results = session.run(mapOf("images" to tensor))

        // Output shape: [1, 300, 6] — YOLO26n (YOLOv11) export WITH built-in NMS
        @Suppress("UNCHECKED_CAST")
        val rawOutput = (results[0].value as Array<Array<FloatArray>>)[0] // [300, 6]

        tensor.close()
        results.close()

        return rawOutput
    }

    fun close() {
        session.close()
        env.close()
    }
}
