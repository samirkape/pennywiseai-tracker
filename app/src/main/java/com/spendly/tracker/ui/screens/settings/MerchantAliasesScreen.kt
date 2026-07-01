package com.spendly.tracker.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendly.tracker.R
import com.spendly.tracker.ui.components.PennyWiseEmptyState
import com.spendly.tracker.ui.components.PennyWiseScaffold
import com.spendly.tracker.ui.components.cards.PennyWiseCardV2
import com.spendly.tracker.ui.theme.Dimensions
import com.spendly.tracker.ui.theme.Spacing
import com.spendly.tracker.utils.MerchantAliasAuditor

private data class MerchantAliasEditTarget(
    val originalSource: String?,
    val sourceMerchant: String,
    val displayName: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantAliasesScreen(
    onNavigateBack: () -> Unit,
    viewModel: MerchantAliasesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var editTarget by remember { mutableStateOf<MerchantAliasEditTarget?>(null) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    PennyWiseScaffold(
        title = "Merchant name mappings",
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editTarget = MerchantAliasEditTarget(
                        originalSource = null,
                        sourceMerchant = "",
                        displayName = "",
                    )
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.merchant_alias_add_cd))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@PennyWiseScaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = Dimensions.Padding.content,
                bottom = Dimensions.Padding.content + 72.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                PennyWiseCardV2(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            text = "How this works",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "When you rename a transaction merchant, Spendly remembers the SMS label " +
                                "→ your name. Only exact matches apply to new SMS. Tap a row to edit, or use + " +
                                "to add manually.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (uiState.suspiciousCount > 0) {
                            Text(
                                text = "${uiState.suspiciousCount} mapping(s) need review",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            if (uiState.items.isEmpty()) {
                item {
                    PennyWiseEmptyState(
                        icon = Icons.Default.Warning,
                        headline = "No mappings yet",
                        description = "Rename a merchant on a transaction, or tap + to add one",
                    )
                }
            } else {
                items(uiState.items, key = { it.alias.sourceMerchant }) { result ->
                    MerchantAliasRow(
                        result = result,
                        onEdit = {
                            editTarget = MerchantAliasEditTarget(
                                originalSource = result.alias.sourceMerchant,
                                sourceMerchant = result.alias.sourceMerchant,
                                displayName = result.alias.displayName,
                            )
                        },
                        onDelete = { pendingDelete = result.alias.sourceMerchant },
                    )
                }
            }
        }
    }

    editTarget?.let { target ->
        MerchantAliasEditDialog(
            target = target,
            onDismiss = { editTarget = null },
            onSave = { source, display ->
                if (target.originalSource == null) {
                    viewModel.addAlias(source, display)
                } else {
                    viewModel.updateAlias(target.originalSource, source, display)
                }
                editTarget = null
            },
        )
    }

    pendingDelete?.let { source ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove mapping?") },
            text = {
                Text("Future SMS with \"$source\" will no longer auto-rename to a saved display name.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAlias(source)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun MerchantAliasEditDialog(
    target: MerchantAliasEditTarget,
    onDismiss: () -> Unit,
    onSave: (source: String, display: String) -> Unit,
) {
    var sourceText by remember(target) { mutableStateOf(target.sourceMerchant) }
    var displayText by remember(target) { mutableStateOf(target.displayName) }
    val isNew = target.originalSource == null
    val canSave = sourceText.isNotBlank() && displayText.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (isNew) R.string.merchant_alias_add_title else R.string.merchant_alias_edit_title,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = sourceText,
                    onValueChange = { sourceText = it },
                    label = { Text(stringResource(R.string.merchant_alias_source_label)) },
                    placeholder = { Text(stringResource(R.string.merchant_alias_source_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = displayText,
                    onValueChange = { displayText = it },
                    label = { Text(stringResource(R.string.merchant_alias_display_label)) },
                    placeholder = { Text(stringResource(R.string.merchant_alias_display_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(sourceText.trim(), displayText.trim()) },
                enabled = canSave,
            ) {
                Text(stringResource(R.string.merchant_alias_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun MerchantAliasRow(
    result: MerchantAliasAuditor.AuditResult,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val riskColor = when (result.risk) {
        MerchantAliasAuditor.RiskLevel.HIGH -> MaterialTheme.colorScheme.error
        MerchantAliasAuditor.RiskLevel.REVIEW -> MaterialTheme.colorScheme.tertiary
        MerchantAliasAuditor.RiskLevel.OK -> MaterialTheme.colorScheme.primary
    }
    val riskLabel = when (result.risk) {
        MerchantAliasAuditor.RiskLevel.HIGH -> "High risk"
        MerchantAliasAuditor.RiskLevel.REVIEW -> "Review"
        MerchantAliasAuditor.RiskLevel.OK -> "OK"
    }

    PennyWiseCardV2(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(riskLabel) },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = riskColor.copy(alpha = 0.15f),
                        disabledLabelColor = riskColor,
                    ),
                )
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.merchant_alias_edit_cd),
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove mapping",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Text(
                text = result.alias.sourceMerchant,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "→ ${result.alias.displayName}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (result.reasons.isNotEmpty()) {
                result.reasons.forEach { reason ->
                    Text(
                        text = "• $reason",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
