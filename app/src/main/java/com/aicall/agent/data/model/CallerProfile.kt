package com.aicall.agent.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "caller_profiles",
    indices = [
        Index(value = ["phoneNumber"]),
        Index(value = ["lastInteractionTime"]),
        Index(value = ["isVerified"])
    ]
)
data class CallerProfile(
    @PrimaryKey
    val phoneNumber: String,
    val contactName: String? = null,
    val claimedName: String? = null,
    val isVerified: Boolean = false,
    val verificationMethod: String = "None",
    val category: CallerCategory = CallerCategory.OTHER,
    val trustScore: Float = 0.5f,
    val totalCalls: Int = 1,
    val lastInteractionTime: Long = System.currentTimeMillis(),
    val userNotes: String = "",
    val memorySummary: String = "",
    val unresolvedRequests: String = "",
    val preferences: String = "",
    val customHandlingPolicy: String = "DEFAULT"
)

