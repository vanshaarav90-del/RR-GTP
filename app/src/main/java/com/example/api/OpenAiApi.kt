package com.example.api

import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

@JsonClass(generateAdapter = true)
data class OpenAiImageRequest(
    val prompt: String,
    val n: Int = 1,
    val size: String = "1024x1024",
    val response_format: String = "b64_json"
)

@JsonClass(generateAdapter = true)
data class OpenAiImageData(
    val b64_json: String? = null,
    val url: String? = null
)

@JsonClass(generateAdapter = true)
data class OpenAiImageResponse(
    val created: Long,
    val data: List<OpenAiImageData>
)

interface OpenAiApiService {
    @POST("v1/images/generations")
    suspend fun generateImage(
        @Header("Authorization") authorization: String,
        @Body request: OpenAiImageRequest
    ): OpenAiImageResponse
}
