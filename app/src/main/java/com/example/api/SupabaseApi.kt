package com.example.api

import com.squareup.moshi.Json
import okhttp3.ResponseBody
import retrofit2.http.*

data class SupabaseSessionDto(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "timestamp") val timestamp: Long
)

data class SupabaseMessageDto(
    @Json(name = "id") val id: String,
    @Json(name = "session_id") val sessionId: String,
    @Json(name = "role") val role: String,
    @Json(name = "content") val content: String,
    @Json(name = "timestamp") val timestamp: Long,
    @Json(name = "attachment_path") val attachmentPath: String?,
    @Json(name = "attachment_type") val attachmentType: String?
)

data class SupabasePreferenceDto(
    @Json(name = "email") val email: String,
    @Json(name = "name") val name: String,
    @Json(name = "is_dark_theme") val isDarkTheme: Boolean,
    @Json(name = "theme_mode") val themeMode: String? = null,
    @Json(name = "gemini_model") val geminiModel: String,
    @Json(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

interface SupabaseApiService {
    @GET("rest/v1/chat_sessions")
    suspend fun getSessions(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Query("order") order: String = "timestamp.desc"
    ): List<SupabaseSessionDto>

    @POST("rest/v1/chat_sessions")
    suspend fun insertSessions(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates",
        @Body sessions: List<SupabaseSessionDto>
    ): ResponseBody

    @GET("rest/v1/chat_messages")
    suspend fun getMessages(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Query("session_id") sessionIdFilter: String, // format: "eq.UUID"
        @Query("order") order: String = "timestamp.asc"
    ): List<SupabaseMessageDto>

    @GET("rest/v1/chat_messages")
    suspend fun getAllMessages(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Query("order") order: String = "timestamp.asc"
    ): List<SupabaseMessageDto>

    @POST("rest/v1/chat_messages")
    suspend fun insertMessages(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates",
        @Body messages: List<SupabaseMessageDto>
    ): ResponseBody

    @GET("rest/v1/user_preferences")
    suspend fun getPreferences(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Query("email") emailFilter: String // format: "eq.user@example.com"
    ): List<SupabasePreferenceDto>

    @POST("rest/v1/user_preferences")
    suspend fun insertPreferences(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates",
        @Body preferences: List<SupabasePreferenceDto>
    ): ResponseBody
}
