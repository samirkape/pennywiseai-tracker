package com.pennywiseai.tracker.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.theme.Dimensions

/**
 * Material Design 3 window-width breakpoints.
 * Compact  < 600 dp  – phones in portrait
 * Medium   600–840dp – small tablets / phones in landscape
 * Expanded > 840 dp  – large tablets / foldables / desktops
 */
enum class WindowWidthSizeClass { Compact, Medium, Expanded }

/** Aggregated size information provided via [LocalWindowSizeInfo]. */
data class WindowSizeInfo(val widthSizeClass: WindowWidthSizeClass) {

    /** True on medium/expanded screens → show NavigationRail instead of bottom bar. */
    val useNavigationRail: Boolean
        get() = widthSizeClass != WindowWidthSizeClass.Compact

    /**
     * Horizontal content padding that scales with the available window width.
     * Compact=16dp, Medium=20dp, Expanded=24dp.
     */
    val contentPadding: Dp
        get() = when (widthSizeClass) {
            WindowWidthSizeClass.Compact  -> 16.dp
            WindowWidthSizeClass.Medium   -> 20.dp
            WindowWidthSizeClass.Expanded -> 24.dp
        }

    /**
     * Bottom padding to add to scrollable content to clear the bottom navigation bar.
     * Returns 0dp when NavigationRail is active (no bottom bar present).
     */
    val bottomNavBarPadding: Dp
        get() = if (useNavigationRail) 0.dp else Dimensions.Component.bottomBarHeight

    /**
     * Recommended maximum width for content containers so text lines don't become
     * uncomfortably long on very wide screens.
     */
    val maxContentWidth: Dp
        get() = when (widthSizeClass) {
            WindowWidthSizeClass.Compact  -> Dp.Infinity
            WindowWidthSizeClass.Medium   -> 840.dp
            WindowWidthSizeClass.Expanded -> 1024.dp
        }
}

/** CompositionLocal carrying the current [WindowSizeInfo] down the UI tree. */
val LocalWindowSizeInfo = compositionLocalOf { WindowSizeInfo(WindowWidthSizeClass.Compact) }

/** Reads the current window configuration and returns a [WindowSizeInfo]. */
@Composable
fun rememberWindowSizeInfo(): WindowSizeInfo {
    val configuration = LocalConfiguration.current
    return WindowSizeInfo(
        widthSizeClass = when {
            configuration.screenWidthDp < 600 -> WindowWidthSizeClass.Compact
            configuration.screenWidthDp < 840 -> WindowWidthSizeClass.Medium
            else                              -> WindowWidthSizeClass.Expanded
        }
    )
}

