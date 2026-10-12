package com.aicall.agent.ai.conversation

import com.aicall.agent.ai.rag.RetrievedFact
import java.util.regex.Pattern

data class AgentResponse(
    val spokenText: String,
    val extractedFacts: List<String> = emptyList(),
    val proposedActionItems: List<String> = emptyList(),
    val shouldHangUp: Boolean = false,
    val shouldEscalateToHuman: Boolean = false,
    val groundingSource: String? = null,
    val confidence: Float = 1.0f
)

interface LlmReasoningEngine {
    suspend fun generateTurnResponse(
        callerNumber: String,
        callerName: String?,
        callerMemoryContext: String? = null,
        conversationHistory: List<Pair<String, String>>, // Speaker ("Caller" or "AI"), text
        userApprovedKnowledge: String,
        preferredLanguage: String
    ): AgentResponse

    suspend fun generateGroundedTurnResponse(
        callerNumber: String,
        callerName: String?,
        callerMemoryContext: String? = null,
        conversationHistory: List<Pair<String, String>>,
        userApprovedKnowledge: String,
        preferredLanguage: String,
        retrievedFacts: List<RetrievedFact> = emptyList()
    ): AgentResponse {
        return generateTurnResponse(
            callerNumber = callerNumber,
            callerName = callerName,
            callerMemoryContext = callerMemoryContext,
            conversationHistory = conversationHistory,
            userApprovedKnowledge = userApprovedKnowledge,
            preferredLanguage = preferredLanguage
        )
    }
}

open class EnglishConversationalReasoningEngine : LlmReasoningEngine {

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
        val lastCallerUtterance = conversationHistory.lastOrNull { it.first == "Caller" }?.second?.trim() ?: ""
        val lowerCaller = lastCallerUtterance.lowercase()
        val isTelugu = preferredLanguage.contains("Telugu", ignoreCase = true)

        // Turn 0: Greeting turn (Transparent AI Assistant introduction)
        if (conversationHistory.isEmpty() || conversationHistory.none { it.first == "Caller" }) {
            val memoryGreeting = if (!callerMemoryContext.isNullOrBlank()) {
                if (isTelugu) {
                    "నమస్కారం, నేను AI కాల్ అసిస్టెంట్ ని. మీ మునుపటి సంభాషణ గుర్తు ఉంది. ఈ రోజు మీకు ఎలా సహాయపడగలను?"
                } else {
                    "Hello, I am the AI call assistant. I recall our previous conversation. How can I assist you today?"
                }
            } else {
                if (isTelugu) {
                    "నమస్కారం, నేను AI కాల్ అసిస్టెంట్ ని. మీరు ఎవరి గురించి మాట్లాడుతున్నారు మరియు మీ కాల్ ఉద్దేశ్యం ఏమిటి?"
                } else {
                    "Hello, I am the AI call assistant. Who am I speaking with and how can I help you?"
                }
            }
            return AgentResponse(
                spokenText = memoryGreeting,
                confidence = 1.0f
            )
        }

        // 1. Hard Emergency & Urgent Escalation Overrides
        if (containsAny(lowerCaller, "urgent", "emergency", "hospital", "accident", "ambulance", "police", "heart attack", "fire")) {
            val reply = if (isTelugu) {
                "ఇది చాలా ముఖ్యమైనదిగా కనిపిస్తుంది. నేను వెంటనే యూజర్ కి అత్యవసర నోటిఫికేషన్ పంపుతున్నాను."
            } else {
                "This seems urgent. I am alerting the user right now to connect with you immediately."
            }
            return AgentResponse(
                spokenText = reply,
                extractedFacts = listOf("Urgent request from caller: $lastCallerUtterance"),
                proposedActionItems = listOf("Urgent callback needed for $callerNumber"),
                shouldEscalateToHuman = true,
                confidence = 1.0f
            )
        }

        // 2. RAG Evidence Grounding
        // Check if any retrieved knowledge documents directly answer or ground the query
        val deliveryFact = retrievedFacts.find { it.category == "DELIVERY" }
        if (containsAny(lowerCaller, "delivery", "parcel", "package", "swiggy", "zomato", "amazon", "courier")) {
            val reply = if (deliveryFact != null && deliveryFact.content.isNotBlank()) {
                deliveryFact.content
            } else if (isTelugu) {
                "దయచేసి పార్సెల్ ని డోర్ దగ్గర లేదా సెక్యూరిటీ వద్ద ఉంచండి. ధన్యవాదాలు."
            } else {
                "Please leave the parcel at the door or with security. Thank you."
            }
            return AgentResponse(
                spokenText = reply,
                extractedFacts = listOf("Delivery arrived: $lastCallerUtterance"),
                proposedActionItems = listOf("Collect parcel from door/security"),
                shouldHangUp = true,
                groundingSource = deliveryFact?.title ?: "Standard Delivery Policy",
                confidence = deliveryFact?.confidence ?: 0.95f
            )
        }

        val workFact = retrievedFacts.find { it.category == "WORK" || it.category == "SCHEDULE" }
        if (containsAny(lowerCaller, "college", "exam", "class", "timetable", "office", "meeting", "work")) {
            val reply = if (isTelugu) {
                "సరేనండి, మీ సమాచారాన్ని నోట్ చేసుకున్నాను. యూజర్ కి వెంటనే తెలియజేస్తాను."
            } else {
                "Understood. I have recorded your message and will notify the user promptly."
            }
            val entities = extractKeyEntities(lastCallerUtterance)
            return AgentResponse(
                spokenText = reply,
                extractedFacts = listOf("College/Work update: $lastCallerUtterance") + entities,
                proposedActionItems = listOf("Review update from ${callerName ?: callerNumber}"),
                shouldHangUp = true,
                groundingSource = workFact?.title ?: "Standard Policy",
                confidence = workFact?.confidence ?: 0.95f
            )
        }

        // Check for specific custom instructions retrieved via RAG
        val customFact = retrievedFacts.find { it.source == "USER_INSTRUCTION" && it.category != "SECURITY" && it.category != "DELIVERY" }
        if (customFact != null && queryMatchesFact(lowerCaller, customFact.content)) {
            val reply = "The user has noted: ${customFact.content}. I will let them know you called."
            return AgentResponse(
                spokenText = reply,
                extractedFacts = listOf("Grounded inquiry answered: ${customFact.title}"),
                proposedActionItems = listOf("Follow up with ${callerName ?: callerNumber}"),
                shouldHangUp = false,
                groundingSource = customFact.title,
                confidence = customFact.confidence
            )
        }

        // 3. Ambiguity & Clarifying Questions
        // If caller is vague (e.g., "tell him to call me back" without context or name)
        if (isVagueInquiry(lowerCaller) && callerName == null) {
            val clarifyingPrompt = "Certainly, could you please tell me your name and what this is regarding so I can pass on your message?"
            return AgentResponse(
                spokenText = clarifyingPrompt,
                extractedFacts = emptyList(),
                proposedActionItems = listOf("Clarification requested from caller"),
                shouldHangUp = false,
                confidence = 0.85f
            )
        }

        // 4. Appointment / Schedule Clarification
        if (containsAny(lowerCaller, "appointment", "schedule", "tomorrow", "meet") && !hasTimeEntity(lowerCaller)) {
            val clarifyingPrompt = "Understood. What specific time were you hoping to meet or speak, so I can check availability?"
            return AgentResponse(
                spokenText = clarifyingPrompt,
                extractedFacts = listOf("Meeting/Appointment requested: $lastCallerUtterance"),
                proposedActionItems = listOf("Follow up on requested appointment with $callerNumber"),
                shouldHangUp = false,
                confidence = 0.90f
            )
        }

        // 5. Goodbye / Polite Closure Turn
        if (containsAny(lowerCaller, "thank you", "thanks", "bye", "goodbye", "have a good day", "see you")) {
            val closing = "You're welcome. Thank you for calling, have a great day."
            return AgentResponse(
                spokenText = closing,
                extractedFacts = emptyList(),
                proposedActionItems = emptyList(),
                shouldHangUp = true,
                confidence = 1.0f
            )
        }

        // 6. Default Polite Message-Taking Closure (Grounds when no specific fact exists)
        val reply = if (isTelugu) {
            "మీ సందేశం రికార్డ్ చేయబడింది. యూజర్ ఫ్రీ అవ్వగానే మీకు తిరిగి కాల్ చేస్తారు. ధన్యవాదాలు."
        } else {
            "Thank you. Your message has been noted, and the user will get back to you soon."
        }
        val extractedEntities = extractKeyEntities(lastCallerUtterance)
        return AgentResponse(
            spokenText = reply,
            extractedFacts = listOf("General message: $lastCallerUtterance") + extractedEntities,
            proposedActionItems = listOf("Call back ${callerName ?: callerNumber}"),
            shouldHangUp = true,
            confidence = 0.90f
        )
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean {
        return keywords.any { text.contains(it, ignoreCase = true) }
    }

    private fun isVagueInquiry(lower: String): Boolean {
        val vaguePhrases = listOf(
            "call me back", "tell him to call", "ask him to call", "tell her to call",
            "is he there", "is she there", "can i speak with", "are you free"
        )
        return vaguePhrases.any { lower.contains(it) } && lower.length < 45
    }

    private fun hasTimeEntity(text: String): Boolean {
        val timeRegex = Pattern.compile("\\b(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm|o'clock)?|morning|afternoon|evening|night)\\b", Pattern.CASE_INSENSITIVE)
        return timeRegex.matcher(text).find()
    }

    private fun extractKeyEntities(text: String): List<String> {
        val entities = mutableListOf<String>()

        // Time / date detection
        val timeMatcher = Pattern.compile("\\b(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?|today|tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b", Pattern.CASE_INSENSITIVE).matcher(text)
        while (timeMatcher.find()) {
            entities.add("Time/Date: ${timeMatcher.group()}")
        }

        // Phone number detection
        val phoneMatcher = Pattern.compile("\\b(?:\\+?\\d{1,3}[- ]?)?\\d{10}\\b").matcher(text)
        while (phoneMatcher.find()) {
            entities.add("Phone: ${phoneMatcher.group()}")
        }

        return entities.distinct()
    }

    private fun queryMatchesFact(query: String, factContent: String): Boolean {
        val queryWords = query.split(Regex("[^a-zA-Z0-9]+")).filter { it.length > 3 }
        val factLower = factContent.lowercase()
        return queryWords.any { factLower.contains(it) }
    }
}

// Backward compatibility alias so existing tests and classes instantiate seamlessly
typealias RuleBasedReasoningEngine = EnglishConversationalReasoningEngine
