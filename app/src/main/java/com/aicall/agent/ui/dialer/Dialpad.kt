package com.aicall.agent.ui.dialer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicall.agent.ui.theme.*

data class DialpadKey(val digit: String, val letters: String, val longPressChar: String? = null)

val dialpadKeys = listOf(
    listOf(DialpadKey("1", ""), DialpadKey("2", "ABC"), DialpadKey("3", "DEF")),
    listOf(DialpadKey("4", "GHI"), DialpadKey("5", "JKL"), DialpadKey("6", "MNO")),
    listOf(DialpadKey("7", "PQRS"), DialpadKey("8", "TUV"), DialpadKey("9", "WXYZ")),
    listOf(DialpadKey("*", ""), DialpadKey("0", "+", longPressChar = "+"), DialpadKey("#", ""))
)

/**
 * Google Phone-inspired tactile numeric dialpad with haptic feedback,
 * sub-letters, and long-press '+' support on '0'.
 */
@Composable
fun Dialpad(
    onDigitClicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (row in dialpadKeys) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (key in row) {
                    DialpadButton(
                        key = key,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDigitClicked(key.digit)
                        },
                        onLongClick = {
                            if (key.longPressChar != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDigitClicked(key.longPressChar)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DialpadButton(
    key: DialpadKey,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(DialpadKeyBg)
            .pointerInput(key) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = key.digit,
                color = DialpadKeyText,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 30.sp
            )
            if (key.letters.isNotEmpty()) {
                Text(
                    text = key.letters,
                    color = DialpadSubText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.2.sp
                )
            } else if (key.digit == "1") {
                // Voice mail icon placeholder or empty line for vertical alignment
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
