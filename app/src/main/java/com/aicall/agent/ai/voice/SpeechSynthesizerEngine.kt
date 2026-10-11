package com.aicall.agent.ai.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

import android.media.AudioAttributes
import android.speech.tts.UtteranceProgressListener

interface SpeechSynthesizerEngine {
    fun speak(text: String, onComplete: () -> Unit = {})
    fun stop()
    fun shutdown()
    fun isReady(): Boolean
}

class AndroidTtsEngine(context: Context) : SpeechSynthesizerEngine {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var queuedUtterance: Pair<String, () -> Unit>? = null
    private var pendingUtteranceCallback: (() -> Unit)? = null

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                try {
                    tts?.language = Locale.US
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    tts?.setAudioAttributes(audioAttributes)
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            val cb = pendingUtteranceCallback
                            pendingUtteranceCallback = null
                            cb?.invoke()
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            val cb = pendingUtteranceCallback
                            pendingUtteranceCallback = null
                            cb?.invoke()
                        }
                        override fun onError(utteranceId: String?, errorCode: Int) {
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

    override fun speak(text: String, onComplete: () -> Unit) {
        if (isInitialized) {
            pendingUtteranceCallback = onComplete
            val utteranceId = "aicall_${System.currentTimeMillis()}"
            val res = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (res != TextToSpeech.SUCCESS) {
                pendingUtteranceCallback = null
                onComplete()
            }
        } else {
            queuedUtterance = Pair(text, onComplete)
        }
    }

    override fun stop() {
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
