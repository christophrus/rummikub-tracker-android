package com.lorus.rummikubtracker.counter.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint

data class LetterboxInfo(
    val scale: Float,   // Scale factor applied to original image
    val padX: Float,    // Horizontal padding in pixels (on 1280-size image)
    val padY: Float     // Vertical padding in pixels (on 1280-size image)
)

object ImagePreprocessor {

    const val INPUT_SIZE = 1280
    private const val PAD_COLOR_VALUE = 114f / 255f

    /**
     * Preprocesses a Bitmap into a CHW float tensor for YOLO inference.
     * Applies letterbox resizing (preserving aspect ratio) and normalizes to 0..1.
     *
     * @return Pair of the float array (CHW, [3, 1280, 1280]) and LetterboxInfo for bbox back-calculation
     */
    fun preprocess(bitmap: Bitmap): Pair<FloatArray, LetterboxInfo> {
        val origW = bitmap.width
        val origH = bitmap.height

        // Compute scale so the longer side fits into INPUT_SIZE
        val scale = INPUT_SIZE.toFloat() / maxOf(origW, origH)
        val scaledW = (origW * scale).toInt()
        val scaledH = (origH * scale).toInt()

        // Padding to center the scaled image in a 1280x1280 canvas
        val padX = (INPUT_SIZE - scaledW) / 2f
        val padY = (INPUT_SIZE - scaledH) / 2f

        // Scale bitmap
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)

        // Create 1280x1280 letterboxed bitmap with gray padding
        val letterboxed = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(letterboxed)
        canvas.drawColor(Color.rgb(114, 114, 114))
        canvas.drawBitmap(scaledBitmap, padX, padY, null)
        scaledBitmap.recycle()

        // Convert to CHW float array normalized to [0, 1]
        val floatArray = FloatArray(3 * INPUT_SIZE * INPUT_SIZE)
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        letterboxed.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        letterboxed.recycle()

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f

            floatArray[i] = r                                          // R channel
            floatArray[INPUT_SIZE * INPUT_SIZE + i] = g                // G channel
            floatArray[2 * INPUT_SIZE * INPUT_SIZE + i] = b            // B channel
        }

        val letterboxInfo = LetterboxInfo(scale = scale, padX = padX, padY = padY)
        return Pair(floatArray, letterboxInfo)
    }

    /**
     * Rotates a bitmap by the given degrees.
     */
    fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    /**
     * Downscales a bitmap if either dimension exceeds maxDimension, preserving aspect ratio.
     */
    fun downscaleIfNeeded(bitmap: Bitmap, maxDimension: Int = 2000): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / maxSide
        val newW = (bitmap.width * scale).toInt()
        val newH = (bitmap.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        if (scaled != bitmap) bitmap.recycle()
        return scaled
    }
}
