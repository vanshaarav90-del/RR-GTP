package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val role: String, // "user", "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val attachmentPath: String? = null,
    val attachmentType: String? = null // "image", "pdf", "text"
)

@Entity(tableName = "generated_images")
data class GeneratedImage(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val prompt: String,
    val imageUrl: String, // Base64 data or local path
    val timestamp: Long = System.currentTimeMillis(),
    val status: String, // "loading", "success", "error"
    val aspectRatio: String = "1:1",
    val modelUsed: String = "gemini-2.5-flash-image"
)

@Entity(tableName = "generated_videos")
data class GeneratedVideo(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val prompt: String,
    val videoUrl: String, // Base64 or procedural identifier
    val timestamp: Long = System.currentTimeMillis(),
    val status: String, // "loading", "success", "error"
    val aspectRatio: String = "16:9",
    val duration: Int = 4, // in seconds
    val style: String = "Cinematic",
    val modelUsed: String = "gemini-3.5-video"
)

@Entity(tableName = "saved_prompts")
data class SavedPrompt(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val text: String,
    val category: String = "General",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "generated_radio_shows")
data class GeneratedRadioShow(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val prompt: String,
    val stationGenre: String,
    val coverArtUrl: String, // Base64 data representation
    val timestamp: Long = System.currentTimeMillis(),
    val status: String, // "loading", "success", "error"
    val scriptJson: String, // Dialogue script segments representation in JSON
    val backgroundMusicStyle: String
)

