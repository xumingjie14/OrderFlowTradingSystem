package com.trading.orderflow.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.trading.orderflow.ui.screens.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderFlowNavigation() {
    val navController = rememberNavController()
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(navController)
            }
            composable(Screen.Chart.route) {
                ChartScreen(navController)
            }
            composable(Screen.Signals.route) {
                SignalsScreen(navController)
            }
            composable(Screen.Backtest.route) {
                BacktestScreen(navController)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(navController)
            }
        }
    }
}

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "仪表盘", Icons.Filled.Dashboard)
    object Chart : Screen("chart", "图表", Icons.Filled.ShowChart)
    object Signals : Screen("signals", "信号", Icons.Filled.Notifications)
    object Backtest : Screen("backtest", "回测", Icons.Filled.Analytics)
    object Settings : Screen("settings", "设置", Icons.Filled.Settings)
}

val bottomNavItems = listOf(
    Screen.Dashboard,
    Screen.Chart,
    Screen.Signals,
    Screen.Backtest,
    Screen.Settings
)