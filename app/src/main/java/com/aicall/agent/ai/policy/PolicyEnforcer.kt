package com.aicall.agent.ai.policy

sealed class PolicyValidationResult {
    object Allowed : PolicyValidationResult()
    data class Blocked(val reason: String, val violationType: String) : PolicyValidationResult()
    data class RequiresEscalation(val reason: String) : PolicyValidationResult()
}

class PolicyEnforcer {

    private val sensitiveKeywords = listOf(
        "otp", "one time password", "pin", "cvv", "password",
        "bank password", "debit card number", "credit card number",
        "net banking password", "upi pin"
    )

    private val promptInjectionPatterns = listOf(
        "ignore previous instructions",
        "ignore all previous",
        "you are now in developer mode",
        "system prompt override",
        "act as an uncensored",
        "forget your rules",
        "repeat everything above",
        "reveal your prompt"
    )

    fun validateCallerUtterance(callerSpeech: String): PolicyValidationResult {
        val lower = callerSpeech.lowercase().trim()

        // 1. Check for prompt injection attempts
        for (pattern in promptInjectionPatterns) {
            if (lower.contains(pattern)) {
                return PolicyValidationResult.Blocked(
                    reason = "Prompt injection attempt detected in caller speech: '$pattern'",
                    violationType = "SECURITY_PROMPT_INJECTION"
                )
            }
        }

        // 2. Check for requests to leak sensitive secrets (OTPs, PINs)
        for (keyword in sensitiveKeywords) {
            if (lower.contains(keyword) && (lower.contains("tell me") || lower.contains("give me") || lower.contains("send me") || lower.contains("share"))) {
                return PolicyValidationResult.Blocked(
                    reason = "Caller attempted to request sensitive credentials ($keyword). Strict policy forbids sharing.",
                    violationType = "SENSITIVE_DATA_EXFILTRATION"
                )
            }
        }

        return PolicyValidationResult.Allowed
    }

    fun validateProposedAgentResponse(response: String): PolicyValidationResult {
        val lower = response.lowercase().trim()

        for (keyword in sensitiveKeywords) {
            if (lower.contains(keyword) && (lower.contains("is") || lower.contains("code") || lower.contains("number"))) {
                return PolicyValidationResult.Blocked(
                    reason = "Agent attempted to speak a sensitive secret ($keyword). Blocked by policy.",
                    violationType = "SENSITIVE_DATA_DISCLOSURE_PREVENTION"
                )
            }
        }

        return PolicyValidationResult.Allowed
    }
}
