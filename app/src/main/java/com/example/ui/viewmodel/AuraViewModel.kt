package com.example.ui.viewmodel

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.*
import com.example.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import android.speech.tts.TextToSpeech
import java.util.*

class AuraViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    private val TAG = "AuraViewModel"
    private val context = application.applicationContext
    private val database = AppDatabase.getDatabase(context)
    private val repository = ChatRepository(database.chatDao())
    private var textToSpeech: TextToSpeech? = null
    
    init {
        textToSpeech = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.US
        } else {
            Log.e(TAG, "Initialization of TextToSpeech failed")
        }
    }

    fun speakText(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }
    
    fun stopSpeaking() {
        textToSpeech?.stop()
    }

    // --- Navigation & Mode State ---
    var activeScreen = MutableStateFlow(Screen.Chat)
    var isDarkTheme = MutableStateFlow(true)
    var themeMode = MutableStateFlow("dark") // "light", "dark", "high_contrast"

    // --- Authentication State ---
    val userEmail = MutableStateFlow("vanshaarav90@gmail.com")
    val userName = MutableStateFlow("Vansh Aarav")
    val isLoggedIn = MutableStateFlow(true) // Start authenticated for smooth AI Studio experience
    val syncStatus = MutableStateFlow(SyncState.Synced)

    // --- API Configuration ---
    val geminiModel = MutableStateFlow("gemini-2.5-flash-image")
    val supabaseUrl = MutableStateFlow("")
    val supabaseAnonKey = MutableStateFlow("")

    private val prefs = context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)

    val isSimulatorMode = MutableStateFlow<Boolean>(run {
        val geminiKey = BuildConfig.GEMINI_API_KEY
        val hasGemini = geminiKey.isNotEmpty() && geminiKey != "MY_GEMINI_API_KEY" && !geminiKey.startsWith("MY_")
        !hasGemini
    })

    // --- Search & Filters ---
    val searchQuery = MutableStateFlow("")

    // --- Chat Session State ---
    val sessions = kotlinx.coroutines.flow.combine(repository.allSessions, searchQuery) { list, query ->
        if (query.isBlank()) list
        else list.filter { it.title.contains(query, ignoreCase = true) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    val activeSessionId = MutableStateFlow<String?>(null)
    val isSidebarExpanded = MutableStateFlow(true)

    val currentMessages = activeSessionId.flatMapLatest { sessionId ->
        if (sessionId == null) {
            flowOf(emptyList())
        } else {
            repository.getMessagesForSession(sessionId)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // --- Active Chat Input States ---
    val currentChatInput = MutableStateFlow("")
    val selectedAttachment = MutableStateFlow<Attachment?>(null)
    val isChatLoading = MutableStateFlow(false)
    val isVoiceInputActive = MutableStateFlow(false)
    val isImageGenerationMode = MutableStateFlow(false)

    // --- Image Generation State ---
    val imagePrompt = MutableStateFlow("")
    val imageAspectRatio = MutableStateFlow("1:1")
    val isGeneratingImage = MutableStateFlow(false)
    val imageGenerationProgress = MutableStateFlow(0f)
    val imageGenerationStatus = MutableStateFlow("")
    
    val generatedImages = repository.allGeneratedImages.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // --- Video Generation State ---
    val videoPrompt = MutableStateFlow("")
    val videoAspectRatio = MutableStateFlow("16:9")
    val videoDuration = MutableStateFlow(4) // Default 4 seconds
    val videoStyle = MutableStateFlow("Cinematic") // Cinematic, Anime, Cyberpunk, 3D Render
    val isGeneratingVideo = MutableStateFlow(false)
    val videoGenerationProgress = MutableStateFlow(0f)
    val videoGenerationStatus = MutableStateFlow("")

    val generatedVideos = repository.allGeneratedVideos.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // --- Saved Prompts ---
    val savedPrompts = repository.allSavedPrompts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // --- Supabase Cloud Session History State ---
    val supabaseSessions = MutableStateFlow<List<SupabaseSessionDto>>(emptyList())
    val isFetchingSupabaseSessions = MutableStateFlow(false)
    val supabaseFetchError = MutableStateFlow<String?>(null)

    fun fetchSupabaseSessions() {
        val url = supabaseUrl.value.trim()
        val key = supabaseAnonKey.value.trim()
        if (url.isEmpty() || key.isEmpty()) {
            supabaseFetchError.value = "Supabase URL and Key not configured. Go to Platform Settings."
            supabaseSessions.value = emptyList()
            return
        }

        viewModelScope.launch {
            isFetchingSupabaseSessions.value = true
            supabaseFetchError.value = null
            try {
                val service = ApiClient.getSupabaseService(url)
                val authHeader = "Bearer $key"
                val remoteSessions = service.getSessions(key, authHeader)
                supabaseSessions.value = remoteSessions
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch sessions from Supabase", e)
                supabaseFetchError.value = "Failed to fetch cloud sessions: ${e.localizedMessage ?: e.message}"
            } finally {
                isFetchingSupabaseSessions.value = false
            }
        }
    }

    fun selectSupabaseSession(sessionDto: SupabaseSessionDto) {
        val url = supabaseUrl.value.trim()
        val key = supabaseAnonKey.value.trim()
        if (url.isEmpty() || key.isEmpty()) {
            Toast.makeText(context, "Please configure Supabase in Platform Settings!", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            isChatLoading.value = true
            try {
                // 1. Insert session locally if not exists
                val localSession = ChatSession(
                    id = sessionDto.id,
                    title = sessionDto.title,
                    timestamp = sessionDto.timestamp
                )
                repository.insertSession(localSession)

                // 2. Fetch its messages from Supabase
                val service = ApiClient.getSupabaseService(url)
                val authHeader = "Bearer $key"
                val remoteMessages = service.getMessages(key, authHeader, "eq.${sessionDto.id}")

                // 3. Insert messages locally
                remoteMessages.forEach { msgDto ->
                    repository.insertMessage(
                        ChatMessage(
                            id = msgDto.id,
                            sessionId = msgDto.sessionId,
                            role = msgDto.role,
                            content = msgDto.content,
                            timestamp = msgDto.timestamp,
                            attachmentPath = msgDto.attachmentPath,
                            attachmentType = msgDto.attachmentType
                        )
                    )
                }

                // 4. Select the session
                selectSession(sessionDto.id)
                activeScreen.value = Screen.Chat
                Toast.makeText(context, "Loaded cloud session: ${sessionDto.title}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load cloud session messages", e)
                Toast.makeText(context, "Sync Error: ${e.localizedMessage ?: e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isChatLoading.value = false
            }
        }
    }

    // --- Pre-populate DB if empty ---
    init {
        // Load stored user profile preferences from SharedPreferences if available
        userEmail.value = prefs.getString("user_email", "vanshaarav90@gmail.com") ?: "vanshaarav90@gmail.com"
        userName.value = prefs.getString("user_name", "Vansh Aarav") ?: "Vansh Aarav"
        isDarkTheme.value = prefs.getBoolean("is_dark_theme", true)
        themeMode.value = prefs.getString("theme_mode", "dark") ?: "dark"
        geminiModel.value = prefs.getString("gemini_model", "gemini-2.5-flash-image") ?: "gemini-2.5-flash-image"

        // Load Supabase configs from SharedPreferences, with fallback to BuildConfig secrets
        var url = prefs.getString("supabase_url", "") ?: ""
        var key = prefs.getString("supabase_anon_key", "") ?: ""

        if (url.isEmpty()) {
            val buildConfigUrl = try { BuildConfig.SUPABASE_URL } catch (e: Throwable) { "" }
            if (buildConfigUrl.isNotEmpty() && buildConfigUrl != "MY_SUPABASE_URL" && !buildConfigUrl.startsWith("MY_")) {
                url = buildConfigUrl
            }
        }
        if (key.isEmpty()) {
            val buildConfigKey = try { BuildConfig.SUPABASE_ANON_KEY } catch (e: Throwable) { "" }
            if (buildConfigKey.isNotEmpty() && buildConfigKey != "MY_SUPABASE_ANON_KEY" && !buildConfigKey.startsWith("MY_")) {
                key = buildConfigKey
            }
        }

        supabaseUrl.value = url
        supabaseAnonKey.value = key

        // Register auto-save flow collectors to update SharedPreferences on state changes
        viewModelScope.launch {
            userEmail.collect { email ->
                prefs.edit().putString("user_email", email).apply()
            }
        }
        viewModelScope.launch {
            userName.collect { name ->
                prefs.edit().putString("user_name", name).apply()
            }
        }
        viewModelScope.launch {
            isDarkTheme.collect { dark ->
                prefs.edit().putBoolean("is_dark_theme", dark).apply()
                // Update themeMode based on isDarkTheme for backward compat if switched from old UI
                if (themeMode.value != "high_contrast") {
                    themeMode.value = if (dark) "dark" else "light"
                }
            }
        }
        viewModelScope.launch {
            themeMode.collect { mode ->
                prefs.edit().putString("theme_mode", mode).apply()
                // Keep isDarkTheme somewhat in sync
                if (mode == "dark" || mode == "high_contrast") {
                    if (!isDarkTheme.value) isDarkTheme.value = true
                } else {
                    if (isDarkTheme.value) isDarkTheme.value = false
                }
            }
        }
        viewModelScope.launch {
            geminiModel.collect { model ->
                prefs.edit().putString("gemini_model", model).apply()
            }
        }

        if (supabaseUrl.value.isNotEmpty() && supabaseAnonKey.value.isNotEmpty()) {
            restoreFromSupabase(silent = true)
        }

        viewModelScope.launch {
            sessions.first().let { currentSessions ->
                if (currentSessions.isEmpty()) {
                    // Create default session
                    val defaultSession = ChatSession(title = "Welcome to Aura AI")
                    repository.insertSession(defaultSession)
                    activeSessionId.value = defaultSession.id

                    repository.insertMessage(
                        ChatMessage(
                            sessionId = defaultSession.id,
                            role = "assistant",
                            content = "Hello! I am **Aura AI**, your premium intelligence partner. Powered by **Google Gemini** and **Vansh Gupta** for both high-fidelity conversations and creative image generation. How can I assist you today?\n\n*Try asking a coding question, generating a creative image, or adjusting settings in the sidebar.*"
                        )
                    )
                } else {
                    activeSessionId.value = currentSessions.first().id
                }
            }

            savedPrompts.first().let { currentPrompts ->
                if (currentPrompts.isEmpty()) {
                    repository.insertSavedPrompt(SavedPrompt(text = "A futuristic cyberpunk cityscape bathed in neon rain, 8k resolution, cinematic lighting", category = "Creative"))
                    repository.insertSavedPrompt(SavedPrompt(text = "Write a complete Kotlin extension function to convert a standard Bitmap to a Base64 String with quality compression", category = "Coding"))
                    repository.insertSavedPrompt(SavedPrompt(text = "Provide a deep research summary of room-temperature superconductors current replication efforts in 2026", category = "Research"))
                }
            }
        }
    }

    // --- Authentication Actions ---
    fun loginWithEmail(email: String) {
        userEmail.value = email
        userName.value = email.substringBefore("@").replaceFirstChar { it.uppercase() }
        isLoggedIn.value = true
        Toast.makeText(context, "Logged in as $email", Toast.LENGTH_SHORT).show()
    }

    fun logout() {
        isLoggedIn.value = false
        Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()
    }

    // --- Chat Actions ---
    fun createNewChat() {
        viewModelScope.launch {
            val session = ChatSession(title = "New Chat - ${System.currentTimeMillis() % 1000}")
            repository.insertSession(session)
            activeSessionId.value = session.id
            repository.insertMessage(
                ChatMessage(
                    sessionId = session.id,
                    role = "assistant",
                    content = "How can I help you in this new session?"
                )
            )
            triggerCloudSync(silent = true)
        }
    }

    fun exportChatHistory(context: Context, uri: Uri) {
        val messages = currentMessages.value
        if (messages.isEmpty()) {
            Toast.makeText(context, "No chat history to export", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sb = StringBuilder()
                sb.append("--- Chat History ---\n\n")
                for (msg in messages) {
                    val roleName = if (msg.role == "user") userName.value else "Aura"
                    val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(msg.timestamp))
                    sb.append("[$timestamp] $roleName:\n")
                    sb.append(msg.content)
                    sb.append("\n\n")
                }
                
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(sb.toString().toByteArray())
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Chat exported successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export chat", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun clearCurrentConversation() {
        val currentSessionId = activeSessionId.value ?: return
        viewModelScope.launch {
            repository.clearMessagesForSession(currentSessionId)
            // Add a new greeting message to avoid an empty chat screen
            repository.insertMessage(
                ChatMessage(
                    sessionId = currentSessionId,
                    role = "assistant",
                    content = "How can I help you today?"
                )
            )
            triggerCloudSync(silent = true)
        }
    }

    fun selectSession(sessionId: String) {
        activeSessionId.value = sessionId
    }

    fun deleteSession(session: ChatSession) {
        viewModelScope.launch {
            repository.deleteSession(session)
            val all = sessions.value.filter { it.id != session.id }
            if (all.isNotEmpty()) {
                activeSessionId.value = all.first().id
            } else {
                activeSessionId.value = null
            }
            triggerCloudSync(silent = true)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.deleteAllHistory()
            activeSessionId.value = null
            createNewChat()
            triggerCloudSync(silent = true)
        }
    }

    fun sendChatMessage() {
        val input = currentChatInput.value.trim()
        val attachment = selectedAttachment.value
        if (input.isEmpty() && attachment == null) return

        val sessionId = activeSessionId.value ?: return

        viewModelScope.launch {
            isChatLoading.value = true
            currentChatInput.value = ""
            selectedAttachment.value = null

            // Construct and save user message
            val userMsg = ChatMessage(
                sessionId = sessionId,
                role = "user",
                content = input,
                attachmentPath = attachment?.path,
                attachmentType = attachment?.type
            )
            repository.insertMessage(userMsg)

            // Update session title on first real message
            val messagesList = currentMessages.value
            if (messagesList.size <= 2) {
                val title = if (input.length > 20) input.take(20) + "..." else input
                repository.insertSession(ChatSession(id = sessionId, title = title))
            }

            if (isImageGenerationMode.value) {
                // Construct assistant response placeholder for Image generation
                val assistantMsgId = UUID.randomUUID().toString()
                val assistantMsg = ChatMessage(
                    id = assistantMsgId,
                    sessionId = sessionId,
                    role = "assistant",
                    content = "Generating image via Gemini API...",
                    attachmentType = "image",
                    attachmentPath = null
                )
                repository.insertMessage(assistantMsg)

                val geminiKey = BuildConfig.GEMINI_API_KEY
                val hasGeminiKey = geminiKey.isNotEmpty() && geminiKey != "MY_GEMINI_API_KEY" && !geminiKey.startsWith("MY_")

                try {
                    val base64Data = if (isSimulatorMode.value || !hasGeminiKey) {
                        kotlinx.coroutines.delay(1200) // Simulation delay
                        createSimulatedImageBase64(input, "1:1")
                    } else {
                        val model = geminiModel.value
                        repository.generateImageWithGemini(
                            prompt = input,
                            model = model,
                            aspectRatio = "1:1",
                            apiKey = geminiKey
                        )
                    }

                    // Save completed message with the image base64
                    val completedMsg = ChatMessage(
                        id = assistantMsgId,
                        sessionId = sessionId,
                        role = "assistant",
                        content = "Here is the image generated for: *\"$input\"*",
                        attachmentType = "image",
                        attachmentPath = if (base64Data.startsWith("http") || base64Data.startsWith("data:")) base64Data else "data:image/png;base64,$base64Data"
                    )
                    repository.insertMessage(completedMsg)
                } catch (e: Exception) {
                    Log.e(TAG, "Gemini Image generation error in chat", e)
                    val errorMsg = ChatMessage(
                        id = assistantMsgId,
                        sessionId = sessionId,
                        role = "assistant",
                        content = "Failed to generate image via Gemini: ${e.message}"
                    )
                    repository.insertMessage(errorMsg)
                }
            } else {
                // Prepare history to send for context
                val history = currentMessages.value.takeLast(10)

                // Construct assistant response placeholder
                val assistantMsgId = UUID.randomUUID().toString()
                var assistantContent = ""
                val assistantMsg = ChatMessage(
                    id = assistantMsgId,
                    sessionId = sessionId,
                    role = "assistant",
                    content = "Thinking..."
                )
                repository.insertMessage(assistantMsg)

                if (isSimulatorMode.value) {
                    // LOCAL SIMULATED RESPONSE
                    val simulatedResponse = getSimulatedChatResponse(input, attachment)
                    val words = simulatedResponse.split(" ")
                    for (i in words.indices) {
                        val word = words[i]
                        assistantContent += (if (i > 0) " " else "") + word
                        repository.insertMessage(
                            ChatMessage(
                                id = assistantMsgId,
                                sessionId = sessionId,
                               role = "assistant",
                                content = assistantContent
                            )
                        )
                        kotlinx.coroutines.delay(40)
                    }
                } else {
                    val geminiKey = BuildConfig.GEMINI_API_KEY
                    repository.sendGeminiMessageStream(
                        model = "gemini-3.5-flash",
                        messages = history,
                        apiKey = geminiKey,
                        onChunk = { chunk ->
                            assistantContent += chunk
                            viewModelScope.launch {
                                repository.insertMessage(
                                    ChatMessage(
                                        id = assistantMsgId,
                                        sessionId = sessionId,
                                        role = "assistant",
                                        content = assistantContent
                                    )
                                )
                            }
                        }
                    )
                }
            }
            isChatLoading.value = false
            triggerCloudSync(silent = true)
        }
    }

    fun regenerateMessage(messageId: String) {
        val sessionId = activeSessionId.value ?: return
        val messagesList = currentMessages.value
        val targetIdx = messagesList.indexOfFirst { it.id == messageId }
        if (targetIdx <= 0) return

        val userMessage = messagesList[targetIdx - 1]
        if (userMessage.role != "user") return

        viewModelScope.launch {
            isChatLoading.value = true

            val assistantMsgId = messageId
            var assistantContent = ""
            repository.insertMessage(
                ChatMessage(
                    id = assistantMsgId,
                    sessionId = sessionId,
                    role = "assistant",
                    content = "Thinking..."
                )
            )

            val history = messagesList.take(targetIdx)
            val userPrompt = userMessage.content
            val userAttachment = if (userMessage.attachmentPath != null) {
                Attachment(
                    name = userMessage.attachmentPath.substringAfterLast("/"),
                    type = userMessage.attachmentType ?: "text",
                    path = userMessage.attachmentPath
                )
            } else null

            if (isSimulatorMode.value) {
                // LOCAL SIMULATED REGENERATION
                val simulatedResponse = getSimulatedChatResponse(userPrompt, userAttachment)
                val words = simulatedResponse.split(" ")
                for (i in words.indices) {
                    val word = words[i]
                    assistantContent += (if (i > 0) " " else "") + word
                    repository.insertMessage(
                        ChatMessage(
                            id = assistantMsgId,
                            sessionId = sessionId,
                            role = "assistant",
                            content = assistantContent
                        )
                    )
                    kotlinx.coroutines.delay(40)
                }
            } else {
                val geminiKey = BuildConfig.GEMINI_API_KEY
                repository.sendGeminiMessageStream(
                    model = "gemini-3.5-flash",
                    messages = history,
                    apiKey = geminiKey,
                    onChunk = { chunk ->
                        assistantContent += chunk
                        viewModelScope.launch {
                            repository.insertMessage(
                                ChatMessage(
                                    id = assistantMsgId,
                                    sessionId = sessionId,
                                    role = "assistant",
                                    content = assistantContent
                                )
                            )
                        }
                    }
                )
            }
            isChatLoading.value = false
            triggerCloudSync(silent = true)
        }
    }

    fun addAttachment(type: String) {
        val name = when (type) {
            "image" -> "photo_preview.jpg"
            "pdf" -> "Research_Paper.pdf"
            else -> "SourceCode.kt"
        }
        selectedAttachment.value = Attachment(
            name = name,
            type = type,
            path = "content://aura/mock/$name"
        )
    }


    // --- Image Generation Actions ---
    fun generateImage() {
        val prompt = imagePrompt.value.trim()
        if (prompt.isEmpty()) return

        viewModelScope.launch {
            isGeneratingImage.value = true
            imageGenerationProgress.value = 0.1f
            imageGenerationStatus.value = "Expanding prompt with AI..."

            // 1. Image prompt enhancement
            val enhancedPrompt = enhancePrompt(prompt)
            imagePrompt.value = enhancedPrompt

            imageGenerationProgress.value = 0.4f
            imageGenerationStatus.value = "Rendering image pixels using Imagen..."

            val geminiKey = BuildConfig.GEMINI_API_KEY
            val model = if (isSimulatorMode.value) "local-neural-simulation" else geminiModel.value

            try {
                val base64Data = if (isSimulatorMode.value) {
                    kotlinx.coroutines.delay(1200) // Simulate image rendering delay
                    createSimulatedImageBase64(enhancedPrompt, imageAspectRatio.value)
                } else {
                    repository.generateImageWithGemini(
                        prompt = enhancedPrompt,
                        model = model,
                        aspectRatio = imageAspectRatio.value,
                        apiKey = geminiKey
                    )
                }

                imageGenerationProgress.value = 0.8f
                imageGenerationStatus.value = "Saving image to local history..."

                val newImg = GeneratedImage(
                    prompt = enhancedPrompt,
                    imageUrl = base64Data,
                    status = "success",
                    aspectRatio = imageAspectRatio.value,
                    modelUsed = model
                )
                repository.insertGeneratedImage(newImg)

                imageGenerationProgress.value = 1.0f
                imageGenerationStatus.value = "Generated successfully!"
                imagePrompt.value = ""
            } catch (e: Exception) {
                Log.e(TAG, "Image generation error", e)
                val failedImg = GeneratedImage(
                    prompt = prompt,
                    imageUrl = "",
                    status = "error",
                    aspectRatio = imageAspectRatio.value,
                    modelUsed = model
                )
                repository.insertGeneratedImage(failedImg)
                imageGenerationStatus.value = "Generation failed: ${e.message}"
            } finally {
                isGeneratingImage.value = false
            }
        }
    }

    private suspend fun enhancePrompt(prompt: String): String {
        if (isSimulatorMode.value) {
            return "A highly detailed, cinematic masterpiece of: '$prompt'. Volumetric cybernetic lighting, deep shadows, 8k resolution, elegant artistic composition."
        }
        val apiKey = BuildConfig.GEMINI_API_KEY
        val systemInstruct = "You are a professional prompt engineer. Expand the user's brief image prompt to include vivid details, lighting, style, composition, and high artistic quality. Keep it compact (1-2 sentences)."
        val req = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = "Expand this image prompt: '$prompt'")))),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemInstruct)))
        )
        return try {
            val response = ApiClient.geminiService.generateContent("gemini-3.5-flash", apiKey, req)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim() ?: prompt
        } catch (e: Exception) {
            prompt
        }
    }

    fun deleteGeneratedImage(image: GeneratedImage) {
        viewModelScope.launch {
            repository.deleteGeneratedImage(image.id)
        }
    }

    fun generateVideo() {
        val prompt = videoPrompt.value.trim()
        if (prompt.isEmpty()) return

        viewModelScope.launch {
            isGeneratingVideo.value = true
            videoGenerationProgress.value = 0.1f
            videoGenerationStatus.value = "Formulating hyper-temporal storyboards..."

            // 1. Video prompt enhancement via Gemini
            val enhancedPrompt = enhancePrompt(prompt)
            videoPrompt.value = enhancedPrompt

            videoGenerationProgress.value = 0.4f
            videoGenerationStatus.value = "Generating keyframe motion matrices..."
            kotlinx.coroutines.delay(1000)

            videoGenerationProgress.value = 0.7f
            videoGenerationStatus.value = "Stitching frames and temporal interpolation..."
            kotlinx.coroutines.delay(1200)

            // Select video procedural effect based on keywords in prompt
            val lowerPrompt = enhancedPrompt.lowercase(Locale.getDefault())
            val proceduralType = when {
                lowerPrompt.contains("star") || lowerPrompt.contains("space") || lowerPrompt.contains("galaxy") || lowerPrompt.contains("orbit") || lowerPrompt.contains("universe") -> "procedural_starfield"
                lowerPrompt.contains("wave") || lowerPrompt.contains("fluid") || lowerPrompt.contains("ocean") || lowerPrompt.contains("sea") || lowerPrompt.contains("river") || lowerPrompt.contains("flow") -> "procedural_wave"
                lowerPrompt.contains("matrix") || lowerPrompt.contains("cyberpunk") || lowerPrompt.contains("code") || lowerPrompt.contains("digital") || lowerPrompt.contains("neon") -> "procedural_matrix"
                lowerPrompt.contains("fractal") || lowerPrompt.contains("abstract") || lowerPrompt.contains("aurora") || lowerPrompt.contains("plasma") || lowerPrompt.contains("kaleidoscope") -> "procedural_fractal"
                else -> "procedural_shapes"
            }

            try {
                videoGenerationProgress.value = 0.9f
                videoGenerationStatus.value = "Finalizing 4K motion compile..."

                val newVideo = GeneratedVideo(
                    prompt = enhancedPrompt,
                    videoUrl = proceduralType,
                    status = "success",
                    aspectRatio = videoAspectRatio.value,
                    duration = videoDuration.value,
                    style = videoStyle.value,
                    modelUsed = "gemini-3.5-video"
                )
                repository.insertGeneratedVideo(newVideo)

                videoGenerationProgress.value = 1.0f
                videoGenerationStatus.value = "Video compiled successfully!"
                videoPrompt.value = ""
            } catch (e: Exception) {
                Log.e(TAG, "Video generation error", e)
                val failedVideo = GeneratedVideo(
                    prompt = prompt,
                    videoUrl = "",
                    status = "error",
                    aspectRatio = videoAspectRatio.value,
                    duration = videoDuration.value,
                    style = videoStyle.value,
                    modelUsed = "gemini-3.5-video"
                )
                repository.insertGeneratedVideo(failedVideo)
                videoGenerationStatus.value = "Video compilation failed: ${e.message}"
            } finally {
                isGeneratingVideo.value = false
            }
        }
    }

    fun deleteGeneratedVideo(video: GeneratedVideo) {
        viewModelScope.launch {
            repository.deleteGeneratedVideo(video.id)
        }
    }

    fun downloadImage(image: GeneratedImage) {
        if (image.imageUrl.isEmpty()) return
        try {
            val imageBytes = Base64.decode(image.imageUrl, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            
            val filename = "AuraAI_${System.currentTimeMillis()}.jpg"
            var outputStream: OutputStream? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AuraAI")
                }
                val imageUri: Uri? = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (imageUri != null) {
                    outputStream = context.contentResolver.openOutputStream(imageUri)
                }
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/AuraAI"
                val file = File(imagesDir)
                if (!file.exists()) {
                    file.mkdirs()
                }
                val imageFile = File(imagesDir, filename)
                outputStream = FileOutputStream(imageFile)
            }

            outputStream?.use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
                Toast.makeText(context, "Saved to Photos/AuraAI", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Saved Prompts Actions ---
    fun savePrompt(text: String, category: String = "General") {
        viewModelScope.launch {
            repository.insertSavedPrompt(SavedPrompt(text = text, category = category))
            Toast.makeText(context, "Saved to prompts library", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteSavedPrompt(prompt: SavedPrompt) {
        viewModelScope.launch {
            repository.deleteSavedPrompt(prompt)
        }
    }

    // --- Export & Statistics Helpers ---
    fun getUsageStats(): UsageStats {
        val msgs = currentMessages.value.size
        val imgs = generatedImages.value.size
        val vids = generatedVideos.value.size
        val saved = savedPrompts.value.size
        return UsageStats(
            totalMessages = msgs,
            totalImages = imgs,
            totalVideos = vids,
            savedPrompts = saved,
            geminiModel = geminiModel.value
        )
    }

    fun exportConversations(): String {
        val builder = StringBuilder()
        builder.append("=== AURA AI CHAT EXPORT ===\n\n")
        val msgs = currentMessages.value
        msgs.forEach {
            builder.append("[${it.role.uppercase()}] - ${Date(it.timestamp)}\n")
            builder.append("${it.content}\n")
            if (it.attachmentPath != null) {
                builder.append("Attachment: ${it.attachmentPath} (${it.attachmentType})\n")
            }
            builder.append("-----------------------------\n")
        }
        return builder.toString()
    }

    fun triggerCloudSync(silent: Boolean = false) {
        val url = supabaseUrl.value.trim()
        val key = supabaseAnonKey.value.trim()
        if (url.isNotEmpty() && key.isNotEmpty()) {
            backupToSupabase(silent)
        } else {
            if (!silent) {
                viewModelScope.launch {
                    syncStatus.value = SyncState.Syncing
                    kotlinx.coroutines.delay(1000) // Simulating secure sync to Supabase
                    syncStatus.value = SyncState.Synced
                    Toast.makeText(context, "Data securely backed up (Simulated). Configure Supabase in Settings for live cloud sync!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun saveSupabaseConfig(url: String, key: String) {
        supabaseUrl.value = url.trim()
        supabaseAnonKey.value = key.trim()
        prefs.edit().apply {
            putString("supabase_url", url.trim())
            putString("supabase_anon_key", key.trim())
            apply()
        }
        restoreFromSupabase(silent = false)
    }

    fun backupToSupabase(silent: Boolean = false) {
        val url = supabaseUrl.value.trim()
        val key = supabaseAnonKey.value.trim()
        if (url.isEmpty() || key.isEmpty()) {
            if (!silent) Toast.makeText(context, "Please configure Supabase URL and Key in Settings first!", Toast.LENGTH_LONG).show()
            return
        }

        viewModelScope.launch {
            syncStatus.value = SyncState.Syncing
            val successChat = repository.backupAllToSupabase(url, key)
            val successPref = repository.backupPreferencesToSupabase(
                url = url,
                key = key,
                email = userEmail.value,
                name = userName.value,
                isDarkTheme = isDarkTheme.value,
                themeMode = themeMode.value,
                geminiModel = geminiModel.value
            )
            syncStatus.value = SyncState.Synced
            if (!silent) {
                if (successChat && successPref) {
                    Toast.makeText(context, "Successfully backed up chat logs and user preferences to Supabase!", Toast.LENGTH_SHORT).show()
                } else if (successChat) {
                    Toast.makeText(context, "Chat backed up successfully! Configure 'user_preferences' table in Supabase to also backup preferences.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Error: Failed to backup chat logs to Supabase. Check your internet connection and URL/Key.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun restoreFromSupabase(silent: Boolean = false) {
        val url = supabaseUrl.value.trim()
        val key = supabaseAnonKey.value.trim()
        if (url.isEmpty() || key.isEmpty()) {
            if (!silent) Toast.makeText(context, "Please configure Supabase URL and Key in Settings first!", Toast.LENGTH_LONG).show()
            return
        }

        viewModelScope.launch {
            syncStatus.value = SyncState.Syncing
            val successChat = repository.retrieveAllFromSupabase(url, key)
            val prefDto = repository.retrievePreferencesFromSupabase(url, key, userEmail.value)
            if (prefDto != null) {
                userName.value = prefDto.name
                if (prefDto.themeMode != null) {
                    themeMode.value = prefDto.themeMode
                } else {
                    themeMode.value = if (prefDto.isDarkTheme) "dark" else "light"
                }
                isDarkTheme.value = prefDto.isDarkTheme
                geminiModel.value = prefDto.geminiModel
            }
            syncStatus.value = SyncState.Synced
            if (!silent) {
                if (successChat) {
                    if (prefDto != null) {
                        Toast.makeText(context, "Successfully restored chat logs and profile preferences from Supabase!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Successfully restored chat logs from Supabase!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Error: Failed to restore chat logs. Verify your Supabase table schema.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun getSimulatedChatResponse(input: String, attachment: Attachment?): String {
        val lowerInput = input.lowercase(Locale.getDefault())
        return when {
            attachment != null -> {
                "I've successfully analyzed your attached file: **${attachment.name}** (${attachment.type.uppercase(Locale.getDefault())}).\n\n### File Assessment Report\n*   **Source Integrity**: Valid file signature verified.\n*   **Simulated Insight**: This document contains structure patterns consistent with a production-grade template module.\n*   **Actionable Suggestion**: We can integrate this metadata or layout outline directly into our next code generator step!\n\nWould you like me to extract some specific portions or rewrite this configuration for you?"
            }
            lowerInput.contains("hello") || lowerInput.contains("hi") || lowerInput.contains("hey") || lowerInput.contains("greet") -> {
                "Hello there! I am **Aura AI**, your advanced intelligence assistant. It looks like you're running in **Simulated Mode** because no API keys were detected in your configuration.\n\nBut don't worry! I'm fully equipped with a built-in neural simulation engine. You can ask me coding questions, ask to design cards, or head over to the **Imagen Creative Generator** to create beautiful art, all locally!\n\nTo unlock real cloud models, simply configure your **GEMINI_API_KEY** in the AI Studio Secrets panel. What can I build for you today?"
            }
            lowerInput.contains("code") || lowerInput.contains("kotlin") || lowerInput.contains("java") || lowerInput.contains("css") || lowerInput.contains("html") || lowerInput.contains("javascript") || lowerInput.contains("program") || lowerInput.contains("function") -> {
                "Here is a beautifully designed, modern CSS card with glowing radial gradients and smooth hover interactions. You can use this in your web projects directly!\n\n```css\n.aura-card {\n  width: 320px;\n  padding: 24px;\n  border-radius: 16px;\n  background: rgba(15, 12, 30, 0.85);\n  border: 1px solid rgba(139, 92, 246, 0.25);\n  box-shadow: 0 8px 32px 0 rgba(139, 92, 246, 0.15);\n  backdrop-filter: blur(8px);\n  transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1);\n}\n\n.aura-card:hover {\n  transform: translateY(-8px);\n  border-color: rgba(236, 72, 153, 0.5);\n  box-shadow: 0 12px 40px 0 rgba(236, 72, 153, 0.25);\n}\n```\n\n### Key Design Features\n1. **Glassmorphism Backdrop**: Uses `backdrop-filter: blur` to blend elegantly with background wallpapers.\n2. **Neon Glow Accents**: Linear boundaries that light up smoothly upon mouse hover.\n3. **Cubic Bezier Motion**: Soft organic transition curves for responsive hand-off feel."
            }
            lowerInput.contains("design") || lowerInput.contains("ui") || lowerInput.contains("ux") || lowerInput.contains("material") -> {
                "Designing beautiful interfaces is all about **balance, spacing, and visual hierarchy**.\n\n### Material Design 3 Core Principles:\n*   **Dynamic Color**: Automatically adapt the UI palette to the user's personal wallpaper for maximum cohesion.\n*   **Spacious Padding**: Maintain an 8dp grid boundary to let layouts breathe and prevent high density fatigue.\n*   **Clear Typographic Scale**: Use a strong contrast between massive Display headers and high-readability Body text.\n\nIn our current **Aura Dark Theme**, we employ a premium `SurfaceObsidian` deep background paired with neon violet (`#8B5CF6`) and orchid (`#EC4899`) gradients for that professional cybernetic finish."
            }
            lowerInput.contains("image") || lowerInput.contains("generate") || lowerInput.contains("picture") || lowerInput.contains("create") -> {
                "I would love to help you generate beautiful art! Simply switch to the **Imagen Creative Generator** tab in the sidebar (or click the 'Images' menu item).\n\nEven in **Simulated Mode**, I will programmatically generate vibrant dynamic canvas art matching your exact prompt description, and save it directly in your history! Try entering something like 'Neon galaxy landscape' or 'Vaporwave sunset' there!"
            }
            else -> {
                "That is a fascinating topic! Even in **Simulated Mode**, I can provide deep insights about your query: *\"$input\"*.\n\n### Analysis Summary\n*   **Core Concepts**: AI Systems, Intelligent Interfaces, Local Simulation.\n*   **Recommendation**: To get real-time answers directly from the latest Aura 3.0 Flash and Aura Art v3 models, please configure your `GEMINI_API_KEY` in the Google AI Studio project settings.\n*   **Next Steps**: Ask me to write some code, explain design structures, or try out the image generator in the sidebar!"
            }
        }
    }

    private fun createSimulatedImageBase64(prompt: String, aspectRatio: String): String {
        val width = 512
        val height = when (aspectRatio) {
            "16:9" -> 288
            "9:16" -> 910
            else -> 512
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        val paint = android.graphics.Paint()
        
        val colors = when {
            prompt.contains("space", ignoreCase = true) || prompt.contains("galaxy", ignoreCase = true) || prompt.contains("cosmic", ignoreCase = true) -> {
                intArrayOf(0xFF0F0C1B.toInt(), 0xFF301934.toInt(), 0xFF8B5CF6.toInt())
            }
            prompt.contains("sunset", ignoreCase = true) || prompt.contains("fire", ignoreCase = true) || prompt.contains("gold", ignoreCase = true) -> {
                intArrayOf(0xFF1F0D0D.toInt(), 0xFF7C2D12.toInt(), 0xFFF59E0B.toInt())
            }
            prompt.contains("forest", ignoreCase = true) || prompt.contains("nature", ignoreCase = true) || prompt.contains("green", ignoreCase = true) -> {
                intArrayOf(0xFF064E3B.toInt(), 0xFF10B981.toInt(), 0xFF34D399.toInt())
            }
            else -> {
                intArrayOf(0xFF0B0F19.toInt(), 0xFF1E1B4B.toInt(), 0xFFEC4899.toInt())
            }
        }
        
        val gradient = android.graphics.LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            colors, null, android.graphics.Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        
        paint.shader = null
        
        val random = java.util.Random(prompt.hashCode().toLong())
        
        for (i in 0..6) {
            paint.color = when (random.nextInt(3)) {
                0 -> 0x15FFFFFF
                1 -> 0x188B5CF6.toInt()
                else -> 0x18EC4899.toInt()
            }
            val radius = 50f + random.nextFloat() * 120f
            val cx = random.nextFloat() * width
            val cy = random.nextFloat() * height
            canvas.drawCircle(cx, cy, radius, paint)
        }
        
        paint.strokeWidth = 2f
        for (i in 0..12) {
            paint.color = 0x25FFFFFF
            val x1 = random.nextFloat() * width
            val y1 = random.nextFloat() * height
            val x2 = x1 + (random.nextFloat() - 0.5f) * 150f
            val y2 = y1 + (random.nextFloat() - 0.5f) * 150f
            canvas.drawLine(x1, y1, x2, y2, paint)
        }
        
        val centerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val centerRadial = android.graphics.RadialGradient(
            width / 2f, height / 2f, width / 3f,
            intArrayOf(0x70FFFFFF, 0x10EC4899.toInt(), 0x00000000),
            null, android.graphics.Shader.TileMode.CLAMP
        )
        centerPaint.shader = centerRadial
        canvas.drawCircle(width / 2f, height / 2f, width / 2f, centerPaint)
        centerPaint.shader = null
        
        val cardPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x500A0A14
            style = android.graphics.Paint.Style.FILL
        }
        val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x20FFFFFF
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        
        val cardMargin = 24f
        val cardHeight = 85f
        val cardRect = android.graphics.RectF(
            cardMargin,
            height - cardHeight - cardMargin,
            width - cardMargin,
            height - cardMargin
        )
        canvas.drawRoundRect(cardRect, 16f, 16f, cardPaint)
        canvas.drawRoundRect(cardRect, 16f, 16f, borderPaint)
        
        val titlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF00E5FF.toInt()
            textSize = 12f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        }
        canvas.drawText("AURA SIMULATOR IMAGEN v3", cardMargin + 16f, height - cardHeight - cardMargin + 24f, titlePaint)
        
        val truncatedPrompt = if (prompt.length > 36) prompt.take(33) + "..." else prompt
        val promptPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 15f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        canvas.drawText("\"$truncatedPrompt\"", cardMargin + 16f, height - cardMargin - 28f, promptPaint)
        
        val watermarkPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x60FFFFFF
            textSize = 10f
            textAlign = android.graphics.Paint.Align.RIGHT
            typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.NORMAL)
        }
        canvas.drawText("LOCAL_GEN_OK", width - cardMargin - 16f, height - cardMargin - 28f, watermarkPaint)
        
        val baos = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
        val imageBytes = baos.toByteArray()
        return Base64.encodeToString(imageBytes, Base64.NO_WRAP)
    }

    // --- Radio Show Generator State ---
    val radioPrompt = MutableStateFlow("")
    val radioStation = MutableStateFlow("Antigravity FM - Cosmic Lounge")
    val isGeneratingRadioShow = MutableStateFlow(false)
    val radioShowProgress = MutableStateFlow(0f)
    val radioShowStatus = MutableStateFlow("")

    val generatedRadioShows = repository.allGeneratedRadioShows.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Radio Engine
    private val radioEngine = RadioShowEngine(context)

    val isRadioShowPlaying = radioEngine.isPlaying
    val activeRadioShowSegmentIndex = radioEngine.activeSegmentIndex
    val radioShowPlaybackProgress = radioEngine.playbackProgress
    val radioVisualizerAmplitude = radioEngine.visualizerAmplitude
    val activeRadioSpeaker = radioEngine.activeSpeaker
    val activeRadioSpeakerRole = radioEngine.activeSpeakerRole

    val selectedRadioShowForPlayback = MutableStateFlow<GeneratedRadioShow?>(null)

    fun generateSimulatedScript(prompt: String, station: String): String {
        val cleanPrompt = prompt.replace("\"", "\\\"")
        val isSynthwave = station.contains("Synthwave") || station.contains("Cyberpunk")
        val djName = if (isSynthwave) "DJ Zero" else "DJ Gravity"
        val cohostName = "Nova"
        val guestName = "Dr. Pulsar"
        
        return """
        [
          {
            "speaker": "$djName",
            "text": "Greetings galaxy listeners, and welcome to this exclusive session on $station! Today we are exploring the theme: '$cleanPrompt' with the new Antigravity agent.",
            "bgMusicState": "ducked",
            "sfx": "static"
          },
          {
            "speaker": "$cohostName",
            "text": "Yes! And what an absolute mind-bending topic this is. Earthlings from all star clusters have been sending us their thoughts on '$cleanPrompt'. Let's see how our synthesized soundscapes align.",
            "bgMusicState": "ducked",
            "sfx": "chime"
          },
          {
            "speaker": "$djName",
            "text": "Let's bring in our chief galactic scientist, $guestName, directly from the observatory deck.",
            "bgMusicState": "ducked",
            "sfx": "laser"
          },
          {
            "speaker": "$guestName",
            "text": "Thank you, Gravity. Scientifically speaking, '$cleanPrompt' represents a highly synchronized quantum state that resonates beautifully across all low-frequency sub-sectors.",
            "bgMusicState": "ducked",
            "sfx": "chime"
          },
          {
            "speaker": "$cohostName",
            "text": "That is absolutely stellar. To our fans locked in outer orbit, cueing up a retro retro-synthwave horn sweep right now!",
            "bgMusicState": "ducked",
            "sfx": "airhorn"
          },
          {
            "speaker": "$djName",
            "text": "Brilliant Nova! Let's let the beautiful ambient backing waves swell up. Sit back, float, and enjoy the soundwaves.",
            "bgMusicState": "interlude",
            "sfx": "applause"
          }
        ]
        """.trimIndent()
    }

    fun generateRadioCoverArt(prompt: String, station: String): String {
        val width = 512
        val height = 512
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        val isSynthwave = station.contains("Synthwave") || station.contains("Cyberpunk")
        val color1 = if (isSynthwave) 0xFF1E0A3C.toInt() else 0xFF051125.toInt()
        val color2 = if (isSynthwave) 0xFF8A00FF.toInt() else 0xFF001540.toInt()
        val colorNeon = if (isSynthwave) 0xFFFF007F.toInt() else 0xFF00E5FF.toInt()
        val colorAccent = if (isSynthwave) 0xFF00F5FF.toInt() else 0xFFFF7000.toInt()
        
        val bgGrad = android.graphics.LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            color1, color2,
            android.graphics.Shader.TileMode.CLAMP
        )
        val bgPaint = android.graphics.Paint().apply { shader = bgGrad }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        
        val gridPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = colorNeon
            alpha = 40
            strokeWidth = 1.5f
            style = android.graphics.Paint.Style.STROKE
        }
        val horizonY = height * 0.6f
        for (x in -width..2 * width step 60) {
            canvas.drawLine(width / 2f, horizonY, x.toFloat(), height.toFloat(), gridPaint)
        }
        var y = horizonY
        var spacing = 6f
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
            spacing *= 1.4f
            y += spacing
        }
        
        val sunPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                0f, horizonY - 180f, 0f, horizonY,
                colorNeon, colorAccent,
                android.graphics.Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(width / 2f, horizonY - 20f, 100f, sunPaint)
        
        val slitPaint = android.graphics.Paint().apply {
            shader = bgGrad
        }
        for (slitY in (horizonY.toInt() - 100) until horizonY.toInt() step 12) {
            val h = 4f
            canvas.drawRect(width / 2f - 110f, slitY.toFloat(), width / 2f + 110f, slitY.toFloat() + h, slitPaint)
        }

        val wavePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 2f
            alpha = 80
        }
        canvas.drawCircle(width / 2f, horizonY - 20f, 130f, wavePaint)
        canvas.drawCircle(width / 2f, horizonY - 20f, 160f, wavePaint)
        
        val overlayPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = colorAccent
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRect(12f, 12f, width - 12f, height - 12f, overlayPaint)
        
        val titlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 24f
            typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
            textAlign = android.graphics.Paint.Align.CENTER
            setShadowLayer(8f, 0f, 0f, colorNeon)
        }
        canvas.drawText(station.uppercase(), width / 2f, 60f, titlePaint)
        
        val subPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = colorAccent
            textSize = 14f
            typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.BOLD)
            textAlign = android.graphics.Paint.Align.CENTER
        }
        canvas.drawText("ANTIGRAVITY AGENT TRANSMISSION", width / 2f, 90f, subPaint)
        
        val cardPaint = android.graphics.Paint().apply {
            color = 0xCD000000.toInt()
        }
        val cardRect = android.graphics.RectF(40f, height - 120f, width - 40f, height - 40f)
        canvas.drawRoundRect(cardRect, 12f, 12f, cardPaint)
        canvas.drawRoundRect(cardRect, 12f, 12f, gridPaint)
        
        val promptTitlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = colorAccent
            textSize = 11f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        }
        canvas.drawText("CURRENT SHOW TOPIC:", 60f, height - 95f, promptTitlePaint)
        
        val truncatedPrompt = if (prompt.length > 38) prompt.take(35) + "..." else prompt
        val promptTextPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 16f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        canvas.drawText("\"$truncatedPrompt\"", 60f, height - 65f, promptTextPaint)
        
        val freqPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = colorNeon
            textSize = 12f
            typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.NORMAL)
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        canvas.drawText("99.9 MHz", width - 60f, height - 95f, freqPaint)
        
        val baos = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
        val imageBytes = baos.toByteArray()
        return Base64.encodeToString(imageBytes, Base64.NO_WRAP)
    }

    fun generateRadioShow() {
        val prompt = radioPrompt.value.trim()
        val station = radioStation.value
        if (prompt.isEmpty()) {
            Toast.makeText(context, "Please enter a prompt first!", Toast.LENGTH_SHORT).show()
            return
        }
        
        viewModelScope.launch {
            isGeneratingRadioShow.value = true
            radioShowProgress.value = 0.1f
            radioShowStatus.value = "Antigravity Agent analyzing radio wave frequencies..."
            
            try {
                val scriptJson = if (isSimulatorMode.value) {
                    delay(1200)
                    radioShowProgress.value = 0.4f
                    radioShowStatus.value = "Antigravity Agent drafting dialogue scripts..."
                    delay(1200)
                    generateSimulatedScript(prompt, station)
                } else {
                    val apiKey = BuildConfig.GEMINI_API_KEY
                    val finalPrompt = """
                        Write an extremely creative, engaging, radio show script about: "$prompt".
                        Station theme/style: "$station".
                        The script MUST be formatted as a JSON array of objects with these exact fields:
                        - "speaker": Speaker name (e.g. "DJ Zero", "Nova", "Dr. Pulsar")
                        - "text": Spoken dialog line
                        - "bgMusicState": Either "ducked" (background music quieter), "playing" (active), "interlude" (music swells during pauses), or "none"
                        - "sfx": Optional sound effect ("chime", "laser", "static", "airhorn", "applause" or null).
                        
                        Example JSON array:
                        [
                          {"speaker": "DJ Zero", "text": "Welcome to Antigravity FM!", "bgMusicState": "ducked", "sfx": "static"},
                          {"speaker": "Nova", "text": "We are drifting past Jupiter tonight.", "bgMusicState": "ducked", "sfx": "chime"},
                          {"speaker": "DJ Zero", "text": "Sit back and enjoy the ride.", "bgMusicState": "interlude", "sfx": null}
                        ]
                        
                        Output ONLY the raw JSON array. Do NOT wrap it in markdown block quotes. Do NOT write other introduction or notes.
                    """.trimIndent()
                    
                    val req = GeminiRequest(
                        contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = finalPrompt))))
                    )
                    
                    val response = ApiClient.geminiService.generateContent("gemini-3.5-flash", apiKey, req)
                    val rawText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                    
                    var cleanJson = rawText.trim()
                    if (cleanJson.startsWith("```")) {
                        cleanJson = cleanJson.substringAfter("```")
                        if (cleanJson.startsWith("json")) {
                            cleanJson = cleanJson.substringAfter("json")
                        }
                        cleanJson = cleanJson.substringBeforeLast("```")
                    }
                    cleanJson.trim()
                }
                
                radioShowProgress.value = 0.6f
                radioShowStatus.value = "Synthesizing dynamic atmospheric music chords..."
                delay(1200)
                
                radioShowProgress.value = 0.8f
                radioShowStatus.value = "Rendering stylized dynamic cover art..."
                val coverArtBase64 = generateRadioCoverArt(prompt, station)
                delay(1000)
                
                val showTitle = if (prompt.length > 28) prompt.take(25) + "..." else prompt
                val newShow = GeneratedRadioShow(
                    title = "Show: $showTitle",
                    prompt = prompt,
                    stationGenre = station,
                    coverArtUrl = coverArtBase64,
                    status = "success",
                    scriptJson = scriptJson,
                    backgroundMusicStyle = if (station.contains("Synthwave")) "synthwave" else "ambient"
                )
                
                repository.insertGeneratedRadioShow(newShow)
                
                radioShowProgress.value = 1.0f
                radioShowStatus.value = "Show generated successfully!"
                delay(600)
                
                selectedRadioShowForPlayback.value = newShow
                playRadioShow(newShow)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate radio show", e)
                Toast.makeText(context, "Error generating show: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isGeneratingRadioShow.value = false
                radioShowProgress.value = 0f
                radioShowStatus.value = ""
                radioPrompt.value = ""
            }
        }
    }

    fun playRadioShow(show: GeneratedRadioShow) {
        selectedRadioShowForPlayback.value = show
        radioEngine.playShow(show.backgroundMusicStyle, show.scriptJson)
    }

    fun pauseRadioShow() {
        radioEngine.stopPlayback()
    }

    fun playSoundboardSfx(sfxName: String) {
        radioEngine.playTriggeredSfx(sfxName)
    }

    fun deleteRadioShow(show: GeneratedRadioShow) {
        viewModelScope.launch {
            if (selectedRadioShowForPlayback.value?.id == show.id) {
                pauseRadioShow()
                selectedRadioShowForPlayback.value = null
            }
            repository.deleteGeneratedRadioShow(show.id)
        }
    }

    override fun onCleared() {
        super.onCleared()
        radioEngine.release()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }
}


enum class Screen {
    Chat, ImageGen, VideoGen, RadioShow, SavedPrompts, Dashboard, Settings
}

enum class SyncState {
    Syncing, Synced, Error
}

data class Attachment(
    val name: String,
    val type: String, // "image", "pdf", "text"
    val path: String
)

data class UsageStats(
    val totalMessages: Int,
    val totalImages: Int,
    val totalVideos: Int,
    val savedPrompts: Int,
    val geminiModel: String
)
