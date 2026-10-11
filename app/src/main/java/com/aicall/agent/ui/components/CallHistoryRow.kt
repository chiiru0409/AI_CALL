package com.aicall.agent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicall.agent.data.model.CallDirection
import com.aicall.agent.data.model.CallRecord
import com.aicall.agent.data.model.HandlingStatus
import com.aicall.agent.data.model.UrgencyLevel
import com.aicall.agent.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Google Phone-inspired contact row for call logs and recent calls.
 */
@Composable
fun CallHistoryRow(
    call: CallRecord,
    onClick: () -> Unit,
    onCallClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isMissed = call.handlingStatus == HandlingStatus.MISSED
    val isAiHandled = call.handlingStatus == HandlingStatus.AI_HANDLED
    val isSpam = call.category.name == "SPAM" || call.category.name == "FRAUD_SUSPECTED"

    val timeLabel = remember(call.startTime) {
        formatCallTime(call.startTime)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Contact Avatar
        ContactAvatar(
            name = call.callerName,
            phoneNumber = call.callerNumber,
            size = 46.dp
        )

        Spacer(modifier = Modifier.width(14.dp))

        // Contact info and call metadata
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = call.callerName ?: call.callerNumber,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isMissed) UrgentRed else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (isAiHandled) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AccentPurple.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "AI",
                            color = AccentPurple,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (isSpam) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
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

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Call type arrow
                val (arrowIcon, arrowTint) = when {
                    isMissed -> Pair(Icons.AutoMirrored.Filled.CallMissed, UrgentRed)
                    isAiHandled -> Pair(Icons.Default.SmartToy, AccentPurple)
                    call.direction == CallDirection.OUTGOING -> Pair(Icons.Default.CallMade, AccentTeal)
                    else -> Pair(Icons.Default.CallReceived, VerifiedGreen)
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
                    fontSize = 13.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Quick Call Button
        IconButton(
            onClick = onCallClick,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(SurfaceCardElevated)
        ) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = "Call ${call.callerName ?: call.callerNumber}",
                tint = VerifiedGreen,
                modifier = Modifier.size(20.dp)
            )
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
