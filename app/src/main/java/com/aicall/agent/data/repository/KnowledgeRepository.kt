package com.aicall.agent.data.repository

import com.aicall.agent.data.db.KnowledgeDocumentDao
import com.aicall.agent.data.model.KnowledgeCategory
import com.aicall.agent.data.model.KnowledgeDocument
import com.aicall.agent.data.model.KnowledgeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class KnowledgeRepository(
    private val knowledgeDocumentDao: KnowledgeDocumentDao
) {
    val allApprovedDocuments: Flow<List<KnowledgeDocument>> = knowledgeDocumentDao.getAllApprovedDocuments()
    val allDocuments: Flow<List<KnowledgeDocument>> = knowledgeDocumentDao.getAllDocuments()

    suspend fun getDocumentById(id: Long): KnowledgeDocument? = knowledgeDocumentDao.getDocumentById(id)

    suspend fun addDocument(document: KnowledgeDocument): Long {
        return knowledgeDocumentDao.insertDocument(document)
    }

    suspend fun updateDocument(document: KnowledgeDocument) {
        knowledgeDocumentDao.updateDocument(document.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteDocument(id: Long) {
        knowledgeDocumentDao.deleteDocumentById(id)
    }

    suspend fun purgeExpired(currentTime: Long = System.currentTimeMillis()): Int {
        return knowledgeDocumentDao.deleteExpiredDocuments(currentTime)
    }

    suspend fun seedInitialKnowledgeIfEmpty(rawInstructions: String) {
        val existing = allDocuments.firstOrNull() ?: emptyList()
        if (existing.isNotEmpty()) return

        val documentsToInsert = mutableListOf<KnowledgeDocument>()

        // 1. Mandatory Core Security & Privacy Rule
        documentsToInsert.add(
            KnowledgeDocument(
                source = KnowledgeSource.USER_INSTRUCTION,
                title = "Security & Privacy Guardrails",
                content = "Never reveal OTP codes, banking passwords, payment details, card PINs, or home addresses to any caller.",
                category = KnowledgeCategory.SECURITY,
                isConfidential = true,
                trustLevel = 1.0f,
                provenance = "System Policy & User Profile"
            )
        )

        // 2. Availability & Callback Policy
        documentsToInsert.add(
            KnowledgeDocument(
                source = KnowledgeSource.USER_INSTRUCTION,
                title = "General Availability Policy",
                content = "The user is currently busy and taking calls via AI assistant. Urgent emergencies are escalated immediately; routine inquiries and messages are recorded for callback.",
                category = KnowledgeCategory.SCHEDULE,
                isConfidential = false,
                trustLevel = 1.0f,
                provenance = "User Profile"
            )
        )

        // 3. Delivery Instructions
        documentsToInsert.add(
            KnowledgeDocument(
                source = KnowledgeSource.USER_INSTRUCTION,
                title = "Package Delivery Handling",
                content = "For food (Swiggy/Zomato) or parcel deliveries, instruct the delivery person to leave the package at the front door or with security guard.",
                category = KnowledgeCategory.DELIVERY,
                isConfidential = false,
                trustLevel = 1.0f,
                provenance = "User Profile"
            )
        )

        // 4. College / Work Message Policy
        documentsToInsert.add(
            KnowledgeDocument(
                source = KnowledgeSource.USER_INSTRUCTION,
                title = "College and Office Updates",
                content = "For college or office updates regarding exams, classes, or meetings, record the key details, timetable, or agenda and reassure the caller that the user will be notified.",
                category = KnowledgeCategory.WORK,
                isConfidential = false,
                trustLevel = 1.0f,
                provenance = "User Profile"
            )
        )

        // 5. Ingest any additional user-specific instructions
        if (rawInstructions.isNotBlank()) {
            val lines = rawInstructions.split("\n", ".").map { it.trim() }.filter { it.length > 15 }
            for (line in lines) {
                // Avoid duplicating default rules
                if (!line.contains("OTP", ignoreCase = true) && !line.contains("delivery", ignoreCase = true)) {
                    documentsToInsert.add(
                        KnowledgeDocument(
                            source = KnowledgeSource.USER_INSTRUCTION,
                            title = "User Preference Note",
                            content = line,
                            category = KnowledgeCategory.GENERAL,
                            isConfidential = false,
                            trustLevel = 1.0f,
                            provenance = "Settings Knowledge Base"
                        )
                    )
                }
            }
        }

        knowledgeDocumentDao.insertDocuments(documentsToInsert)
    }

    suspend fun clearAll() {
        knowledgeDocumentDao.clearAll()
    }
}
