package com.pennywiseai.tracker.ui.screens.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import kotlinx.coroutines.delay
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransferKind
import com.pennywiseai.tracker.domain.model.QuickKeywordApplyScope
import com.pennywiseai.tracker.domain.model.QuickKeywordExpenseChannel
import com.pennywiseai.tracker.domain.model.QuickKeywordMatchField
import com.pennywiseai.tracker.domain.model.QuickKeywordTextMatchMode
import com.pennywiseai.tracker.ui.components.QuickKeywordCategoryPicker
import com.pennywiseai.tracker.ui.components.QuickKeywordMatchFieldChips
import com.pennywiseai.tracker.ui.components.QuickKeywordTagsPicker
import com.pennywiseai.tracker.ui.components.QuickKeywordTextMatchModeSelector
import com.pennywiseai.tracker.ui.components.TransactionTypeFilterChips
import com.pennywiseai.tracker.domain.service.QuickKeywordRuleCompiler
import com.pennywiseai.tracker.domain.service.QuickKeywordRuleMatcher
import com.pennywiseai.tracker.domain.usecase.BatchApplyResult
import com.pennywiseai.tracker.ui.components.PennyWiseCard
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.viewmodel.QuickKeywordRulesViewModel
import com.pennywiseai.tracker.ui.viewmodel.SaveQuickRuleOutcome

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditQuickKeywordRuleScreen(
    ruleId: String?,
    onNavigateBack: () -> Unit,
    viewModel: QuickKeywordRulesViewModel = hiltViewModel(),
) {
    val quickRules by viewModel.quickRules.collectAsStateWithLifecycle()
    val applyState by viewModel.applyState.collectAsStateWithLifecycle()
    val batchPreview by viewModel.batchPreview.collectAsStateWithLifecycle()
    val undoSession by viewModel.undoSession.collectAsStateWithLifecycle()
    val categoryEntities by viewModel.categoryEntities.collectAsStateWithLifecycle()
    val usedCategoryNames by viewModel.usedCategoryNames.collectAsStateWithLifecycle()
    val usedTags by viewModel.usedTags.collectAsStateWithLifecycle()
    val liveMatchStats by viewModel.liveMatchStats.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val existingRule = ruleId?.let { id -> quickRules.firstOrNull { it.id == id } }
    val existingInput = existingRule?.let { QuickKeywordRuleCompiler.decompile(it) }
    val isNewRule = existingRule == null

    var name by remember { mutableStateOf("") }
    var keywordsText by remember { mutableStateOf("") }
    var textMatchMode by remember { mutableStateOf(QuickKeywordTextMatchMode.DEFAULT) }
    var matchField by remember { mutableStateOf(QuickKeywordMatchField.DEFAULT) }
    var merchantLabel by remember { mutableStateOf("") }
    var categoryLabel by remember { mutableStateOf("") }
    var pendingTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var overwriteTags by remember { mutableStateOf(false) }
    var matchType by remember { mutableStateOf<TransactionType?>(null) }
    var matchExpenseChannel by remember { mutableStateOf<QuickKeywordExpenseChannel?>(null) }
    var matchTransferKind by remember { mutableStateOf<String?>(null) }
    var syncNameWithLabel by remember { mutableStateOf(true) }
    var runOnPastWhenSaved by remember { mutableStateOf(false) }
    var applyUncategorizedOnly by remember { mutableStateOf(false) }
    var overwriteMerchant by remember { mutableStateOf(true) }
    var overwriteCategory by remember { mutableStateOf(true) }
    var overwriteTransactionType by remember { mutableStateOf(false) }
    var forceOverwriteExisting by remember { mutableStateOf(false) }
    var isActive by remember { mutableStateOf(true) }
    var nameManuallyEdited by remember { mutableStateOf(false) }
    var showValidationError by remember { mutableStateOf(false) }
    var showApplyResult by remember {
        mutableStateOf<Pair<BatchApplyResult, QuickKeywordRuleMatcher.BatchStats?>?>(null)
    }
    var isSaving by remember { mutableStateOf(false) }
    var showApplyScopeDialog by remember { mutableStateOf(false) }
    var pendingForceRun by remember { mutableStateOf(false) }
    var pendingRunOnly by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshUndoSession()
    }

    LaunchedEffect(applyState.result, applyState.diagnostics, isSaving, undoSession) {
        val result = applyState.result
        if (result != null && !isSaving) {
            showApplyResult = result to applyState.diagnostics
            val session = undoSession
            if (session != null && result.totalUpdated > 0) {
                scope.launch {
                    val snackResult = snackbarHostState.showSnackbar(
                        message = context.getString(
                            R.string.quick_keyword_undo_snackbar,
                            result.totalUpdated,
                            session.remainingMinutes(),
                        ),
                        actionLabel = context.getString(R.string.quick_keyword_undo_action),
                        duration = SnackbarDuration.Long,
                    )
                    if (snackResult == SnackbarResult.ActionPerformed) {
                        viewModel.undoLastBatchApply { undone ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(
                                        if (undone) R.string.quick_keyword_undo_success
                                        else R.string.quick_keyword_undo_expired,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(existingInput) {
        if (existingInput != null) {
            name = existingInput.name
            keywordsText = existingInput.keywords.joinToString("\n")
            textMatchMode = existingInput.textMatchMode
            matchField = existingInput.matchField
            merchantLabel = existingInput.merchantLabel
            categoryLabel = existingInput.categoryLabel
            pendingTags = existingInput.tags
            overwriteTags = existingInput.overwriteTags
            matchType = existingInput.matchType
            matchExpenseChannel = existingInput.matchExpenseChannel
            matchTransferKind = existingInput.matchTransferKind
            syncNameWithLabel = existingInput.syncNameWithLabel
            runOnPastWhenSaved = existingInput.runOnPastWhenSaved
            applyUncategorizedOnly = existingInput.applyUncategorizedOnly
            overwriteMerchant = existingInput.overwriteMerchant
            overwriteCategory = existingInput.overwriteCategory
            overwriteTransactionType = existingInput.overwriteTransactionType
            forceOverwriteExisting = existingInput.forceOverwriteExisting
            isActive = existingInput.isActive
            nameManuallyEdited = !existingInput.syncNameWithLabel
        }
    }

    fun buildInput() = QuickKeywordRuleCompiler.QuickKeywordRuleInput(
        name = name,
        keywords = QuickKeywordRuleCompiler.parseKeywords(keywordsText),
        textMatchMode = textMatchMode,
        matchField = matchField,
        merchantLabel = merchantLabel,
        categoryLabel = categoryLabel,
        tags = pendingTags,
        overwriteTags = overwriteTags,
        matchType = matchType,
        matchExpenseChannel = matchExpenseChannel,
        matchTransferKind = matchTransferKind,
        syncNameWithLabel = syncNameWithLabel,
        runOnPastWhenSaved = runOnPastWhenSaved,
        applyUncategorizedOnly = applyUncategorizedOnly,
        overwriteMerchant = overwriteMerchant,
        overwriteCategory = overwriteCategory,
        overwriteTransactionType = overwriteTransactionType,
        forceOverwriteExisting = forceOverwriteExisting,
        isActive = isActive,
    )

    fun handleSaveOutcome(outcome: SaveQuickRuleOutcome) {
        if (!outcome.success) {
            showValidationError = true
            return
        }
        if (outcome.awaitingBatchConfirm) {
            return
        }
        val batch = outcome.batchApply
        if (batch != null) {
            showApplyResult = batch to outcome.diagnostics
        } else {
            onNavigateBack()
        }
    }

    fun saveWithScope(forceRunNow: Boolean, applyScope: QuickKeywordApplyScope) {
        val input = buildInput()
        isSaving = true
        viewModel.saveQuickRule(
            input = input,
            existingRule = existingRule,
            forceRunNow = forceRunNow,
            applyScope = applyScope,
        ) { outcome ->
            isSaving = false
            handleSaveOutcome(outcome)
        }
    }

    LaunchedEffect(
        keywordsText,
        textMatchMode,
        matchField,
        matchType,
        matchExpenseChannel,
        matchTransferKind,
        applyUncategorizedOnly,
        overwriteMerchant,
        overwriteCategory,
        overwriteTransactionType,
        forceOverwriteExisting,
        merchantLabel,
        categoryLabel,
        pendingTags,
        overwriteTags,
    ) {
        delay(400)
        viewModel.refreshLiveMatchStats(buildInput())
    }

    fun save(forceRunNow: Boolean) {
        val input = buildInput()
        if (!input.validate()) {
            showValidationError = true
            return
        }
        if (forceRunNow || input.runOnPastWhenSaved) {
            pendingForceRun = forceRunNow
            pendingRunOnly = false
            showApplyScopeDialog = true
            return
        }
        isSaving = true
        viewModel.saveQuickRule(
            input = input,
            existingRule = existingRule,
            forceRunNow = false,
        ) { outcome ->
            isSaving = false
            handleSaveOutcome(outcome)
        }
    }

    if (showApplyScopeDialog) {
        val dialogMessage = when {
            pendingRunOnly && name.isNotBlank() ->
                stringResource(R.string.quick_keyword_apply_message, name)
            pendingRunOnly ->
                stringResource(R.string.quick_keyword_apply_message_generic)
            name.isNotBlank() ->
                stringResource(R.string.quick_keyword_apply_on_save_message, name)
            else ->
                stringResource(R.string.quick_keyword_apply_on_save_message_generic)
        }
        QuickKeywordApplyScopeDialog(
            title = stringResource(R.string.quick_keyword_apply_title),
            message = dialogMessage,
            uncategorizedOnly = applyUncategorizedOnly,
            onUncategorizedOnlyChange = { applyUncategorizedOnly = it },
            onDismiss = {
                showApplyScopeDialog = false
                pendingForceRun = false
                pendingRunOnly = false
            },
            onConfirm = { scope ->
                showApplyScopeDialog = false
                if (pendingRunOnly) {
                    val input = buildInput()
                    if (input.validate()) {
                        viewModel.prepareBatchApplyFromInput(
                            ruleName = name.ifBlank { existingRule?.name ?: "Keyword rule" },
                            input = input,
                            applyScope = scope,
                        )
                    } else {
                        showValidationError = true
                    }
                    pendingRunOnly = false
                } else {
                    saveWithScope(forceRunNow = pendingForceRun || buildInput().runOnPastWhenSaved, applyScope = scope)
                    pendingForceRun = false
                }
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
                                context.getString(
                                    if (undone) R.string.quick_keyword_undo_success
                                    else R.string.quick_keyword_undo_expired,
                                ),
                            )
                        }
                        if (undone) {
                            showApplyResult = null
                            viewModel.clearApplyState()
                            onNavigateBack()
                        }
                    }
                }
            } else {
                null
            },
            onDismiss = {
                showApplyResult = null
                viewModel.clearApplyState()
                onNavigateBack()
            },
        )
    }

    val isRunningApply = applyState.isRunning || applyState.isPreparingPreview || isSaving

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isNewRule) {
                            stringResource(R.string.quick_keyword_add_title)
                        } else {
                            stringResource(R.string.quick_keyword_edit_title)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, enabled = !isRunningApply) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.padding(Spacing.sm))
                    } else {
                        TextButton(onClick = { save(forceRunNow = false) }) {
                            Text(stringResource(R.string.quick_keyword_save))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (applyState.isPreparingPreview || applyState.isRunning) {
                if (applyState.isPreparingPreview) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = stringResource(R.string.quick_keyword_batch_preparing),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                val progress = applyState.progress
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = {
                            if (progress.second > 0) {
                                progress.first.toFloat() / progress.second
                            } else 0f
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(
                            R.string.quick_keyword_run_progress,
                            progress.first,
                            progress.second,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    nameManuallyEdited = true
                },
                label = { Text(stringResource(R.string.quick_keyword_rule_name)) },
                placeholder = { Text(stringResource(R.string.quick_keyword_rule_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isRunningApply,
            )

            PennyWiseCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(
                        text = stringResource(R.string.quick_keyword_section_when_matches),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    OutlinedTextField(
                        value = keywordsText,
                        onValueChange = { keywordsText = it },
                        label = { Text(stringResource(R.string.quick_keyword_keywords_label)) },
                        placeholder = { Text(stringResource(R.string.quick_keyword_keywords_hint)) },
                        supportingText = {
                            Text(stringResource(R.string.quick_keyword_keywords_supporting))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        enabled = !isRunningApply,
                    )
                    QuickKeywordTextMatchModeSelector(
                        selected = textMatchMode,
                        onSelected = { textMatchMode = it },
                        enabled = !isRunningApply,
                    )
                    QuickKeywordMatchFieldChips(
                        selected = matchField,
                        onSelected = { matchField = it },
                        enabled = !isRunningApply,
                    )
                    TransactionTypeFilterChips(
                        matchType = matchType,
                        matchExpenseChannel = matchExpenseChannel,
                        matchTransferKind = matchTransferKind,
                        onMatchTypeChange = { matchType = it },
                        onExpenseChannelChange = { matchExpenseChannel = it },
                        onTransferKindChange = { matchTransferKind = it },
                        enabled = !isRunningApply,
                        showAnyType = true,
                        title = stringResource(R.string.quick_keyword_type_filter_title),
                    )
                    if (matchExpenseChannel == QuickKeywordExpenseChannel.ACCOUNT) {
                        Text(
                            text = stringResource(R.string.quick_keyword_type_account_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    liveMatchStats?.let { stats ->
                        Text(
                            text = stringResource(
                                R.string.quick_keyword_live_match_stats,
                                stats.keywordMatched,
                                stats.poolSize,
                                stats.wouldUpdate,
                                stats.typeRejected,
                                stats.noKeywordHit,
                                stats.alreadyLabeled,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (stats.keywordMatched == 0) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                        if (stats.keywordMatched == 0 && stats.rejectionSamples.isNotEmpty()) {
                            Text(
                                text = stringResource(
                                    R.string.quick_keyword_live_match_debug_hint,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            PennyWiseCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(
                        text = stringResource(R.string.quick_keyword_section_set_on_match),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = stringResource(R.string.quick_keyword_section_set_on_match_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        FilterChip(
                            selected = overwriteMerchant,
                            onClick = { overwriteMerchant = !overwriteMerchant },
                            label = { Text(stringResource(R.string.quick_keyword_set_merchant_chip)) },
                            enabled = !isRunningApply,
                        )
                        FilterChip(
                            selected = overwriteCategory,
                            onClick = { overwriteCategory = !overwriteCategory },
                            label = { Text(stringResource(R.string.quick_keyword_set_category_chip)) },
                            enabled = !isRunningApply,
                        )
                        FilterChip(
                            selected = overwriteTransactionType,
                            onClick = {
                                overwriteTransactionType = !overwriteTransactionType
                            },
                            label = { Text(stringResource(R.string.quick_keyword_set_type_chip)) },
                            enabled = !isRunningApply && matchType != null,
                        )
                        FilterChip(
                            selected = overwriteTags,
                            onClick = { overwriteTags = !overwriteTags },
                            label = { Text(stringResource(R.string.quick_keyword_set_tags_chip)) },
                            enabled = !isRunningApply,
                        )
                    }
                    if (overwriteMerchant) {
                        OutlinedTextField(
                            value = merchantLabel,
                            onValueChange = { new ->
                                merchantLabel = new
                                if (syncNameWithLabel && !nameManuallyEdited) {
                                    name = new
                                }
                            },
                            label = { Text(stringResource(R.string.quick_keyword_merchant_label)) },
                            placeholder = { Text(stringResource(R.string.quick_keyword_merchant_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isRunningApply,
                        )
                    }
                    if (overwriteCategory) {
                        QuickKeywordCategoryPicker(
                            categoryLabel = categoryLabel,
                            onCategorySelected = { categoryLabel = it },
                            categoryEntities = categoryEntities,
                            usedCategoryNames = usedCategoryNames,
                            enabled = !isRunningApply,
                        )
                    }
                    if (overwriteTags) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                        QuickKeywordTagsPicker(
                            pendingTags = pendingTags,
                            onAddTag = { tag ->
                                val trimmed = tag.trim()
                                if (trimmed.isNotBlank() && trimmed !in pendingTags) {
                                    pendingTags = pendingTags + trimmed
                                }
                            },
                            onRemoveTag = { tag -> pendingTags = pendingTags - tag },
                            usedTags = usedTags,
                            enabled = !isRunningApply,
                        )
                    }
                    QuickKeywordToggleRow(
                        title = stringResource(R.string.quick_keyword_overwrite_force),
                        subtitle = stringResource(R.string.quick_keyword_overwrite_force_hint),
                        checked = forceOverwriteExisting,
                        onCheckedChange = { forceOverwriteExisting = it },
                        enabled = !isRunningApply,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.quick_keyword_rule_sync_name),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = stringResource(R.string.quick_keyword_rule_sync_name_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = syncNameWithLabel,
                            onCheckedChange = {
                                syncNameWithLabel = it
                                if (it && !nameManuallyEdited) {
                                    name = merchantLabel
                                }
                            },
                            enabled = !isRunningApply,
                        )
                    }
                }
            }

            PennyWiseCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        text = stringResource(R.string.quick_keyword_rule_behavior_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.quick_keyword_active_title),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Switch(
                            checked = isActive,
                            onCheckedChange = { isActive = it },
                            enabled = !isRunningApply,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.quick_keyword_rule_run_on_save),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = stringResource(R.string.quick_keyword_rule_run_on_save_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = runOnPastWhenSaved,
                            onCheckedChange = { runOnPastWhenSaved = it },
                            enabled = !isRunningApply && isActive,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.quick_keyword_apply_uncategorized_only),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = stringResource(R.string.quick_keyword_apply_uncategorized_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = applyUncategorizedOnly,
                            onCheckedChange = { applyUncategorizedOnly = it },
                            enabled = !isRunningApply,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Button(
                    onClick = { save(forceRunNow = true) },
                    modifier = Modifier.weight(1f),
                    enabled = !isRunningApply && isActive,
                ) {
                    Text(stringResource(R.string.quick_keyword_save_and_run))
                }
                if (!isNewRule && existingRule != null) {
                    OutlinedButton(
                        onClick = {
                            val input = buildInput()
                            if (input.validate()) {
                                pendingRunOnly = true
                                showApplyScopeDialog = true
                            } else {
                                showValidationError = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isRunningApply && existingRule.isActive,
                    ) {
                        Text(stringResource(R.string.quick_keyword_run_now_button))
                    }
                }
            }

            if (showValidationError) {
                Text(
                    text = stringResource(R.string.quick_keyword_validation_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun QuickKeywordToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}
