package com.lorus.rummikubtracker.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.lorus.rummikubtracker.data.repository.PlayerRepository
import com.lorus.rummikubtracker.domain.model.Config
import com.lorus.rummikubtracker.domain.model.Player
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerRepository: PlayerRepository
) {
    fun getAllPlayers(): Flow<List<Player>> = playerRepository.getAllPlayers()

    suspend fun savePlayer(name: String, imagePath: String? = null) {
        playerRepository.savePlayer(Player(name = name, imagePath = imagePath))
    }

    suspend fun deletePlayer(name: String) {
        val imageFile = getImageFile(name)
        if (imageFile.exists()) imageFile.delete()
        playerRepository.deletePlayer(name)
    }

    suspend fun deleteAllPlayers() {
        playerRepository.deleteAllPlayers()
    }

    suspend fun compressAndSaveImage(playerName: String, sourcePath: String): String? {
        return try {
            var bitmap = BitmapFactory.decodeFile(sourcePath) ?: return null

            // Fix EXIF rotation so the saved image has correct orientation
            bitmap = fixExifRotation(sourcePath, bitmap)

            val compressed = resizeAndCompress(bitmap)

            val dir = File(context.filesDir, "avatars")
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, "${playerName}_avatar.jpg")
            FileOutputStream(file).use { out ->
                compressed.compress(Bitmap.CompressFormat.JPEG, Config.JPEG_QUALITY, out)
            }
            compressed.recycle()
            if (compressed != bitmap) bitmap.recycle()
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun fixExifRotation(sourcePath: String, bitmap: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(sourcePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees != 0f) {
                val matrix = Matrix().apply { postRotate(degrees) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) bitmap.recycle()
                rotated
            } else bitmap
        } catch (_: Exception) {
            bitmap
        }
    }

    private fun resizeAndCompress(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxSize = Config.MAX_IMAGE_SIZE
        if (width <= maxSize && height <= maxSize) return bitmap
        val ratio = minOf(maxSize.toFloat() / width, maxSize.toFloat() / height)
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun getImageFile(playerName: String): File {
        val dir = File(context.filesDir, "avatars")
        return File(dir, "${playerName}_avatar.jpg")
    }
}
