package com.aicall.agent.data.db

import androidx.room.*
import com.aicall.agent.data.model.KnowledgeCategory
import com.aicall.agent.data.model.KnowledgeDocument
import kotlinx.coroutines.flow.Flow

@Dao
interface KnowledgeDocumentDao {
    @Query("SELECT * FROM knowledge_documents WHERE isApproved = 1 ORDER BY updatedAt DESC")
    fun getAllApprovedDocuments(): Flow<List<KnowledgeDocument>>

    @Query("SELECT * FROM knowledge_documents ORDER BY updatedAt DESC")
    fun getAllDocuments(): Flow<List<KnowledgeDocument>>

    @Query("SELECT * FROM knowledge_documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: Long): KnowledgeDocument?

    @Query("""
        SELECT * FROM knowledge_documents 
        WHERE isApproved = 1 
          AND (targetPhoneNumber IS NULL OR targetPhoneNumber = :callerNumber OR targetPhoneNumber = '')
          AND (expiresAt IS NULL OR expiresAt > :currentTime)
        ORDER BY trustLevel DESC, updatedAt DESC
    """)
    suspend fun getCandidateDocumentsForCaller(callerNumber: String, currentTime: Long): List<KnowledgeDocument>

    @Query("""
        SELECT * FROM knowledge_documents 
        WHERE isApproved = 1 
          AND (expiresAt IS NULL OR expiresAt > :currentTime)
          AND category = :category
        ORDER BY trustLevel DESC, updatedAt DESC
    """)
    suspend fun getDocumentsByCategory(category: KnowledgeCategory, currentTime: Long): List<KnowledgeDocument>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: KnowledgeDocument): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocuments(documents: List<KnowledgeDocument>): List<Long>

    @Update
    suspend fun updateDocument(document: KnowledgeDocument)

    @Delete
    suspend fun deleteDocument(document: KnowledgeDocument)

    @Query("DELETE FROM knowledge_documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)

    @Query("DELETE FROM knowledge_documents WHERE expiresAt IS NOT NULL AND expiresAt <= :currentTime")
    suspend fun deleteExpiredDocuments(currentTime: Long): Int

    @Query("DELETE FROM knowledge_documents")
    suspend fun clearAll()
}
