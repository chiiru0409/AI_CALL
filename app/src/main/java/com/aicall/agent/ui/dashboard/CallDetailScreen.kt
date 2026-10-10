package com.aicall.agent.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicall.agent.data.model.CallRecord
import com.aicall.agent.data.model.UrgencyLevel
import com.aicall.agent.data.repository.CallRepository
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
                title = { Text("Call Intelligence Record", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Record", tint = TextMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = call.callerName ?: call.callerNumber,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = call.callerNumber,
                                    fontSize = 14.sp,
                                    color = TextSecondary
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (call.urgencyLevel == UrgencyLevel.EMERGENCY || call.urgencyLevel == UrgencyLevel.HIGH) UrgentRedBg else SurfaceCardElevated
                            ) {
                                Text(
                                    text = call.urgencyLevel.name,
                                    color = if (call.urgencyLevel == UrgencyLevel.EMERGENCY || call.urgencyLevel == UrgencyLevel.HIGH) UrgentRed else CyanAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = SurfaceCardBorder)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            InfoItem(label = "Category", value = call.category.name)
                            InfoItem(label = "Handling", value = call.handlingStatus.name.replace('_', ' '))
                            InfoItem(label = "Duration", value = "${call.durationSeconds}s")
                        }
                    }
                }
            }

            // Summary & Intent Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "AI Intent & Summary",
                            style = MaterialTheme.typography.titleMedium,
                            color = CyanAccent
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = call.outcomeSummary.ifBlank { if (call.intent.isNotBlank()) "Intent: ${call.intent}" else "No summary available for this call." },
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                        )
                    }
                }
            }

            // Follow-Up Action Items for this call
            if (actionItems.isNotEmpty()) {
                item {
                    Text(
                        text = "Follow-Up Tasks (${actionItems.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = CyanAccent
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
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Full Conversation Transcript",
                            style = MaterialTheme.typography.titleMedium,
                            color = CyanAccent
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        if (call.transcript.isBlank()) {
                            Text(
                                text = "Transcript unavailable (Human-to-human call or audio not captured).",
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        } else {
                            Text(
                                text = call.transcript,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }

            // Security, Identity & Risk Assessment Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Security & Verification Posture",
                            style = MaterialTheme.typography.titleMedium,
                            color = CyanAccent
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "• Verification Status: ${call.verificationStatus}",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "• Evaluated Risk Level: ${call.riskLevel.name}",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "• Location Data: ${call.callerLocation}",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "• Recording Facility: ${call.recordingStatus}",
                            color = TextSecondary,
                            fontSize = 13.sp
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
            title = { Text("Delete Call Record?") },
            text = { Text("This will remove this call record, transcript, and associated follow-up tasks from local storage.") },
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
                    Text("Delete", color = UrgentRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun InfoItem(label: String, value: String) {
    Column {
        Text(text = label, color = TextMuted, fontSize = 11.sp)
        Text(text = value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

