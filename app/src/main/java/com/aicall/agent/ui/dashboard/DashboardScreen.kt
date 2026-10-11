package com.aicall.agent.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicall.agent.data.model.ActionItem
import com.aicall.agent.data.model.CallRecord
import com.aicall.agent.data.model.HandlingStatus
import com.aicall.agent.data.model.UrgencyLevel
import com.aicall.agent.data.repository.CallRepository
import com.aicall.agent.ui.components.ContactAvatar
import com.aicall.agent.ui.components.GoogleSearchBar
import com.aicall.agent.ui.theme.*
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
            .background(DarkBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Title & Header
        item {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text(
                    text = "Intelligence Dashboard",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextPrimary
                )
                Text(
                    text = "Real-time cellular analytics, persistent caller memory & tasks",
                    fontSize = 13.sp,
                    color = DarkTextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // Google-style Pill Search Bar
        item {
            GoogleSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholderText = "Search name, number, intent, summary..."
            )
        }

        // Metrics KPI Grid
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
                        tint = TealAccent,
                        modifier = Modifier.weight(1f),
                        isSelected = selectedCategoryFilter == "ALL",
                        onClick = { selectedCategoryFilter = "ALL" }
                    )
                    MetricCard(
                        title = "AI Handled",
                        count = aiCount,
                        icon = Icons.Default.Psychology,
                        tint = AccentPurple,
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
                        tint = DangerRed,
                        modifier = Modifier.weight(1f),
                        isSelected = selectedCategoryFilter == "URGENT",
                        onClick = { selectedCategoryFilter = "URGENT" }
                    )
                    MetricCard(
                        title = "Spam / Fraud",
                        count = spamCount,
                        icon = Icons.Default.Shield,
                        tint = DangerRed.copy(alpha = 0.8f),
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
                        tint = CallAnswerGreen,
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

        // Quick Category Filter Chips
        item {
            val filters = listOf(
                "ALL" to "All",
                "AI_HANDLED" to "AI Handled",
                "URGENT" to "Urgent",
                "SPAM" to "Spam",
                "ANSWERED" to "Answered",
                "MISSED" to "Missed"
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filters) { (key, label) ->
                    val isSelected = selectedCategoryFilter == key
                    Surface(
                        onClick = { selectedCategoryFilter = key },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) AccentPurple.copy(alpha = 0.2f) else DarkSurface,
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, AccentPurple) else null
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) AccentPurple else DarkTextSecondary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
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
                            text = "Pending Action Items (${actionItems.size})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkTextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (item in actionItems.take(5)) {
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
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = DarkTextPrimary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Empty state vs Call Cards
        if (filteredCalls.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
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
                            tint = DarkTextSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No call intelligence records found",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = DarkTextPrimary
                        )
                        Text(
                            text = "When cellular calls are answered or screened by AI, their transcripts, summaries, and action items will appear here.",
                            fontSize = 12.sp,
                            color = DarkTextSecondary,
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
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    count: Int,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) DarkElevatedSurface else DarkSurface
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, tint) else null
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = DarkTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(tint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = count.toString(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = DarkTextPrimary
            )
        }
    }
}

@Composable
fun ActionItemRow(item: ActionItem, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.isCompleted,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = CallAnswerGreen,
                    uncheckedColor = DarkTextSecondary
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = DarkTextPrimary
                )
                if (item.description.isNotBlank()) {
                    Text(
                        text = item.description,
                        fontSize = 12.sp,
                        color = DarkTextSecondary,
                        modifier = Modifier.padding(top = 2.dp)
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    ContactAvatar(
                        name = call.callerName,
                        phoneNumber = call.callerNumber,
                        size = 42.dp,
                        showPhoneBadge = false
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = call.callerName ?: call.callerNumber,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = "${call.category.name} • ${call.handlingStatus.name.replace('_', ' ')}",
                                fontSize = 11.sp,
                                color = if (call.handlingStatus == HandlingStatus.AI_HANDLED) AccentPurple else DarkTextSecondary
                            )
                        }
                    }
                }

                if (call.urgencyLevel == UrgencyLevel.HIGH || call.urgencyLevel == UrgencyLevel.EMERGENCY) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DangerRed.copy(alpha = 0.18f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DangerRed.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = call.urgencyLevel.name,
                            color = DangerRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (call.intent.isNotBlank() || call.outcomeSummary.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkElevatedSurface
                ) {
                    Text(
                        text = call.outcomeSummary.ifBlank { "Intent: ${call.intent}" },
                        fontSize = 12.sp,
                        color = DarkTextSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
