package com.aicall.agent.ui.dialer

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicall.agent.data.model.CallRecord
import com.aicall.agent.data.model.HandlingStatus
import com.aicall.agent.data.model.UrgencyLevel
import com.aicall.agent.ui.theme.*
import kotlinx.coroutines.flow.Flow

@Composable
fun DialerScreen(
    recentCallsFlow: Flow<List<CallRecord>>,
    onCallInitiated: (String) -> Unit
) {
    val context = LocalContext.current
    var inputNumber by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Recents, 1: Keypad
    val recentCalls by recentCallsFlow.collectAsState(initial = emptyList())

    val roleManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.getSystemService(RoleManager::class.java)
    } else null

    val isDefaultDialer = roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) ?: true

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ -> }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp)
    ) {
        // Default Phone App Role Prompt Banner
        if (!isDefaultDialer && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCardElevated)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Set as Default Phone App",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Enables AI auto-screening and native cellular dialer handling.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val intent = roleManager?.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                            if (intent != null) roleLauncher.launch(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                    ) {
                        Text("Enable", color = BackgroundDark, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Sub Navigation Header (Recents vs Keypad)
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = SurfaceDark,
            contentColor = CyanAccent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp))
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Recents", fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Keypad", fontWeight = FontWeight.SemiBold) }
            )
        }

        if (selectedTab == 0) {
            // Recents Call List
            if (recentCalls.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No recent calls yet", color = TextSecondary)
                        Text(
                            "Incoming and dialed cellular calls will appear here",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(recentCalls) { call ->
                        RecentCallRow(call = call, onCallClick = { onCallInitiated(call.callerNumber) })
                    }
                }
            }
        } else {
            // Keypad Screen
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Dialed Number Display
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = inputNumber.ifEmpty { "Enter Number" },
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (inputNumber.isEmpty()) TextMuted else TextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    if (inputNumber.isNotEmpty()) {
                        IconButton(onClick = { inputNumber = inputNumber.dropLast(1) }) {
                            Icon(
                                imageVector = Icons.Default.Backspace,
                                contentDescription = "Delete",
                                tint = TextSecondary
                            )
                        }
                    }
                }

                // Dialpad Grid
                Dialpad(
                    onDigitClicked = { digit -> inputNumber += digit },
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Call Action Button
                Box(
                    modifier = Modifier
                        .padding(bottom = 24.dp)
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(CallActionGreen)
                        .clickable {
                            if (inputNumber.isNotBlank()) {
                                onCallInitiated(inputNumber)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Place Call",
                        tint = TextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RecentCallRow(call: CallRecord, onCallClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCallClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        when (call.handlingStatus) {
                            HandlingStatus.AI_HANDLED -> VioletAgentDark
                            HandlingStatus.MISSED -> UrgentRedBg
                            else -> SurfaceCardElevated
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (call.handlingStatus) {
                        HandlingStatus.AI_HANDLED -> Icons.Default.Psychology
                        HandlingStatus.MISSED -> Icons.Default.CallMissed
                        else -> Icons.Default.Call
                    },
                    contentDescription = null,
                    tint = when (call.handlingStatus) {
                        HandlingStatus.AI_HANDLED -> CyanAccent
                        HandlingStatus.MISSED -> UrgentRed
                        else -> VerifiedGreen
                    },
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = call.callerName ?: call.callerNumber,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = call.category.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanAccent
                    )
                    Text(
                        text = " • ${call.handlingStatus.name.replace('_', ' ')}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            if (call.urgencyLevel == UrgencyLevel.HIGH || call.urgencyLevel == UrgencyLevel.EMERGENCY) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(UrgentRedBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "URGENT",
                        color = UrgentRed,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            IconButton(onClick = onCallClick) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call",
                    tint = VerifiedGreen
                )
            }
        }
    }
}
