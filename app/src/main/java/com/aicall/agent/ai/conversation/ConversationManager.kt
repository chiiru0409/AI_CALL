package com.aicall.agent.ai.conversation

import com.aicall.agent.ai.policy.PolicyEnforcer
import com.aicall.agent.ai.policy.PolicyValidationResult
import com.aicall.agent.ai.policy.RiskAndUrgencyAssessor
import com.aicall.agent.ai.voice.SpeechRecognizerEngine
import com.aicall.agent.ai.voice.SpeechSynthesizerEngine
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
    private val speechSynthesizer: SpeechSynthesizerEngine
) {
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _conversationState = MutableStateFlow(ConversationState())
    val conversationState: StateFlow<ConversationState> = _conversationState.asStateFlow()

    private var currentSessionId: String = ""
    private var knowledgeBaseText: String = ""
    private var preferredLanguage: String = "Telugu-English"

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

            // Generate initial AI transparent introduction turn
            val introResponse = reasoningEngine.generateTurnResponse(
                callerNumber = callerNumber,
                callerName = callerName ?: existingProfile?.contactName,
                callerMemoryContext = memoryContext,
                conversationHistory = emptyList(),
                userApprovedKnowledge = knowledgeBaseText,
                preferredLanguage = preferredLanguage
            )

            val sanitizedIntro = policyEnforcer.sanitizeOutgoingSpeech(introResponse.spokenText)
            recordTurn("AI", sanitizedIntro)
            speechSynthesizer.speak(sanitizedIntro)
            speechRecognizer.startListening()
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

        recordTurn("Caller", callerText)

        // 2. Generate agent turn
        coroutineScope.launch {
            val state = _conversationState.value
            val history = state.turns
            val response = reasoningEngine.generateTurnResponse(
                callerNumber = state.callerNumber,
                callerName = state.callerName,
                callerMemoryContext = state.callerMemoryContext,
                conversationHistory = history,
                userApprovedKnowledge = knowledgeBaseText,
                preferredLanguage = preferredLanguage
            )

            // Validate and sanitize proposed agent output
            val outputCheck = policyEnforcer.validateProposedAgentResponse(response.spokenText)
            val candidateText = if (outputCheck is PolicyValidationResult.Allowed) {
                response.spokenText
            } else {
                "I am unable to provide that information."
            }
            val finalText = policyEnforcer.sanitizeOutgoingSpeech(candidateText)

            recordTurn("AI", finalText)
            speechSynthesizer.speak(finalText) {
                if (response.shouldEscalateToHuman) {
                    _conversationState.value = _conversationState.value.copy(isEscalated = true)
                    onEscalateRequested()
                } else if (response.shouldHangUp) {
                    onHangUpRequested()
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

    fun endSessionAndPersist(outcome: HandlingStatus = HandlingStatus.AI_HANDLED) {
        val state = _conversationState.value
        if (!state.isActive) return

        speechRecognizer.stopListening()
        speechSynthesizer.stop()

        val fullTranscript = state.turns.joinToString("\n") { "${it.first}: ${it.second}" }
        val assessment = riskAssessor.assessConversation(
            callerNumber = state.callerNumber,
            isContact = state.callerName != null,
            transcript = fullTranscript
        )

        coroutineScope.launch {
            // Save CallRecord in Room
            val record = CallRecord(
                sessionId = state.sessionId,
                callerNumber = state.callerNumber,
                callerName = state.callerName,
                direction = CallDirection.INCOMING,
                durationSeconds = (state.turns.size * 5).toLong(),
                category = assessment.category,
                intent = assessment.intent,
                urgencyLevel = assessment.urgency,
                riskLevel = assessment.risk,
                handlingStatus = outcome,
                outcomeSummary = assessment.summary,
                transcript = fullTranscript,
                recordingStatus = "Transcript preserved in local storage",
                callerLocation = "Telangana, India (Cell tower approximation)",
                verificationStatus = if (state.callerName != null) "Contact List Match" else "Unverified"
            )
            callRepository.saveCallRecord(record)

            // Extract potential caller identity claim from transcript (e.g., "I am Rohit", "This is Suresh")
            val claimedName = extractClaimedName(fullTranscript)

            // Update caller memory profile in Room
            callerMemoryRepository.updateCallerMemory(
                phoneNumber = state.callerNumber,
                contactName = state.callerName,
                claimedName = claimedName,
                category = assessment.category,
                memorySummary = "Call on ${java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}: ${assessment.summary}",
                unresolvedRequest = if (assessment.shouldEscalate) "Urgent callback requested regarding ${assessment.intent}" else null
            )

            // Create persistent action item in Room if callback needed
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

