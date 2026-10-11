package com.aicall.agent.ui.incall

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicall.agent.ui.theme.AppShapes
import com.aicall.agent.ui.theme.AvatarColors
import com.aicall.agent.ui.theme.SurfaceCardElevated
import com.aicall.agent.ui.theme.TextPrimary
import kotlin.math.abs

/**
 * Secure Dialer InCallAvatarDisplay with 120.dp squircle avatar,
 * high-contrast initials, and deterministic background colors.
 */
@Composable
fun InCallAvatarDisplay(
    callerName: String?,
    callerNumber: String,
    modifier: Modifier = Modifier,
    size: Dp = 112.dp
) {
    val identifier = callerName?.trim()?.ifEmpty { null } ?: callerNumber.trim().ifEmpty { "Call" }
    val backgroundColor = remember(identifier) {
        val hash = abs(identifier.hashCode())
        AvatarColors[hash % AvatarColors.size]
    }

    val initials = remember(callerName, callerNumber) {
        if (!callerName.isNullOrBlank()) {
            val words = callerName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.size >= 2) {
                val first = words[0].firstOrNull { it.isLetter() }
                val second = words[1].firstOrNull { it.isLetter() }
                if (first != null && second != null) "$first$second".uppercase()
                else words[0].take(1).uppercase()
            } else {
                words.firstOrNull()?.take(1)?.uppercase() ?: ""
            }
        } else {
            ""
        }
    }

    Surface(
        modifier = modifier.size(size),
        shape = AppShapes.Avatar,
        color = backgroundColor,
        shadowElevation = 8.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (initials.isNotEmpty()) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 42.sp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = callerName ?: callerNumber,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.55f)
                )
            }
        }
    }
}
