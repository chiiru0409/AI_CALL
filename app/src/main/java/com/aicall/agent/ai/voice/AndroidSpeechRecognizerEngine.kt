package com.aicall.agent.ai.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * On-device offline Speech Recognition Engine using Android's native [SpeechRecognizer].
 * Adapted from Reference B (`AndroidSpeechEngine.kt`).
 *
 * Runs 100% locally and offline on Android 12+ (API 31+) and Android 16 (API 36)
 * via [SpeechRecognizer.createOnDeviceSpeechRecognizer].
 * Requires 0 MB external model downloads and 0 additional native libraries (Zero Budget).
 */
class AndroidSpeechRecognizerEngine(
    private val context: Context,
    private val preferOffline: Boolean = true
) : SpeechRecognizerEngine {

    companion object {
        private const val TAG = "AndroidSpeechEngine"

        fun isSupported(context: Context): Boolean {
            return SpeechRecognizer.isRecognitionAvailable(context)
        }

        fun isOnDeviceAvailable(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            } else {
                false
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val _speechFlow = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val recognizedSpeechFlow: Flow<String> = _speechFlow.asSharedFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    @Volatile
    private var isListeningState = false

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "SpeechRecognizer: Ready for speech")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "SpeechRecognizer: Beginning of speech detected")
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "SpeechRecognizer: End of speech detected")
        }

        override fun onError(error: Int) {
            val errorMsg = errorCodeToString(error)
            Log.w(TAG, "SpeechRecognizer error: $errorMsg ($error)")

            // Auto-recover and continue listening for speech pauses or timeouts if still active
            if (isListeningState) {
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        mainHandler.postDelayed({
                            if (isListeningState) {
                                startListeningInternal()
                            }
                        }, 200L)
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        mainHandler.postDelayed({
                            if (isListeningState) {
                                recreateRecognizer()
                                startListeningInternal()
                            }
                        }, 500L)
                    }
                    else -> {
                        // Other errors: client, audio, permissions
                    }
                }
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognizedText = matches?.firstOrNull()?.trim() ?: ""
            Log.i(TAG, "SpeechRecognizer results: '$recognizedText'")

            if (recognizedText.isNotBlank()) {
                _speechFlow.tryEmit(recognizedText)
            }

            // If still in listening state, prepare for next utterance (multi-turn)
            if (isListeningState) {
                mainHandler.postDelayed({
                    if (isListeningState) {
                        startListeningInternal()
                    }
                }, 200L)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partialText = partials?.firstOrNull()?.trim() ?: ""
            if (partialText.isNotBlank()) {
                Log.d(TAG, "SpeechRecognizer partial: '$partialText'")
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    init {
        mainHandler.post {
            createRecognizer()
        }
    }

    private fun createRecognizer() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = if (preferOffline && isOnDeviceAvailable(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Log.i(TAG, "Instantiating On-Device SpeechRecognizer (API 31+)")
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                Log.i(TAG, "Instantiating standard SpeechRecognizer")
                SpeechRecognizer.createSpeechRecognizer(context)
            }
            speechRecognizer?.setRecognitionListener(listener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create SpeechRecognizer", e)
            speechRecognizer = null
        }
    }

    private fun recreateRecognizer() {
        createRecognizer()
    }

    override fun startListening() {
        isListeningState = true
        mainHandler.post {
            startListeningInternal()
        }
    }

    private fun startListeningInternal() {
        if (!isListeningState) return
        val recognizer = speechRecognizer ?: run {
            createRecognizer()
            speechRecognizer
        } ?: return

        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                if (preferOffline && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            }
            recognizer.startListening(intent)
            Log.d(TAG, "SpeechRecognizer startListening requested")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting SpeechRecognizer", e)
        }
    }

    override fun stopListening() {
        isListeningState = false
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping SpeechRecognizer: ${e.message}")
            }
        }
    }

    override fun isAvailable(): Boolean {
        return isSupported(context)
    }

    fun release() {
        isListeningState = false
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing SpeechRecognizer: ${e.message}")
            }
            speechRecognizer = null
        }
    }

    private fun errorCodeToString(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
        SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
        SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
        SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
        SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
        else -> "ERROR_UNKNOWN($error)"
    }
}
