package com.aicall.agent.ai.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Hybrid Speech Recognizer Engine selecting between:
 * 1. Offline Whisper.cpp ([WhisperSpeechRecognizerEngine]) when model file is present.
 * 2. On-device Android [SpeechRecognizer] ([AndroidSpeechRecognizerEngine]) as a zero-budget, 0 MB download fallback.
 */
class HybridSpeechRecognizerEngine(
    private val context: Context,
    private val whisperEngine: WhisperSpeechRecognizerEngine? = null,
    private val androidSpeechEngine: AndroidSpeechRecognizerEngine = AndroidSpeechRecognizerEngine(context)
) : SpeechRecognizerEngine {

    companion object {
        private const val TAG = "HybridSpeechEngine"
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _speechFlow = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val recognizedSpeechFlow: Flow<String> = _speechFlow.asSharedFlow()

    @Volatile
    private var isListeningState = false

    init {
        // Collect from Whisper engine if available
        whisperEngine?.let { engine ->
            scope.launch {
                engine.recognizedSpeechFlow.collect { text ->
                    if (isListeningState && text.isNotBlank()) {
                        Log.i(TAG, "Whisper engine emitted: '$text'")
                        _speechFlow.tryEmit(text)
                    }
                }
            }
        }

        // Collect from Android native on-device speech engine
        scope.launch {
            androidSpeechEngine.recognizedSpeechFlow.collect { text ->
                if (isListeningState && text.isNotBlank()) {
                    Log.i(TAG, "Android speech engine emitted: '$text'")
                    _speechFlow.tryEmit(text)
                }
            }
        }
    }

    override fun startListening() {
        isListeningState = true
        val useWhisper = whisperEngine != null && whisperEngine.isAvailable()

        if (useWhisper) {
            Log.i(TAG, "Starting Whisper speech recognition engine")
            whisperEngine?.startListening()
        } else {
            Log.i(TAG, "Starting Android on-device speech recognition engine (Zero-Budget fallback)")
            androidSpeechEngine.startListening()
        }
    }

    override fun stopListening() {
        isListeningState = false
        whisperEngine?.stopListening()
        androidSpeechEngine.stopListening()
        Log.d(TAG, "Stopped all speech recognition engines")
    }

    override fun isAvailable(): Boolean {
        return (whisperEngine != null && whisperEngine.isAvailable()) || androidSpeechEngine.isAvailable()
    }

    fun getActiveEngineName(): String {
        return if (whisperEngine != null && whisperEngine.isAvailable()) {
            "Whisper.cpp (On-Device GGML)"
        } else {
            "Android On-Device SpeechRecognizer (Built-In)"
        }
    }
}
