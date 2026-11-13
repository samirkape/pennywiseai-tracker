package com.spendly.tracker.ui.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.spendly.tracker.ui.theme.Dimensions
import kotlinx.coroutines.delay

/**
 * Time gap in milliseconds between the intro of successive items.
 */
private const val STAGGER_STEP_MS = 60

/**
 * One-shot intro for list rows: fade + slight upward slide, staggered per [index].
 *
 * Intended for short, fully-visible lists (e.g. the Home grouped feed card) where
 * all rows compose together — each row waits `[index] * STAGGER_STEP_MS` before
 * animating in. Rows retain their final state afterwards, so repeated scrolling
 * never re-triggers the effect.
 *
 * Pass [enabled] = false to render rows immediately and skip the motion — call
 * sites combine this with `rememberReducedMotion()` so users who disable system
 * animations never see the stagger.
 */
fun Modifier.staggeredIntro(
    index: Int,
    maxStaggerItems: Int = 8,
    enabled: Boolean = true,
): Modifier =
    if (!enabled) {
        Modifier
    } else {
        composed {
            val staggerIndex = index.coerceAtMost(maxStaggerItems - 1)
            val slidePx = with(LocalDensity.current) { 12.dp.toPx() }
            val fraction = remember { Animatable(0f) }
            LaunchedEffect(fraction) {
                if (staggerIndex > 0) {
                    delay(staggerIndex * STAGGER_STEP_MS.toLong())
                }
                fraction.animateTo(
                    1f,
                    tween(
                        durationMillis = Dimensions.Animation.standard,
                        easing = Dimensions.Easing.decelerate,
                    ),
                )
            }
            graphicsLayer {
                alpha = fraction.value
                translationY = (1f - fraction.value) * slidePx
            }
        }
    }
