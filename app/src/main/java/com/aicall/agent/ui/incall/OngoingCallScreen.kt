package com.aicall.agent.ui.incall

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicall.agent.ai.conversation.ConversationState
import com.aicall.agent.service.telecom.CurrentCallInfo
import com.aicall.agent.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/**
 * Secure Dialer-adapted OngoingCallScreen with monospaced tabular duration timer,
 * squircle InCallControlGrid (Keypad, Mute, Speaker, Hold, AI Takeover, Transcript),
 * sliding DTMF keypad, and centered hero Hang Up button.
 */
@Composable
fun OngoingCallScreen(
    callInfoFlow: StateFlow<CurrentCallInfo>,
    conversationStateFlow: StateFlow<ConversationState>,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onTakeOver: () -> Unit,
    onSendToAi: () -> Unit,
    onHangUp: () -> Unit,
    onDtmfTone: (Char) -> Unit
) {
    val callInfo by callInfoFlow.collectAsState()
    val convState by conversationStateFlow.collectAsState()
    val haptic = LocalHapticFeedback.current

    var elapsedSeconds by remember { mutableLongStateOf(0) }
    var showKeypad by remember { mutableStateOf(false) }
    var showTranscript by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            elapsedSeconds++
        }
    }

    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    val timeFormatted = String.format("%02d:%02d", minutes, seconds)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP SECTION: Header with tabular duration timer and identity
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                InCallHeader(
                    statusText = if (callInfo.isAiHandling) "AI Agent Screening Call" else "Ongoing Cellular Call",
                    callerName = callInfo.callerName,
                    callerNumber = callInfo.phoneNumber,
                    durationText = timeFormatted,
                    isVerified = callInfo.callerName != null
                )

                Spacer(modifier = Modifier.height(16.dp))

                // CENTER CONTENT: Avatar OR Live Transcript
                if (showTranscript) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 240.dp),
                        shape = AppShapes.Card,
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        border = BorderStroke(1.dp, SurfaceCardBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.SmartToy, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("LIVE AI TRANSCRIPT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AccentPurple)
                                }
                                Text("${convState.turns.size} turns", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (convState.turns.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(100.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Waiting for caller dialogue...", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(convState.turns) { turn ->
                                        val (speaker, text) = turn
                                        val isUser = speaker.equals("caller", ignoreCase = true)
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(AppShapes.Small)
                                                .background(if (isUser) SurfaceCardElevated else AccentPurple.copy(alpha = 0.15f))
                                                .padding(8.dp)
                                        ) {
                                            Text(
                                                text = if (isUser) (callInfo.callerName ?: "Caller") else "AI Agent",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isUser) AccentTeal else AccentPurple
                                            )
                                            Text(
                                                text = text,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    InCallAvatarDisplay(
                        callerName = callInfo.callerName,
                        callerNumber = callInfo.phoneNumber,
                        size = 100.dp
                    )
                }
            }

            var isOnHold by remember { mutableStateOf(false) }

            // MIDDLE SECTION: InCallControlGrid (Squircle control buttons)
            InCallControlGrid(
                isKeypadOpen = showKeypad,
                onToggleKeypad = { showKeypad = !showKeypad },
                isMuted = callInfo.isMuted,
                onToggleMute = onToggleMute,
                isSpeakerOn = callInfo.isSpeakerOn,
                onToggleSpeaker = onToggleSpeaker,
                isOnHold = isOnHold,
                onToggleHold = { isOnHold = !isOnHold },
                isAiHandling = callInfo.isAiHandling,
                onToggleAiHandling = {
                    if (callInfo.isAiHandling) onTakeOver() else onSendToAi()
                },
                isTranscriptVisible = showTranscript,
                onToggleTranscript = { showTranscript = !showTranscript }
            )

            // BOTTOM SECTION: Centered Hero Red Hang Up Button
            InCallBottomBar(
                isIncoming = false,
                onAnswer = {},
                onHangUp = onHangUp
            )
        }

        // Sliding In-Call DTMF Keypad
        AnimatedVisibility(
            visible = showKeypad,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = AppShapes.BottomSheet,
                color = SurfaceDark,
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
                border = BorderStroke(1.dp, SurfaceCardBorder.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Keypad (DTMF Tones)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        IconButton(onClick = { showKeypad = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val dtmfKeys = listOf(
                        listOf('1', '2', '3'),
                        listOf('4', '5', '6'),
                        listOf('7', '8', '9'),
                        listOf('*', '0', '#')
                    )

                    for (row in dtmfKeys) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            for (keyChar in row) {
                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onDtmfTone(keyChar)
                                    },
                                    shape = AppShapes.Keypad,
                                    color = SurfaceCard,
                                    contentColor = TextPrimary,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = keyChar.toString(),
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
