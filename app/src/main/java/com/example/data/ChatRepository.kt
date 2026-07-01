package com.example.data

import android.util.Log
import com.example.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class ChatRepository(private val chatDao: ChatDao) {

    private val TAG = "ChatRepository"

    val allSessions: Flow<List<ChatSession>> = chatDao.getAllSessions()
    val allGeneratedImages: Flow<List<GeneratedImage>> = chatDao.getAllGeneratedImages()
    val allGeneratedVideos: Flow<List<GeneratedVideo>> = chatDao.getAllGeneratedVideos()
    val allSavedPrompts: Flow<List<SavedPrompt>> = chatDao.getAllSavedPrompts()
    val allGeneratedRadioShows: Flow<List<GeneratedRadioShow>> = chatDao.getAllGeneratedRadioShows()

    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>> {
        return chatDao.getMessagesForSession(sessionId)
    }

    suspend fun insertSession(session: ChatSession) = withContext(Dispatchers.IO) {
        chatDao.insertSession(session)
    }

    suspend fun clearMessagesForSession(sessionId: String) = withContext(Dispatchers.IO) {
        chatDao.deleteMessagesForSession(sessionId)
    }

    suspend fun deleteSession(session: ChatSession) = withContext(Dispatchers.IO) {
        chatDao.deleteSession(session)
        chatDao.deleteMessagesForSession(session.id)
    }

    suspend fun deleteAllHistory() = withContext(Dispatchers.IO) {
        chatDao.deleteAllSessions()
        chatDao.deleteAllMessages()
    }

    suspend fun insertMessage(message: ChatMessage) = withContext(Dispatchers.IO) {
        chatDao.insertMessage(message)
    }

    suspend fun searchMessages(query: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        chatDao.searchMessages(query)
    }

    suspend fun insertGeneratedImage(image: GeneratedImage) = withContext(Dispatchers.IO) {
        chatDao.insertGeneratedImage(image)
    }

    suspend fun deleteGeneratedImage(id: String) = withContext(Dispatchers.IO) {
        chatDao.deleteGeneratedImage(id)
    }

    suspend fun insertGeneratedVideo(video: GeneratedVideo) = withContext(Dispatchers.IO) {
        chatDao.insertGeneratedVideo(video)
    }

    suspend fun deleteGeneratedVideo(id: String) = withContext(Dispatchers.IO) {
        chatDao.deleteGeneratedVideo(id)
    }

    suspend fun insertSavedPrompt(prompt: SavedPrompt) = withContext(Dispatchers.IO) {
        chatDao.insertSavedPrompt(prompt)
    }

    suspend fun deleteSavedPrompt(prompt: SavedPrompt) = withContext(Dispatchers.IO) {
        chatDao.deleteSavedPrompt(prompt)
    }

    suspend fun insertGeneratedRadioShow(show: GeneratedRadioShow) = withContext(Dispatchers.IO) {
        chatDao.insertGeneratedRadioShow(show)
    }

    suspend fun deleteGeneratedRadioShow(id: String) = withContext(Dispatchers.IO) {
        chatDao.deleteGeneratedRadioShow(id)
    }

    suspend fun deleteAllGeneratedRadioShows() = withContext(Dispatchers.IO) {
        chatDao.deleteAllGeneratedRadioShows()
    }

    // --- Fallback Gemini Chat Call with Streaming ---
    suspend fun sendGeminiMessageStream(
        model: String,
        messages: List<ChatMessage>,
        apiKey: String,
        onChunk: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val apiContents = messages.map {
            GeminiContent(parts = listOf(GeminiPart(text = it.content)))
        }
        val request = GeminiRequest(contents = apiContents)

        try {
            val responseBody = ApiClient.geminiService.generateContentStream(model, apiKey, request)
            val reader = BufferedReader(InputStreamReader(responseBody.byteStream()))
            var line: String?
            val fullResponse = StringBuilder()

            while (reader.readLine().also { line = it } != null) {
                val trimmed = line?.trim() ?: continue
                if (trimmed.isBlank()) continue
                
                // Gemini stream chunks might start with comma or square brackets, let's extract "text": "..." using regex or JSONObject
                try {
                    // Try parsing line as a full JSONObject
                    val cleanLine = trimmed.trimStart(',', '[', ']').trimEnd(']')
                    if (cleanLine.isNotBlank()) {
                        val obj = JSONObject(cleanLine)
                        val candidates = obj.optJSONArray("candidates")
                        val firstCandidate = candidates?.optJSONObject(0)
                        val contentObj = firstCandidate?.optJSONObject("content")
                        val parts = contentObj?.optJSONArray("parts")
                        val firstPart = parts?.optJSONObject(0)
                        val text = firstPart?.optString("text")
                        if (!text.isNullOrEmpty()) {
                            withContext(Dispatchers.Main) {
                                onChunk(text)
                            }
                            fullResponse.append(text)
                        }
                    }
                } catch (e: Exception) {
                    // Fallback to simple regex matches for "text" : "..."
                    val regex = "\"text\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                    val matches = regex.findAll(trimmed)
                    for (match in matches) {
                        val text = match.groupValues[1]
                            .replace("\\n", "\n")
                            .replace("\\t", "\t")
                            .replace("\\\"", "\"")
                        withContext(Dispatchers.Main) {
                            onChunk(text)
                        }
                        fullResponse.append(text)
                    }
                }
            }
            if (fullResponse.isEmpty()) {
                // If streaming parsing returned empty, fall back to non-streaming
                val nonStreamResponse = ApiClient.geminiService.generateContent(model, apiKey, request)
                val text = nonStreamResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "No response from Gemini"
                withContext(Dispatchers.Main) {
                    onChunk(text)
                }
                text
            } else {
                fullResponse.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini API call failed", e)
            "Error calling Gemini API: ${e.message}"
        }
    }

    // --- OpenAI Image Generation ---
    suspend fun generateImageWithOpenAi(
        prompt: String,
        apiKey: String
    ): String = withContext(Dispatchers.IO) {
        val request = OpenAiImageRequest(
            prompt = prompt,
            n = 1,
            size = "1024x1024",
            response_format = "b64_json"
        )
        try {
            val response = ApiClient.openAiService.generateImage(
                authorization = "Bearer $apiKey",
                request = request
            )
            val base64Data = response.data.firstOrNull()?.b64_json
                ?: response.data.firstOrNull()?.url
                ?: throw Exception("No image content or URL returned from OpenAI")
            base64Data
        } catch (e: Exception) {
            Log.e(TAG, "OpenAI Image generation failed", e)
            throw e
        }
    }

    // --- Gemini Image Generation ---
    suspend fun generateImageWithGemini(
        prompt: String,
        model: String,
        aspectRatio: String,
        apiKey: String
    ): String = withContext(Dispatchers.IO) {
        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
            generationConfig = GeminiGenerationConfig(
                imageConfig = GeminiImageConfig(aspectRatio = aspectRatio, imageSize = "1K"),
                responseModalities = listOf("TEXT", "IMAGE")
            )
        )

        try {
            val response = ApiClient.geminiService.generateContent(model, apiKey, request)
            // Look for inlineData in parts
            val parts = response.candidates?.firstOrNull()?.content?.parts
            var base64Data: String? = null
            
            parts?.forEach { part ->
                if (part.inlineData != null && part.inlineData.mimeType.startsWith("image/")) {
                    base64Data = part.inlineData.data
                }
            }

            base64Data ?: throw Exception("No image data found in response")
        } catch (e: Exception) {
            Log.e(TAG, "Gemini Image generation failed", e)
            throw e
        }
    }

    // --- Supabase Cloud Storage Persistence ---
    suspend fun backupAllToSupabase(url: String, key: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val service = ApiClient.getSupabaseService(url)
            val authHeader = "Bearer $key"

            // 1. Fetch local sessions and convert
            val localSessions = chatDao.getAllSessionsList()
            if (localSessions.isNotEmpty()) {
                val sessionDtos = localSessions.map {
                    SupabaseSessionDto(id = it.id, title = it.title, timestamp = it.timestamp)
                }
                service.insertSessions(key, authHeader, sessions = sessionDtos)
            }

            // 2. Fetch local messages and convert
            val localMessages = chatDao.getAllMessagesList()
            if (localMessages.isNotEmpty()) {
                val messageDtos = localMessages.map {
                    SupabaseMessageDto(
                        id = it.id,
                        sessionId = it.sessionId,
                        role = it.role,
                        content = it.content,
                        timestamp = it.timestamp,
                        attachmentPath = it.attachmentPath,
                        attachmentType = it.attachmentType
                    )
                }
                service.insertMessages(key, authHeader, messages = messageDtos)
            }
            true
        } catch (e: Exception) {
            Log.e("SupabaseSync", "Failed to backup data to Supabase", e)
            false
        }
    }

    suspend fun retrieveAllFromSupabase(url: String, key: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val service = ApiClient.getSupabaseService(url)
            val authHeader = "Bearer $key"

            // 1. Fetch sessions from Supabase
            val remoteSessions = service.getSessions(key, authHeader)
            remoteSessions.forEach { dto ->
                chatDao.insertSession(ChatSession(id = dto.id, title = dto.title, timestamp = dto.timestamp))
            }

            // 2. Fetch all messages from Supabase
            val remoteMessages = service.getAllMessages(key, authHeader)
            remoteMessages.forEach { dto ->
                chatDao.insertMessage(
                    ChatMessage(
                        id = dto.id,
                        sessionId = dto.sessionId,
                        role = dto.role,
                        content = dto.content,
                        timestamp = dto.timestamp,
                        attachmentPath = dto.attachmentPath,
                        attachmentType = dto.attachmentType
                    )
                )
            }
            true
        } catch (e: Exception) {
            Log.e("SupabaseSync", "Failed to retrieve data from Supabase", e)
            false
        }
    }

    // --- Supabase Preferences Sync ---
    suspend fun backupPreferencesToSupabase(
        url: String,
        key: String,
        email: String,
        name: String,
        isDarkTheme: Boolean,
        themeMode: String,
        geminiModel: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val service = ApiClient.getSupabaseService(url)
            val authHeader = "Bearer $key"
            val prefDto = SupabasePreferenceDto(
                email = email,
                name = name,
                isDarkTheme = isDarkTheme,
                themeMode = themeMode,
                geminiModel = geminiModel
            )
            service.insertPreferences(key, authHeader, preferences = listOf(prefDto))
            true
        } catch (e: Exception) {
            Log.e("SupabaseSync", "Failed to backup preferences to Supabase", e)
            false
        }
    }

    suspend fun retrievePreferencesFromSupabase(
        url: String,
        key: String,
        email: String
    ): SupabasePreferenceDto? = withContext(Dispatchers.IO) {
        try {
            val service = ApiClient.getSupabaseService(url)
            val authHeader = "Bearer $key"
            val list = service.getPreferences(key, authHeader, emailFilter = "eq.$email")
            list.firstOrNull()
        } catch (e: Exception) {
            Log.e("SupabaseSync", "Failed to retrieve preferences from Supabase", e)
            null
        }
    }
}
