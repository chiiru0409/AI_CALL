package com.aicall.agent.ui.incall

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.aicall.agent.ui.theme.*

/**
 * Secure Dialer InCallBottomBar with squircle action buttons for
 * Answer (Green), Decline/Hang Up (Red), and Screen with AI (Purple).
 */
@Composable
fun InCallBottomBar(
    isIncoming: Boolean,
    onAnswer: () -> Unit,
    onHangUp: () -> Unit,
    onScreenWithAi: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val buttonShape = AppShapes.Keypad

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 16.dp),
        horizontalArrangement = if (isIncoming) Arrangement.SpaceBetween else Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isIncoming) {
            // Answer Button (Hero Green Squircle)
            val answerInteractionSource = remember { MutableInteractionSource() }
            val isAnswerPressed by answerInteractionSource.collectIsPressedAsState()
            val answerScale by animateFloatAsState(
                targetValue = if (isAnswerPressed) 0.92f else 1.0f,
                animationSpec = spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "answer_scale"
            )

            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onAnswer()
                },
                interactionSource = answerInteractionSource,
                color = getCallGreenColor(),
                contentColor = Color.White,
                shape = buttonShape,
                modifier = Modifier
                    .size(width = 84.dp, height = 64.dp)
                    .scale(answerScale)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Answer Call",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Screen With AI Action Button (Center Squircle)
            if (onScreenWithAi != null) {
                val aiInteractionSource = remember { MutableInteractionSource() }
                val isAiPressed by aiInteractionSource.collectIsPressedAsState()
                val aiScale by animateFloatAsState(
                    targetValue = if (isAiPressed) 0.92f else 1.0f,
                    animationSpec = spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "ai_screen_scale"
                )

                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onScreenWithAi()
                    },
                    interactionSource = aiInteractionSource,
                    color = AccentPurple,
                    contentColor = BackgroundDark,
                    shape = buttonShape,
                    modifier = Modifier
                        .size(width = 84.dp, height = 64.dp)
                        .scale(aiScale)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = "Screen with AI",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // Decline Button (Decline Red Squircle)
            val declineInteractionSource = remember { MutableInteractionSource() }
            val isDeclinePressed by declineInteractionSource.collectIsPressedAsState()
            val declineScale by animateFloatAsState(
                targetValue = if (isDeclinePressed) 0.92f else 1.0f,
                animationSpec = spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "decline_scale"
            )

            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onHangUp()
                },
                interactionSource = declineInteractionSource,
                color = getDeclineRedColor(),
                contentColor = Color.White,
                shape = buttonShape,
                modifier = Modifier
                    .size(width = 84.dp, height = 64.dp)
                    .scale(declineScale)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Decline Call",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        } else {
            // Ongoing Call: Hero Hang Up Button (Centered Red Squircle)
            val hangUpInteractionSource = remember { MutableInteractionSource() }
            val isHangUpPressed by hangUpInteractionSource.collectIsPressedAsState()
            val hangUpScale by animateFloatAsState(
                targetValue = if (isHangUpPressed) 0.92f else 1.0f,
                animationSpec = spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "hangup_scale"
            )

            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onHangUp()
                },
                interactionSource = hangUpInteractionSource,
                color = getDeclineRedColor(),
                contentColor = Color.White,
                shape = buttonShape,
                modifier = Modifier
                    .size(width = 88.dp, height = 64.dp)
                    .scale(hangUpScale)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    }
}
