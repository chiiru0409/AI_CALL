package com.aicall.agent.ui.dialer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicall.agent.ui.theme.*

data class DialKey(
    val digit: String,
    val letters: String,
    val longPressChar: String? = null
)

val DIALPAD_KEYS = listOf(
    DialKey("1", ""),
    DialKey("2", "ABC"),
    DialKey("3", "DEF"),
    DialKey("4", "GHI"),
    DialKey("5", "JKL"),
    DialKey("6", "MNO"),
    DialKey("7", "PQRS"),
    DialKey("8", "TUV"),
    DialKey("9", "WXYZ"),
    DialKey("*", ""),
    DialKey("0", "+", longPressChar = "+"),
    DialKey("#", "")
)

/**
 * Secure Dialer tactile numeric dialpad with squircle buttons, spring scaling,
 * responsive typography, long-press '+' and backspace-clear, and DTMF tones.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Dialpad(
    inputValue: String,
    onValueChange: (String) -> Unit,
    onCallClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager }

    var toneGenerator by remember { mutableStateOf<ToneGenerator?>(null) }
    DisposableEffect(Unit) {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_DTMF, 75)
        } catch (_: Exception) {}
        onDispose {
            try { toneGenerator?.release() } catch (_: Exception) {}
            toneGenerator = null
        }
    }

    fun playTone(digit: String) {
        toneGenerator?.let { tg ->
            try {
                val tone = when (digit) {
                    "1" -> ToneGenerator.TONE_DTMF_1
                    "2" -> ToneGenerator.TONE_DTMF_2
                    "3" -> ToneGenerator.TONE_DTMF_3
                    "4" -> ToneGenerator.TONE_DTMF_4
                    "5" -> ToneGenerator.TONE_DTMF_5
                    "6" -> ToneGenerator.TONE_DTMF_6
                    "7" -> ToneGenerator.TONE_DTMF_7
                    "8" -> ToneGenerator.TONE_DTMF_8
                    "9" -> ToneGenerator.TONE_DTMF_9
                    "0" -> ToneGenerator.TONE_DTMF_0
                    "*" -> ToneGenerator.TONE_DTMF_S
                    "#" -> ToneGenerator.TONE_DTMF_P
                    else -> null
                }
                if (tone != null) tg.startTone(tone, 120)
            } catch (_: Exception) {}
        }
    }

    val keyShape = AppShapes.Keypad
    var showClipboardMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Dialed Number Container (AppShapes.Keypad Squircle)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            shape = keyShape,
            color = Color.Transparent,
            border = BorderStroke(1.5.dp, SurfaceCardBorder)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                        onLongClick = { showClipboardMenu = true }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (inputValue.isEmpty()) {
                    Text(
                        text = "Enter a number",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Normal,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                } else {
                    val fontSize = when {
                        inputValue.length > 16 -> 22.sp
                        inputValue.length > 12 -> 26.sp
                        else -> 32.sp
                    }
                    Text(
                        text = inputValue,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontSize = fontSize,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        ),
                        maxLines = 1
                    )
                }

                DropdownMenu(
                    expanded = showClipboardMenu,
                    onDismissRequest = { showClipboardMenu = false }
                ) {
                    val hasClip = clipboardManager?.hasPrimaryClip() == true
                    if (hasClip) {
                        val clipText = clipboardManager?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                        val digits = clipText.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
                        if (digits.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Paste: $digits") },
                                onClick = {
                                    onValueChange(inputValue + digits)
                                    showClipboardMenu = false
                                }
                            )
                        }
                    }
                    if (inputValue.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Copy") },
                            onClick = {
                                try {
                                    val clip = ClipData.newPlainText("phone_number", inputValue)
                                    clipboardManager?.setPrimaryClip(clip)
                                } catch (_: Exception) {}
                                showClipboardMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Clear") },
                            onClick = {
                                onValueChange("")
                                showClipboardMenu = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // 12-Key Numeric Grid (4 rows x 3 columns)
        for (row in 0 until 4) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (col in 0 until 3) {
                    val key = DIALPAD_KEYS[row * 3 + col]
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val keyScale by animateFloatAsState(
                        targetValue = if (isPressed) 0.92f else 1.0f,
                        animationSpec = spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "key_scale_${key.digit}"
                    )

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .scale(keyScale)
                            .clip(keyShape)
                            .combinedClickable(
                                interactionSource = interactionSource,
                                indication = ripple(),
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    playTone(key.digit)
                                    onValueChange(inputValue + key.digit)
                                },
                                onLongClick = {
                                    if (key.longPressChar != null) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        playTone(key.longPressChar)
                                        onValueChange(inputValue + key.longPressChar)
                                    }
                                }
                            ),
                        shape = keyShape,
                        color = SurfaceCard,
                        contentColor = TextPrimary
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = key.digit,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                lineHeight = 26.sp
                            )
                            if (key.letters.isNotEmpty()) {
                                Text(
                                    text = key.letters,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextMuted,
                                    letterSpacing = 1.2.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Bottom Action Row: Paste | Hero Call Button | Backspace
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Paste Button
            val pasteInteractionSource = remember { MutableInteractionSource() }
            val isPastePressed by pasteInteractionSource.collectIsPressedAsState()
            val pasteScale by animateFloatAsState(
                targetValue = if (isPastePressed) 0.92f else 1.0f,
                animationSpec = spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "paste_btn_scale"
            )

            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val clipText = clipboardManager?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                    val digits = clipText.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
                    if (digits.isNotEmpty()) {
                        onValueChange(inputValue + digits)
                    }
                },
                shape = keyShape,
                color = SurfaceCard,
                contentColor = TextSecondary,
                interactionSource = pasteInteractionSource,
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp)
                    .scale(pasteScale)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Paste number",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Hero Call Button (Centered, Squircle, Green)
            val callInteractionSource = remember { MutableInteractionSource() }
            val isCallPressed by callInteractionSource.collectIsPressedAsState()
            val callScale by animateFloatAsState(
                targetValue = if (isCallPressed) 0.92f else 1.0f,
                animationSpec = spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "hero_call_scale"
            )

            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (inputValue.isNotBlank()) {
                        onCallClick(inputValue)
                    }
                },
                shape = keyShape,
                color = getCallGreenColor(),
                contentColor = Color.White,
                interactionSource = callInteractionSource,
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp)
                    .scale(callScale)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Place call",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Backspace Button (Tap = drop last char, Long press = clear all)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp),
                contentAlignment = Alignment.Center
            ) {
                if (inputValue.isNotEmpty()) {
                    val backspaceInteractionSource = remember { MutableInteractionSource() }
                    val isBackspacePressed by backspaceInteractionSource.collectIsPressedAsState()
                    val backspaceScale by animateFloatAsState(
                        targetValue = if (isBackspacePressed) 0.92f else 1.0f,
                        animationSpec = spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "backspace_scale"
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(backspaceScale)
                            .clip(keyShape)
                            .combinedClickable(
                                interactionSource = backspaceInteractionSource,
                                indication = ripple(),
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onValueChange(inputValue.dropLast(1))
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onValueChange("")
                                }
                            ),
                        shape = keyShape,
                        color = SurfaceCard,
                        contentColor = TextPrimary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Backspace",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
