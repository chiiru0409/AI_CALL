package com.aicall.agent.ui.incall

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicall.agent.ai.conversation.ConversationState
import com.aicall.agent.service.telecom.AppCallState
import com.aicall.agent.service.telecom.CurrentCallInfo
import com.aicall.agent.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

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

    var elapsedSeconds by remember { mutableLongStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            elapsedSeconds++
        }
    }

    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    val timeFormatted = String.format("%02d:%02d", minutes, seconds)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top section: Caller & Duration
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 28.dp)
        ) {
            Text(
                text = callInfo.callerName ?: callInfo.phoneNumber.ifEmpty { "Active Call" },
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            if (callInfo.callerName != null) {
                Text(
                    text = callInfo.phoneNumber,
                    fontSize = 14.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Text(
                text = timeFormatted,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = CyanAccent,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Handling Mode Indicator
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (callInfo.isAiHandling) VioletAgentDark else SurfaceCardElevated,
                modifier = Modifier.padding(top = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (callInfo.isAiHandling) Icons.Default.Psychology else Icons.Default.Person,
                        contentDescription = null,
                        tint = if (callInfo.isAiHandling) CyanAccent else VerifiedGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (callInfo.isAiHandling) "AI Agent Handling Call" else "Direct Cellular Call",
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                }
            }
        }

        // Middle section: Live Transcript stream when AI Agent handles the call
        if (callInfo.isAiHandling) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 12.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Live Conversation Stream",
                        style = MaterialTheme.typography.titleMedium,
                        color = CyanAccent
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (convState.turns.isEmpty()) {
                        Text(
                            text = "AI Agent listening and preparing initial greeting...",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(convState.turns) { (speaker, text) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (speaker == "AI") Arrangement.Start else Arrangement.End
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (speaker == "AI") VioletAgentDark else SurfaceCardElevated)
                                            .padding(10.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = speaker,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (speaker == "AI") CyanAccent else TextSecondary
                                            )
                                            Text(
                                                text = text,
                                                fontSize = 13.sp,
                                                color = TextPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        // Bottom section: In-Call Controls
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Handoff Button (Take over from AI / Hand off to AI)
            if (callInfo.isAiHandling) {
                Button(
                    onClick = onTakeOver,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VerifiedGreen)
                ) {
                    Icon(imageVector = Icons.Default.PhoneInTalk, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Take Over Call Directly", fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = onSendToAi,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VioletAgent)
                ) {
                    Icon(imageVector = Icons.Default.SmartToy, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Hand Off to AI Agent", fontWeight = FontWeight.Bold)
                }
            }

            // Audio & Control Grid (Mute, Speaker, Keypad)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                InCallControlButton(
                    icon = if (callInfo.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    label = if (callInfo.isMuted) "Unmute" else "Mute",
                    isActive = callInfo.isMuted,
                    onClick = onToggleMute
                )

                InCallControlButton(
                    icon = if (callInfo.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                    label = "Speaker",
                    isActive = callInfo.isSpeakerOn,
                    onClick = onToggleSpeaker
                )

                InCallControlButton(
                    icon = Icons.Default.Dialpad,
                    label = "Keypad",
                    isActive = false,
                    onClick = { /* Toggle Keypad overlay */ }
                )
            }

            // End Call Action Button
            Box(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(CallActionRed)
                    .clickable { onHangUp() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "End Call",
                    tint = TextPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun InCallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (isActive) CyanAccent else SurfaceCardElevated)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) BackgroundDark else TextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = TextSecondary
        )
    }
}
