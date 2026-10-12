package com.aicall.agent.ai.rag

import com.aicall.agent.data.db.ActionItemDao
import com.aicall.agent.data.db.CallerProfileDao
import com.aicall.agent.data.db.KnowledgeDocumentDao
import com.aicall.agent.data.model.KnowledgeDocument
import com.aicall.agent.data.util.PhoneNumberUtils
import kotlinx.coroutines.flow.firstOrNull

class LocalRagRetriever(
    private val knowledgeDocumentDao: KnowledgeDocumentDao,
    private val callerProfileDao: CallerProfileDao,
    private val actionItemDao: ActionItemDao? = null
) {

    private val promptInjectionTokens = listOf(
        "ignore previous instructions",
        "system prompt",
        "developer mode",
        "override safety",
        "disregard",
        "jailbreak",
        "you are now an ai that"
    )

    suspend fun retrieveRelevantFacts(
        callerNumber: String,
        callerName: String?,
        isCallerVerified: Boolean,
        callerUtterance: String,
        currentTime: Long = System.currentTimeMillis()
    ): List<RetrievedFact> {
        val normalizedCallerNumber = PhoneNumberUtils.normalize(callerNumber)
        val queryTokens = tokenize(callerUtterance)

        val retrievedCandidates = mutableListOf<ScoredFact>()

        // 1. Ingest approved Knowledge Documents
        val candidateDocs = knowledgeDocumentDao.getCandidateDocumentsForCaller(
            callerNumber = normalizedCallerNumber,
            currentTime = currentTime
        )

        for (doc in candidateDocs) {
            // Strict Privacy Isolation: If doc is caller-specific, ensure strict phone number match
            if (!doc.targetPhoneNumber.isNullOrBlank()) {
                val targetNorm = PhoneNumberUtils.normalize(doc.targetPhoneNumber)
                if (targetNorm != normalizedCallerNumber && doc.targetPhoneNumber != callerNumber) {
                    continue // Caller isolation: NEVER leak Caller A's facts to Caller B
                }
            }

            // Confidentiality Guard: Confidential facts are NEVER exposed to unverified callers
            if (doc.isConfidential && !isCallerVerified) {
                continue
            }

            // Expiration Guard: Stale facts with past expiration timestamps are dropped
            if (doc.expiresAt != null && doc.expiresAt <= currentTime) {
                continue
            }

            val sanitizedContent = sanitizeContent(doc.content)
            val score = calculateRelevanceScore(
                queryTokens = queryTokens,
                title = doc.title,
                content = sanitizedContent,
                baseTrust = doc.trustLevel,
                updatedAt = doc.updatedAt,
                currentTime = currentTime
            )

            if (score > 0.15f) {
                retrievedCandidates.add(
                    ScoredFact(
                        fact = RetrievedFact(
                            id = doc.id,
                            title = doc.title,
                            content = sanitizedContent,
                            source = doc.source.name,
                            category = doc.category.name,
                            confidence = doc.trustLevel,
                            provenance = doc.provenance,
                            isCallerSpecific = !doc.targetPhoneNumber.isNullOrBlank(),
                            timestamp = doc.updatedAt
                        ),
                        score = score
                    )
                )
            }
        }

        // 2. Ingest Caller Profile Notes & Memory (for this specific caller only)
        val profile = callerProfileDao.getProfile(normalizedCallerNumber) ?: callerProfileDao.getProfile(callerNumber)
        if (profile != null) {
            val isVerifiedContact = profile.contactName != null || profile.isVerified

            // Ingest Caller Notes if present
            if (profile.userNotes.isNotBlank()) {
                val sanitizedNotes = sanitizeContent(profile.userNotes)
                val noteScore = calculateRelevanceScore(
                    queryTokens = queryTokens,
                    title = "User Note for Caller",
                    content = sanitizedNotes,
                    baseTrust = if (isVerifiedContact) 0.95f else 0.50f,
                    updatedAt = profile.lastInteractionTime,
                    currentTime = currentTime
                )
                retrievedCandidates.add(
                    ScoredFact(
                        fact = RetrievedFact(
                            id = -100L,
                            title = "Caller-Specific User Note",
                            content = sanitizedNotes,
                            source = "CALLER_NOTE",
                            category = "PREFERENCE",
                            confidence = if (isVerifiedContact) 0.95f else 0.50f,
                            provenance = "Contacts Note for ${profile.contactName ?: profile.phoneNumber}",
                            isCallerSpecific = true,
                            timestamp = profile.lastInteractionTime
                        ),
                        score = maxOf(0.40f, noteScore)
                    )
                )
            }

            // Ingest Unresolved Requests if present
            if (profile.unresolvedRequests.isNotBlank()) {
                val sanitizedUnresolved = sanitizeContent(profile.unresolvedRequests)
                retrievedCandidates.add(
                    ScoredFact(
                        fact = RetrievedFact(
                            id = -200L,
                            title = "Unresolved Action Item",
                            content = sanitizedUnresolved,
                            source = "UNRESOLVED_REQUEST",
                            category = "WORK",
                            confidence = 0.90f,
                            provenance = "Previous Conversation Memory",
                            isCallerSpecific = true,
                            timestamp = profile.lastInteractionTime
                        ),
                        score = 0.60f
                    )
                )
            }
        }

        // 3. Ingest Active Action Items (if DAO available)
        if (actionItemDao != null && profile != null) {
            val pendingItems = actionItemDao.getPendingActionItems().firstOrNull() ?: emptyList()
            for (item in pendingItems) {
                val itemTokens = tokenize(item.title + " " + item.description)
                val overlap = queryTokens.intersect(itemTokens).size
                if (overlap > 0) {
                    retrievedCandidates.add(
                        ScoredFact(
                            fact = RetrievedFact(
                                id = item.id,
                                title = item.title,
                                content = sanitizeContent(item.description.ifBlank { item.title }),
                                source = "ACTION_ITEM",
                                category = "WORK",
                                confidence = 0.85f,
                                provenance = "Action Items List",
                                isCallerSpecific = false,
                                timestamp = item.createdAt
                            ),
                            score = 0.50f + (overlap * 0.15f)
                        )
                    )
                }
            }
        }

        // Rank by score descending and return Top-K (up to 4 concise facts)
        return retrievedCandidates
            .sortedByDescending { it.score }
            .take(4)
            .map { it.fact }
    }

    private fun sanitizeContent(raw: String): String {
        var clean = raw
        for (token in promptInjectionTokens) {
            if (clean.contains(token, ignoreCase = true)) {
                // Neutralize injection attempt
                clean = clean.replace(Regex(Regex.escape(token), RegexOption.IGNORE_CASE), "[DEFUSED_INSTRUCTION]")
            }
        }
        return clean.trim()
    }

    private fun tokenize(text: String): Set<String> {
        val stopwords = setOf(
            "the", "a", "an", "is", "am", "are", "was", "were", "to", "in", "for", "on", "with",
            "at", "by", "from", "and", "or", "of", "it", "this", "that", "i", "you", "he", "she",
            "we", "they", "me", "my", "your", "hello", "hi", "hey", "please", "call", "calling"
        )
        return text.lowercase()
            .split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.length > 2 && !stopwords.contains(it) }
            .toSet()
    }

    private fun calculateRelevanceScore(
        queryTokens: Set<String>,
        title: String,
        content: String,
        baseTrust: Float,
        updatedAt: Long,
        currentTime: Long
    ): Float {
        val titleTokens = tokenize(title)
        val contentTokens = tokenize(content)

        val titleOverlap = queryTokens.intersect(titleTokens).size
        val contentOverlap = queryTokens.intersect(contentTokens).size

        var overlapScore = (titleOverlap * 0.35f) + (contentOverlap * 0.20f)
        if (queryTokens.isEmpty()) {
            overlapScore = 0.20f // General availability facts can match empty or greeting turns
        }

        // Recency bonus: facts updated within the last 7 days get a minor boost
        val ageHours = maxOf(0L, (currentTime - updatedAt) / (1000L * 60 * 60))
        val freshnessScore = when {
            ageHours < 24 -> 0.15f
            ageHours < 168 -> 0.10f
            else -> 0.05f
        }

        return (overlapScore * 0.60f) + (baseTrust * 0.30f) + freshnessScore
    }

    private data class ScoredFact(
        val fact: RetrievedFact,
        val score: Float
    )
}
