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
        
        val updatedSummary = if (existing != null && existing.memorySummary.isNotBlank()) {
            "${existing.memorySummary}\n---\n$memorySummary"
        } else {
            memorySummary
        }

        val updatedUnresolved = when {
            !unresolvedRequest.isNullOrBlank() -> unresolvedRequest
            existing != null -> existing.unresolvedRequests
            else -> ""
        }

        val isContactMatch = contactName != null
        val updated = if (existing != null) {
            existing.copy(
                contactName = contactName ?: existing.contactName,
                claimedName = claimedName ?: existing.claimedName,
                isVerified = if (isContactMatch) true else existing.isVerified,
                verificationMethod = if (isContactMatch) "Device Contacts Match" else existing.verificationMethod,
                trustScore = if (isContactMatch) 0.85f else existing.trustScore,
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
                claimedName = claimedName,
                isVerified = isContactMatch, // Only device contacts are automatically verified
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


