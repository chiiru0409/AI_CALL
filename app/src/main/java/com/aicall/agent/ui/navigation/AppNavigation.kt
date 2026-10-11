package com.aicall.agent.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.aicall.agent.AiCallApp
import com.aicall.agent.data.model.CallRecord
import com.aicall.agent.ui.agent.AgentControlsScreen
import com.aicall.agent.ui.dashboard.CallDetailScreen
import com.aicall.agent.ui.dashboard.DashboardScreen
import com.aicall.agent.ui.diagnostics.DiagnosticsScreen
import com.aicall.agent.ui.dialer.DialerScreen
import com.aicall.agent.ui.settings.SettingsScreen
import com.aicall.agent.ui.theme.*

@Composable
fun AppNavigation(
    app: AiCallApp,
    onMakeCall: (String) -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val haptic = LocalHapticFeedback.current

    var selectedDetailCall by remember { mutableStateOf<CallRecord?>(null) }

    val screens = listOf(
        Screen.Phone,
        Screen.Intelligence,
        Screen.AgentControls,
        Screen.Diagnostics,
        Screen.Settings
    )

    Scaffold(
        bottomBar = {
            if (selectedDetailCall == null) {
                NavigationBar(
                    containerColor = SurfaceDark,
                    tonalElevation = 6.dp
                ) {
                    screens.forEach { screen ->
                        val isSelected = currentRoute == screen.route
                        val animatedScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.15f else 1.0f,
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioMediumBouncy
                            ),
                            label = "nav_icon_scale"
                        )

                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .scale(animatedScale)
                                )
                            },
                            label = {
                                Text(
                                    text = screen.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            selected = isSelected,
                            onClick = {
                                if (!isSelected) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentPurple,
                                selectedTextColor = AccentPurple,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted,
                                indicatorColor = AccentPurple.copy(alpha = 0.18f)
                            )
                        )
                    }
                }
            }
        },
        containerColor = BackgroundDark
    ) { paddingValues ->
        if (selectedDetailCall != null) {
            CallDetailScreen(
                call = selectedDetailCall!!,
                callRepository = app.callRepository,
                onBack = { selectedDetailCall = null }
            )
        } else {
            NavHost(
                navController = navController,
                startDestination = Screen.Phone.route,
                modifier = Modifier.padding(paddingValues)
            ) {
                composable(Screen.Phone.route) {
                    DialerScreen(
                        recentCallsFlow = app.callRepository.allCalls,
                        onCallInitiated = onMakeCall,
                        onCallRecordDetailClicked = { record ->
                            selectedDetailCall = record
                        },
                        onSettingsClicked = {
                            navController.navigate(Screen.Settings.route)
                        }
                    )
                }

                composable(Screen.Intelligence.route) {
                    DashboardScreen(
                        callRepository = app.callRepository,
                        onCallRecordClicked = { record ->
                            selectedDetailCall = record
                        }
                    )
                }

                composable(Screen.AgentControls.route) {
                    AgentControlsScreen(configRepository = app.agentConfigRepository)
                }

                composable(Screen.Diagnostics.route) {
                    DiagnosticsScreen()
                }

                composable(Screen.Settings.route) {
                    SettingsScreen()
                }
            }
        }
    }
}
