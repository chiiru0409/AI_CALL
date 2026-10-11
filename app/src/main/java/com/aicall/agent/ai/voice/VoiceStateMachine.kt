package com.aicall.agent.ai.voice

import android.util.Log

/**
 * Deterministic Voice State Machine adapted from reference assistant.
 * Handles transitions between IDLE, LISTENING, THINKING, SPEAKING, and ESCALATED.
 * Supports barge-in interruption with a 300ms echo-bleed rejection window.
 */
class VoiceStateMachine(
    private val callback: StateCallback
) {
    interface StateCallback {
        fun onTransitionToListening()
        fun onTransitionToThinking(speechSamples: FloatArray)
        fun onTransitionToSpeaking()
        fun onTransitionToIdle()
        fun onTransitionToEscalated()
        fun stopTtsPlayback()
    }

    private var state = VoiceState.IDLE
    private var lastTtsStartTime: Long = 0L

    companion object {
        private const val TAG = "VoiceStateMachine"
        private const val ECHO_BLEED_WINDOW_MS = 300L
    }

    @Synchronized
    fun getCurrentState(): VoiceState = state

    @Synchronized
    private fun logD(msg: String) {
        try {
            android.util.Log.d(TAG, msg)
        } catch (_: Throwable) {
            println("[$TAG] D: $msg")
        }
    }

    private fun logI(msg: String) {
        try {
            android.util.Log.i(TAG, msg)
        } catch (_: Throwable) {
            println("[$TAG] I: $msg")
        }
    }

    @Synchronized
    fun startListening() {
        if (state == VoiceState.IDLE || state == VoiceState.SPEAKING) {
            state = VoiceState.LISTENING
            logD("State transition: -> LISTENING")
            callback.onTransitionToListening()
        }
    }

    @Synchronized
    fun stopListening() {
        if (state != VoiceState.IDLE && state != VoiceState.ESCALATED) {
            logD("State transition: $state -> IDLE")
            state = VoiceState.IDLE
            callback.onTransitionToIdle()
        }
    }

    @Synchronized
    fun onSpeechStartDetected() {
        when (state) {
            VoiceState.SPEAKING -> {
                val elapsedSinceTtsStart = System.currentTimeMillis() - lastTtsStartTime
                if (elapsedSinceTtsStart < ECHO_BLEED_WINDOW_MS) {
                    logD("Ignoring speech start during echo bleed window ($elapsedSinceTtsStart ms < $ECHO_BLEED_WINDOW_MS ms).")
                    return
                }
                logI("[Barge-In] Caller speech detected during TTS. Stopping playback and listening.")
                callback.stopTtsPlayback()
                state = VoiceState.LISTENING
                callback.onTransitionToListening()
            }
            VoiceState.LISTENING -> {
                logD("Speech start detected while listening.")
            }
            else -> {
                logD("Speech start detected during state: $state. Ignoring.")
            }
        }
    }

    @Synchronized
    fun onSpeechEndDetected(samples: FloatArray) {
        if (state == VoiceState.LISTENING) {
            logD("State transition: LISTENING -> THINKING")
            state = VoiceState.THINKING
            callback.onTransitionToThinking(samples)
        } else {
            logD("Speech end detected during state: $state. Ignoring.")
        }
    }

    @Synchronized
    fun onTtsPlaybackStarted() {
        lastTtsStartTime = System.currentTimeMillis()
        if (state != VoiceState.ESCALATED) {
            logD("State transition: $state -> SPEAKING")
            state = VoiceState.SPEAKING
            callback.onTransitionToSpeaking()
        }
    }

    @Synchronized
    fun onTtsPlaybackFinished() {
        if (state == VoiceState.SPEAKING) {
            logD("State transition: SPEAKING -> LISTENING")
            state = VoiceState.LISTENING
            callback.onTransitionToListening()
        }
    }

    @Synchronized
    fun escalateToHuman() {
        logD("State transition: $state -> ESCALATED")
        state = VoiceState.ESCALATED
        callback.stopTtsPlayback()
        callback.onTransitionToEscalated()
    }

    @Synchronized
    fun reset() {
        state = VoiceState.IDLE
        callback.onTransitionToIdle()
    }
}
