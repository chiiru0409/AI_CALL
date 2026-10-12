package com.aicall.agent.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class KnowledgeSource {
    USER_INSTRUCTION,
    PERSONAL_FACT,
    CALLER_NOTE,
    CALL_PREFERENCE,
    VERIFIED_MEMORY
}

enum class KnowledgeCategory {
    GENERAL,
    SCHEDULE,
    WORK,
    DELIVERY,
    FAMILY,
    SECURITY,
    PREFERENCE
}

@Entity(
    tableName = "knowledge_documents",
    indices = [
        Index(value = ["isApproved"]),
        Index(value = ["targetPhoneNumber"]),
        Index(value = ["expiresAt"]),
        Index(value = ["category"])
    ]
)
data class KnowledgeDocument(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val source: KnowledgeSource = KnowledgeSource.USER_INSTRUCTION,
    val title: String,
    val content: String,
    val category: KnowledgeCategory = KnowledgeCategory.GENERAL,
    val targetPhoneNumber: String? = null,
    val isConfidential: Boolean = false,
    val trustLevel: Float = 1.0f,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,
    val isApproved: Boolean = true,
    val provenance: String = "User Profile"
)
