package com.aicall.agent

import com.aicall.agent.ai.conversation.AgentResponse
import com.aicall.agent.ai.conversation.ConversationManager
import com.aicall.agent.ai.conversation.LlmReasoningEngine
import com.aicall.agent.ai.conversation.RuleBasedReasoningEngine
import com.aicall.agent.ai.policy.PolicyEnforcer
import com.aicall.agent.ai.policy.RiskAndUrgencyAssessor
import com.aicall.agent.ai.voice.DefaultSpeechRecognizerEngine
import com.aicall.agent.ai.voice.SpeechSynthesizerEngine
import com.aicall.agent.data.db.*
import com.aicall.agent.data.model.*
import com.aicall.agent.data.repository.CallRepository
import com.aicall.agent.data.repository.CallerMemoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CallHandlingLifecycleRegressionTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var riskAssessor: RiskAndUrgencyAssessor
    private lateinit var converters: Converters

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        riskAssessor = RiskAndUrgencyAssessor()
        converters = Converters()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testBlankTranscriptProducesNotAssessedRisk() {
        // When cellular call speech is not intercepted or transcript is blank,
        // risk assessment MUST be NOT_ASSESSED, not SAFE.
        val emptyResultContact = riskAssessor.assessConversation(
            callerNumber = "+919876543210",
            isContact = true,
            transcript = ""
        )
        assertEquals(
            "Blank transcript for contact must NOT produce SAFE verdict",
            RiskLevel.NOT_ASSESSED,
            emptyResultContact.risk
        )
        assertTrue(emptyResultContact.summary.contains("Risk was not assessed"))

        val emptyResultNonContact = riskAssessor.assessConversation(
            callerNumber = "+919876543210",
            isContact = false,
            transcript = "   "
        )
        assertEquals(
            "Blank transcript for unknown number must produce NOT_ASSESSED",
            RiskLevel.NOT_ASSESSED,
            emptyResultNonContact.risk
        )
    }

    @Test
    fun testNonBlankLegitimateTranscriptProducesSafeForContact() {
        val legitimateResult = riskAssessor.assessConversation(
            callerNumber = "+919876543210",
            isContact = true,
            transcript = "Caller: Hi, just checking in on the project notes we discussed earlier."
        )
        assertEquals(RiskLevel.SAFE, legitimateResult.risk)
    }

    @Test
    fun testConvertersRiskLevelSupportAndSafeFallback() {
        assertEquals("SAFE", converters.fromRiskLevel(RiskLevel.SAFE))
        assertEquals(RiskLevel.SAFE, converters.toRiskLevel("SAFE"))

        assertEquals("NOT_ASSESSED", converters.fromRiskLevel(RiskLevel.NOT_ASSESSED))
        assertEquals(RiskLevel.NOT_ASSESSED, converters.toRiskLevel("NOT_ASSESSED"))

        // Invalid or corrupted value must fall back to NOT_ASSESSED, never falsely to SAFE
        assertEquals(
            "Corrupted or invalid risk level string must fall back to NOT_ASSESSED",
            RiskLevel.NOT_ASSESSED,
            converters.toRiskLevel("UNKNOWN_OR_CORRUPTED_STRING")
        )
    }

    @Test
    fun testConversationManagerRejectsAiHandledWhenNoTurnsOccurred() = runTest {
        val fakeCallDao = FakeCallRecordDao()
        val fakeActionDao = FakeActionItemDao()
        val fakeAuditDao = FakeAuditLogDao()
        val fakeProfileDao = FakeCallerProfileDao()

        val callRepo = CallRepository(fakeCallDao, fakeActionDao, fakeAuditDao)
        val memoryRepo = CallerMemoryRepository(fakeProfileDao)
        val speechRec = DefaultSpeechRecognizerEngine()
        val fakeSpeechSyn = FakeSpeechSynthesizer()

        val manager = ConversationManager(
            reasoningEngine = RuleBasedReasoningEngine(),
            policyEnforcer = PolicyEnforcer(),
            riskAssessor = riskAssessor,
            callRepository = callRepo,
            callerMemoryRepository = memoryRepo,
            speechRecognizer = speechRec,
            speechSynthesizer = fakeSpeechSyn
        )

        manager.startAiSession(
            sessionId = "sess-unanswered-1",
            callerNumber = "+919876543210",
            callerName = "Test Contact",
            knowledge = "Available for urgent calls",
            language = "English",
            onHangUpRequested = {},
            onEscalateRequested = {}
        )
        advanceUntilIdle()

        // When call disconnects without any caller conversation, endSessionAndPersist is called.
        // Even if caller requested AI_HANDLED, 0-turn session must be downgraded to MISSED.
        manager.endSessionAndPersist(
            outcome = HandlingStatus.AI_HANDLED,
            actualDurationSeconds = 0L,
            customSummary = "Missed call (Ringing timed out before answer)"
        )
        advanceUntilIdle()

        val saved = fakeCallDao.insertedRecords.firstOrNull()
        assertNotNull("CallRecord must be persisted", saved)
        assertEquals(
            "Unanswered/unintercepted call must NOT be marked AI_HANDLED",
            HandlingStatus.MISSED,
            saved?.handlingStatus
        )
        assertEquals(0L, saved?.durationSeconds)
        assertEquals(RiskLevel.NOT_ASSESSED, saved?.riskLevel)
        assertFalse("Conversation state must be idle", manager.conversationState.value.isActive)
    }

    @Test
    fun testConversationManagerCollectsRecognizedSpeechFlow() = runTest {
        val fakeCallDao = FakeCallRecordDao()
        val fakeActionDao = FakeActionItemDao()
        val fakeAuditDao = FakeAuditLogDao()
        val fakeProfileDao = FakeCallerProfileDao()

        val callRepo = CallRepository(fakeCallDao, fakeActionDao, fakeAuditDao)
        val memoryRepo = CallerMemoryRepository(fakeProfileDao)
        val speechRec = DefaultSpeechRecognizerEngine()
        val fakeSpeechSyn = FakeSpeechSynthesizer()

        val manager = ConversationManager(
            reasoningEngine = RuleBasedReasoningEngine(),
            policyEnforcer = PolicyEnforcer(),
            riskAssessor = riskAssessor,
            callRepository = callRepo,
            callerMemoryRepository = memoryRepo,
            speechRecognizer = speechRec,
            speechSynthesizer = fakeSpeechSyn
        )

        manager.startAiSession(
            sessionId = "sess-speech-flow-1",
            callerNumber = "+919876543210",
            callerName = "Test Contact",
            knowledge = "Exam notes available",
            language = "English",
            onHangUpRequested = {},
            onEscalateRequested = {}
        )
        advanceUntilIdle()

        // Verify recognizer engine is in listening state
        assertTrue(speechRec.isAvailable())

        // Emit speech into recognizedSpeechFlow
        speechRec.emitRecognizedSpeech("Hello, this is a delivery confirmation for your Amazon parcel.")
        advanceUntilIdle()

        val state = manager.conversationState.value
        val callerTurn = state.turns.find { it.first == "Caller" }
        assertNotNull("Caller utterance from recognizedSpeechFlow must be recorded in turns", callerTurn)
        assertEquals("Hello, this is a delivery confirmation for your Amazon parcel.", callerTurn?.second)
    }

    @Test
    fun testCallDurationMath() {
        val connectTime = 1000000L
        val endTimeConnected = 1042000L
        val durationConnected = maxOf(0L, (endTimeConnected - connectTime) / 1000L)
        assertEquals(42L, durationConnected)

        // Unconnected call (never active)
        val unconnectedConnectTime: Long? = null
        val durationUnconnected = if (unconnectedConnectTime != null) {
            maxOf(0L, (endTimeConnected - unconnectedConnectTime) / 1000L)
        } else {
            0L
        }
        assertEquals(0L, durationUnconnected)
    }

    @Test
    fun testTakenOverCallPreservesEscalatedStatusAndNeverOverwrittenAsUserAnswered() = runTest {
        val fakeCallDao = FakeCallRecordDao()
        val fakeActionDao = FakeActionItemDao()
        val fakeAuditDao = FakeAuditLogDao()
        val fakeProfileDao = FakeCallerProfileDao()

        val callRepo = CallRepository(fakeCallDao, fakeActionDao, fakeAuditDao)
        val memoryRepo = CallerMemoryRepository(fakeProfileDao)
        val speechRec = DefaultSpeechRecognizerEngine()
        val fakeSpeechSyn = FakeSpeechSynthesizer()

        val manager = ConversationManager(
            reasoningEngine = RuleBasedReasoningEngine(),
            policyEnforcer = PolicyEnforcer(),
            riskAssessor = riskAssessor,
            callRepository = callRepo,
            callerMemoryRepository = memoryRepo,
            speechRecognizer = speechRec,
            speechSynthesizer = fakeSpeechSyn
        )

        manager.startAiSession(
            sessionId = "sess-escalated-1",
            callerNumber = "+919163043334",
            callerName = "Gangavaram Chiru Deep",
            knowledge = "Urgent calls only",
            language = "English",
            onHangUpRequested = {},
            onEscalateRequested = {}
        )
        advanceUntilIdle()

        // User took over call after 39s connection time
        manager.endSessionAndPersist(
            outcome = HandlingStatus.ESCALATED_TO_USER,
            actualDurationSeconds = 39L,
            customSummary = "AI screened call, then escalated and taken over by user. Duration: 39s."
        )
        advanceUntilIdle()

        val saved = fakeCallDao.insertedRecords.firstOrNull()
        assertNotNull("CallRecord must be persisted", saved)
        assertEquals(
            "Taken over call must NEVER be marked USER_ANSWERED",
            HandlingStatus.ESCALATED_TO_USER,
            saved?.handlingStatus
        )
        assertEquals(39L, saved?.durationSeconds)
        assertTrue(saved?.outcomeSummary?.contains("taken over by user") == true)
        assertEquals(
            "Saved Address Book Contact (Unverified Caller ID)",
            saved?.verificationStatus
        )
        assertEquals(
            "Recording unavailable on this device (Cellular downlink audio not intercepted by OS)",
            saved?.recordingStatus
        )
    }

    @Test
    fun testVoiceStateMachineTransitionsAndBargeIn() {
        var ttsStopped = false
        var transitionCount = 0

        val stateMachine = com.aicall.agent.ai.voice.VoiceStateMachine(
            callback = object : com.aicall.agent.ai.voice.VoiceStateMachine.StateCallback {
                override fun onTransitionToListening() { transitionCount++ }
                override fun onTransitionToThinking(speechSamples: FloatArray) { transitionCount++ }
                override fun onTransitionToSpeaking() { transitionCount++ }
                override fun onTransitionToIdle() { transitionCount++ }
                override fun onTransitionToEscalated() { transitionCount++ }
                override fun stopTtsPlayback() { ttsStopped = true }
            }
        )

        assertEquals(com.aicall.agent.ai.voice.VoiceState.IDLE, stateMachine.getCurrentState())

        // 1. Transition to LISTENING
        stateMachine.startListening()
        assertEquals(com.aicall.agent.ai.voice.VoiceState.LISTENING, stateMachine.getCurrentState())

        // 2. Speech end -> THINKING
        stateMachine.onSpeechEndDetected(FloatArray(1600))
        assertEquals(com.aicall.agent.ai.voice.VoiceState.THINKING, stateMachine.getCurrentState())

        // 3. TTS begins speaking -> SPEAKING
        stateMachine.onTtsPlaybackStarted()
        assertEquals(com.aicall.agent.ai.voice.VoiceState.SPEAKING, stateMachine.getCurrentState())

        // 4. Barge-in interruption after echo bleed window
        Thread.sleep(350)
        stateMachine.onSpeechStartDetected()
        assertTrue("Barge-in speech must trigger stopTtsPlayback()", ttsStopped)
        assertEquals(com.aicall.agent.ai.voice.VoiceState.LISTENING, stateMachine.getCurrentState())

        // 5. User escalates to human
        stateMachine.escalateToHuman()
        assertEquals(com.aicall.agent.ai.voice.VoiceState.ESCALATED, stateMachine.getCurrentState())
    }

    // Lightweight in-memory test doubles
    private class FakeSpeechSynthesizer : SpeechSynthesizerEngine {
        override fun speak(text: String, onComplete: () -> Unit) { onComplete() }
        override fun stop() {}
        override fun shutdown() {}
        override fun isReady(): Boolean = true
    }

    private class FakeCallRecordDao : CallRecordDao {
        val insertedRecords = mutableListOf<CallRecord>()
        override fun getAllRecords(): Flow<List<CallRecord>> = flowOf(insertedRecords)
        override suspend fun getRecordById(sessionId: String): CallRecord? = insertedRecords.find { it.sessionId == sessionId }
        override fun getRecordsForCaller(phoneNumber: String): Flow<List<CallRecord>> = flowOf(insertedRecords.filter { it.callerNumber == phoneNumber })
        override suspend fun getLatestRecordForCaller(phoneNumber: String): CallRecord? = insertedRecords.lastOrNull { it.callerNumber == phoneNumber }
        override fun getAiHandledRecords(): Flow<List<CallRecord>> = flowOf(insertedRecords.filter { it.handlingStatus == HandlingStatus.AI_HANDLED })
        override fun getUrgentRecords(): Flow<List<CallRecord>> = flowOf(insertedRecords.filter { it.urgencyLevel == UrgencyLevel.HIGH || it.urgencyLevel == UrgencyLevel.EMERGENCY })
        override fun getSpamAndFraudRecords(): Flow<List<CallRecord>> = flowOf(insertedRecords.filter { it.riskLevel == RiskLevel.SUSPICIOUS || it.riskLevel == RiskLevel.HIGH_RISK_FRAUD })
        override fun getTotalCallCount(): Flow<Int> = flowOf(insertedRecords.size)
        override fun getAiHandledCount(): Flow<Int> = flowOf(insertedRecords.count { it.handlingStatus == HandlingStatus.AI_HANDLED })
        override fun getUserAnsweredCount(): Flow<Int> = flowOf(insertedRecords.count { it.handlingStatus == HandlingStatus.USER_ANSWERED })
        override fun getMissedCount(): Flow<Int> = flowOf(insertedRecords.count { it.handlingStatus == HandlingStatus.MISSED })
        override fun getUrgentCount(): Flow<Int> = flowOf(insertedRecords.count { it.urgencyLevel == UrgencyLevel.HIGH || it.urgencyLevel == UrgencyLevel.EMERGENCY })
        override fun getSpamCount(): Flow<Int> = flowOf(insertedRecords.count { it.riskLevel == RiskLevel.SUSPICIOUS || it.riskLevel == RiskLevel.HIGH_RISK_FRAUD })
        override suspend fun insertRecord(record: CallRecord) { insertedRecords.add(record) }
        override suspend fun updateRecord(record: CallRecord) {}
        override suspend fun deleteRecord(record: CallRecord) { insertedRecords.remove(record) }
        override suspend fun clearAll() { insertedRecords.clear() }
    }

    private class FakeActionItemDao : ActionItemDao {
        override fun getPendingActionItems(): Flow<List<ActionItem>> = flowOf(emptyList())
        override fun getAllActionItems(): Flow<List<ActionItem>> = flowOf(emptyList())
        override fun getPendingCount(): Flow<Int> = flowOf(0)
        override fun getItemsForCall(sessionId: String): Flow<List<ActionItem>> = flowOf(emptyList())
        override fun getPendingItemsForCall(sessionId: String): Flow<List<ActionItem>> = flowOf(emptyList())
        override suspend fun getActionItemById(id: Long): ActionItem? = null
        override suspend fun insertActionItem(item: ActionItem) {}
        override suspend fun updateActionItem(item: ActionItem) {}
        override suspend fun deleteActionItem(item: ActionItem) {}
        override suspend fun deleteItemsForCall(sessionId: String) {}
    }

    private class FakeAuditLogDao : AuditLogDao {
        override fun getRecentLogs(): Flow<List<AuditLog>> = flowOf(emptyList())
        override suspend fun insertLog(log: AuditLog) {}
    }

    private class FakeCallerProfileDao : CallerProfileDao {
        override fun getAllProfiles(): Flow<List<CallerProfile>> = flowOf(emptyList())
        override suspend fun getProfile(phoneNumber: String): CallerProfile? = null
        override suspend fun insertOrUpdate(profile: CallerProfile) {}
        override suspend fun deleteProfile(profile: CallerProfile) {}
        override suspend fun deleteProfileByNumber(phoneNumber: String) {}
    }
}
