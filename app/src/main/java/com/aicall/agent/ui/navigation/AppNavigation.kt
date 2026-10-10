package com.aicall.agent.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import com.aicall.agent.ui.theme.BackgroundDark
import com.aicall.agent.ui.theme.CyanAccent
import com.aicall.agent.ui.theme.SurfaceDark
import com.aicall.agent.ui.theme.TextMuted

@Composable
fun AppNavigation(
    app: AiCallApp,
    onMakeCall: (String) -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

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
                    tonalElevation = 8.dp
                ) {
                    screens.forEach { screen ->
                        val isSelected = currentRoute == screen.route
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = isSelected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CyanAccent,
                                selectedTextColor = CyanAccent,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted,
                                indicatorColor = BackgroundDark
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
                        onCallInitiated = onMakeCall
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
