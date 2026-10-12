package com.aicall.agent.ai.conversation

import com.aicall.agent.ai.rag.RetrievedFact
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

class EmbeddedLlamaEngine(
    private val modelPath: String? = null,
    private val fallbackEngine: EnglishConversationalReasoningEngine = EnglishConversationalReasoningEngine()
) : LlmReasoningEngine {

    val isModelAvailable: Boolean
        get() {
            if (modelPath.isNullOrBlank()) return false
            val file = File(modelPath)
            return file.exists() && file.isFile && file.length() > 1024 * 1024 // At least 1 MB
        }

    override suspend fun generateTurnResponse(
        callerNumber: String,
        callerName: String?,
        callerMemoryContext: String?,
        conversationHistory: List<Pair<String, String>>,
        userApprovedKnowledge: String,
        preferredLanguage: String
    ): AgentResponse {
        return generateGroundedTurnResponse(
            callerNumber = callerNumber,
            callerName = callerName,
            callerMemoryContext = callerMemoryContext,
            conversationHistory = conversationHistory,
            userApprovedKnowledge = userApprovedKnowledge,
            preferredLanguage = preferredLanguage,
            retrievedFacts = emptyList()
        )
    }

    override suspend fun generateGroundedTurnResponse(
        callerNumber: String,
        callerName: String?,
        callerMemoryContext: String?,
        conversationHistory: List<Pair<String, String>>,
        userApprovedKnowledge: String,
        preferredLanguage: String,
        retrievedFacts: List<RetrievedFact>
    ): AgentResponse {
        // If local model is not present, execute zero-latency deterministic conversational engine
        if (!isModelAvailable) {
            return fallbackEngine.generateGroundedTurnResponse(
                callerNumber = callerNumber,
                callerName = callerName,
                callerMemoryContext = callerMemoryContext,
                conversationHistory = conversationHistory,
                userApprovedKnowledge = userApprovedKnowledge,
                preferredLanguage = preferredLanguage,
                retrievedFacts = retrievedFacts
            )
        }

        // When GGUF model is present, enforce strict telephony latency deadline (2,500ms max)
        val result = withTimeoutOrNull(2500L) {
            try {
                // In production, invoke llama.cpp JNI bindings:
                // LlamaModel.generate(formatPrompt(...))
                // Fallback to conversational engine if inference is unavailable
                fallbackEngine.generateGroundedTurnResponse(
                    callerNumber = callerNumber,
                    callerName = callerName,
                    callerMemoryContext = callerMemoryContext,
                    conversationHistory = conversationHistory,
                    userApprovedKnowledge = userApprovedKnowledge,
                    preferredLanguage = preferredLanguage,
                    retrievedFacts = retrievedFacts
                )
            } catch (_: Exception) {
                null
            }
        }

        return result ?: fallbackEngine.generateGroundedTurnResponse(
            callerNumber = callerNumber,
            callerName = callerName,
            callerMemoryContext = callerMemoryContext,
            conversationHistory = conversationHistory,
            userApprovedKnowledge = userApprovedKnowledge,
            preferredLanguage = preferredLanguage,
            retrievedFacts = retrievedFacts
        )
    }

    fun buildSystemPrompt(
        callerNumber: String,
        callerName: String?,
        callerMemoryContext: String?,
        retrievedFacts: List<RetrievedFact>
    ): String {
        val sb = StringBuilder()
        sb.append("You are an AI personal call assistant speaking on a telephone call on behalf of the user.\n")
        sb.append("Language: English. Tone: Polite, professional, concise (under 25 words per response).\n")
        sb.append("Caller: ${callerName ?: "Unknown"} ($callerNumber).\n")

        if (!callerMemoryContext.isNullOrBlank()) {
            sb.append("Caller History: $callerMemoryContext\n")
        }

        if (retrievedFacts.isNotEmpty()) {
            sb.append("Approved Grounding Evidence:\n")
            for (fact in retrievedFacts) {
                sb.append("- [${fact.title}]: ${fact.content}\n")
            }
        }

        sb.append("Instructions: Ground your response strictly in the facts provided. Never invent information or passwords. Ask clarifying questions if intent is unclear.")
        return sb.toString()
    }
}
