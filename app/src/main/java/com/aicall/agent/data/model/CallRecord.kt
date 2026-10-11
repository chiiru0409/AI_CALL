package com.aicall.agent.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class CallDirection {
    INCOMING, OUTGOING
}

enum class CallerCategory {
    FAMILY, FRIENDS, WORK, COLLEGE, BANKING, LOANS, DELIVERY, SPAM, FRAUD_SUSPECTED, OTHER
}

enum class UrgencyLevel {
    LOW, MEDIUM, HIGH, EMERGENCY
}

enum class RiskLevel {
    SAFE, UNKNOWN, SUSPICIOUS, HIGH_RISK_FRAUD, NOT_ASSESSED
}

enum class HandlingStatus {
    USER_ANSWERED, AI_HANDLED, MISSED, REJECTED, ESCALATED_TO_USER, QUICK_CALLBACK_SENT
}

@Entity(
    tableName = "call_records",
    indices = [
        Index(value = ["startTime"]),
        Index(value = ["callerNumber"]),
        Index(value = ["urgencyLevel"]),
        Index(value = ["riskLevel"]),
        Index(value = ["handlingStatus"])
    ]
)
data class CallRecord(
    @PrimaryKey
    val sessionId: String,
    val callerNumber: String,
    val callerName: String? = null,
    val direction: CallDirection = CallDirection.INCOMING,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val durationSeconds: Long = 0,
    val category: CallerCategory = CallerCategory.OTHER,
    val intent: String = "",
    val urgencyLevel: UrgencyLevel = UrgencyLevel.LOW,
    val riskLevel: RiskLevel = RiskLevel.NOT_ASSESSED,
    val handlingStatus: HandlingStatus = HandlingStatus.MISSED,
    val outcomeSummary: String = "",
    val transcript: String = "",
    val recordingStatus: String = "Recording unavailable on this device",
    val extractedFactsJson: String = "[]",
    val actionItemsJson: String = "[]",
    val callerLocation: String = "Location unavailable",
    val verificationStatus: String = "Unverified",
    val processingError: String? = null
)

