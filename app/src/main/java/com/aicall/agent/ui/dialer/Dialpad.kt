package com.aicall.agent.ui.dialer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicall.agent.ui.theme.*

data class DialpadKey(val digit: String, val letters: String)

val dialpadKeys = listOf(
    listOf(DialpadKey("1", ""), DialpadKey("2", "ABC"), DialpadKey("3", "DEF")),
    listOf(DialpadKey("4", "GHI"), DialpadKey("5", "JKL"), DialpadKey("6", "MNO")),
    listOf(DialpadKey("7", "PQRS"), DialpadKey("8", "TUV"), DialpadKey("9", "WXYZ")),
    listOf(DialpadKey("*", ""), DialpadKey("0", "+"), DialpadKey("#", ""))
)

@Composable
fun Dialpad(
    onDigitClicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (row in dialpadKeys) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (key in row) {
                    DialpadButton(
                        key = key,
                        onClick = { onDigitClicked(key.digit) }
                    )
                }
            }
        }
    }
}

@Composable
fun DialpadButton(
    key: DialpadKey,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(DialpadKeyBg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = key.digit,
                color = DialpadKeyText,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (key.letters.isNotEmpty()) {
                Text(
                    text = key.letters,
                    color = DialpadSubText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
