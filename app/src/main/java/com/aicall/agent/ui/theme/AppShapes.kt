package com.aicall.agent.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

enum class ShapeStyle(val key: String) {
    SQUIRCLE("squircle"),
    ROUNDED("rounded"),
    CIRCULAR("circular"),
    SQUARE("square");

    companion object {
        fun fromKey(key: String): ShapeStyle {
            return entries.find { it.key.equals(key, ignoreCase = true) } ?: SQUIRCLE
        }
    }
}

/**
 * Single source of truth for UI geometry adapted from Secure Dialer.
 * Avatar, Keypad buttons, Chips, Cards, Dialogs, and Sheets reference these tokens.
 */
data class UnifiedShapeTokens(
    val avatarShape: Shape,
    val buttonShape: Shape,
    val cardShape: Shape,
    val chipShape: Shape,
    val smallShape: Shape
)

fun calculateUnifiedShapes(style: ShapeStyle = ShapeStyle.SQUIRCLE): UnifiedShapeTokens {
    return when (style) {
        ShapeStyle.SQUIRCLE,
        ShapeStyle.CIRCULAR -> UnifiedShapeTokens(
            avatarShape = RoundedCornerShape(percent = 32),
            buttonShape = RoundedCornerShape(percent = 28),
            cardShape = RoundedCornerShape(percent = 20),
            chipShape = RoundedCornerShape(percent = 25),
            smallShape = RoundedCornerShape(percent = 20)
        )
        ShapeStyle.ROUNDED -> UnifiedShapeTokens(
            avatarShape = RoundedCornerShape(percent = 24),
            buttonShape = RoundedCornerShape(percent = 20),
            cardShape = RoundedCornerShape(percent = 16),
            chipShape = RoundedCornerShape(percent = 20),
            smallShape = RoundedCornerShape(percent = 16)
        )
        ShapeStyle.SQUARE -> UnifiedShapeTokens(
            avatarShape = RoundedCornerShape(percent = 8),
            buttonShape = RoundedCornerShape(percent = 8),
            cardShape = RoundedCornerShape(percent = 6),
            chipShape = RoundedCornerShape(percent = 6),
            smallShape = RoundedCornerShape(percent = 6)
        )
    }
}

val LocalUnifiedShapes = compositionLocalOf { calculateUnifiedShapes(ShapeStyle.SQUIRCLE) }

object AppShapes {
    // SQUIRCLE TOKENS (Secure Dialer Design System)
    val Avatar = RoundedCornerShape(percent = 32)
    val Keypad = RoundedCornerShape(percent = 28)
    val Chip = RoundedCornerShape(percent = 25)
    val Card = RoundedCornerShape(18.dp)
    val Dialog = RoundedCornerShape(24.dp)
    val BottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val Small = RoundedCornerShape(percent = 20)

    val current: UnifiedShapeTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalUnifiedShapes.current
}
