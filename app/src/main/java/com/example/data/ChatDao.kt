package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    // --- Chat Sessions ---
    @Query("SELECT * FROM chat_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<ChatSession>>

    @Query("SELECT * FROM chat_sessions")
    suspend fun getAllSessionsList(): List<ChatSession>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSession)

    @Delete
    suspend fun deleteSession(session: ChatSession)

    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAllSessions()

    // --- Chat Messages ---
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages")
    suspend fun getAllMessagesList(): List<ChatMessage>

    @Query("SELECT * FROM chat_messages WHERE content LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    suspend fun searchMessages(query: String): List<ChatMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllMessages()

    // --- Generated Images ---
    @Query("SELECT * FROM generated_images ORDER BY timestamp DESC")
    fun getAllGeneratedImages(): Flow<List<GeneratedImage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGeneratedImage(image: GeneratedImage)

    @Query("DELETE FROM generated_images WHERE id = :id")
    suspend fun deleteGeneratedImage(id: String)

    @Query("DELETE FROM generated_images")
    suspend fun deleteAllGeneratedImages()

    // --- Generated Videos ---
    @Query("SELECT * FROM generated_videos ORDER BY timestamp DESC")
    fun getAllGeneratedVideos(): Flow<List<GeneratedVideo>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGeneratedVideo(video: GeneratedVideo)

    @Query("DELETE FROM generated_videos WHERE id = :id")
    suspend fun deleteGeneratedVideo(id: String)

    @Query("DELETE FROM generated_videos")
    suspend fun deleteAllGeneratedVideos()

    // --- Saved Prompts ---
    @Query("SELECT * FROM saved_prompts ORDER BY timestamp DESC")
    fun getAllSavedPrompts(): Flow<List<SavedPrompt>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedPrompt(prompt: SavedPrompt)

    @Delete
    suspend fun deleteSavedPrompt(prompt: SavedPrompt)

    // --- Generated Radio Shows ---
    @Query("SELECT * FROM generated_radio_shows ORDER BY timestamp DESC")
    fun getAllGeneratedRadioShows(): Flow<List<GeneratedRadioShow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGeneratedRadioShow(show: GeneratedRadioShow)

    @Query("DELETE FROM generated_radio_shows WHERE id = :id")
    suspend fun deleteGeneratedRadioShow(id: String)

    @Query("DELETE FROM generated_radio_shows")
    suspend fun deleteAllGeneratedRadioShows()
}
