package com.aicall.agent.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class RuleConditionType {
    CALLER_CATEGORY,
    PHONE_NUMBER_CONTAINS,
    KEYWORD_IN_INTENT,
    TIME_OF_DAY,
    UNKNOWN_CALLER,
    URGENT_CALLER
}

enum class RuleActionType {
    AUTO_HANDLE_AI,
    ALWAYS_RING_USER,
    REJECT_AND_SMS,
    ALERT_URGENT_NOTIFICATION,
    BLOCK_SILENTLY
}

@Entity(
    tableName = "agent_rules",
    indices = [
        Index(value = ["isEnabled"]),
        Index(value = ["priority"])
    ]
)
data class AgentRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val conditionType: RuleConditionType,
    val matchValue: String,
    val actionType: RuleActionType,
    val priority: Int = 1,
    val isEnabled: Boolean = true
)

@Entity(
    tableName = "agent_schedules",
    indices = [
        Index(value = ["isEnabled"])
    ]
)
data class AgentSchedule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val daysOfWeekMask: Int = 0x7F, // Bitmask for Mon-Sun (1=Mon, 2=Tue, 4=Wed, etc.)
    val startMinuteOfDay: Int = 540, // 09:00 AM
    val endMinuteOfDay: Int = 1020, // 05:00 PM
    val crossesMidnight: Boolean = false,
    val isEnabled: Boolean = true
)

@Entity(
    tableName = "action_items",
    indices = [
        Index(value = ["callSessionId"]),
        Index(value = ["isCompleted"]),
        Index(value = ["createdAt"])
    ]
)
data class ActionItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val callSessionId: String,
    val title: String,
    val description: String = "",
    val priority: String = "NORMAL", // LOW, NORMAL, HIGH, URGENT
    val dueDate: Long? = null,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "audit_logs",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["callSessionId"])
    ]
)
data class AuditLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String,
    val callSessionId: String? = null,
    val description: String,
    val severity: String = "INFO"
)

