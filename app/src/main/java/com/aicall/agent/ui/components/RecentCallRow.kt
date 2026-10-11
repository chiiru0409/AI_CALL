package com.aicall.agent.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicall.agent.data.model.CallDirection
import com.aicall.agent.data.model.CallRecord
import com.aicall.agent.data.model.HandlingStatus
import com.aicall.agent.data.model.RiskLevel
import com.aicall.agent.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Secure Dialer-adapted RecentCallRow with Squircle geometry, expandable details,
 * AI handling status badges, and direct call/message triggers.
 */
@Composable
fun RecentCallRow(
    call: CallRecord,
    onCallClick: () -> Unit,
    onDetailsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var isExpanded by remember { mutableStateOf(false) }

    val isMissed = call.handlingStatus == HandlingStatus.MISSED
    val isAiHandled = call.handlingStatus == HandlingStatus.AI_HANDLED
    val isSpam = call.category.name == "SPAM" || call.category.name == "FRAUD_SUSPECTED"

    val timeLabel = remember(call.startTime) {
        formatCallTime(call.startTime)
    }

    val containerColor = if (isExpanded) SurfaceCardElevated else SurfaceCard

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                isExpanded = !isExpanded
            },
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, SurfaceCardBorder.copy(alpha = if (isExpanded) 0.8f else 0.4f)),
        shape = AppShapes.Card
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // SQUIRCLE AVATAR (percent = 32)
                ContactAvatar(
                    name = call.callerName,
                    phoneNumber = call.callerNumber,
                    size = 44.dp,
                    shape = AppShapes.Avatar
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Caller identity & Direction icon
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = call.callerName ?: call.callerNumber,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isMissed) UrgentRed else TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (isAiHandled) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = AppShapes.Small,
                                color = AccentPurple.copy(alpha = 0.18f),
                                border = BorderStroke(0.8.dp, AccentPurple.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SmartToy,
                                        contentDescription = null,
                                        tint = AccentPurple,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "AI Handled",
                                        color = AccentPurple,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (isSpam) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = AppShapes.Small,
                                color = SpamRose.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "SPAM",
                                    color = SpamRose,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (arrowIcon, arrowTint) = when {
                            isMissed -> Pair(Icons.AutoMirrored.Filled.CallMissed, UrgentRed)
                            isAiHandled -> Pair(Icons.Default.SmartToy, AccentPurple)
                            call.direction == CallDirection.OUTGOING -> Pair(Icons.AutoMirrored.Filled.CallMade, AccentTeal)
                            else -> Pair(Icons.AutoMirrored.Filled.CallReceived, VerifiedGreen)
                        }

                        Icon(
                            imageVector = arrowIcon,
                            contentDescription = null,
                            tint = arrowTint,
                            modifier = Modifier.size(14.dp)
                        )

                        Spacer(modifier = Modifier.width(5.dp))

                        Text(
                            text = if (call.callerName != null) "${call.callerNumber} • $timeLabel" else timeLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Call Action Button
                val callInteractionSource = remember { MutableInteractionSource() }
                val isCallPressed by callInteractionSource.collectIsPressedAsState()
                val callScale by animateFloatAsState(
                    targetValue = if (isCallPressed) 0.92f else 1.0f,
                    animationSpec = spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "call_btn_scale"
                )

                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCallClick()
                    },
                    shape = AppShapes.Keypad,
                    color = getCallGreenColor().copy(alpha = 0.15f),
                    contentColor = VerifiedGreen,
                    interactionSource = callInteractionSource,
                    modifier = Modifier
                        .size(40.dp)
                        .scale(callScale)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Call ${call.callerName ?: call.callerNumber}",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Expandable Action Drawer (Secure Dialer pattern)
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(color = SurfaceCardBorder.copy(alpha = 0.5f), thickness = 0.8.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Call summary / duration info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Duration: ${formatDuration(call.durationSeconds)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                        if (call.riskLevel != RiskLevel.NOT_ASSESSED) {
                            Text(
                                text = "Risk: ${call.riskLevel.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (call.riskLevel == RiskLevel.HIGH_RISK_FRAUD || call.riskLevel == RiskLevel.SUSPICIOUS) UrgentRed else VerifiedGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick Action Chips Row: Call | SMS | Intelligence Details
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Call Chip
                        Surface(
                            onClick = onCallClick,
                            shape = AppShapes.Chip,
                            color = getCallGreenColor(),
                            contentColor = Color.White,
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Call", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Message Chip
                        Surface(
                            onClick = {
                                val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("smsto:${Uri.encode(call.callerNumber)}")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                try { context.startActivity(smsIntent) } catch (_: Exception) {}
                            },
                            shape = AppShapes.Chip,
                            color = SurfaceCardBorder,
                            contentColor = TextPrimary,
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SMS", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        // Intelligence / Info Chip
                        Surface(
                            onClick = onDetailsClick,
                            shape = AppShapes.Chip,
                            color = AccentPurple.copy(alpha = 0.2f),
                            contentColor = AccentPurple,
                            border = BorderStroke(0.8.dp, AccentPurple.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Details", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatCallTime(timestamp: Long): String {
    if (timestamp <= 0L) return "Recent"
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60 * 1000L -> "Just now"
        diff < 60 * 60 * 1000L -> "${diff / (60 * 1000L)}m ago"
        diff < 24 * 60 * 60 * 1000L -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
        diff < 48 * 60 * 60 * 1000L -> "Yesterday"
        else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun formatDuration(seconds: Long): String {
    if (seconds <= 0L) return "0s"
    val mins = seconds / 60
    val remSecs = seconds % 60
    return if (mins > 0) "${mins}m ${remSecs}s" else "${remSecs}s"
}
