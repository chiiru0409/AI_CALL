package com.aicall.agent.service.telecom

import android.content.Context
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import com.aicall.agent.data.model.CallDirection
import com.aicall.agent.data.model.CallRecord
import com.aicall.agent.data.model.HandlingStatus
import com.aicall.agent.data.model.RiskLevel
import com.aicall.agent.data.repository.AgentConfigRepository
import com.aicall.agent.data.repository.CallRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppCallState {
    IDLE,
    RINGING_INCOMING,
    DIALING_OUTGOING,
    ACTIVE_HUMAN,
    ACTIVE_AI_AGENT,
    DISCONNECTED
}

data class CurrentCallInfo(
    val sessionId: String = "",
    val phoneNumber: String = "",
    val callerName: String? = null,
    val callState: AppCallState = AppCallState.IDLE,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isAiHandling: Boolean = false,
    val durationSeconds: Long = 0
)

class CallManager private constructor() {
    private var activeTelecomCall: Call? = null
    private var appContext: Context? = null
    private var callRepository: CallRepository? = null
    private var conversationManager: com.aicall.agent.ai.conversation.ConversationManager? = null
    private var agentConfigRepository: AgentConfigRepository? = null

    private var callStartTimeMillis: Long = 0L
    private var callConnectTimeMillis: Long? = null
    private var callEndTimeMillis: Long? = null
    private var isExplicitlyDeclined: Boolean = false
    private var hasEverBeenActive: Boolean = false
    private var wasEverAiHandled: Boolean = false
    private var wasTakenOverByHuman: Boolean = false

    private var autoScreenDelayMillis: Long = 12_000L // 12 seconds (~3-4 rings), before carrier 30s timeout
    private var unattendedCallJob: Job? = null

    private val _callStateFlow = MutableStateFlow(CurrentCallInfo())
    val callStateFlow: StateFlow<CurrentCallInfo> = _callStateFlow.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)

    fun initialize(
        context: Context,
        repository: CallRepository,
        conversationMgr: com.aicall.agent.ai.conversation.ConversationManager? = null,
        agentConfigRepo: AgentConfigRepository? = null
    ) {
        appContext = context.applicationContext
        callRepository = repository
        conversationManager = conversationMgr
        agentConfigRepository = agentConfigRepo
    }

    fun setAutoScreenDelayMillis(delayMs: Long) {
        autoScreenDelayMillis = delayMs
    }

    private val telecomCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (state == Call.STATE_ACTIVE) {
                hasEverBeenActive = true
                if (callConnectTimeMillis == null) {
                    callConnectTimeMillis = System.currentTimeMillis()
                }
            }
            updateFromTelecomCall(call)
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            updateFromTelecomCall(call)
        }
    }

    fun onCallAudioStateChanged(audioState: CallAudioState?) {
        if (audioState != null) {
            _callStateFlow.value = _callStateFlow.value.copy(
                isMuted = audioState.isMuted,
                isSpeakerOn = audioState.route == CallAudioState.ROUTE_SPEAKER
            )
        }
    }

    fun onCallAdded(call: Call) {
        callStartTimeMillis = System.currentTimeMillis()
        callConnectTimeMillis = null
        callEndTimeMillis = null
        isExplicitlyDeclined = false
        hasEverBeenActive = false
        wasEverAiHandled = false
        wasTakenOverByHuman = false

        unattendedCallJob?.cancel()
        unattendedCallJob = null

        activeTelecomCall = call
        call.registerCallback(telecomCallback)
        updateFromTelecomCall(call)

        // Workstream A: Unattended call auto-screening timer
        if (call.state == Call.STATE_RINGING) {
            scheduleUnattendedAutoScreen(call)
        }
    }

    private fun scheduleUnattendedAutoScreen(call: Call) {
        val isAgentActive = agentConfigRepository?.isAgentActiveNow() ?: true
        if (!isAgentActive) return

        unattendedCallJob = scope.launch {
            delay(autoScreenDelayMillis)
            if (activeTelecomCall == call && call.state == Call.STATE_RINGING && !isExplicitlyDeclined) {
                android.util.Log.i(
                    "CallManager",
                    "Unattended incoming call timed out after ${autoScreenDelayMillis}ms. Auto-answering with AI agent before carrier timeout."
                )
                sendToAiAgent()

                val state = _callStateFlow.value
                val knowledge = agentConfigRepository?.approvedKnowledgeBase?.value ?: ""
                val language = agentConfigRepository?.preferredLanguage?.value ?: "English"

                conversationManager?.startAiSession(
                    sessionId = state.sessionId,
                    callerNumber = state.phoneNumber,
                    callerName = state.callerName,
                    knowledge = knowledge,
                    language = language,
                    onHangUpRequested = {
                        hangUpCall()
                        conversationManager?.endSessionAndPersist()
                    },
                    onEscalateRequested = {
                        takeOverFromAi()
                    }
                )
            }
        }
    }

    fun onCallRemoved(call: Call) {
        if (activeTelecomCall == call) {
            unattendedCallJob?.cancel()
            unattendedCallJob = null

            val endTime = System.currentTimeMillis()
            callEndTimeMillis = endTime
            val lastState = _callStateFlow.value
            val wasConnected = hasEverBeenActive || callConnectTimeMillis != null
            val finalDuration = if (callConnectTimeMillis != null) {
                maxOf(0L, (endTime - callConnectTimeMillis!!) / 1000L)
            } else {
                0L
            }

            // Strictly truthful handling criteria:
            // 1. Unconnected: MISSED or REJECTED.
            // 2. Connected + Taken over by user: ESCALATED_TO_USER (never falsely claimed as USER_ANSWERED).
            // 3. Connected + AI Handled: AI_HANDLED only if genuine caller dialogue occurred; otherwise MISSED with technical explanation.
            // 4. Connected + User answered directly: USER_ANSWERED.
            val activeTurns = conversationManager?.conversationState?.value?.turns ?: emptyList()
            val hasCallerDialogue = activeTurns.any { it.first == "Caller" }

            val handlingStatus: HandlingStatus
            val outcomeSummary: String

            if (!wasConnected) {
                if (isExplicitlyDeclined) {
                    handlingStatus = HandlingStatus.REJECTED
                    outcomeSummary = "Call declined by user"
                } else {
                    handlingStatus = HandlingStatus.MISSED
                    outcomeSummary = if (wasEverAiHandled) {
                        "Missed call (AI screening activated during ringing, but caller disconnected before answer completed)"
                    } else {
                        "Missed call (Ringing timed out without answer)"
                    }
                }
            } else {
                if (wasTakenOverByHuman) {
                    handlingStatus = HandlingStatus.ESCALATED_TO_USER
                    outcomeSummary = if (hasCallerDialogue) {
                        "AI screened call (${activeTurns.size} conversational turns), then escalated and taken over by user. Duration: ${finalDuration}s."
                    } else {
                        "AI screening activated on connected call, then taken over by user (Cellular downlink audio not intercepted by OS). Duration: ${finalDuration}s."
                    }
                } else if (wasEverAiHandled || lastState.isAiHandling) {
                    if (hasCallerDialogue) {
                        handlingStatus = HandlingStatus.AI_HANDLED
                        outcomeSummary = "AI successfully handled call (${activeTurns.size} conversational turns)"
                    } else {
                        handlingStatus = HandlingStatus.MISSED
                        outcomeSummary = "AI agent answered call on device, but remote caller speech could not be captured over cellular downlink (OS permission restriction: CAPTURE_AUDIO_OUTPUT). Transcript unavailable."
                    }
                } else {
                    handlingStatus = HandlingStatus.USER_ANSWERED
                    outcomeSummary = "Call answered directly by user"
                }
            }

            val isConversationActive = conversationManager?.conversationState?.value?.isActive == true

            if (isConversationActive && conversationManager != null) {
                conversationManager?.endSessionAndPersist(
                    outcome = handlingStatus,
                    actualDurationSeconds = finalDuration,
                    customSummary = outcomeSummary
                )
            } else if (lastState.sessionId.isNotBlank() && callRepository != null) {
                scope.launch {
                    val record = CallRecord(
                        sessionId = lastState.sessionId,
                        callerNumber = lastState.phoneNumber,
                        callerName = lastState.callerName,
                        direction = if (lastState.callState == AppCallState.DIALING_OUTGOING) CallDirection.OUTGOING else CallDirection.INCOMING,
                        startTime = if (callStartTimeMillis > 0) callStartTimeMillis else System.currentTimeMillis() - (finalDuration * 1000L),
                        endTime = endTime,
                        durationSeconds = finalDuration,
                        handlingStatus = handlingStatus,
                        outcomeSummary = outcomeSummary,
                        transcript = "",
                        riskLevel = RiskLevel.NOT_ASSESSED,
                        recordingStatus = "Recording unavailable on this device (Cellular downlink audio not intercepted by OS)",
                        verificationStatus = if (lastState.callerName != null) "Saved Address Book Contact (Unverified Caller ID)" else "Unsaved Caller Number (Unverified Caller ID)"
                    )
                    callRepository?.saveCallRecord(record)
                }
            }

            activeTelecomCall?.unregisterCallback(telecomCallback)
            activeTelecomCall = null
            callConnectTimeMillis = null
            callEndTimeMillis = null
            hasEverBeenActive = false
            isExplicitlyDeclined = false
            wasEverAiHandled = false
            wasTakenOverByHuman = false
            _callStateFlow.value = CurrentCallInfo(callState = AppCallState.IDLE)
        }
    }

    private fun updateFromTelecomCall(call: Call?) {
        if (call == null) {
            _callStateFlow.value = CurrentCallInfo(callState = AppCallState.IDLE)
            return
        }

        val handle = call.details?.handle?.schemeSpecificPart ?: "Unknown"
        val displayName = call.details?.callerDisplayName
        val sessionId = _callStateFlow.value.sessionId.ifBlank { UUID.randomUUID().toString() }

        val appState = when (call.state) {
            Call.STATE_RINGING -> AppCallState.RINGING_INCOMING
            Call.STATE_DIALING, Call.STATE_CONNECTING -> AppCallState.DIALING_OUTGOING
            Call.STATE_ACTIVE -> if (_callStateFlow.value.isAiHandling) AppCallState.ACTIVE_AI_AGENT else AppCallState.ACTIVE_HUMAN
            Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> AppCallState.DISCONNECTED
            else -> AppCallState.IDLE
        }

        val currentDuration = if (callConnectTimeMillis != null) {
            maxOf(0L, (System.currentTimeMillis() - callConnectTimeMillis!!) / 1000L)
        } else {
            0L
        }

        _callStateFlow.value = _callStateFlow.value.copy(
            sessionId = sessionId,
            phoneNumber = handle,
            callerName = displayName,
            callState = appState,
            durationSeconds = currentDuration
        )
    }

    fun answerCallDirectly() {
        unattendedCallJob?.cancel()
        unattendedCallJob = null
        val call = activeTelecomCall ?: return
        if (call.state == Call.STATE_RINGING) {
            call.answer(VideoProfile.STATE_AUDIO_ONLY)
        }
        _callStateFlow.value = _callStateFlow.value.copy(
            callState = AppCallState.ACTIVE_HUMAN,
            isAiHandling = false
        )
    }

    fun sendToAiAgent() {
        unattendedCallJob?.cancel()
        unattendedCallJob = null
        wasEverAiHandled = true
        val call = activeTelecomCall ?: return
        if (call.state == Call.STATE_RINGING) {
            call.answer(VideoProfile.STATE_AUDIO_ONLY)
        }
        _callStateFlow.value = _callStateFlow.value.copy(
            callState = AppCallState.ACTIVE_AI_AGENT,
            isAiHandling = true
        )
    }

    fun takeOverFromAi() {
        wasTakenOverByHuman = true
        wasEverAiHandled = true
        _callStateFlow.value = _callStateFlow.value.copy(
            callState = AppCallState.ACTIVE_HUMAN,
            isAiHandling = false
        )
    }

    fun declineCall() {
        unattendedCallJob?.cancel()
        unattendedCallJob = null
        isExplicitlyDeclined = true
        val call = activeTelecomCall ?: return
        if (call.state == Call.STATE_RINGING) {
            call.reject(false, null)
        } else {
            call.disconnect()
        }
    }

    fun hangUpCall() {
        unattendedCallJob?.cancel()
        unattendedCallJob = null
        activeTelecomCall?.disconnect()
    }

    fun toggleMute() {
        val newState = !_callStateFlow.value.isMuted
        _callStateFlow.value = _callStateFlow.value.copy(isMuted = newState)
    }

    fun toggleSpeaker() {
        val newState = !_callStateFlow.value.isSpeakerOn
        _callStateFlow.value = _callStateFlow.value.copy(isSpeakerOn = newState)
    }

    fun sendDtmfTone(digit: Char) {
        activeTelecomCall?.playDtmfTone(digit)
        activeTelecomCall?.stopDtmfTone()
    }

    companion object {
        val instance: CallManager by lazy { CallManager() }
    }
}
