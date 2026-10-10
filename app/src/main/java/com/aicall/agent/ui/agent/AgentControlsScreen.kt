package com.aicall.agent.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicall.agent.data.model.AgentRule
import com.aicall.agent.data.model.AgentSchedule
import com.aicall.agent.data.model.RuleActionType
import com.aicall.agent.data.model.RuleConditionType
import com.aicall.agent.data.repository.AgentConfigRepository
import com.aicall.agent.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun AgentControlsScreen(
    configRepository: AgentConfigRepository
) {
    val isAgentEnabled by configRepository.isAgentModeEnabled.collectAsState()
    val isScheduleMode by configRepository.isScheduleModeActive.collectAsState()
    val preferredLanguage by configRepository.preferredLanguage.collectAsState()
    val knowledgeBase by configRepository.approvedKnowledgeBase.collectAsState()

    val rules by configRepository.allRules.collectAsState(initial = emptyList())
    val schedules by configRepository.allSchedules.collectAsState(initial = emptyList())

    val scope = rememberCoroutineScope()
    var editingKnowledge by remember { mutableStateOf(false) }
    var knowledgeText by remember(knowledgeBase) { mutableStateOf(knowledgeBase) }

    var showAddRuleDialog by remember { mutableStateOf(false) }
    var showAddScheduleDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text(
                    text = "AI Agent Controls & Rules",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Configure autonomous call-handling modes, schedules, and safety policies",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        // Master Operating Mode Card
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Autonomous Call Agent",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = if (isAgentEnabled) "Active: Ready to converse with eligible callers" else "Paused: Calls ring normally",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isAgentEnabled) CyanAccent else TextMuted,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = isAgentEnabled,
                            onCheckedChange = { configRepository.setAgentMode(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = CyanAccent, checkedTrackColor = VioletAgentDark)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = SurfaceCardBorder)
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Scheduled Operating Mode",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Only activate AI Agent during specified time windows",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = isScheduleMode,
                            onCheckedChange = { configRepository.setScheduleMode(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = VioletAgent, checkedTrackColor = SurfaceCardElevated)
                        )
                    }
                }
            }
        }

        // Language & Code-Switching Preference Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Voice Agent Language",
                        style = MaterialTheme.typography.titleMedium,
                        color = CyanAccent
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Telugu-English", "English", "Telugu", "Hindi").forEach { lang ->
                            val isSelected = preferredLanguage == lang
                            FilterChip(
                                selected = isSelected,
                                onClick = { configRepository.setPreferredLanguage(lang) },
                                label = { Text(lang, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = VioletAgent,
                                    selectedLabelColor = TextPrimary
                                )
                            )
                        }
                    }
                }
            }
        }

        // Approved Knowledge Base Section
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
                        Text(
                            text = "Approved User Knowledge",
                            style = MaterialTheme.typography.titleMedium,
                            color = CyanAccent
                        )
                        IconButton(onClick = {
                            if (editingKnowledge) {
                                configRepository.updateKnowledgeBase(knowledgeText)
                            }
                            editingKnowledge = !editingKnowledge
                        }) {
                            Icon(
                                imageVector = if (editingKnowledge) Icons.Default.Check else Icons.Default.Edit,
                                contentDescription = "Edit Knowledge",
                                tint = CyanAccent
                            )
                        }
                    }
                    Text(
                        text = "The AI Agent is strictly restricted to information approved here. Secrets, OTPs, and passwords are unconditionally blocked.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (editingKnowledge) {
                        OutlinedTextField(
                            value = knowledgeText,
                            onValueChange = { knowledgeText = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanAccent,
                                unfocusedBorderColor = SurfaceCardBorder
                            )
                        )
                    } else {
                        Text(
                            text = knowledgeBase.ifBlank { "No knowledge base configured yet." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        // Plain-Language Caller Rules Header & Button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Handling Rules (${rules.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Button(
                    onClick = {
                        scope.launch {
                            configRepository.saveRule(
                                AgentRule(
                                    title = "Auto-handle Delivery calls",
                                    conditionType = RuleConditionType.CALLER_CATEGORY,
                                    matchValue = "DELIVERY",
                                    actionType = RuleActionType.AUTO_HANDLE_AI
                                )
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceCardElevated)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Sample Rule", fontSize = 12.sp)
                }
            }
        }

        if (rules.isEmpty()) {
            item {
                Text(
                    text = "No custom handling rules configured yet. Standard AI policy applies.",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            }
        } else {
            items(rules) { rule ->
                RuleCard(
                    rule = rule,
                    onDelete = { scope.launch { configRepository.deleteRule(rule) } }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun RuleCard(rule: AgentRule, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = "IF: ${rule.conditionType.name} (${rule.matchValue}) → THEN: ${rule.actionType.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanAccent
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Rule",
                    tint = TextMuted
                )
            }
        }
    }
}
