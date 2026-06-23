package org.lorus.rummiq.counter.ml

import android.graphics.Bitmap

object OrientationPreprocessor {

    private const val INPUT_SIZE = 224

    // ImageNet normalization constants
    private const val MEAN_R = 0.485f
    private const val MEAN_G = 0.456f
    private const val MEAN_B = 0.406f
    private const val STD_R = 0.229f
    private const val STD_G = 0.224f
    private const val STD_B = 0.225f

    /**
     * Preprocesses a Bitmap for the MobileNetV3-Small orientation model.
     *
     * 1. Resizes to 224×224 (simple stretch, no letterbox needed for orientation)
     * 2. Converts pixels to CHW float array
     * 3. Normalizes with ImageNet mean/std
     *
     * @return FloatArray of shape [1, 3, 224, 224] in CHW format, ImageNet-normalized
     */
    fun preprocess(bitmap: Bitmap): FloatArray {
        // Resize to 224×224
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)

        val floatArray = FloatArray(3 * INPUT_SIZE * INPUT_SIZE)
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        resized.recycle()

        val channelSize = INPUT_SIZE * INPUT_SIZE

        for (i in pixels.indices) {
            val pixel = pixels[i]

            // Extract RGB and normalize to 0..1
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f

            // Apply ImageNet normalization: (x - mean) / std
            floatArray[i] = (r - MEAN_R) / STD_R                        // R channel
            floatArray[channelSize + i] = (g - MEAN_G) / STD_G          // G channel
            floatArray[2 * channelSize + i] = (b - MEAN_B) / STD_B      // B channel
        }

        return floatArray
    }
}
