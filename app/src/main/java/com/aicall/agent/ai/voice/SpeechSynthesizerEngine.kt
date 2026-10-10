package com.aicall.agent.ai.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

interface SpeechSynthesizerEngine {
    fun speak(text: String, onComplete: () -> Unit = {})
    fun stop()
    fun shutdown()
    fun isReady(): Boolean
}

class AndroidTtsEngine(context: Context) : SpeechSynthesizerEngine {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                isInitialized = true
            }
        }
    }

    override fun speak(text: String, onComplete: () -> Unit) {
        if (isInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "aicall_utterance")
            onComplete()
        }
    }

    override fun stop() {
        tts?.stop()
    }

    override fun shutdown() {
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    override fun isReady(): Boolean = isInitialized
}
