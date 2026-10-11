package com.aicall.agent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicall.agent.ui.theme.AvatarColors
import com.aicall.agent.ui.theme.SurfaceCardElevated
import com.aicall.agent.ui.theme.TextPrimary
import kotlin.math.abs

/**
 * Google Phone-inspired circular contact avatar with deterministic pastel color,
 * initials calculation, and optional bottom-right phone badge overlay.
 */
@Composable
fun ContactAvatar(
    name: String?,
    modifier: Modifier = Modifier,
    phoneNumber: String? = null,
    size: Dp = 48.dp,
    showPhoneBadge: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val identifier = name?.trim()?.ifEmpty { null } ?: phoneNumber?.trim() ?: "?"
    val backgroundColor = remember(identifier) {
        val hash = abs(identifier.hashCode())
        AvatarColors[hash % AvatarColors.size]
    }

    val initials = remember(name, phoneNumber) {
        getInitials(name, phoneNumber)
    }

    val clickableModifier = if (onClick != null) {
        modifier.clickable { onClick() }
    } else {
        modifier
    }

    Box(
        modifier = clickableModifier
            .size(size),
        contentAlignment = Alignment.Center
    ) {
        // Main Avatar Circle
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            if (initials.isNotEmpty()) {
                Text(
                    text = initials,
                    color = Color.White,
                    fontSize = (size.value * 0.42f).sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.5).sp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = name ?: "Contact",
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.55f)
                )
            }
        }

        // Optional bottom-right phone badge overlay (Google Phone Favorites style)
        if (showPhoneBadge) {
            Box(
                modifier = Modifier
                    .size(size * 0.36f)
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    tint = Color(0xFF1E293B),
                    modifier = Modifier.size(size * 0.22f)
                )
            }
        }
    }
}

private fun getInitials(name: String?, phoneNumber: String?): String {
    if (!name.isNullOrBlank()) {
        val trimmed = name.trim()
        val words = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size >= 2) {
            val first = words[0].firstOrNull { it.isLetter() }
            val second = words[1].firstOrNull { it.isLetter() }
            if (first != null && second != null) {
                return "$first$second".uppercase()
            }
        }
        val firstLetter = trimmed.firstOrNull { it.isLetter() }
        if (firstLetter != null) {
            return firstLetter.uppercase()
        }
    }

    // Fallback if only numeric phone number
    if (!phoneNumber.isNullOrBlank()) {
        val digitsOnly = phoneNumber.filter { it.isDigit() }
        if (digitsOnly.isNotEmpty()) {
            return digitsOnly.takeLast(1)
        }
    }
    return ""
}
