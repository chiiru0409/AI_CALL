package com.aicall.agent.ai.policy

import com.aicall.agent.data.model.CallerCategory
import com.aicall.agent.data.model.RiskLevel
import com.aicall.agent.data.model.UrgencyLevel

data class AssessmentResult(
    val category: CallerCategory,
    val urgency: UrgencyLevel,
    val risk: RiskLevel,
    val intent: String,
    val summary: String,
    val shouldEscalate: Boolean
)

class RiskAndUrgencyAssessor {

    fun assessConversation(
        callerNumber: String,
        isContact: Boolean,
        transcript: String
    ): AssessmentResult {
        if (transcript.isBlank()) {
            return AssessmentResult(
                category = CallerCategory.OTHER,
                urgency = UrgencyLevel.LOW,
                risk = RiskLevel.NOT_ASSESSED,
                intent = "No conversation recorded",
                summary = "Call ended without speech or conversation transcript. Risk was not assessed.",
                shouldEscalate = false
            )
        }

        val lower = transcript.lowercase()

        // 1. Urgency Assessment (including subtle indicators & multilingual signals)
        val isEmergency = lower.contains("hospital") || lower.contains("accident") ||
                lower.contains("emergency") || lower.contains("icu") ||
                lower.contains("police") || lower.contains("urgent") ||
                lower.contains("twaraga") || lower.contains("ippude") || // Telugu urgency keywords
                lower.contains("turant") || lower.contains("jaldi")      // Hindi urgency keywords

        val isHighUrgency = isEmergency || lower.contains("important") ||
                lower.contains("asap") || lower.contains("immediately") ||
                lower.contains("crucial") || lower.contains("call back now")

        val urgency = when {
            isEmergency -> UrgencyLevel.EMERGENCY
            isHighUrgency -> UrgencyLevel.HIGH
            lower.contains("callback") || lower.contains("call back") -> UrgencyLevel.MEDIUM
            else -> UrgencyLevel.LOW
        }

        // 2. Risk & Fraud Assessment
        val isFraud = lower.contains("lottery") || lower.contains("won prize") ||
                lower.contains("kyc expired") || lower.contains("account blocked") ||
                lower.contains("electricity bill disconnect") || lower.contains("send otp") ||
                lower.contains("share pin") || lower.contains("remote app") ||
                lower.contains("anydesk") || lower.contains("teamviewer")

        val isSpam = !isContact && (lower.contains("loan") || lower.contains("credit card") ||
                lower.contains("insurance policy") || lower.contains("investment plan") ||
                lower.contains("real estate") || lower.contains("flat sale"))

        val risk = when {
            isFraud -> RiskLevel.HIGH_RISK_FRAUD
            isSpam -> RiskLevel.SUSPICIOUS
            !isContact -> RiskLevel.UNKNOWN
            else -> RiskLevel.SAFE
        }

        // 3. Category Assessment
        val category = when {
            isFraud -> CallerCategory.FRAUD_SUSPECTED
            isSpam -> CallerCategory.SPAM
            lower.contains("college") || lower.contains("exam") || lower.contains("faculty") || lower.contains("professor") -> CallerCategory.COLLEGE
            lower.contains("delivery") || lower.contains("parcel") || lower.contains("amazon") || lower.contains("swiggy") || lower.contains("zomato") -> CallerCategory.DELIVERY
            lower.contains("bank") || lower.contains("branch") -> CallerCategory.BANKING
            lower.contains("loan") -> CallerCategory.LOANS
            lower.contains("office") || lower.contains("meeting") || lower.contains("project") -> CallerCategory.WORK
            isContact -> CallerCategory.FRIENDS
            else -> CallerCategory.OTHER
        }

        val intent = when (category) {
            CallerCategory.DELIVERY -> "Delivery arrival confirmation"
            CallerCategory.COLLEGE -> "Academic / College notification"
            CallerCategory.BANKING -> "Banking inquiry"
            CallerCategory.SPAM -> "Unsolicited marketing"
            CallerCategory.FRAUD_SUSPECTED -> "Suspicious credential or money solicitation"
            CallerCategory.WORK -> "Work coordination"
            else -> if (isEmergency) "Urgent situation inquiry" else "Personal conversation"
        }

        val summary = "Caller discussed: $intent. Urgency evaluated as $urgency, Risk evaluated as $risk."

        return AssessmentResult(
            category = category,
            urgency = urgency,
            risk = risk,
            intent = intent,
            summary = summary,
            shouldEscalate = urgency == UrgencyLevel.EMERGENCY || urgency == UrgencyLevel.HIGH
        )
    }
}
