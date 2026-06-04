package com.pennywiseai.tracker.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.pennywiseai.tracker.core.Constants

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Home : BottomNavItem(
        route = Constants.Routes.HOME,
        title = "Home",
        icon = Icons.Default.Home
    )

    data object Budgets : BottomNavItem(
        route = Constants.Routes.BUDGETS,
        title = "Budgets",
        icon = Icons.Default.AccountBalanceWallet
    )

    data object Analytics : BottomNavItem(
        route = Constants.Routes.ANALYTICS,
        title = "Analytics",
        icon = Icons.Default.Analytics
    )

    data object Settings : BottomNavItem(
        route = Constants.Routes.SETTINGS,
        title = "Settings",
        icon = Icons.Default.Settings
    )
}