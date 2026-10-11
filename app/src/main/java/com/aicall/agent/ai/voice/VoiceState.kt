package com.aicall.agent.ai.voice

/**
 * Deterministic state of the voice conversation pipeline.
 */
enum class VoiceState {
    /** Standby; microphone paused, TTS idle. */
    IDLE,

    /** Active microphone listening; VAD tracking speech. */
    LISTENING,

    /** Utterance end detected; LLM / rule reasoning generating response. */
    THINKING,

    /** Speech synthesis audio output actively playing. */
    SPEAKING,

    /** Human user took over call; AI voice processing suspended. */
    ESCALATED
}
