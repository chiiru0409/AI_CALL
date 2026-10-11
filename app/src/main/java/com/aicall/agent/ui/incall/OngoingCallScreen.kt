package com.aicall.agent.ui.incall

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicall.agent.ai.conversation.ConversationState
import com.aicall.agent.service.telecom.CurrentCallInfo
import com.aicall.agent.ui.components.ContactAvatar
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
    val haptic = LocalHapticFeedback.current

    var elapsedSeconds by remember { mutableLongStateOf(0) }
    var showKeypad by remember { mutableStateOf(false) }

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
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP SECTION: Timer Pill & Caller Details (Google Phone layout)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp)
            ) {
                // Timer Pill with red recording/live dot
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DarkElevatedSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (callInfo.isAiHandling) AccentPurple else DangerRed)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = timeFormatted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = DarkTextPrimary
                        )
                    }
                }

                // Caller Name
                Text(
                    text = callInfo.callerName ?: callInfo.phoneNumber.ifEmpty { "Active Call" },
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkTextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Phone Number or Subtitle
                Text(
                    text = if (callInfo.callerName != null) callInfo.phoneNumber else "Mobile",
                    fontSize = 15.sp,
                    color = DarkTextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Call Mode Pill (AI vs Direct)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (callInfo.isAiHandling) AccentPurple.copy(alpha = 0.2f) else DarkSurface,
                    border = if (callInfo.isAiHandling) {
                        androidx.compose.foundation.BorderStroke(1.dp, AccentPurple.copy(alpha = 0.5f))
                    } else null,
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (callInfo.isAiHandling) Icons.Default.Psychology else Icons.Default.Phone,
                            contentDescription = null,
                            tint = if (callInfo.isAiHandling) AccentPurple else TealAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (callInfo.isAiHandling) "AI Agent Handling Call" else "Direct Cellular Call",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (callInfo.isAiHandling) AccentPurple else DarkTextSecondary
                        )
                    }
                }
            }

            // MIDDLE SECTION: Avatar or AI Conversation Stream
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (callInfo.isAiHandling) {
                    // Elevated Card for AI Live Transcription
                    Card(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.RecordVoiceOver,
                                        contentDescription = null,
                                        tint = AccentPurple,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "AI Live Conversation",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentPurple
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = DarkElevatedSurface
                                ) {
                                    Text(
                                        text = "Screening",
                                        fontSize = 11.sp,
                                        color = DarkTextSecondary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (convState.turns.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Agent active. Listening for caller's query...",
                                        color = DarkTextSecondary,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(convState.turns) { (speaker, text) ->
                                        val isAi = speaker == "AI"
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = if (isAi) Alignment.Start else Alignment.End
                                        ) {
                                            Text(
                                                text = if (isAi) "AI Assistant" else (callInfo.callerName ?: "Caller"),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isAi) AccentPurple else TealAccent,
                                                modifier = Modifier.padding(bottom = 2.dp, start = 4.dp, end = 4.dp)
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = if (isAi) AccentPurple.copy(alpha = 0.15f) else DarkElevatedSurface,
                                                border = if (isAi) androidx.compose.foundation.BorderStroke(1.dp, AccentPurple.copy(alpha = 0.3f)) else null
                                            ) {
                                                Text(
                                                    text = text,
                                                    fontSize = 13.sp,
                                                    color = DarkTextPrimary,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Standard Google Phone Large Avatar
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        ContactAvatar(
                            name = callInfo.callerName,
                            phoneNumber = callInfo.phoneNumber,
                            size = 120.dp
                        )
                    }
                }
            }

            // BOTTOM SECTION: Controls & End Call (Google Phone style)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Google Phone 2x3 Control Grid in Elevated Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Row 1: Keypad, Mute, Speaker
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            GooglePhoneSquircleButton(
                                icon = Icons.Default.Dialpad,
                                label = "Keypad",
                                isActive = showKeypad,
                                activeColor = DarkTextPrimary,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showKeypad = !showKeypad
                                }
                            )

                            GooglePhoneSquircleButton(
                                icon = if (callInfo.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                label = if (callInfo.isMuted) "Unmute" else "Mute",
                                isActive = callInfo.isMuted,
                                activeColor = DangerRed,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleMute()
                                }
                            )

                            GooglePhoneSquircleButton(
                                icon = if (callInfo.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                                label = "Speaker",
                                isActive = callInfo.isSpeakerOn,
                                activeColor = TealAccent,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleSpeaker()
                                }
                            )
                        }

                        // Row 2: AI Handoff / Takeover, Hold/Note, More
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            if (callInfo.isAiHandling) {
                                GooglePhoneSquircleButton(
                                    icon = Icons.Default.Person,
                                    label = "Take Over",
                                    isActive = true,
                                    activeColor = CallAnswerGreen,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onTakeOver()
                                    }
                                )
                            } else {
                                GooglePhoneSquircleButton(
                                    icon = Icons.Default.SmartToy,
                                    label = "AI Agent",
                                    isActive = false,
                                    activeColor = AccentPurple,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onSendToAi()
                                    }
                                )
                            }

                            GooglePhoneSquircleButton(
                                icon = Icons.Default.Pause,
                                label = "Hold",
                                isActive = false,
                                activeColor = DarkTextPrimary,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    // Hold action placeholder
                                }
                            )

                            GooglePhoneSquircleButton(
                                icon = Icons.Default.MoreVert,
                                label = "More",
                                isActive = false,
                                activeColor = DarkTextPrimary,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            )
                        }
                    }
                }

                // Google Phone Wide Pill Hangup Button (Matching Reference Image 1)
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onHangUp()
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(64.dp),
                    shape = RoundedCornerShape(32.dp),
                    color = DangerRed,
                    shadowElevation = 6.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "End Call",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }

        // Animated In-Call DTMF Keypad Overlay
        AnimatedVisibility(
            visible = showKeypad,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            InCallDtmfKeypad(
                onDigit = { digit ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDtmfTone(digit)
                },
                onClose = { showKeypad = false }
            )
        }
    }
}

/**
 * Google Phone Squircle In-Call Control Button (matches Reference Image 1 & 5).
 */
@Composable
fun GooglePhoneSquircleButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(76.dp)
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(18.dp),
            color = if (isActive) activeColor.copy(alpha = 0.22f) else DarkElevatedSurface,
            border = if (isActive) androidx.compose.foundation.BorderStroke(1.5.dp, activeColor) else null
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) activeColor else DarkTextPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (isActive) activeColor else DarkTextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Clean DTMF dialpad overlay for sending in-call touch tones.
 */
@Composable
fun InCallDtmfKeypad(
    onDigit: (Char) -> Unit,
    onClose: () -> Unit
) {
    val keypadRows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf('*', '0', '#')
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
        color = DarkSurface,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Touch-tone Keypad",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextPrimary
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Keypad",
                        tint = DarkTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            keypadRows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { char ->
                        Surface(
                            onClick = { onDigit(char) },
                            modifier = Modifier.size(60.dp),
                            shape = CircleShape,
                            color = DarkElevatedSurface
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = char.toString(),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = DarkTextPrimary
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
