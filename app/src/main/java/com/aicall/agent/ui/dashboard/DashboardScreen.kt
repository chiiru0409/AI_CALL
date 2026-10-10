package com.aicall.agent.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicall.agent.data.model.ActionItem
import com.aicall.agent.data.model.CallRecord
import com.aicall.agent.data.model.HandlingStatus
import com.aicall.agent.data.model.UrgencyLevel
import com.aicall.agent.data.repository.CallRepository
import com.aicall.agent.ui.theme.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    callRepository: CallRepository,
    onCallRecordClicked: (CallRecord) -> Unit
) {
    val totalCount by callRepository.totalCount.collectAsState(initial = 0)
    val aiCount by callRepository.aiHandledCount.collectAsState(initial = 0)
    val userAnsweredCount by callRepository.userAnsweredCount.collectAsState(initial = 0)
    val missedCount by callRepository.missedCount.collectAsState(initial = 0)
    val urgentCount by callRepository.urgentCount.collectAsState(initial = 0)
    val spamCount by callRepository.spamCount.collectAsState(initial = 0)
    val pendingTasksCount by callRepository.pendingActionItemsCount.collectAsState(initial = 0)

    val calls by callRepository.allCalls.collectAsState(initial = emptyList())
    val actionItems by callRepository.pendingActionItems.collectAsState(initial = emptyList())

    val scope = rememberCoroutineScope()
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }

    val filteredCalls = remember(calls, selectedCategoryFilter, searchQuery) {
        calls.filter { call ->
            val matchesCategory = when (selectedCategoryFilter) {
                "AI_HANDLED" -> call.handlingStatus == HandlingStatus.AI_HANDLED
                "URGENT" -> call.urgencyLevel == UrgencyLevel.HIGH || call.urgencyLevel == UrgencyLevel.EMERGENCY
                "SPAM" -> call.category.name == "SPAM" || call.category.name == "FRAUD_SUSPECTED"
                "ANSWERED" -> call.handlingStatus == HandlingStatus.USER_ANSWERED
                "MISSED" -> call.handlingStatus == HandlingStatus.MISSED
                else -> true
            }
            val matchesSearch = if (searchQuery.isBlank()) true else {
                call.callerNumber.contains(searchQuery, ignoreCase = true) ||
                        (call.callerName?.contains(searchQuery, ignoreCase = true) == true) ||
                        call.intent.contains(searchQuery, ignoreCase = true) ||
                        call.outcomeSummary.contains(searchQuery, ignoreCase = true)
            }
            matchesCategory && matchesSearch
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Dashboard Title
        item {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text(
                    text = "Intelligence Dashboard",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Real-time cellular analytics, persistent caller memory & tasks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search by name, number, intent, summary...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanAccent,
                    unfocusedBorderColor = SurfaceCardBorder,
                    focusedContainerColor = SurfaceCard,
                    unfocusedContainerColor = SurfaceCard
                )
            )
        }

        // Metrics KPI Grid (Total, AI Handled, Urgent, Spam, Pending Tasks)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Total Calls",
                        count = totalCount,
                        icon = Icons.Default.Call,
                        tint = CyanAccent,
                        modifier = Modifier.weight(1f),
                        isSelected = selectedCategoryFilter == "ALL",
                        onClick = { selectedCategoryFilter = "ALL" }
                    )
                    MetricCard(
                        title = "AI Handled",
                        count = aiCount,
                        icon = Icons.Default.Psychology,
                        tint = VioletAgent,
                        modifier = Modifier.weight(1f),
                        isSelected = selectedCategoryFilter == "AI_HANDLED",
                        onClick = { selectedCategoryFilter = "AI_HANDLED" }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Urgent / Alerts",
                        count = urgentCount,
                        icon = Icons.Default.Warning,
                        tint = UrgentRed,
                        modifier = Modifier.weight(1f),
                        isSelected = selectedCategoryFilter == "URGENT",
                        onClick = { selectedCategoryFilter = "URGENT" }
                    )
                    MetricCard(
                        title = "Spam / Fraud",
                        count = spamCount,
                        icon = Icons.Default.Shield,
                        tint = SpamRose,
                        modifier = Modifier.weight(1f),
                        isSelected = selectedCategoryFilter == "SPAM",
                        onClick = { selectedCategoryFilter = "SPAM" }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "User Answered",
                        count = userAnsweredCount,
                        icon = Icons.Default.PhoneCallback,
                        tint = VerifiedGreen,
                        modifier = Modifier.weight(1f),
                        isSelected = selectedCategoryFilter == "ANSWERED",
                        onClick = { selectedCategoryFilter = "ANSWERED" }
                    )
                    MetricCard(
                        title = "Pending Tasks",
                        count = pendingTasksCount,
                        icon = Icons.Default.Checklist,
                        tint = WarningAmber,
                        modifier = Modifier.weight(1f),
                        isSelected = false,
                        onClick = { }
                    )
                }
            }
        }


        // Follow-Up Action Items Section
        if (actionItems.isNotEmpty()) {
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Follow-Up Action Items (${actionItems.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = CyanAccent
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (item in actionItems.take(4)) {
                            ActionItemRow(
                                item = item,
                                onToggle = {
                                    scope.launch { callRepository.toggleActionItem(item) }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Call Intelligence Stream Header
        item {
            Text(
                text = "Call Intelligence Log (${filteredCalls.size})",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
        }

        // Empty state vs Call Cards
        if (filteredCalls.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Insights,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No call intelligence records found",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = "When cellular calls are answered or screened by AI, their transcripts, summaries, and action items will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        } else {
            items(filteredCalls) { call ->
                CallIntelligenceCard(call = call, onClick = { onCallRecordClicked(call) })
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    count: Int,
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) SurfaceCardElevated else SurfaceCard
        ),
        border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(tint)) else null
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, fontSize = 12.sp)
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = count.toString(),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
    }
}

@Composable
fun ActionItemRow(item: ActionItem, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.isCompleted,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = VerifiedGreen, uncheckedColor = TextMuted)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
                if (item.description.isNotBlank()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun CallIntelligenceCard(call: CallRecord, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
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
                                HandlingStatus.AI_HANDLED -> Icons.Default.SmartToy
                                HandlingStatus.MISSED -> Icons.AutoMirrored.Filled.CallMissed
                                else -> Icons.Default.Phone
                            },
                            contentDescription = null,
                            tint = when (call.handlingStatus) {
                                HandlingStatus.AI_HANDLED -> CyanAccent
                                HandlingStatus.MISSED -> UrgentRed
                                else -> VerifiedGreen
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = call.callerName ?: call.callerNumber,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "${call.category.name} • ${call.handlingStatus.name.replace('_', ' ')}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanAccent
                        )
                    }
                }

                if (call.urgencyLevel == UrgencyLevel.HIGH || call.urgencyLevel == UrgencyLevel.EMERGENCY) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = UrgentRedBg
                    ) {
                        Text(
                            text = call.urgencyLevel.name,
                            color = UrgentRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (call.intent.isNotBlank() || call.outcomeSummary.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = call.outcomeSummary.ifBlank { "Intent: ${call.intent}" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}
