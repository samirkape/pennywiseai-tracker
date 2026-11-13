package com.spendly.tracker.ui.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spendly.tracker.ui.theme.Dimensions

/**
 * Success feedback: circle ring sweeps in, then the check is drawn on top.
 * Drawn natively (no Lottie dependency). Plays once; skips straight to the
 * final state when the user has system animations disabled.
 */
@Composable
fun AnimatedCheckmark(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    val reducedMotion = rememberReducedMotion()
    val progress = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            1f,
            tween(
                durationMillis = Dimensions.Animation.slow,
                easing = Dimensions.Easing.decelerate,
            ),
        )
    }
    val strokePx = with(LocalDensity.current) { 2.5.dp.toPx() }

    Canvas(modifier = modifier.size(size)) {
        val w = size.toPx()
        val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)

        // Ring: finishes in the first 60% of the animation.
        val ringFraction = (progress.value / 0.6f).coerceIn(0f, 1f)
        drawArc(
            color = tint,
            startAngle = -90f,
            sweepAngle = 360f * ringFraction,
            useCenter = false,
            style = stroke,
        )

        // Check: right side of the animation; the vertex is drawn by both
        // segments so the joint stays clean mid-draw.
        val checkFraction = ((progress.value - 0.6f) / 0.4f).coerceIn(0f, 1f)
        if (checkFraction > 0f) {
            val start = Offset(w * 0.28f, w * 0.53f)
            val vertex = Offset(w * 0.44f, w * 0.72f)
            val end = Offset(w * 0.74f, w * 0.32f)

            val firstFraction = (checkFraction / 0.45f).coerceIn(0f, 1f)
            drawLine(
                color = tint,
                start = start,
                end = Offset(
                    start.x + (vertex.x - start.x) * firstFraction,
                    start.y + (vertex.y - start.y) * firstFraction,
                ),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
            val secondFraction = ((checkFraction - 0.45f) / 0.55f).coerceIn(0f, 1f)
            if (secondFraction > 0f) {
                drawLine(
                    color = tint,
                    start = vertex,
                    end = Offset(
                        vertex.x + (end.x - vertex.x) * secondFraction,
                        vertex.y + (end.y - vertex.y) * secondFraction,
                    ),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
