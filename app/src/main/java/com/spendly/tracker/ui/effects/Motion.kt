package com.spendly.tracker.ui.effects

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * True when the user has disabled system animations
 * (Settings > Accessibility > Remove animations sets every animator scale to 0).
 *
 * Read once per composition — the animator scale effectively never changes mid-session.
 * Components should use this to skip decorative motion (staggers, reveals, fills)
 * and show the end state immediately. Functional feedback (ripples etc.) stays
 * untouched since it's handled by the framework.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        val resolver = context.contentResolver
        Settings.Global.getFloat(
            resolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f &&
            Settings.Global.getFloat(
                resolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1f,
            ) == 0f
    }
}
