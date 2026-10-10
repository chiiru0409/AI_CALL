package com.aicall.agent.service.telecom

import android.content.Context
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import com.aicall.agent.data.model.CallDirection
import com.aicall.agent.data.model.CallRecord
import com.aicall.agent.data.model.HandlingStatus
import com.aicall.agent.data.repository.CallRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    private val _callStateFlow = MutableStateFlow(CurrentCallInfo())
    val callStateFlow: StateFlow<CurrentCallInfo> = _callStateFlow.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)

    fun initialize(context: Context, repository: CallRepository) {
        appContext = context.applicationContext
        callRepository = repository
    }

    private val telecomCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
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
        activeTelecomCall = call
        call.registerCallback(telecomCallback)
        updateFromTelecomCall(call)
    }

    fun onCallRemoved(call: Call) {
        if (activeTelecomCall == call) {
            val lastState = _callStateFlow.value
            if (lastState.sessionId.isNotBlank() && callRepository != null) {
                scope.launch {
                    val record = CallRecord(
                        sessionId = lastState.sessionId,
                        callerNumber = lastState.phoneNumber,
                        callerName = lastState.callerName,
                        direction = if (lastState.callState == AppCallState.DIALING_OUTGOING) CallDirection.OUTGOING else CallDirection.INCOMING,
                        durationSeconds = lastState.durationSeconds,
                        handlingStatus = if (lastState.isAiHandling) HandlingStatus.AI_HANDLED else HandlingStatus.USER_ANSWERED,
                        outcomeSummary = "Call ended normally"
                    )
                    callRepository?.saveCallRecord(record)
                }
            }

            activeTelecomCall?.unregisterCallback(telecomCallback)
            activeTelecomCall = null
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

        _callStateFlow.value = _callStateFlow.value.copy(
            sessionId = sessionId,
            phoneNumber = handle,
            callerName = displayName,
            callState = appState
        )
    }

    fun answerCallDirectly() {
        activeTelecomCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
        _callStateFlow.value = _callStateFlow.value.copy(
            callState = AppCallState.ACTIVE_HUMAN,
            isAiHandling = false
        )
    }

    fun sendToAiAgent() {
        activeTelecomCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
        _callStateFlow.value = _callStateFlow.value.copy(
            callState = AppCallState.ACTIVE_AI_AGENT,
            isAiHandling = true
        )
    }

    fun takeOverFromAi() {
        _callStateFlow.value = _callStateFlow.value.copy(
            callState = AppCallState.ACTIVE_HUMAN,
            isAiHandling = false
        )
    }

    fun declineCall() {
        activeTelecomCall?.reject(false, null)
    }

    fun hangUpCall() {
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
