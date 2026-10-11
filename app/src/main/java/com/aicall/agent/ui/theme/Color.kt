package com.aicall.agent.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// Google Phone & Secure Dialer Refined Palette
val BackgroundDark = Color(0xFF101216)
val SurfaceDark = Color(0xFF181B20)
val SurfaceCard = Color(0xFF20242D)
val SurfaceCardElevated = Color(0xFF282D38)
val SurfaceCardBorder = Color(0xFF363B48)
val GoogleSearchBarBg = Color(0xFF20242D)
val SearchBarBorder = Color(0xFF323744)

// Unified Dark Theme Aliases
val DarkBackground = BackgroundDark
val DarkSurface = SurfaceDark
val DarkElevatedSurface = SurfaceCardElevated
val DarkTextPrimary = Color(0xFFF4F3F8)
val DarkTextSecondary = Color(0xFFB4B6C3)
val DarkTextMuted = Color(0xFF75788A)

// Accents & AI Identity
val AccentPurple = Color(0xFFB69CFF) // AI Lavender / Primary Branding
val AccentTeal = Color(0xFF80CBC4)   // Secondary Accent
val TealAccent = AccentTeal
val CyanAccent = Color(0xFF80CBC4)   // Clean Teal Accent
val CyanDark = Color(0xFF4DB6AC)
val VioletAgent = Color(0xFFB69CFF)
val VioletAgentDark = Color(0xFF7C4DFF)

// Status & Telephony Action Colors (Secure Dialer Palette)
val VerifiedGreen = Color(0xFF4CAF50)
val VerifiedGreenBg = Color(0xFF1B3B22)
val CallAnswerGreen = Color(0xFF4CAF50)
val UrgentRed = Color(0xFFFF6B6B)
val UrgentRedBg = Color(0xFF4A1E20)
val DangerRed = Color(0xFFFF5252)
val WarningAmber = Color(0xFFFFB74D)
val WarningAmberBg = Color(0xFF42280E)
val SpamRose = Color(0xFFFF5252)

// Secure Dialer Action Colors & Helpers
val CallActionGreen = Color(0xFF2E7D32)
val CallHeroGreen = Color(0xFF4CAF50)
val CallActionRed = Color(0xFFE53935)
val CallActionAI = Color(0xFFB69CFF)
val DialpadKeyBg = Color(0xFF20242D)
val DialpadKeyBgPressed = Color(0xFF323744)
val DialpadKeyText = Color(0xFFF4F3F8)
val DialpadSubText = Color(0xFF9093A2)

fun getCallGreenColor(): Color = Color(0xFF2E7D32)
fun getOnCallGreenColor(): Color = Color(0xFFFFFFFF)
fun getDeclineRedColor(): Color = Color(0xFFC62828)
fun getOnDeclineRedColor(): Color = Color(0xFFFFFFFF)

// Typography & Text
val TextPrimary = Color(0xFFF4F3F8)
val TextSecondary = Color(0xFFB4B6C3)
val TextMuted = Color(0xFF75788A)

// General Color Tokens
object ColorTokens {
    val White = Color(0xFFFFFFFF)
    val Transparent = Color(0x00000000)
    val PureBlack = Color(0xFF000000)
}

// Composition Locals for theme flexibility
val LocalAmoledMode = compositionLocalOf { false }
val LocalM3Expressive = compositionLocalOf { true }

// Material Contact Avatar Pastel Palette
val AvatarColors = listOf(
    Color(0xFFEF5350), // Red
    Color(0xFFAB47BC), // Purple
    Color(0xFF5C6BC0), // Indigo
    Color(0xFF26A69A), // Teal
    Color(0xFFFFA726), // Orange
    Color(0xFF42A5F5), // Blue
    Color(0xFF66BB6A), // Green
    Color(0xFFEC407A), // Pink
    Color(0xFF29B6F6), // Light Blue
    Color(0xFFFF7043)  // Deep Orange
)

// High-contrast Avatar Pairs (WCAG AAA compliant)
val AvatarOrange = Color(0xFFFFDBCB)
val AvatarOrangeText = Color(0xFF311300)
val AvatarBlue = Color(0xFFD1E4FF)
val AvatarBlueText = Color(0xFF001D36)
val AvatarGreen = Color(0xFFD9E7CB)
val AvatarGreenText = Color(0xFF141E0D)
