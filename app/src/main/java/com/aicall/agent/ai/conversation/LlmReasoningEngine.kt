package com.aicall.agent.ai.conversation

data class AgentResponse(
    val spokenText: String,
    val extractedFacts: List<String> = emptyList(),
    val proposedActionItems: List<String> = emptyList(),
    val shouldHangUp: Boolean = false,
    val shouldEscalateToHuman: Boolean = false
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
}

class RuleBasedReasoningEngine : LlmReasoningEngine {
    override suspend fun generateTurnResponse(
        callerNumber: String,
        callerName: String?,
        callerMemoryContext: String?,
        conversationHistory: List<Pair<String, String>>,
        userApprovedKnowledge: String,
        preferredLanguage: String
    ): AgentResponse {
        val lastCallerUtterance = conversationHistory.lastOrNull { it.first == "Caller" }?.second?.lowercase() ?: ""
        val isTelugu = preferredLanguage.contains("Telugu", ignoreCase = true)

        if (conversationHistory.isEmpty() || conversationHistory.size <= 1) {
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
            return AgentResponse(spokenText = memoryGreeting)
        }

        // Delivery calls
        if (lastCallerUtterance.contains("delivery") || lastCallerUtterance.contains("parcel") || lastCallerUtterance.contains("swiggy") || lastCallerUtterance.contains("zomato")) {
            val reply = if (isTelugu) {
                "దయచేసి పార్సెల్ ని డోర్ దగ్గర లేదా సెక్యూరిటీ వద్ద ఉంచండి. ధన్యవాదాలు."
            } else {
                "Please leave the parcel at the door or with security. Thank you."
            }
            return AgentResponse(
                spokenText = reply,
                extractedFacts = listOf("Delivery arrived"),
                proposedActionItems = listOf("Collect parcel from door/security"),
                shouldHangUp = true
            )
        }

        // College or work
        if (lastCallerUtterance.contains("college") || lastCallerUtterance.contains("exam") || lastCallerUtterance.contains("class") || lastCallerUtterance.contains("office")) {
            val reply = if (isTelugu) {
                "సరేనండి, మీ సమాచారాన్ని నోట్ చేసుకున్నాను. యూజర్ కి వెంటనే తెలియజేస్తాను."
            } else {
                "Understood. I have recorded your message and will notify the user promptly."
            }
            return AgentResponse(
                spokenText = reply,
                extractedFacts = listOf("College/Work update: $lastCallerUtterance"),
                proposedActionItems = listOf("Review update from $callerNumber"),
                shouldHangUp = true
            )
        }

        // Urgency / Emergency
        if (lastCallerUtterance.contains("urgent") || lastCallerUtterance.contains("emergency") || lastCallerUtterance.contains("hospital")) {
            val reply = if (isTelugu) {
                "ఇది చాలా ముఖ్యమైనదిగా కనిపిస్తుంది. నేను వెంటనే యూజర్ కి అత్యవసర నోటిఫికేషన్ పంపుతున్నాను."
            } else {
                "This seems urgent. I am alerting the user right now to connect with you immediately."
            }
            return AgentResponse(
                spokenText = reply,
                extractedFacts = listOf("Urgent request from caller"),
                proposedActionItems = listOf("Urgent callback needed for $callerNumber"),
                shouldEscalateToHuman = true
            )
        }

        // Default polite closure / recording callback
        val reply = if (isTelugu) {
            "మీ సందేశం రికార్డ్ చేయబడింది. యూజర్ ఫ్రీ అవ్వగానే మీకు తిరిగి కాల్ చేస్తారు. ధన్యవాదాలు."
        } else {
            "Thank you. Your message has been noted, and the user will get back to you soon."
        }
        return AgentResponse(
            spokenText = reply,
            extractedFacts = listOf("General message: $lastCallerUtterance"),
            proposedActionItems = listOf("Call back $callerNumber"),
            shouldHangUp = true
        )
    }
}

