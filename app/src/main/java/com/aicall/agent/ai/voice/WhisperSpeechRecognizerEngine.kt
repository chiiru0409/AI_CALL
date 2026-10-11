package com.aicall.agent.ai.voice

import android.content.Context
import android.util.Log
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import dev.ffmpegkit.whisper.WhisperModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device Whisper speech recognition engine adapter implementing [SpeechRecognizerEngine].
 * Operates 100% offline using Whisper.cpp native runtime on arm64-v8a.
 */
class WhisperSpeechRecognizerEngine(
    private val context: Context,
    private val modelPath: String,
    private val defaultLanguage: String? = null,
    private val numThreads: Int = 4,
    private val audioStorageManager: AudioStorageManager = AudioStorageManager(context)
) : SpeechRecognizerEngine {

    companion object {
        private const val TAG = "WhisperSpeechEngine"
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _speechFlow = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val recognizedSpeechFlow: Flow<String> = _speechFlow.asSharedFlow()

    private var whisperModel: WhisperModel? = null
    @Volatile
    private var isListeningState = false
    private var turnCounter = 0

    private var audioCaptureManager: AudioCaptureManager? = null

    private val captureListener = object : AudioCaptureManager.AudioCaptureListener {
        override fun onSpeechStart() {
            Log.d(TAG, "Speech start detected by VAD")
        }

        override fun onSpeechEnd(samples: FloatArray) {
            if (!isListeningState || samples.isEmpty()) return
            scope.launch(Dispatchers.Default) {
                try {
                    val turn = ++turnCounter
                    val wavFile = audioStorageManager.saveSamplesAsWav(samples, "whisper_live", turn)
                    if (wavFile != null && wavFile.exists()) {
                        Log.i(TAG, "Transcribing speech segment ${wavFile.name} (${samples.size} samples)")
                        val transcript = transcribeAudioFile(wavFile.absolutePath)
                        Log.i(TAG, "Whisper transcribed: '$transcript'")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing speech segment", e)
                }
            }
        }

        override fun onAmplitudeChanged(amplitude: Float) {}
    }

    init {
        // Attempt eager load if model file exists
        val file = File(modelPath)
        if (file.exists() && file.canRead()) {
            scope.launch(Dispatchers.IO) {
                try {
                    whisperModel = Whisper.loadModel(context, file.absolutePath)
                    Log.i(TAG, "Whisper model loaded successfully from $modelPath")
                } catch (e: Exception) {
                    Log.w(TAG, "Eager model loading failed: ${e.message}", e)
                }
            }
        } else {
            Log.w(TAG, "Whisper model file not found at $modelPath; engine will remain standby")
        }

        audioCaptureManager = AudioCaptureManager(context, scope, captureListener)
    }

    override fun startListening() {
        isListeningState = true
        Log.d(TAG, "Whisper engine listening active")
        try {
            audioCaptureManager?.start()
        } catch (e: Exception) {
            Log.w(TAG, "Could not start audio capture: ${e.message}")
        }
    }

    override fun stopListening() {
        isListeningState = false
        Log.d(TAG, "Whisper engine listening paused")
        try {
            audioCaptureManager?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping audio capture: ${e.message}")
        }
    }

    override fun isAvailable(): Boolean {
        return whisperModel != null && whisperModel?.isValid == true
    }

    /**
     * Transcribes an on-device audio file offline using Whisper.cpp.
     * Emits recognized text into [recognizedSpeechFlow] if listening is active.
     */
    suspend fun transcribeAudioFile(
        audioPath: String,
        language: String? = defaultLanguage
    ): String = withContext(Dispatchers.Default) {
        val model = whisperModel
        if (model == null || !model.isValid) {
            Log.e(TAG, "Cannot transcribe: Whisper model not ready")
            return@withContext ""
        }

        val config = WhisperConfig(
            language = language ?: "auto",
            translate = false,
            threads = numThreads,
            maxSegmentLength = 0,
            printTimestamps = false
        )

        try {
            val result = Whisper.transcribe(model, audioPath, config)
            val text = result.text.trim()
            if (isListeningState && text.isNotEmpty()) {
                _speechFlow.tryEmit(text)
            }
            text
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed for $audioPath: ${e.message}", e)
            ""
        }
    }

    fun release() {
        stopListening()
        whisperModel?.let { model ->
            try {
                Whisper.releaseModel(model)
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing Whisper model", e)
            }
        }
        whisperModel = null
        isListeningState = false
    }
}
