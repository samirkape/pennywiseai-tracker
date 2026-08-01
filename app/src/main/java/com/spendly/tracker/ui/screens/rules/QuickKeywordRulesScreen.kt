package com.spendly.tracker.ui.screens.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendly.tracker.R
import com.spendly.tracker.domain.model.rule.TransactionRule
import com.spendly.tracker.domain.service.QuickKeywordRuleCompiler
import com.spendly.tracker.domain.service.QuickKeywordRuleMatcher
import com.spendly.tracker.ui.components.CustomTitleTopAppBar
import com.spendly.tracker.ui.components.SpendlyCard
import com.spendly.tracker.ui.components.cards.SectionHeaderV2
import com.spendly.tracker.ui.effects.overScrollVertical
import com.spendly.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.spendly.tracker.ui.theme.Dimensions
import com.spendly.tracker.ui.theme.Spacing
import com.spendly.tracker.ui.viewmodel.QuickKeywordRulesViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.filled.PlayArrow
import com.spendly.tracker.domain.usecase.BatchApplyResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickKeywordRulesScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String?) -> Unit,
    viewModel: QuickKeywordRulesViewModel = hiltViewModel(),
) {
    val quickRules by viewModel.quickRules.collectAsStateWithLifecycle()
    val applyState by viewModel.applyState.collectAsStateWithLifecycle()
    val batchPreview by viewModel.batchPreview.collectAsStateWithLifecycle()
    val undoSession by viewModel.undoSession.collectAsStateWithLifecycle()
    var ruleToDelete by remember { mutableStateOf<TransactionRule?>(null) }
    var ruleToRun by remember { mutableStateOf<TransactionRule?>(null) }
    var applyUncategorizedOnly by remember { mutableStateOf(false) }
    var showApplyResult by remember {
        mutableStateOf<Pair<BatchApplyResult, com.spendly.tracker.domain.service.QuickKeywordRuleMatcher.BatchStats?>?>(null)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current

    val scrollBehaviorSmall = androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }
    val lazyListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.refreshUndoSession()
    }

    LaunchedEffect(applyState.result, applyState.diagnostics, undoSession) {
        applyState.result?.let { result ->
            showApplyResult = result to applyState.diagnostics
            val session = undoSession
            if (session != null && result.totalUpdated > 0) {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = resources.getString(
                            R.string.quick_keyword_undo_snackbar,
                            result.totalUpdated,
                            session.remainingMinutes(),
                        ),
                        actionLabel = resources.getString(R.string.quick_keyword_undo_action),
                        duration = androidx.compose.material3.SnackbarDuration.Long,
                    ).let { action ->
                        if (action == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                            viewModel.undoLastBatchApply { undone ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (undone) {
                                            R.string.quick_keyword_undo_success
                                        } else {
                                            R.string.quick_keyword_undo_expired
                                        }.let { resources.getString(it) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = stringResource(R.string.quick_keyword_rules_title),
                hasBackButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                hazeState = hazeState,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToEdit(null) },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.quick_keyword_add_title))
            }
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .background(MaterialTheme.colorScheme.background)
                .overScrollVertical(),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 80.dp,
            ),
            state = lazyListState,
            flingBehavior = rememberOverscrollFlingBehavior { lazyListState },
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (applyState.isPreparingPreview || applyState.isRunning) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = if (applyState.isPreparingPreview) {
                                stringResource(R.string.quick_keyword_batch_preparing)
                            } else {
                                applyState.progress?.let { (done, total) ->
                                    stringResource(R.string.quick_keyword_run_progress, done, total)
                                } ?: stringResource(R.string.quick_keyword_batch_confirm_applying)
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            item {
                SpendlyCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.quick_keyword_info_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = stringResource(R.string.quick_keyword_info_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }

            if (quickRules.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.quick_keyword_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.sm),
                    )
                }
            } else {
                item { SectionHeaderV2(title = stringResource(R.string.quick_keyword_section_active)) }
                items(quickRules, key = { it.id }) { rule ->
                    QuickKeywordRuleCard(
                        rule = rule,
                        onToggle = { viewModel.toggleRule(rule.id, it) },
                        onEdit = { onNavigateToEdit(rule.id) },
                        onDelete = { ruleToDelete = rule },
                        onApplyToPast = {
                            ruleToRun = rule
                            applyUncategorizedOnly = QuickKeywordRuleCompiler.decompile(rule)
                                ?.applyUncategorizedOnly
                                ?: false
                        },
                        onRunNow = {
                            ruleToRun = rule
                            applyUncategorizedOnly = QuickKeywordRuleCompiler.decompile(rule)
                                ?.applyUncategorizedOnly
                                ?: false
                        },
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.quick_keyword_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.sm),
                )
            }
        }
    }

    ruleToDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            title = { Text(stringResource(R.string.quick_keyword_delete_title)) },
            text = { Text(stringResource(R.string.quick_keyword_delete_message, rule.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRule(rule.id)
                        ruleToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.quick_keyword_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { ruleToDelete = null }) {
                    Text(stringResource(R.string.pay_period_cancel))
                }
            },
        )
    }

    ruleToRun?.let { rule ->
        QuickKeywordApplyScopeDialog(
            title = stringResource(R.string.quick_keyword_apply_title),
            message = stringResource(R.string.quick_keyword_apply_message, rule.name),
            uncategorizedOnly = applyUncategorizedOnly,
            onUncategorizedOnlyChange = { applyUncategorizedOnly = it },
            onDismiss = { ruleToRun = null },
            onConfirm = { scope ->
                viewModel.prepareBatchApplyFromRule(rule, applyUncategorizedOnly, scope)
                ruleToRun = null
            },
        )
    }

    batchPreview?.let { preview ->
        QuickKeywordBatchConfirmDialog(
            preview = preview,
            isApplying = applyState.isRunning,
            onDismiss = { viewModel.dismissBatchPreview() },
            onConfirm = { viewModel.confirmPendingBatchApply() },
        )
    }

    showApplyResult?.let { (batch, diagnostics) ->
        val undoMins = undoSession?.remainingMinutes()
        QuickKeywordApplyResultDialog(
            batch = batch,
            diagnostics = diagnostics,
            undoMinutesRemaining = undoMins,
            onUndo = if (undoMins != null && undoMins > 0) {
                {
                    viewModel.undoLastBatchApply { undone ->
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                resources.getString(
                                    if (undone) R.string.quick_keyword_undo_success
                                    else R.string.quick_keyword_undo_expired,
                                ),
                            )
                        }
                        if (undone) {
                            showApplyResult = null
                            viewModel.clearApplyState()
                        }
                    }
                }
            } else {
                null
            },
            onDismiss = {
                showApplyResult = null
                viewModel.clearApplyState()
            },
        )
    }
}

@Composable
private fun QuickKeywordRuleCard(
    rule: TransactionRule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onApplyToPast: () -> Unit,
    onRunNow: () -> Unit,
) {
    val input = QuickKeywordRuleCompiler.decompile(rule)
    var showMenu by remember { mutableStateOf(false) }

    SpendlyCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                input?.let {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.Icon.small),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(
                                R.string.quick_keyword_card_summary,
                                it.keywords.joinToString(", "),
                                it.merchantLabel,
                                it.categoryLabel,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (it.matchType != null) {
                        Text(
                            text = QuickKeywordRuleMatcher.matchTypeDescription(it),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (it.runOnPastWhenSaved) {
                        Text(
                            text = stringResource(R.string.quick_keyword_badge_run_on_save),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (it.overwriteTransactionType) {
                        Text(
                            text = stringResource(R.string.quick_keyword_badge_overwrite_type),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.quick_keyword_menu_edit)) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.quick_keyword_run_now_button)) },
                            leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onRunNow()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.quick_keyword_menu_apply)) },
                            leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onApplyToPast()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.quick_keyword_menu_delete)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                        )
                    }
                }
                Switch(checked = rule.isActive, onCheckedChange = onToggle)
            }
        }
    }
}
