package com.aicall.agent.ui.incall

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.aicall.agent.AiCallApp
import com.aicall.agent.service.telecom.CallManager
import com.aicall.agent.ui.theme.AIPersonalCallAgentTheme

class OngoingCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val app = application as AiCallApp

        setContent {
            AIPersonalCallAgentTheme {
                OngoingCallScreen(
                    callInfoFlow = CallManager.instance.callStateFlow,
                    conversationStateFlow = app.conversationManager.conversationState,
                    onToggleMute = { CallManager.instance.toggleMute() },
                    onToggleSpeaker = { CallManager.instance.toggleSpeaker() },
                    onTakeOver = {
                        CallManager.instance.takeOverFromAi()
                        app.conversationManager.endSessionAndPersist()
                    },
                    onSendToAi = {
                        val state = CallManager.instance.callStateFlow.value
                        CallManager.instance.sendToAiAgent()
                        app.conversationManager.startAiSession(
                            sessionId = state.sessionId,
                            callerNumber = state.phoneNumber,
                            callerName = state.callerName,
                            knowledge = app.agentConfigRepository.approvedKnowledgeBase.value,
                            language = app.agentConfigRepository.preferredLanguage.value,
                            onHangUpRequested = {
                                CallManager.instance.hangUpCall()
                                app.conversationManager.endSessionAndPersist()
                                finish()
                            },
                            onEscalateRequested = {
                                CallManager.instance.takeOverFromAi()
                            }
                        )
                    },
                    onHangUp = {
                        CallManager.instance.hangUpCall()
                        app.conversationManager.endSessionAndPersist()
                        finish()
                    },
                    onDtmfTone = { digit -> CallManager.instance.sendDtmfTone(digit) }
                )
            }
        }
    }
}
