package com.aicall.agent.ui.incall

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aicall.agent.ui.theme.AccentPurple
import com.aicall.agent.ui.theme.AccentTeal
import com.aicall.agent.ui.theme.AppShapes

/**
 * Secure Dialer-adapted InCallControlGrid with squircle buttons for
 * Keypad, Mute, Speaker, Hold, AI Takeover/Screening, and Transcript toggling.
 */
@Composable
fun InCallControlGrid(
    isKeypadOpen: Boolean,
    onToggleKeypad: () -> Unit,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    isSpeakerOn: Boolean,
    onToggleSpeaker: () -> Unit,
    isOnHold: Boolean,
    onToggleHold: () -> Unit,
    isAiHandling: Boolean,
    onToggleAiHandling: () -> Unit,
    isTranscriptVisible: Boolean,
    onToggleTranscript: () -> Unit,
    modifier: Modifier = Modifier
) {
    val btnShape = AppShapes.Keypad
    val buttonHeight = 66.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Row 1: Keypad, Mute, Speaker
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InCallButton(
                icon = Icons.Default.Dialpad,
                label = "Keypad",
                isActive = isKeypadOpen,
                onClick = onToggleKeypad,
                shape = btnShape,
                modifier = Modifier
                    .weight(1f)
                    .height(buttonHeight)
            )

            InCallButton(
                icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                label = if (isMuted) "Unmute" else "Mute",
                isActive = isMuted,
                onClick = onToggleMute,
                shape = btnShape,
                modifier = Modifier
                    .weight(1f)
                    .height(buttonHeight)
            )

            InCallButton(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                label = "Speaker",
                isActive = isSpeakerOn,
                onClick = onToggleSpeaker,
                shape = btnShape,
                activeColor = AccentTeal,
                modifier = Modifier
                    .weight(1f)
                    .height(buttonHeight)
            )
        }

        // Row 2: Hold, AI Takeover, Transcript
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InCallButton(
                icon = if (isOnHold) Icons.Default.PlayArrow else Icons.Default.Pause,
                label = if (isOnHold) "Unhold" else "Hold",
                isActive = isOnHold,
                onClick = onToggleHold,
                shape = btnShape,
                modifier = Modifier
                    .weight(1f)
                    .height(buttonHeight)
            )

            InCallButton(
                icon = if (isAiHandling) Icons.Default.Person else Icons.Default.SmartToy,
                label = if (isAiHandling) "Take Over" else "AI Screen",
                isActive = isAiHandling,
                onClick = onToggleAiHandling,
                shape = btnShape,
                activeColor = AccentPurple,
                isPulsing = isAiHandling,
                modifier = Modifier
                    .weight(1f)
                    .height(buttonHeight)
            )

            InCallButton(
                icon = Icons.Default.Description,
                label = "Transcript",
                isActive = isTranscriptVisible,
                onClick = onToggleTranscript,
                shape = btnShape,
                activeColor = AccentTeal,
                modifier = Modifier
                    .weight(1f)
                    .height(buttonHeight)
            )
        }
    }
}
