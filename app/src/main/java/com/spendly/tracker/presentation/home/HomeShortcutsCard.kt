package com.spendly.tracker.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendly.tracker.ui.components.cards.SpendlyCardV2
import com.spendly.tracker.ui.theme.Spacing
import com.spendly.tracker.ui.theme.spendGreen
import com.spendly.tracker.ui.theme.spendGreenBg
import com.spendly.tracker.ui.theme.spendPurple
import com.spendly.tracker.ui.theme.spendPurpleBg

@Composable
fun HomeShortcutsCard(
    prepaidCount: Int,
    loanCount: Int,
    groupCount: Int,
    onPrepaidExpenses: () -> Unit,
    onLoans: () -> Unit,
    onTransactionGroups: () -> Unit,
    modifier: Modifier = Modifier,
    onNetWorth: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ShortcutTile(
            icon = Icons.Outlined.Receipt,
            tint = scheme.spendGreen,
            tintBg = scheme.spendGreenBg,
            label = "PREPAID",
            value = prepaidCount.toString(),
            caption = if (prepaidCount == 1) "active plan" else "active plans",
            onClick = onPrepaidExpenses,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        ShortcutTile(
            icon = Icons.Outlined.SwapHoriz,
            tint = scheme.spendPurple,
            tintBg = scheme.spendPurpleBg,
            label = "LOANS",
            value = loanCount.toString(),
            caption = "open",
            onClick = onLoans,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        ShortcutTile(
            icon = Icons.Outlined.Folder,
            tint = scheme.primary,
            tintBg = scheme.primaryContainer.copy(alpha = 0.35f),
            label = "GROUPS",
            value = groupCount.toString(),
            caption = if (groupCount == 1) "group" else "groups",
            onClick = onTransactionGroups,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
    if (onNetWorth != null) {
        ShortcutTile(
            icon = Icons.Outlined.AccountBalance,
            tint = scheme.spendGreen,
            tintBg = scheme.spendGreenBg,
            label = "NET WORTH",
            value = "Assets & liabilities",
            caption = "cash, gold, FDs, investments",
            onClick = onNetWorth,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    }
}

@Composable
private fun ShortcutTile(
    icon: ImageVector,
    tint: Color,
    tintBg: Color,
    label: String,
    value: String,
    caption: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SpendlyCardV2(
        modifier = modifier,
        onClick = onClick,
        contentPadding = Spacing.md,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(tintBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                letterSpacing = 0.66.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
