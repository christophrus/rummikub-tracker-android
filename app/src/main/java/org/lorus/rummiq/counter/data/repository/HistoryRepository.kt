package org.lorus.rummiq.counter.data.repository

import android.content.Context
import android.graphics.Bitmap
import org.lorus.rummiq.counter.data.local.AnalysisDao
import org.lorus.rummiq.counter.data.local.AnalysisResultEntity
import org.lorus.rummiq.counter.data.local.AnalysisResultWithTiles
import org.lorus.rummiq.counter.data.local.DetectedTileEntity
import org.lorus.rummiq.counter.model.AnalysisResult
import org.lorus.rummiq.counter.model.DetectedTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class HistoryRepository(
    private val dao: AnalysisDao,
    private val context: Context
) {

    fun getAllResults(): Flow<List<AnalysisResultWithTiles>> {
        return dao.getAllResultsWithTiles()
    }

    suspend fun saveResult(result: AnalysisResult, tiles: List<DetectedTile>, bitmap: Bitmap?) {
        val imageDir = File(context.filesDir, "history_images")
        if (!imageDir.exists()) imageDir.mkdirs()

        val imagePath: String? = if (bitmap != null) {
            val fileName = "img_${System.currentTimeMillis()}.jpg"
            val file = File(imageDir, fileName)
            withContext(Dispatchers.IO) {
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
            }
            file.absolutePath
        } else null

        val resultEntity = AnalysisResultEntity(
            timestamp = System.currentTimeMillis(),
            totalScore = result.totalScore,
            tileCount = result.tileCount,
            processingTimeMs = result.processingTimeMs,
            imagePath = imagePath,
            imageWidth = result.imageWidth,
            imageHeight = result.imageHeight
        )
        val resultId = dao.insertResult(resultEntity)

        val tileEntities = tiles.map { tile ->
            DetectedTileEntity(
                resultId = resultId,
                number = tile.number,
                confidence = tile.confidence,
                isJoker = tile.isJoker,
                x = tile.x,
                y = tile.y,
                width = tile.width,
                height = tile.height
            )
        }
        dao.insertTiles(tileEntities)
    }

    suspend fun getResultWithTiles(resultId: Long): AnalysisResultWithTiles? {
        return dao.getResultWithTiles(resultId)
    }

    suspend fun deleteResult(resultId: Long) {
        // Also delete the saved image file
        val entry = dao.getResultWithTiles(resultId)
        entry?.result?.imagePath?.let { path ->
            File(path).delete()
        }
        dao.deleteResult(resultId)
    }
}
