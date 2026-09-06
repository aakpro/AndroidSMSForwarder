package com.smsforwarder.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    data object Logs : Screen("logs", "Logs", Icons.Default.History)

    companion object {
        val bottomNavItems = listOf(Home, Settings, Logs)
    }
}
