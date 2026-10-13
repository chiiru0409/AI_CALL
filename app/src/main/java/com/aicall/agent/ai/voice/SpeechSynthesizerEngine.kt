package com.aicall.agent.ai.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

interface SpeechSynthesizerEngine {
    fun speak(text: String, onComplete: () -> Unit = {})
    fun stop()
    fun shutdown()
    fun isReady(): Boolean
}

class AndroidTtsEngine(context: Context) : SpeechSynthesizerEngine {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var queuedUtterance: Pair<String, () -> Unit>? = null
    private var pendingUtteranceCallback: (() -> Unit)? = null
    private var activeFocusRequest: AudioFocusRequest? = null

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                try {
                    tts?.language = Locale.US
                    tts?.setAudioAttributes(audioAttributes)
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            releaseAudioFocus()
                            val cb = pendingUtteranceCallback
                            pendingUtteranceCallback = null
                            cb?.invoke()
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            releaseAudioFocus()
                            val cb = pendingUtteranceCallback
                            pendingUtteranceCallback = null
                            cb?.invoke()
                        }
                        override fun onError(utteranceId: String?, errorCode: Int) {
                            releaseAudioFocus()
                            val cb = pendingUtteranceCallback
                            pendingUtteranceCallback = null
                            cb?.invoke()
                        }
                    })
                } catch (_: Exception) {
                }
                isInitialized = true
                queuedUtterance?.let { (text, cb) ->
                    queuedUtterance = null
                    speak(text, cb)
                }
            }
        }
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusReq = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(audioAttributes)
                    .setOnAudioFocusChangeListener { /* transient focus */ }
                    .build()
                activeFocusRequest = focusReq
                audioManager?.requestAudioFocus(focusReq)
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (_: Exception) {}
    }

    private fun releaseAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activeFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
                activeFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
        } catch (_: Exception) {}
    }

    override fun speak(text: String, onComplete: () -> Unit) {
        if (isInitialized) {
            requestAudioFocus()
            pendingUtteranceCallback = onComplete
            val utteranceId = "aicall_${System.currentTimeMillis()}"
            val res = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (res != TextToSpeech.SUCCESS) {
                releaseAudioFocus()
                pendingUtteranceCallback = null
                onComplete()
            }
        } else {
            queuedUtterance = Pair(text, onComplete)
        }
    }

    override fun stop() {
        releaseAudioFocus()
        tts?.stop()
        pendingUtteranceCallback = null
        queuedUtterance = null
    }

    override fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    override fun isReady(): Boolean = isInitialized
}
