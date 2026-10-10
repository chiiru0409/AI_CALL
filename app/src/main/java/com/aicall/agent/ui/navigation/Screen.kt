package com.aicall.agent.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Phone : Screen("phone", "Phone", Icons.Default.Call)
    object Intelligence : Screen("intelligence", "Intelligence", Icons.Default.Dashboard)
    object AgentControls : Screen("agent_controls", "Agent", Icons.Default.Psychology)
    object Diagnostics : Screen("diagnostics", "Diagnostics", Icons.Default.Build)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}
