package org.lorus.rummiq.data.remote

import org.lorus.rummiq.domain.model.Config
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TileScanService @Inject constructor(
    private val analyzeApi: AnalyzeApi
) {
    suspend fun scanTiles(imageFile: File): Result<AnalyzeResponse> {
        return try {
            val requestBody = imageFile.asRequestBody("image/jpeg".toMediaType())
            val part = MultipartBody.Part.createFormData("image", imageFile.name, requestBody)
            val response = analyzeApi.analyzeTiles(part)

            if (response.error != null) {
                Result.failure(Exception(response.error))
            } else {
                Result.success(response)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
