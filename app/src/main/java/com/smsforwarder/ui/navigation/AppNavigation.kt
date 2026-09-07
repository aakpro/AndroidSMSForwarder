package com.smsforwarder.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.smsforwarder.data.preferences.AppPreferences
import com.smsforwarder.data.preferences.SecurePreferences
import com.smsforwarder.ui.screens.HomeScreen
import com.smsforwarder.ui.screens.LogsScreen
import com.smsforwarder.ui.screens.SettingsScreen

@Composable
fun AppNavigation(
    appPreferences: AppPreferences,
    securePreferences: SecurePreferences,
    onRequestPermissions: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                Screen.bottomNavItems.forEach { screen ->
                    val isSelected = currentDestination?.route == screen.route
                    val title = androidx.compose.ui.res.stringResource(screen.titleRes)
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = title) },
                        label = { Text(title) },
                        selected = isSelected,
                        onClick = {
                            if (!isSelected) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    appPreferences = appPreferences,
                    onRequestPermissions = onRequestPermissions
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    appPreferences = appPreferences,
                    securePreferences = securePreferences
                )
            }
            composable(Screen.Logs.route) {
                LogsScreen()
            }
        }
    }
}
