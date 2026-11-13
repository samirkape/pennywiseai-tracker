package com.spendly.tracker.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Provides [SharedTransitionScope] from the root [SharedTransitionLayout]
 * to deeply nested composables without threading through every function signature.
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * Provides the [AnimatedVisibilityScope] of the current navigation destination
 * so shared element modifiers can reference it from nested composables.
 */
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Applies [sharedElement] with the current scopes when both are available
 * (inside [SharedTransitionLayout] + a nav destination that provides
 * [LocalNavAnimatedVisibilityScope]); otherwise acts as a no-op so call sites
 * stay safe outside the transition system.
 */
@androidx.compose.runtime.Composable
fun Modifier.sharedElementIfAvailable(key: String): Modifier {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    return if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            this@sharedElementIfAvailable.sharedElement(
                sharedTransitionScope.rememberSharedContentState(key = key),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    } else {
        this
    }
}
