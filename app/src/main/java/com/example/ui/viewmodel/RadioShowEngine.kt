package com.example.ui.viewmodel

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import kotlin.math.sin

data class RadioSegment(
    val speaker: String,
    val text: String,
    val bgMusicState: String, // "playing", "ducked", "interlude", "none"
    val sfx: String? = null // "chime", "laser", "static", "airhorn", "applause"
)

class RadioShowEngine(private val context: Context) : TextToSpeech.OnInitListener {

    private val TAG = "RadioShowEngine"
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val musicEngine = ProceduralMusicEngine()
    private val sfxPlayer = SFXPlayer()

    // --- Active Playback State ---
    val isPlaying = MutableStateFlow(false)
    val activeSegmentIndex = MutableStateFlow(-1)
    val playbackProgress = MutableStateFlow(0f)
    val visualizerAmplitude = MutableStateFlow(0.1f)
    val activeSpeaker = MutableStateFlow("")
    val activeSpeakerRole = MutableStateFlow("") // "Host", "Co-host", "Guest"

    private var segments: List<RadioSegment> = emptyList()
    private var playJob: Job? = null
    private var sequencerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { t ->
                t.language = Locale.US
                t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        val index = utteranceId?.toIntOrNull() ?: return
                        activeSegmentIndex.value = index
                        if (index in segments.indices) {
                            val seg = segments[index]
                            activeSpeaker.value = seg.speaker
                            activeSpeakerRole.value = when (seg.speaker.lowercase()) {
                                "host", "dj gravity", "dj zero" -> "Host"
                                "nova", "co-host" -> "Co-host"
                                "pulsar", "guest", "dr. pulsar" -> "Guest"
                                else -> "Host"
                            }
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        // Handled by the sequencer flow
                    }

                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "TTS utterance error: $utteranceId")
                    }
                })
                isTtsReady = true
                Log.d(TAG, "TextToSpeech successfully initialized.")
            }
        } else {
            Log.e(TAG, "Failed to initialize TextToSpeech.")
        }
    }

    fun playShow(genre: String, scriptJson: String) {
        stopPlayback()
        
        segments = parseScript(scriptJson)
        if (segments.isEmpty()) {
            Log.e(TAG, "No valid segments parsed from script JSON")
            return
        }

        isPlaying.value = true
        
        playJob = sequencerScope.launch {
            // 1. Start background music
            musicEngine.startMusic(genre)
            musicEngine.setVolumeSmoothly(0.12f) // Interlude/intro volume
            
            // Visualizer idle oscillation
            val visualizerJob = launch(Dispatchers.Default) {
                var tick = 0f
                while (isActive) {
                    tick += 0.15f
                    // If speaking, oscillate higher. Otherwise, low idle hum.
                    val isSpeaking = activeSpeaker.value.isNotEmpty() && tts?.isSpeaking == true
                    if (isSpeaking) {
                        visualizerAmplitude.value = (0.35f + 0.45f * sin(tick) * sin(tick * 0.7f).coerceIn(-1f, 1f) + (Math.random() * 0.1f).toFloat()).coerceIn(0.1f, 0.95f)
                    } else {
                        visualizerAmplitude.value = (0.05f + 0.08f * sin(tick * 0.4f).coerceIn(-1f, 1f)).coerceIn(0.02f, 0.2f)
                    }
                    delay(50)
                }
            }

            // Small intro interlude delay
            delay(1500)

            for (i in segments.indices) {
                if (!isPlaying.value) break
                val seg = segments[i]
                activeSegmentIndex.value = i
                playbackProgress.value = (i.toFloat() / segments.size)

                // 2. Adjust background music volume based on script
                when (seg.bgMusicState.lowercase()) {
                    "ducked" -> musicEngine.setVolumeSmoothly(0.03f)
                    "playing" -> musicEngine.setVolumeSmoothly(0.09f)
                    "interlude" -> musicEngine.setVolumeSmoothly(0.18f)
                    "none" -> musicEngine.setVolumeSmoothly(0f)
                    else -> musicEngine.setVolumeSmoothly(0.03f)
                }

                // 3. Play SFX if specified
                seg.sfx?.let { sfxName ->
                    if (sfxName.isNotEmpty()) {
                        sfxPlayer.playSfx(sfxName)
                        delay(1200) // Allow SFX to breathe/play
                    }
                }

                if (seg.text.isNotBlank()) {
                    // Adjust TTS voice parameters for different speakers
                    tts?.let { t ->
                        when (seg.speaker.lowercase()) {
                            "nova", "co-host" -> {
                                t.setPitch(1.22f)
                                t.setSpeechRate(1.08f)
                            }
                            "pulsar", "guest", "dr. pulsar" -> {
                                t.setPitch(0.82f)
                                t.setSpeechRate(0.88f)
                            }
                            else -> { // DJ Zero / Host
                                t.setPitch(0.96f)
                                t.setSpeechRate(0.95f)
                            }
                        }

                        val params = Bundle().apply {
                            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, i.toString())
                        }
                        
                        // Speak dialogue line
                        t.speak(seg.text, TextToSpeech.QUEUE_FLUSH, params, i.toString())

                        // Wait until speaking ends or play is cancelled
                        while (t.isSpeaking && isPlaying.value) {
                            delay(100)
                        }
                    }
                }

                // Brief gap between segments
                delay(800)
            }

            // Fade out music at the end
            musicEngine.setVolumeSmoothly(0f)
            delay(1500)
            
            visualizerJob.cancel()
            stopPlayback()
        }
    }

    fun playTriggeredSfx(sfxName: String) {
        sequencerScope.launch {
            sfxPlayer.playSfx(sfxName)
        }
    }

    fun stopPlayback() {
        isPlaying.value = false
        activeSpeaker.value = ""
        activeSpeakerRole.value = ""
        activeSegmentIndex.value = -1
        playbackProgress.value = 0f
        visualizerAmplitude.value = 0.05f
        playJob?.cancel()
        tts?.stop()
        musicEngine.stopMusic()
    }

    fun release() {
        stopPlayback()
        tts?.shutdown()
        sequencerScope.cancel()
    }

    private fun parseScript(json: String): List<RadioSegment> {
        val list = mutableListOf<RadioSegment>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    RadioSegment(
                        speaker = obj.optString("speaker", "Host"),
                        text = obj.optString("text", ""),
                        bgMusicState = obj.optString("bgMusicState", "ducked"),
                        sfx = obj.optString("sfx").let { if (it == "null" || it.isEmpty()) null else it }
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse script JSON: $json", e)
            // Fallback default script
            list.add(RadioSegment("DJ Zero", "This is DJ Zero live in outer space. Antigravity radio brings you the smooth ambient waves.", "ducked", "static"))
            list.add(RadioSegment("Nova", "Welcome back earthlings! It's a wonderful night here on orbit station 9.", "ducked", "chime"))
            list.add(RadioSegment("DJ Zero", "Indeed Nova. Let's let the synthesized chords wash over us as we fly into deep space.", "interlude", null))
        }
        return list
    }
}

// --- Procedural Background Music Synthesizer ---
class ProceduralMusicEngine {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var volume = 0.1f
    private var targetVolume = 0.1f

    fun startMusic(genre: String) {
        if (isPlaying) return
        isPlaying = true

        val notes = when (genre.lowercase()) {
            "ambient", "lounge", "cosmic lounge" -> doubleArrayOf(130.81, 164.81, 196.00, 246.94) // Cmaj7: C3, E3, G3, B3
            "synthwave", "cyberpunk" -> doubleArrayOf(110.00, 130.81, 164.81, 220.00) // Am: A2, C3, E3, A3
            "deep jazz", "jazz" -> doubleArrayOf(146.83, 174.61, 220.00, 261.63) // Dm7: D3, F3, A3, C4
            "comedy", "talk show" -> doubleArrayOf(130.81, 164.81, 196.00, 220.00) // C6: C3, E3, G3, A3
            else -> doubleArrayOf(130.81, 164.81, 196.00, 261.63) // C Major
        }

        Thread {
            val sampleRate = 22050
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val track = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2,
                AudioTrack.MODE_STREAM
            )
            audioTrack = track
            try {
                track.play()
            } catch (e: Exception) {
                Log.e("ProceduralMusicEngine", "AudioTrack play failed", e)
                return@Thread
            }

            val buffer = ShortArray(1024)
            var phase = 0.0
            while (isPlaying) {
                // Smooth interpolation of volume (ducking support)
                if (volume < targetVolume) {
                    volume = (volume + 0.005f).coerceAtMost(targetVolume)
                } else if (volume > targetVolume) {
                    volume = (volume - 0.005f).coerceAtLeast(targetVolume)
                }

                for (i in buffer.indices) {
                    var sample = 0.0
                    for (note in notes) {
                        sample += sin(phase * note)
                    }
                    sample /= notes.size
                    // Apply low pass filter and clip to 16-bit PCM range
                    buffer[i] = (sample * 32767.0 * volume).toInt().coerceIn(-32768, 32767).toShort()
                    phase += 2.0 * Math.PI / sampleRate
                    // Prevent phase variable overflow
                    if (phase > 2.0 * Math.PI * 100) {
                        phase -= 2.0 * Math.PI * 100
                    }
                }
                try {
                    track.write(buffer, 0, buffer.size)
                } catch (e: Exception) {
                    break
                }
            }
            try {
                track.stop()
                track.release()
            } catch (e: Exception) {}
        }.start()
    }

    fun setVolumeSmoothly(target: Float) {
        targetVolume = target.coerceIn(0f, 1f)
    }

    fun stopMusic() {
        isPlaying = false
    }
}

// --- Procedural Sound Effects (SFX) Player ---
class SFXPlayer {
    fun playSfx(sfxName: String) {
        Thread {
            val sampleRate = 22050
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val track = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2,
                AudioTrack.MODE_STREAM
            )

            try {
                track.play()
            } catch (e: Exception) {
                return@Thread
            }

            val buffer = ShortArray(512)
            var durationSamples = 0
            val maxDuration = when (sfxName.lowercase()) {
                "chime" -> sampleRate * 1.5 // 1.5s
                "laser" -> sampleRate * 0.6 // 0.6s
                "static" -> sampleRate * 0.8 // 0.8s
                "airhorn" -> sampleRate * 1.2 // 1.2s
                "applause" -> sampleRate * 2.0 // 2s
                else -> sampleRate * 0.5
            }

            var phase = 0.0
            val random = Random()

            while (durationSamples < maxDuration) {
                val progress = durationSamples.toDouble() / maxDuration
                
                for (i in buffer.indices) {
                    var sample = 0.0
                    
                    when (sfxName.lowercase()) {
                        "chime" -> {
                            // Arpeggio of sine waves fading out
                            val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.50) // C5, E5, G5, C6
                            for (j in notes.indices) {
                                val delayTime = j * (maxDuration / 6.0)
                                if (durationSamples > delayTime) {
                                    val noteProgress = (durationSamples - delayTime) / (maxDuration - delayTime)
                                    val envelope = (1.0 - noteProgress).coerceAtLeast(0.0)
                                    sample += sin(phase * notes[j]) * envelope * 0.25
                                }
                            }
                            phase += 2.0 * Math.PI / sampleRate
                        }
                        "laser" -> {
                            // Sweeping downward frequency
                            val currentFreq = 1800.0 * (1.0 - progress) * (1.0 - progress) + 150.0
                            sample = sin(phase) * 0.4
                            phase += 2.0 * Math.PI * currentFreq / sampleRate
                        }
                        "static" -> {
                            // Pure white noise mixed with low hum
                            sample = (random.nextDouble() * 2.0 - 1.0) * 0.15 * (1.0 - progress)
                        }
                        "airhorn" -> {
                            // Brassy aggressive square wave chord (similar to industrial retro horn)
                            val baseFreq = 180.0
                            val f1 = baseFreq
                            val f2 = baseFreq * 1.2
                            val f3 = baseFreq * 1.5
                            val envelope = if (progress < 0.1) progress / 0.1 else (1.0 - progress)
                            
                            val wave = (if (sin(phase * f1) > 0) 1.0 else -1.0) +
                                       (if (sin(phase * f2) > 0) 0.8 else -0.8) +
                                       (if (sin(phase * f3) > 0) 0.6 else -0.6)
                            sample = wave * 0.15 * envelope
                            phase += 2.0 * Math.PI / sampleRate
                        }
                        "applause" -> {
                            // Dense crackling static clicks simulating clapping hands
                            val densityFactor = if (progress < 0.2) progress / 0.2 else (1.0 - progress)
                            sample = if (random.nextDouble() < 0.08 * densityFactor) {
                                (random.nextDouble() * 2.0 - 1.0) * 0.3
                            } else {
                                0.0
                            }
                            // Add low applause hum
                            sample += sin(phase * 110.0) * 0.05 * densityFactor
                            phase += 2.0 * Math.PI / sampleRate
                        }
                        else -> {
                            // Simple beep
                            sample = sin(phase * 440.0) * 0.2
                            phase += 2.0 * Math.PI / sampleRate
                        }
                    }

                    buffer[i] = (sample * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
                }
                
                track.write(buffer, 0, buffer.size)
                durationSamples += buffer.size
            }
            
            try {
                track.stop()
                track.release()
            } catch (e: Exception) {}
        }.start()
    }
}
