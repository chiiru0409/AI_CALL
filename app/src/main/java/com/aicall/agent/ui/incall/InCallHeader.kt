package com.aicall.agent.ui.incall

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicall.agent.ui.theme.*

/**
 * Secure Dialer InCallHeader displaying network/call state,
 * monospaced tabular duration timer, caller identity, and verification status.
 */
@Composable
fun InCallHeader(
    statusText: String,
    callerName: String?,
    callerNumber: String,
    durationText: String? = null,
    isVerified: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status indicator (e.g. "Ongoing Call • SIM 1" or "Incoming Cellular Call")
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Call Timer (if active)
        if (!durationText.isNullOrBlank()) {
            Text(
                text = durationText,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = AccentPurple
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Caller Display Name
        Text(
            text = callerName ?: callerNumber.ifEmpty { "Cellular Call" },
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp),
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Phone Number (if name is present)
        if (!callerName.isNullOrBlank() && callerNumber.isNotBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = callerNumber,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Verification Pill (Secure Dialer & AI_CALL verified caller match)
        Surface(
            shape = AppShapes.Chip,
            color = if (isVerified) VerifiedGreenBg.copy(alpha = 0.45f) else SurfaceCardElevated,
            border = BorderStroke(
                0.8.dp,
                if (isVerified) VerifiedGreen.copy(alpha = 0.5f) else SurfaceCardBorder
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isVerified) Icons.Default.VerifiedUser else Icons.Default.Shield,
                    contentDescription = null,
                    tint = if (isVerified) VerifiedGreen else TextSecondary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isVerified) "Verified Contact Match" else "Cellular Network Call",
                    color = if (isVerified) VerifiedGreen else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
