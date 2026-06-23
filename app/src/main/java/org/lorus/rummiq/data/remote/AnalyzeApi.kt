package org.lorus.rummiq.data.remote

import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

@Serializable
data class AnalyzeResponse(
    val total_score: Int = 0,
    val tiles: List<TileInfo> = emptyList(),
    val error: String? = null
)

@Serializable
data class TileInfo(
    val number: Int = 0,
    val color: String = "",
    val confidence: Double = 0.0
)

interface AnalyzeApi {
    @Multipart
    @POST("analyze")
    suspend fun analyzeTiles(
        @Part image: MultipartBody.Part
    ): AnalyzeResponse
}
