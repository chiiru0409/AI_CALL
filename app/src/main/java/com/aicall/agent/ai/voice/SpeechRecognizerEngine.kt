package com.aicall.agent.ai.voice

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

interface SpeechRecognizerEngine {
    val recognizedSpeechFlow: Flow<String>
    fun startListening()
    fun stopListening()
    fun isAvailable(): Boolean
}

class DefaultSpeechRecognizerEngine : SpeechRecognizerEngine {
    private val _speechFlow = MutableSharedFlow<String>(extraBufferCapacity = 10)
    override val recognizedSpeechFlow: Flow<String> = _speechFlow.asSharedFlow()

    private var isListening = false

    override fun startListening() {
        isListening = true
    }

    override fun stopListening() {
        isListening = false
    }

    override fun isAvailable(): Boolean = true

    fun emitRecognizedSpeech(text: String) {
        _speechFlow.tryEmit(text)
    }
}
