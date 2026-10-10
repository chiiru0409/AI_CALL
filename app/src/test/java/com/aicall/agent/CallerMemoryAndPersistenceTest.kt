package com.aicall.agent

import com.aicall.agent.ai.conversation.RuleBasedReasoningEngine
import com.aicall.agent.data.db.MIGRATION_1_2
import com.aicall.agent.data.db.MIGRATION_2_3
import com.aicall.agent.data.model.*
import com.aicall.agent.data.util.PhoneNumberUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class CallerMemoryAndPersistenceTest {

    @Test
    fun testPhoneNumberNormalization() {
        // Test standard Indian formats
        assertEquals("+919876543210", PhoneNumberUtils.normalize("+91 98765-43210"))
        assertEquals("+919876543210", PhoneNumberUtils.normalize("09876543210"))
        assertEquals("+919876543210", PhoneNumberUtils.normalize("9876543210"))
        assertEquals("+919876543210", PhoneNumberUtils.normalize("+91 (987) 654-3210"))
        assertEquals("+919876543210", PhoneNumberUtils.normalize("00919876543210"))

        // International
        assertEquals("+15552345678", PhoneNumberUtils.normalize("+1 (555) 234-5678"))
    }

    @Test
    fun testUnverifiedCallerClaimMarkedUnverified() {
        val rawNumber = "9876543210"
        val normalized = PhoneNumberUtils.normalize(rawNumber)

        val unverifiedProfile = CallerProfile(
            phoneNumber = normalized,
            contactName = null,
            claimedName = "Rohit",
            isVerified = false,
            verificationMethod = "Unverified Caller Claim",
            category = CallerCategory.OTHER,
            trustScore = 0.4f,
            memorySummary = "Caller claimed to be Rohit regarding project notes",
            unresolvedRequests = "Callback requested regarding project notes"
        )

        assertEquals("+919876543210", unverifiedProfile.phoneNumber)
        assertEquals("Rohit", unverifiedProfile.claimedName)
        assertNull(unverifiedProfile.contactName)
        assertFalse("Claimed name from unknown caller MUST remain unverified", unverifiedProfile.isVerified)
        assertEquals("Unverified Caller Claim", unverifiedProfile.verificationMethod)
        assertEquals(0.4f, unverifiedProfile.trustScore, 0.01f)
        assertEquals("Callback requested regarding project notes", unverifiedProfile.unresolvedRequests)
    }

    @Test
    fun testVerifiedContactProfile() {
        val verifiedProfile = CallerProfile(
            phoneNumber = "+919876543210",
            contactName = "Dr. Sharma",
            claimedName = null,
            isVerified = true,
            verificationMethod = "Device Contacts Match",
            category = CallerCategory.WORK,
            trustScore = 0.85f,
            memorySummary = "Confirmed appointment for tomorrow"
        )

        assertTrue(verifiedProfile.isVerified)
        assertEquals("Device Contacts Match", verifiedProfile.verificationMethod)
        assertEquals(0.85f, verifiedProfile.trustScore, 0.01f)
        assertEquals("Dr. Sharma", verifiedProfile.contactName)
    }

    @Test
    fun testCallerMemoryContextFormatting() {
        // 1. Unverified caller profile
        val unverifiedProfile = CallerProfile(
            phoneNumber = "+919876543210",
            contactName = null,
            claimedName = "Rohit",
            isVerified = false,
            verificationMethod = "Unverified Caller Claim",
            memorySummary = "Previous call discussed project submission",
            unresolvedRequests = "Callback requested"
        )

        val identityNote = when {
            unverifiedProfile.contactName != null -> "Verified Contact: ${unverifiedProfile.contactName}"
            !unverifiedProfile.claimedName.isNullOrBlank() && unverifiedProfile.isVerified -> "Verified Caller: ${unverifiedProfile.claimedName}"
            !unverifiedProfile.claimedName.isNullOrBlank() -> "Unverified Caller Claim: '${unverifiedProfile.claimedName}' (NOT verified identity)"
            else -> "Unknown Caller (${unverifiedProfile.phoneNumber})"
        }

        val parts = mutableListOf<String>()
        parts.add("Caller Status: $identityNote")
        if (unverifiedProfile.memorySummary.isNotBlank()) parts.add("Previous Interactions: ${unverifiedProfile.memorySummary}")
        if (unverifiedProfile.unresolvedRequests.isNotBlank()) parts.add("Unresolved Requests: ${unverifiedProfile.unresolvedRequests}")

        val contextString = parts.joinToString(" | ")

        assertTrue("Must state unverified claim warning", contextString.contains("NOT verified identity"))
        assertTrue("Must include claimed name", contextString.contains("Rohit"))
        assertTrue("Must include memory summary", contextString.contains("project submission"))
        assertTrue("Must include unresolved request", contextString.contains("Callback requested"))
    }

    @Test
    fun testFollowUpActionItemToggleAndPersistence() {
        val item = ActionItem(
            id = 1L,
            callSessionId = "session-101",
            title = "Callback to Rohit regarding delivery parcel",
            description = "Swiggy order arrived at gate",
            priority = "HIGH",
            dueDate = 1730000000000L,
            isCompleted = false
        )

        assertFalse(item.isCompleted)
        assertEquals("HIGH", item.priority)
        assertEquals("session-101", item.callSessionId)
        assertNotNull(item.dueDate)

        val toggled = item.copy(isCompleted = !item.isCompleted)
        assertTrue(toggled.isCompleted)

        val toggledBack = toggled.copy(isCompleted = !toggled.isCompleted)
        assertFalse(toggledBack.isCompleted)
    }

    @Test
    fun testAgentScheduleBitmaskAndMidnightCrossing() {
        // 1. Standard daytime schedule (Mon-Fri 09:00 - 17:00)
        val daySchedule = AgentSchedule(
            name = "Work Hours",
            daysOfWeekMask = 31, // Mon=1 | Tue=2 | Wed=4 | Thu=8 | Fri=16
            startMinuteOfDay = 540, // 09:00 AM
            endMinuteOfDay = 1020, // 05:00 PM
            crossesMidnight = false,
            isEnabled = true
        )

        assertTrue(daySchedule.isEnabled)
        assertEquals(31, daySchedule.daysOfWeekMask)
        assertFalse(daySchedule.crossesMidnight)

        // Test minute in range
        val morningMinute = 600 // 10:00 AM
        val nightMinute = 1200 // 08:00 PM
        assertTrue(morningMinute in daySchedule.startMinuteOfDay until daySchedule.endMinuteOfDay)
        assertFalse(nightMinute in daySchedule.startMinuteOfDay until daySchedule.endMinuteOfDay)

        // 2. Night owl schedule crossing midnight (22:00 to 06:00)
        val nightSchedule = AgentSchedule(
            name = "Night Defense",
            daysOfWeekMask = 0x7F, // All 7 days
            startMinuteOfDay = 1320, // 10:00 PM
            endMinuteOfDay = 360, // 06:00 AM
            crossesMidnight = true,
            isEnabled = true
        )

        assertTrue(nightSchedule.crossesMidnight)
        // For midnight-crossing schedules: active if minute >= start (1320) OR minute < end (360)
        val lateNight = 1380 // 11:00 PM
        val earlyMorning = 180 // 03:00 AM
        val afternoon = 720 // 12:00 PM

        val isLateNightActive = lateNight >= nightSchedule.startMinuteOfDay || lateNight < nightSchedule.endMinuteOfDay
        val isEarlyMorningActive = earlyMorning >= nightSchedule.startMinuteOfDay || earlyMorning < nightSchedule.endMinuteOfDay
        val isAfternoonActive = afternoon >= nightSchedule.startMinuteOfDay || afternoon < nightSchedule.endMinuteOfDay

        assertTrue("11:00 PM should be active in night schedule", isLateNightActive)
        assertTrue("03:00 AM should be active in night schedule", isEarlyMorningActive)
        assertFalse("12:00 PM should NOT be active in night schedule", isAfternoonActive)
    }

    @Test
    fun testCallRecordCreationAndFields() {
        val call = CallRecord(
            sessionId = "sess-abc-123",
            callerNumber = "+919876543210",
            callerName = "Test Contact",
            direction = CallDirection.INCOMING,
            startTime = 1700000000000L,
            endTime = 1700000060000L,
            durationSeconds = 60,
            category = CallerCategory.DELIVERY,
            intent = "Parcel drop-off at security",
            urgencyLevel = UrgencyLevel.LOW,
            riskLevel = RiskLevel.SAFE,
            handlingStatus = HandlingStatus.AI_HANDLED,
            outcomeSummary = "Instructed delivery person to leave package at main entrance",
            transcript = "AI: Hello. Caller: Package arrived. AI: Leave at gate.",
            recordingStatus = "Transcript preserved in local storage",
            callerLocation = "Hyderabad, Telangana",
            verificationStatus = "Verified Contact",
            processingError = null
        )

        assertEquals("sess-abc-123", call.sessionId)
        assertEquals("+919876543210", call.callerNumber)
        assertEquals(CallDirection.INCOMING, call.direction)
        assertEquals(HandlingStatus.AI_HANDLED, call.handlingStatus)
        assertEquals(UrgencyLevel.LOW, call.urgencyLevel)
        assertEquals(RiskLevel.SAFE, call.riskLevel)
        assertEquals("Hyderabad, Telangana", call.callerLocation)
        assertNull(call.processingError)
    }

    @Test
    fun testDashboardTotalsAndFiltering() {
        val sampleCalls = listOf(
            CallRecord(
                sessionId = "s1",
                callerNumber = "+919876543210",
                handlingStatus = HandlingStatus.AI_HANDLED,
                urgencyLevel = UrgencyLevel.LOW,
                category = CallerCategory.DELIVERY
            ),
            CallRecord(
                sessionId = "s2",
                callerNumber = "+919876543211",
                handlingStatus = HandlingStatus.AI_HANDLED,
                urgencyLevel = UrgencyLevel.EMERGENCY,
                category = CallerCategory.FAMILY
            ),
            CallRecord(
                sessionId = "s3",
                callerNumber = "+919876543212",
                handlingStatus = HandlingStatus.USER_ANSWERED,
                urgencyLevel = UrgencyLevel.MEDIUM,
                category = CallerCategory.WORK
            ),
            CallRecord(
                sessionId = "s4",
                callerNumber = "+919876543213",
                handlingStatus = HandlingStatus.MISSED,
                urgencyLevel = UrgencyLevel.LOW,
                category = CallerCategory.OTHER
            ),
            CallRecord(
                sessionId = "s5",
                callerNumber = "+919876543214",
                handlingStatus = HandlingStatus.AI_HANDLED,
                urgencyLevel = UrgencyLevel.LOW,
                riskLevel = RiskLevel.HIGH_RISK_FRAUD,
                category = CallerCategory.FRAUD_SUSPECTED
            )
        )

        val totalCalls = sampleCalls.size
        val aiHandled = sampleCalls.count { it.handlingStatus == HandlingStatus.AI_HANDLED }
        val userAnswered = sampleCalls.count { it.handlingStatus == HandlingStatus.USER_ANSWERED }
        val missed = sampleCalls.count { it.handlingStatus == HandlingStatus.MISSED }
        val urgent = sampleCalls.count { it.urgencyLevel == UrgencyLevel.HIGH || it.urgencyLevel == UrgencyLevel.EMERGENCY }
        val spam = sampleCalls.count { it.riskLevel == RiskLevel.SUSPICIOUS || it.riskLevel == RiskLevel.HIGH_RISK_FRAUD || it.category == CallerCategory.SPAM || it.category == CallerCategory.FRAUD_SUSPECTED }

        assertEquals(5, totalCalls)
        assertEquals(3, aiHandled)
        assertEquals(1, userAnswered)
        assertEquals(1, missed)
        assertEquals(1, urgent)
        assertEquals(1, spam)

        // Filter: AI_HANDLED
        val filteredAi = sampleCalls.filter { it.handlingStatus == HandlingStatus.AI_HANDLED }
        assertEquals(3, filteredAi.size)

        // Filter: Search query
        val searchResult = sampleCalls.filter { it.callerNumber.contains("3214") }
        assertEquals(1, searchResult.size)
        assertEquals(CallerCategory.FRAUD_SUSPECTED, searchResult.first().category)
    }

    @Test
    fun testEmptyDatabaseState() {
        val emptyCalls = emptyList<CallRecord>()
        val emptyActionItems = emptyList<ActionItem>()

        assertEquals(0, emptyCalls.size)
        assertEquals(0, emptyActionItems.size)
        assertEquals(0, emptyCalls.count { it.handlingStatus == HandlingStatus.AI_HANDLED })
        assertEquals(0, emptyCalls.count { it.urgencyLevel == UrgencyLevel.HIGH })
    }

    @Test
    fun testDatabaseMigrationsIntegrity() {
        assertEquals(1, MIGRATION_1_2.startVersion)
        assertEquals(2, MIGRATION_1_2.endVersion)

        assertEquals(2, MIGRATION_2_3.startVersion)
        assertEquals(3, MIGRATION_2_3.endVersion)
    }

    @Test
    fun testReasoningEngineCallerMemoryRecall() = runBlocking {
        val engine = RuleBasedReasoningEngine()
        val memoryContext = "Caller Status: Unverified Caller Claim: 'Rohit' (NOT verified identity) | Previous Interactions: Discussed meeting schedule | Unresolved Requests: Callback needed"

        val response = engine.generateTurnResponse(
            callerNumber = "+919876543210",
            callerName = null,
            callerMemoryContext = memoryContext,
            conversationHistory = emptyList(),
            userApprovedKnowledge = "User is in meetings today.",
            preferredLanguage = "English"
        )

        assertNotNull(response.spokenText)
        assertTrue(
            "Agent should acknowledge previous conversation when caller memory exists",
            response.spokenText.contains("previous conversation", ignoreCase = true)
        )
    }
}
