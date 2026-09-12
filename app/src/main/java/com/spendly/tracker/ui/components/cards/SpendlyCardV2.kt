package com.spendly.tracker.ui.components.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spendly.tracker.ui.theme.Spacing

@Composable
fun SpendlyCardV2(
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = MaterialTheme.shapes.large,
    colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    border: BorderStroke? = BorderStroke(0.dp, Color.Transparent),
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = Spacing.md,
    content: @Composable ColumnScope.() -> Unit
) {
    if (onClick != null) {
        Card(
            modifier = modifier,
            onClick = onClick,
            colors = colors,
            shape = shape,
            elevation = elevation,
            border = border,
        ) {
            Column(
                modifier = Modifier.padding(contentPadding)
            ) {
                content()
            }
        }
    } else {
        Card(
            modifier = modifier,
            colors = colors,
            shape = shape,
            elevation = elevation,
            border = border,
        ) {
            Column(
                modifier = Modifier.padding(contentPadding)
            ) {
                content()
            }
        }
    }
}
