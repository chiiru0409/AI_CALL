package com.aicall.agent.ui.incall

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicall.agent.service.telecom.CurrentCallInfo
import com.aicall.agent.ui.theme.*
import kotlinx.coroutines.flow.StateFlow

/**
 * Secure Dialer-adapted incoming call screen with prominent squircle avatar,
 * verified contact badge, call reason preview, and 3 squircle action buttons
 * (Answer Direct, Screen with AI, and Decline).
 */
@Composable
fun IncomingCallScreen(
    callInfoFlow: StateFlow<CurrentCallInfo>,
    onAnswerDirectly: () -> Unit,
    onSendToAiAgent: () -> Unit,
    onQuickCallback: () -> Unit,
    onDecline: () -> Unit
) {
    val callInfo by callInfoFlow.collectAsState()
    val isVerified = callInfo.callerName != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF141720),
                        BackgroundDark,
                        Color(0xFF0C0D11)
                    )
                )
            )
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP SECTION: Status, Caller Name, and Verification
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
            ) {
                InCallHeader(
                    statusText = "Incoming Cellular Call",
                    callerName = callInfo.callerName,
                    callerNumber = callInfo.phoneNumber,
                    isVerified = isVerified
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Centered Squircle Avatar (Secure Dialer style, 112.dp)
                InCallAvatarDisplay(
                    callerName = callInfo.callerName,
                    callerNumber = callInfo.phoneNumber,
                    size = 112.dp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Call Reason Card (AI_CALL Intelligence Preview)
                Card(
                    modifier = Modifier.fillMaxWidth(0.92f),
                    shape = AppShapes.Card,
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    border = BorderStroke(1.dp, SurfaceCardBorder.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = AccentPurple,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "CALL POLICY PREVIEW",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = AccentPurple,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isVerified) {
                                "Known contact caller. AI agent is standing by to screen or take notes if unanswered."
                            } else {
                                "Unsaved cellular number. AI agent will verify purpose, urgency, and block spam."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // BOTTOM SECTION: Squircle Answer, Screen with AI, and Decline controls
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = onQuickCallback,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = "Decline with Quick SMS Response",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }

                InCallBottomBar(
                    isIncoming = true,
                    onAnswer = onAnswerDirectly,
                    onHangUp = onDecline,
                    onScreenWithAi = onSendToAiAgent
                )
            }
        }
    }
}
