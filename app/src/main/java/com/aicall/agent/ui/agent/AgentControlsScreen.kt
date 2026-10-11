package com.aicall.agent.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text(
                    text = "AI Agent Controls & Rules",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextPrimary
                )
                Text(
                    text = "Configure autonomous call-handling modes, schedules, and safety policies",
                    fontSize = 13.sp,
                    color = DarkTextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // Master Operating Mode Card
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Autonomous Call Agent",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkTextPrimary
                            )
                            Text(
                                text = if (isAgentEnabled) "Active: Ready to converse with eligible callers" else "Paused: Calls ring normally",
                                fontSize = 12.sp,
                                color = if (isAgentEnabled) AccentPurple else DarkTextSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Switch(
                            checked = isAgentEnabled,
                            onCheckedChange = { configRepository.setAgentMode(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ColorTokens.White,
                                checkedTrackColor = AccentPurple,
                                uncheckedThumbColor = DarkTextSecondary,
                                uncheckedTrackColor = DarkElevatedSurface
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = DarkElevatedSurface)
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Scheduled Operating Mode",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkTextPrimary
                            )
                            Text(
                                text = "Only activate AI Agent during specified time windows",
                                fontSize = 12.sp,
                                color = DarkTextSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Switch(
                            checked = isScheduleMode,
                            onCheckedChange = { configRepository.setScheduleMode(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ColorTokens.White,
                                checkedTrackColor = TealAccent,
                                uncheckedThumbColor = DarkTextSecondary,
                                uncheckedTrackColor = DarkElevatedSurface
                            )
                        )
                    }
                }
            }
        }

        // Language & Code-Switching Preference Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Voice Agent Language",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkTextPrimary
                    )
                    Text(
                        text = "Select primary language and code-switching profile for conversation",
                        fontSize = 12.sp,
                        color = DarkTextSecondary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(listOf("Telugu-English", "English", "Telugu", "Hindi")) { lang ->
                            val isSelected = preferredLanguage == lang
                            Surface(
                                onClick = { configRepository.setPreferredLanguage(lang) },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) AccentPurple.copy(alpha = 0.2f) else DarkElevatedSurface,
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, AccentPurple) else null
                            ) {
                                Text(
                                    text = lang,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) AccentPurple else DarkTextPrimary,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Approved Knowledge Base Section
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Approved User Knowledge",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentPurple
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
                                tint = AccentPurple
                            )
                        }
                    }
                    Text(
                        text = "The AI Agent is strictly restricted to information approved here. Secrets, OTPs, and passwords are unconditionally blocked.",
                        fontSize = 12.sp,
                        color = DarkTextSecondary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                    )

                    if (editingKnowledge) {
                        OutlinedTextField(
                            value = knowledgeText,
                            onValueChange = { knowledgeText = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentPurple,
                                unfocusedBorderColor = DarkElevatedSurface,
                                focusedContainerColor = DarkElevatedSurface,
                                unfocusedContainerColor = DarkElevatedSurface,
                                focusedTextColor = DarkTextPrimary,
                                unfocusedTextColor = DarkTextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DarkElevatedSurface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = knowledgeBase.ifBlank { "No knowledge base configured yet." },
                                fontSize = 13.sp,
                                color = DarkTextPrimary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
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
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkTextPrimary
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
                    colors = ButtonDefaults.buttonColors(containerColor = DarkElevatedSurface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = DarkTextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Sample Rule", fontSize = 12.sp, color = DarkTextPrimary)
                }
            }
        }

        if (rules.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = DarkSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No custom handling rules configured yet. Standard AI policy applies.",
                        color = DarkTextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
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
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = DarkTextPrimary
                )
                Text(
                    text = "IF: ${rule.conditionType.name} (${rule.matchValue}) → THEN: ${rule.actionType.name}",
                    fontSize = 11.sp,
                    color = AccentPurple,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete Rule",
                    tint = DangerRed.copy(alpha = 0.8f)
                )
            }
        }
    }
}
