package com.aicall.agent.ai.conversation

import com.aicall.agent.ai.policy.PolicyEnforcer
import com.aicall.agent.ai.policy.PolicyValidationResult
import com.aicall.agent.ai.policy.RiskAndUrgencyAssessor
import com.aicall.agent.ai.voice.AudioStorageManager
import com.aicall.agent.ai.voice.SpeechRecognizerEngine
import com.aicall.agent.ai.voice.SpeechSynthesizerEngine
import com.aicall.agent.ai.voice.VoiceStateMachine
import com.aicall.agent.data.model.*
import com.aicall.agent.data.repository.CallRepository
import com.aicall.agent.data.repository.CallerMemoryRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ConversationState(
    val sessionId: String = "",
    val callerNumber: String = "",
    val callerName: String? = null,
    val callerMemoryContext: String? = null,
    val isActive: Boolean = false,
    val turns: List<Pair<String, String>> = emptyList(), // ("Caller"/"AI", text)
    val latestAiUtterance: String = "",
    val isEscalated: Boolean = false
)

class ConversationManager(
    private val reasoningEngine: LlmReasoningEngine,
    private val policyEnforcer: PolicyEnforcer,
    private val riskAssessor: RiskAndUrgencyAssessor,
    private val callRepository: CallRepository,
    private val callerMemoryRepository: CallerMemoryRepository,
    private val speechRecognizer: SpeechRecognizerEngine,
    private val speechSynthesizer: SpeechSynthesizerEngine,
    private val audioStorageManager: AudioStorageManager? = null,
    private val localRagRetriever: com.aicall.agent.ai.rag.LocalRagRetriever? = null
) {
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _conversationState = MutableStateFlow(ConversationState())
    val conversationState: StateFlow<ConversationState> = _conversationState.asStateFlow()

    private var currentSessionId: String = ""
    private var knowledgeBaseText: String = ""
    private var preferredLanguage: String = "English"
    private var speechCollectionJob: Job? = null
    private val accumulatedExtractedFacts = mutableListOf<String>()

    val voiceStateMachine = VoiceStateMachine(
        callback = object : VoiceStateMachine.StateCallback {
            override fun onTransitionToListening() {
                speechRecognizer.startListening()
            }
            override fun onTransitionToThinking(speechSamples: FloatArray) {}
            override fun onTransitionToSpeaking() {}
            override fun onTransitionToIdle() {
                speechRecognizer.stopListening()
            }
            override fun onTransitionToEscalated() {
                speechRecognizer.stopListening()
            }
            override fun stopTtsPlayback() {
                speechSynthesizer.stop()
            }
        }
    )

    fun startAiSession(
        sessionId: String,
        callerNumber: String,
        callerName: String?,
        knowledge: String,
        language: String,
        onHangUpRequested: () -> Unit,
        onEscalateRequested: () -> Unit
    ) {
        currentSessionId = sessionId
        knowledgeBaseText = knowledge
        preferredLanguage = language

        // Subscribe to recognized speech flow from speechRecognizer
        speechCollectionJob?.cancel()
        speechCollectionJob = coroutineScope.launch {
            speechRecognizer.recognizedSpeechFlow.collect { recognizedText ->
                if (_conversationState.value.isActive && recognizedText.isNotBlank()) {
                    handleCallerInput(
                        callerText = recognizedText,
                        onHangUpRequested = onHangUpRequested,
                        onEscalateRequested = onEscalateRequested
                    )
                }
            }
        }

        coroutineScope.launch {
            // Retrieve caller memory from Room persistence
            val existingProfile = callerMemoryRepository.getProfile(callerNumber)
            val memoryContext = callerMemoryRepository.getCallerMemoryContext(callerNumber)

            _conversationState.value = ConversationState(
                sessionId = sessionId,
                callerNumber = callerNumber,
                callerName = callerName ?: existingProfile?.contactName,
                callerMemoryContext = memoryContext,
                isActive = true,
                turns = emptyList()
            )

            val isVerified = existingProfile?.isVerified == true || callerName != null
            val initialFacts = localRagRetriever?.retrieveRelevantFacts(
                callerNumber = callerNumber,
                callerName = callerName ?: existingProfile?.contactName,
                isCallerVerified = isVerified,
                callerUtterance = ""
            ) ?: emptyList()

            // Generate initial AI transparent introduction turn
            val introResponse = reasoningEngine.generateGroundedTurnResponse(
                callerNumber = callerNumber,
                callerName = callerName ?: existingProfile?.contactName,
                callerMemoryContext = memoryContext,
                conversationHistory = emptyList(),
                userApprovedKnowledge = knowledgeBaseText,
                preferredLanguage = preferredLanguage,
                retrievedFacts = initialFacts
            )

            val sanitizedIntro = policyEnforcer.sanitizeOutgoingSpeech(introResponse.spokenText)
            recordTurn("AI", sanitizedIntro)
            voiceStateMachine.onTtsPlaybackStarted()
            speechSynthesizer.speak(sanitizedIntro) {
                voiceStateMachine.onTtsPlaybackFinished()
                voiceStateMachine.startListening()
            }
        }
    }

    fun handleCallerInput(
        callerText: String,
        onHangUpRequested: () -> Unit,
        onEscalateRequested: () -> Unit
    ) {
        if (!_conversationState.value.isActive) return

        // 1. Validate caller utterance against deterministic security policy with cross-turn context
        when (val policyCheck = policyEnforcer.validateCallerUtterance(callerText, _conversationState.value.turns)) {
            is PolicyValidationResult.Blocked -> {
                recordTurn("Caller", callerText)
                val blockMessage = "I cannot process this request due to security policies."
                recordTurn("AI", blockMessage)
                speechSynthesizer.speak(blockMessage) {
                    onHangUpRequested()
                }
                return
            }
            is PolicyValidationResult.RequiresEscalation -> {
                onEscalateRequested()
                return
            }
            is PolicyValidationResult.Allowed -> {
                // Proceed
            }
        }

        voiceStateMachine.onSpeechEndDetected(floatArrayOf())
        recordTurn("Caller", callerText)

        // 2. Generate agent turn
        coroutineScope.launch {
            val state = _conversationState.value
            val history = state.turns
            val profile = callerMemoryRepository.getProfile(state.callerNumber)
            val isVerified = state.callerName != null || profile?.isVerified == true
            val retrievedFacts = localRagRetriever?.retrieveRelevantFacts(
                callerNumber = state.callerNumber,
                callerName = state.callerName,
                isCallerVerified = isVerified,
                callerUtterance = callerText
            ) ?: emptyList()

            val response = reasoningEngine.generateGroundedTurnResponse(
                callerNumber = state.callerNumber,
                callerName = state.callerName,
                callerMemoryContext = state.callerMemoryContext,
                conversationHistory = history,
                userApprovedKnowledge = knowledgeBaseText,
                preferredLanguage = preferredLanguage,
                retrievedFacts = retrievedFacts
            )

            if (response.extractedFacts.isNotEmpty()) {
                accumulatedExtractedFacts.addAll(response.extractedFacts)
            }

            // Validate and sanitize proposed agent output
            val outputCheck = policyEnforcer.validateProposedAgentResponse(response.spokenText)
            val candidateText = if (outputCheck is PolicyValidationResult.Allowed) {
                response.spokenText
            } else {
                "I am unable to provide that information."
            }
            val finalText = policyEnforcer.sanitizeOutgoingSpeech(candidateText)

            recordTurn("AI", finalText)
            voiceStateMachine.onTtsPlaybackStarted()
            speechSynthesizer.speak(finalText) {
                voiceStateMachine.onTtsPlaybackFinished()
                if (response.shouldEscalateToHuman) {
                    _conversationState.value = _conversationState.value.copy(isEscalated = true)
                    voiceStateMachine.escalateToHuman()
                    onEscalateRequested()
                } else if (response.shouldHangUp) {
                    voiceStateMachine.reset()
                    onHangUpRequested()
                } else {
                    voiceStateMachine.startListening()
                }
            }
        }
    }

    private fun recordTurn(speaker: String, text: String) {
        val currentTurns = _conversationState.value.turns.toMutableList()
        currentTurns.add(Pair(speaker, text))
        _conversationState.value = _conversationState.value.copy(
            turns = currentTurns,
            latestAiUtterance = if (speaker == "AI") text else _conversationState.value.latestAiUtterance
        )
    }

    fun endSessionAndPersist(
        outcome: HandlingStatus = HandlingStatus.AI_HANDLED,
        actualDurationSeconds: Long? = null,
        customSummary: String? = null,
        isLocalVoiceTest: Boolean = false,
        cleanupSessionAudio: Boolean = true
    ) {
        val state = _conversationState.value
        if (!state.isActive) return

        speechCollectionJob?.cancel()
        speechCollectionJob = null
        voiceStateMachine.reset()
        speechRecognizer.stopListening()
        speechSynthesizer.stop()

        if (cleanupSessionAudio) {
            audioStorageManager?.deleteSessionAudio(state.sessionId)
        }

        // Strict criterion: AI_HANDLED requires documented handling criteria (actual conversation turns with caller)
        val hasCallerDialogue = state.turns.any { it.first == "Caller" }
        val effectiveOutcome = if (!hasCallerDialogue && outcome == HandlingStatus.AI_HANDLED) {
            HandlingStatus.MISSED
        } else {
            outcome
        }

        val finalDuration = actualDurationSeconds ?: (state.turns.size * 5).toLong()

        // If no caller speech was ever intercepted/exchanged, transcript is empty/unavailable
        val fullTranscript = if (hasCallerDialogue) {
            state.turns.joinToString("\n") { "${it.first}: ${it.second}" }
        } else {
            ""
        }

        val assessment = riskAssessor.assessConversation(
            callerNumber = state.callerNumber,
            isContact = state.callerName != null,
            transcript = fullTranscript
        )

        val finalSummary = when {
            !hasCallerDialogue && customSummary != null -> customSummary
            !hasCallerDialogue -> "Call ended with no caller conversation (Transcript unavailable)."
            customSummary != null -> "${assessment.summary} ($customSummary)"
            else -> assessment.summary
        }

        val recordingStatusText = when {
            isLocalVoiceTest && fullTranscript.isNotBlank() -> "Local voice test transcript preserved in Room storage"
            fullTranscript.isNotBlank() -> "Transcript preserved in local storage"
            else -> "Recording unavailable on this device (Cellular downlink audio not intercepted by OS)"
        }

        coroutineScope.launch {
            // Save CallRecord in Room with actual duration and verified handling criteria
            val record = CallRecord(
                sessionId = state.sessionId,
                callerNumber = state.callerNumber,
                callerName = state.callerName,
                direction = CallDirection.INCOMING,
                startTime = System.currentTimeMillis() - (finalDuration * 1000L),
                endTime = System.currentTimeMillis(),
                durationSeconds = finalDuration,
                category = assessment.category,
                intent = assessment.intent,
                urgencyLevel = assessment.urgency,
                riskLevel = assessment.risk,
                handlingStatus = effectiveOutcome,
                outcomeSummary = finalSummary,
                transcript = fullTranscript,
                recordingStatus = recordingStatusText,
                callerLocation = "Telangana, India (Cell tower approximation)",
                verificationStatus = if (state.callerName != null) "Saved Address Book Contact (Unverified Caller ID)" else "Unsaved Caller Number (Unverified Caller ID)",
                extractedFactsJson = if (accumulatedExtractedFacts.isNotEmpty()) {
                    accumulatedExtractedFacts.distinct().joinToString(prefix = "[\"", separator = "\",\"", postfix = "\"]") {
                        it.replace("\"", "\\\"")
                    }
                } else "[]"
            )
            callRepository.saveCallRecord(record)

            // Update caller memory profile in Room only when a genuine transcript exists
            if (fullTranscript.isNotBlank()) {
                val claimedName = extractClaimedName(fullTranscript)

                callerMemoryRepository.updateCallerMemory(
                    phoneNumber = state.callerNumber,
                    contactName = state.callerName,
                    claimedName = claimedName,
                    category = assessment.category,
                    memorySummary = "Call on ${java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}: ${assessment.summary}",
                    unresolvedRequest = if (assessment.shouldEscalate) "Urgent callback requested regarding ${assessment.intent}" else null
                )

                if (assessment.shouldEscalate) {
                    callRepository.addActionItem(
                        ActionItem(
                            callSessionId = state.sessionId,
                            title = "Urgent Callback to ${state.callerName ?: claimedName ?: state.callerNumber}",
                            description = "Reason: ${assessment.intent}",
                            priority = if (assessment.urgency == UrgencyLevel.EMERGENCY) "URGENT" else "HIGH"
                        )
                    )
                }
            }
        }

        accumulatedExtractedFacts.clear()
        _conversationState.value = ConversationState()
    }

    private fun extractClaimedName(transcript: String): String? {
        val nameRegex = Regex("(?:i am|this is|my name is|speaking is)\\s+([a-zA-Z]{2,30})", RegexOption.IGNORE_CASE)
        val match = nameRegex.find(transcript)
        val candidate = match?.groupValues?.getOrNull(1)?.trim() ?: return null

        val forbiddenTokens = setOf(
            "system", "admin", "administrator", "root", "developer", "prompt",
            "assistant", "ai", "bot", "override", "instruction", "mode", "user"
        )
        if (forbiddenTokens.contains(candidate.lowercase())) {
            return null
        }
        return candidate.replaceFirstChar { it.uppercase() }
    }
}

