package com.smsforwarder.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.smsforwarder.R

sealed class Screen(val route: String, @StringRes val titleRes: Int, val icon: ImageVector) {
    data object Home : Screen("home", R.string.nav_home, Icons.Default.Home)
    data object Settings : Screen("settings", R.string.nav_settings, Icons.Default.Settings)
    data object Logs : Screen("logs", R.string.nav_logs, Icons.Default.History)

    companion object {
        val bottomNavItems = listOf(Home, Settings, Logs)
    }
}
