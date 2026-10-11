package com.aicall.agent.ui.incall

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.aicall.agent.AiCallApp
import com.aicall.agent.service.telecom.CallManager
import com.aicall.agent.ui.theme.AIPersonalCallAgentTheme

class IncomingCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show on lock screen & wake screen
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

        setContent {
            AIPersonalCallAgentTheme {
                IncomingCallScreen(
                    callInfoFlow = CallManager.instance.callStateFlow,
                    onAnswerDirectly = {
                        CallManager.instance.answerCallDirectly()
                        val intent = Intent(this@IncomingCallActivity, OngoingCallActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(intent)
                        finish()
                    },
                    onSendToAiAgent = {
                        val state = CallManager.instance.callStateFlow.value
                        val app = application as AiCallApp
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
                            },
                            onEscalateRequested = {
                                CallManager.instance.takeOverFromAi()
                            }
                        )
                        val intent = Intent(this@IncomingCallActivity, OngoingCallActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(intent)
                        finish()
                    },
                    onQuickCallback = {
                        CallManager.instance.declineCall()
                        finish()
                    },
                    onDecline = {
                        CallManager.instance.declineCall()
                        finish()
                    }
                )
            }
        }
    }
}
