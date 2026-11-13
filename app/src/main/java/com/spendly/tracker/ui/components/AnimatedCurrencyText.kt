package com.spendly.tracker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.spendly.tracker.ui.effects.rememberReducedMotion
import com.spendly.tracker.ui.theme.Dimensions

@Composable
fun AnimatedCurrencyText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    brush: Brush? = null,
) = AnimatedCurrencyText(
    text = AnnotatedString(text),
    style = style,
    modifier = modifier,
    color = color,
    fontWeight = fontWeight,
    brush = brush,
)

@Composable
fun AnimatedCurrencyText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    brush: Brush? = null,
) {
    // Respect system "remove animations": render the value directly.
    if (rememberReducedMotion()) {
        Text(
            text = text,
            style = style.copy(brush = brush),
            color = color,
            fontWeight = fontWeight,
        )
        return
    }
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            slideInVertically(
                animationSpec = tween(Dimensions.Animation.medium),
                initialOffsetY = { it }
            ) togetherWith slideOutVertically(
                animationSpec = tween(Dimensions.Animation.medium),
                targetOffsetY = { -it }
            )
        },
        label = "currencyAnimation",
        modifier = modifier
    ) { targetText ->
        if (brush != null) {
            Text(
                text = targetText,
                style = style.copy(brush = brush),
                fontWeight = fontWeight,
            )
        } else {
            Text(
                text = targetText,
                style = style,
                color = color,
                fontWeight = fontWeight,
            )
        }
    }
}
