package com.aicall.agent.data.repository

import com.aicall.agent.data.db.CallerProfileDao
import com.aicall.agent.data.model.CallerCategory
import com.aicall.agent.data.model.CallerProfile
import com.aicall.agent.data.util.PhoneNumberUtils
import kotlinx.coroutines.flow.Flow

class CallerMemoryRepository(
    private val callerProfileDao: CallerProfileDao
) {
    val allProfiles: Flow<List<CallerProfile>> = callerProfileDao.getAllProfiles()

    suspend fun getProfile(phoneNumber: String): CallerProfile? {
        val normalized = PhoneNumberUtils.normalize(phoneNumber)
        return callerProfileDao.getProfile(normalized) ?: callerProfileDao.getProfile(phoneNumber)
    }

    suspend fun getCallerMemoryContext(phoneNumber: String): String? {
        val profile = getProfile(phoneNumber) ?: return null
        if (profile.memorySummary.isBlank() && profile.unresolvedRequests.isBlank() && profile.claimedName.isNullOrBlank()) {
            return null
        }

        val identityNote = when {
            profile.contactName != null -> "Verified Contact: ${profile.contactName}"
            !profile.claimedName.isNullOrBlank() && profile.isVerified -> "Verified Caller: ${profile.claimedName}"
            !profile.claimedName.isNullOrBlank() -> "Unverified Caller Claim: '${profile.claimedName}' (NOT verified identity)"
            else -> "Unknown Caller (${profile.phoneNumber})"
        }

        val parts = mutableListOf<String>()
        parts.add("Caller Status: $identityNote")
        if (profile.memorySummary.isNotBlank()) {
            parts.add("Previous Interactions: ${profile.memorySummary}")
        }
        if (profile.unresolvedRequests.isNotBlank()) {
            parts.add("Unresolved Requests: ${profile.unresolvedRequests}")
        }
        if (profile.preferences.isNotBlank()) {
            parts.add("Preferences: ${profile.preferences}")
        }

        return parts.joinToString(" | ")
    }

    suspend fun updateCallerMemory(
        phoneNumber: String,
        contactName: String?,
        claimedName: String?,
        category: CallerCategory,
        memorySummary: String,
        unresolvedRequest: String? = null
    ) {
        val normalized = PhoneNumberUtils.normalize(phoneNumber)
        val existing = callerProfileDao.getProfile(normalized) ?: callerProfileDao.getProfile(phoneNumber)

        val sanitizedClaim = claimedName?.trim()
            ?.replace(Regex("[^a-zA-Z\\s]"), "")
            ?.take(40)
            ?.ifBlank { null }

        val cleanSummary = memorySummary.trim().replace(Regex("""[<>{}\r]"""), " ").take(300)
        val updatedSummary = if (existing != null && existing.memorySummary.isNotBlank()) {
            "${existing.memorySummary.takeLast(600)}\n---\n$cleanSummary"
        } else {
            cleanSummary
        }.take(1000)

        val updatedUnresolved = when {
            !unresolvedRequest.isNullOrBlank() -> unresolvedRequest.trim().replace(Regex("""[<>{}\r\n]"""), " ").take(150)
            existing != null -> existing.unresolvedRequests
            else -> ""
        }

        val isContactMatch = contactName != null
        val updated = if (existing != null) {
            existing.copy(
                contactName = contactName ?: existing.contactName,
                claimedName = sanitizedClaim ?: existing.claimedName,
                isVerified = if (isContactMatch) true else existing.isVerified,
                verificationMethod = if (isContactMatch) "Device Contacts Match" else existing.verificationMethod,
                trustScore = if (isContactMatch) 0.85f else minOf(existing.trustScore, 0.40f), // Trust never auto-inflates on repeated calls
                category = category,
                totalCalls = existing.totalCalls + 1,
                lastInteractionTime = System.currentTimeMillis(),
                memorySummary = updatedSummary,
                unresolvedRequests = updatedUnresolved
            )
        } else {
            CallerProfile(
                phoneNumber = normalized,
                contactName = contactName,
                claimedName = sanitizedClaim,
                isVerified = isContactMatch, // Only device contacts receive high initial trust
                verificationMethod = if (isContactMatch) "Device Contacts Match" else "Unverified Caller Claim",
                category = category,
                trustScore = if (isContactMatch) 0.85f else 0.4f,
                totalCalls = 1,
                lastInteractionTime = System.currentTimeMillis(),
                memorySummary = updatedSummary,
                unresolvedRequests = updatedUnresolved
            )
        }
        callerProfileDao.insertOrUpdate(updated)
    }

    suspend fun saveProfile(profile: CallerProfile) {
        callerProfileDao.insertOrUpdate(profile)
    }

    suspend fun setVerifiedStatus(phoneNumber: String, isVerified: Boolean, method: String) {
        val normalized = PhoneNumberUtils.normalize(phoneNumber)
        val existing = callerProfileDao.getProfile(normalized) ?: callerProfileDao.getProfile(phoneNumber)
        if (existing != null) {
            callerProfileDao.insertOrUpdate(
                existing.copy(
                    isVerified = isVerified,
                    verificationMethod = method,
                    trustScore = if (isVerified) 0.95f else 0.3f
                )
            )
        }
    }

    suspend fun clearUnresolvedRequest(phoneNumber: String) {
        val normalized = PhoneNumberUtils.normalize(phoneNumber)
        val existing = callerProfileDao.getProfile(normalized) ?: callerProfileDao.getProfile(phoneNumber)
        if (existing != null) {
            callerProfileDao.insertOrUpdate(
                existing.copy(unresolvedRequests = "")
            )
        }
    }

    suspend fun deleteProfile(phoneNumber: String) {
        val normalized = PhoneNumberUtils.normalize(phoneNumber)
        callerProfileDao.deleteProfileByNumber(normalized)
        callerProfileDao.deleteProfileByNumber(phoneNumber)
    }
}


