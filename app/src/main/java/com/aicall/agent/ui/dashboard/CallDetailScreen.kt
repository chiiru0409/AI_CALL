package com.aicall.agent.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicall.agent.data.model.CallRecord
import com.aicall.agent.data.model.UrgencyLevel
import com.aicall.agent.data.repository.CallRepository
import com.aicall.agent.ui.components.ContactAvatar
import com.aicall.agent.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallDetailScreen(
    call: CallRecord,
    callRepository: CallRepository,
    onBack: () -> Unit
) {
    val actionItems by callRepository.getActionItemsForCall(call.sessionId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Call Intelligence Record",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = DarkTextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Record",
                            tint = DangerRed.copy(alpha = 0.8f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Contact Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ContactAvatar(
                                name = call.callerName,
                                phoneNumber = call.callerNumber,
                                size = 56.dp,
                                showPhoneBadge = false
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = call.callerName ?: call.callerNumber,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkTextPrimary
                                )
                                Text(
                                    text = call.callerNumber,
                                    fontSize = 14.sp,
                                    color = DarkTextSecondary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            if (call.urgencyLevel == UrgencyLevel.EMERGENCY || call.urgencyLevel == UrgencyLevel.HIGH) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = DangerRed.copy(alpha = 0.2f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = call.urgencyLevel.name,
                                        color = DangerRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = DarkElevatedSurface)
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            DetailInfoItem(label = "Category", value = call.category.name)
                            DetailInfoItem(label = "Handling", value = call.handlingStatus.name.replace('_', ' '))
                            DetailInfoItem(label = "Duration", value = "${call.durationSeconds}s")
                        }
                    }
                }
            }

            // Summary & Intent Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "AI Intent & Summary",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentPurple
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = call.outcomeSummary.ifBlank {
                                if (call.intent.isNotBlank()) "Intent: ${call.intent}" else "No summary available for this call."
                            },
                            fontSize = 14.sp,
                            color = DarkTextPrimary,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // Follow-Up Action Items for this call
            if (actionItems.isNotEmpty()) {
                item {
                    Text(
                        text = "Follow-Up Tasks (${actionItems.size})",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkTextPrimary
                    )
                }

                items(actionItems) { item ->
                    ActionItemRow(
                        item = item,
                        onToggle = {
                            scope.launch { callRepository.toggleActionItem(item) }
                        }
                    )
                }
            }

            // Transcript Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Full Conversation Transcript",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TealAccent
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        if (call.transcript.isBlank()) {
                            Text(
                                text = "Transcript unavailable (Direct cellular call or speech audio not intercepted).",
                                color = DarkTextSecondary,
                                fontSize = 13.sp
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = DarkElevatedSurface
                            ) {
                                Text(
                                    text = call.transcript,
                                    color = DarkTextPrimary,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Security, Identity & Risk Assessment Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Security & Verification Posture",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkTextPrimary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "• Verification Status: ${call.verificationStatus}",
                            color = DarkTextSecondary,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "• Evaluated Risk Level: ${call.riskLevel.name}",
                            color = DarkTextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            text = "• Location Data: ${call.callerLocation}",
                            color = DarkTextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            text = "• Recording Facility: ${call.recordingStatus}",
                            color = DarkTextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = DarkSurface,
            title = {
                Text("Delete Call Record?", color = DarkTextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "This will remove this call record, transcript, and associated follow-up tasks from local storage.",
                    color = DarkTextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            callRepository.deleteCallRecord(call)
                            onBack()
                        }
                        showDeleteConfirm = false
                    }
                ) {
                    Text("Delete", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            }
        )
    }
}

@Composable
fun DetailInfoItem(label: String, value: String) {
    Column {
        Text(text = label, color = DarkTextSecondary, fontSize = 11.sp)
        Text(
            text = value,
            color = DarkTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
