package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.icons.BrandIcons
import com.pennywiseai.tracker.ui.icons.CategoryIcons
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.icons.IconProvider
import com.pennywiseai.tracker.ui.icons.IconResource

/**
 * Displays a brand icon with intelligent fallback
 */
@Composable
fun BrandIcon(
    merchantName: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    showBackground: Boolean = true,
    categoryOverride: String? = null,
    iconKey: String? = null,
) {
    val iconResource = IconProvider.getIconForMerchant(
        merchantName = merchantName,
        categoryOverride = categoryOverride,
    )

    // If brand icon not found and custom category icon is provided, use it
    val finalIconResource = if (iconResource is IconResource.VectorIcon && iconKey != null && categoryOverride != null) {
        // Check if this is using the default category fallback (not a brand icon)
        // If so, use the custom category icon from database
        val resolvedIcon = CategoryIcons.getIcon(CategoryIcons.resolveKey(categoryOverride, iconKey))
        val categoryInfo = CategoryMapping.categories[categoryOverride]
            ?: CategoryMapping.categories["Others"]!!
        IconResource.VectorIcon(
            icon = resolvedIcon,
            tint = categoryInfo.color
        )
    } else {
        iconResource
    }
    val brandColor = BrandIcons.getBrandColor(merchantName)

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (showBackground) {
                    Modifier
                        .clip(CircleShape)
                        .background(
                            brandColor?.let { Color(it.toColorInt()) }
                                ?: MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(8.dp)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        when (finalIconResource) {
            is IconResource.DrawableResource -> {
                // Brand logo from drawable
                Image(
                    painter = painterResource(id = finalIconResource.resId),
                    contentDescription = merchantName,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is IconResource.VectorIcon -> {
                // Category icon fallback
                // Use white tint only when a brand color provides a dark background;
                // when falling back to surfaceVariant (light in light mode), use the
                // icon's own tint so it remains visible.
                Icon(
                    imageVector = finalIconResource.icon,
                    contentDescription = merchantName,
                    tint = if (showBackground && brandColor != null) Color.White else finalIconResource.tint,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Letter avatar for merchants without icons
 */
@Composable
fun LetterAvatar(
    merchantName: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val letter = merchantName.firstOrNull()?.uppercase() ?: "?"
    val backgroundColor = BrandIcons.getBrandColor(merchantName)?.let { 
        Color(it.toColorInt()) 
    } ?: generateColorFromString(merchantName)
    
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * Category icon with consistent styling.
 * Pass [iconKey] when the stored category icon is known.
 */
@Composable
fun CategoryIcon(
    category: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color? = null,
    iconKey: String? = null,
) {
    val categoryInfo = CategoryMapping.categories[category]
        ?: CategoryMapping.categories["Others"]!!
    val resolvedIcon = CategoryIcons.getIcon(CategoryIcons.resolveKey(category, iconKey))

    Icon(
        imageVector = resolvedIcon,
        contentDescription = category,
        tint = tint ?: categoryInfo.color,
        modifier = modifier.size(size)
    )
}

/**
 * Utility function to generate consistent colors from strings
 */
private fun generateColorFromString(str: String): Color {
    val colors = listOf(
        Color(0xFF6750A4), // Material Purple
        Color(0xFF0061A4), // Material Blue
        Color(0xFF006D40), // Material Green
        Color(0xFFB3261E), // Material Red
        Color(0xFF9A4521), // Material Orange
        Color(0xFF6D4C41), // Material Brown
        Color(0xFF455A64), // Material Blue Grey
        Color(0xFF5E35B1), // Deep Purple
        Color(0xFF43A047), // Green
        Color(0xFFE53935), // Red
    )
    
    val hash = str.hashCode()
    return colors[Math.abs(hash) % colors.size]
}

/**
 * Extension to convert hex string to Color Int
 */
private fun String.toColorInt(): Int {
    // Remove # if present and parse hex
    val hex = this.removePrefix("#")
    return android.graphics.Color.parseColor("#$hex")
}