package com.spendly.tracker.presentation.home

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spendly.tracker.R
import com.spendly.tracker.ui.theme.Dimensions
import com.spendly.tracker.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeActionsSheet(
    visible: Boolean,
    isScanning: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onSearch: () -> Unit,
    onBudgets: () -> Unit,
    onAnalytics: () -> Unit,
    onFullResync: () -> Unit,
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.Padding.content)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = stringResource(R.string.home_actions_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )

            HomeActionRow(
                icon = Icons.Default.Sync,
                title = stringResource(R.string.home_action_refresh),
                subtitle = stringResource(R.string.home_action_refresh_subtitle),
                showProgress = isScanning,
                onClick = {
                    onDismiss()
                    onRefresh()
                },
            )
            HomeActionRow(
                icon = Icons.Default.Add,
                title = stringResource(R.string.home_action_add),
                subtitle = null,
                onClick = {
                    onDismiss()
                    onAdd()
                },
            )
            HomeActionRow(
                icon = Icons.Default.Search,
                title = stringResource(R.string.home_action_search),
                subtitle = null,
                onClick = {
                    onDismiss()
                    onSearch()
                },
            )
            HomeActionRow(
                icon = Icons.Outlined.Savings,
                title = stringResource(R.string.home_action_budgets),
                subtitle = null,
                onClick = {
                    onDismiss()
                    onBudgets()
                },
            )
            HomeActionRow(
                icon = Icons.Default.BarChart,
                title = stringResource(R.string.home_action_analytics),
                subtitle = null,
                onClick = {
                    onDismiss()
                    onAnalytics()
                },
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = Spacing.sm),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )

            HomeActionRow(
                icon = Icons.Default.Sync,
                title = stringResource(R.string.home_action_full_resync),
                subtitle = stringResource(R.string.home_action_full_resync_subtitle),
                onClick = {
                    onDismiss()
                    onFullResync()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    showProgress: Boolean = false,
) {
    val view = LocalView.current
    Surface(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onClick()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (showProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
