package org.lorus.rummiq.counter.ml

import android.util.Log
import org.lorus.rummiq.counter.model.DetectedTile

object NmsProcessor {

    private const val TAG = "NmsProcessor"

    /**
     * Post-processes YOLO output that already has built-in NMS applied.
     *
     * @param predictions  Output [300, 6] from YoloDetector with built-in NMS.
     *                     Each row: [x1, y1, x2, y2, confidence, class_id]
     *                     Coordinates are in letterbox pixel space (0..1280).
     * @param letterboxInfo Letterbox scaling/padding info for coordinate back-mapping
     * @param origWidth    Original image width
     * @param origHeight   Original image height
     * @param confThreshold Minimum confidence to keep a detection
     * @return List of detected tiles, sorted left-to-right
     */
    fun postProcess(
        predictions: Array<FloatArray>,
        letterboxInfo: LetterboxInfo,
        origWidth: Int,
        origHeight: Int,
        confThreshold: Float = 0.25f
    ): List<DetectedTile> {
        Log.d(TAG, "postProcess with confThreshold=${confThreshold * 100}%")
        val candidates = mutableListOf<DetectedTile>()

        // Debug: log first 3 raw outputs
        for (i in 0 until minOf(3, predictions.size)) {
            val p = predictions[i]
            Log.d(TAG, "raw[$i]: x1=${p[0]} y1=${p[1]} x2=${p[2]} y2=${p[3]} conf=${p[4]} cls=${p[5]}")
        }

        var totalFiltered = 0
        var confFiltered = 0
        var clsFiltered = 0
        var boxFiltered = 0

        for (pred in predictions) {
            val x1 = pred[0]
            val y1 = pred[1]
            val x2 = pred[2]
            val y2 = pred[3]
            val confidence = pred[4]
            val classId = pred[5].toInt()

            // Filter out padding detections
            if (classId < 0 || classId > 13) { clsFiltered++; continue }
            if (confidence < confThreshold) { confFiltered++; continue }

            // YOLO26n (YOLOv11) built-in NMS returns coords in letterbox pixel space (0..1280).
            // Map back to original image: subtract padding, divide by scale.
            val origX1 = ((x1 - letterboxInfo.padX) / letterboxInfo.scale).coerceIn(0f, origWidth.toFloat())
            val origY1 = ((y1 - letterboxInfo.padY) / letterboxInfo.scale).coerceIn(0f, origHeight.toFloat())
            val origX2 = ((x2 - letterboxInfo.padX) / letterboxInfo.scale).coerceIn(0f, origWidth.toFloat())
            val origY2 = ((y2 - letterboxInfo.padY) / letterboxInfo.scale).coerceIn(0f, origHeight.toFloat())

            // Ensure valid box (x1 <= x2, y1 <= y2)
            val minX = minOf(origX1, origX2)
            val minY = minOf(origY1, origY2)
            val maxX = maxOf(origX1, origX2)
            val maxY = maxOf(origY1, origY2)
            val boxW = (maxX - minX).toInt()
            val boxH = (maxY - minY).toInt()
            if (boxW <= 0 || boxH <= 0) { boxFiltered++; continue }
            totalFiltered++

            // Class index 0–12 = tile values 1–13, index 13 = Joker
            val isJoker = classId == 13
            val number = if (isJoker) null else classId + 1

            candidates.add(
                DetectedTile(
                    number = number,
                    confidence = confidence,
                    isJoker = isJoker,
                    x = minX.toInt(),
                    y = minY.toInt(),
                    width = boxW,
                    height = boxH
                )
            )
        }

        Log.d(TAG, "Filtered: cls=$clsFiltered conf=$confFiltered box=$boxFiltered → ${candidates.size} tiles")
        return candidates.sortedBy { it.x }
    }
}
