package com.pennywiseai.tracker.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.repository.KeywordRuleBatchUndoRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.model.KeywordBatchUndoSession
import com.pennywiseai.tracker.domain.model.PendingKeywordBatchApply
import com.pennywiseai.tracker.domain.model.QuickKeywordApplyScope
import com.pennywiseai.tracker.domain.model.QuickKeywordBatchChange
import com.pennywiseai.tracker.domain.model.QuickKeywordBatchPreview
import com.pennywiseai.tracker.domain.model.rule.TransactionRule
import com.pennywiseai.tracker.domain.repository.RuleRepository
import com.pennywiseai.tracker.domain.service.QuickKeywordRuleCompiler
import com.pennywiseai.tracker.domain.service.QuickKeywordRuleMatcher
import com.pennywiseai.tracker.domain.usecase.ApplyRulesToPastTransactionsUseCase
import com.pennywiseai.tracker.domain.usecase.BatchApplyResult
import com.pennywiseai.tracker.domain.usecase.DryRunResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SaveQuickRuleOutcome(
    val success: Boolean,
    val savedRule: TransactionRule? = null,
    val batchApply: BatchApplyResult? = null,
    val diagnostics: QuickKeywordRuleMatcher.BatchStats? = null,
    /** True when batch apply is waiting for user confirmation (preview shown). */
    val awaitingBatchConfirm: Boolean = false,
)

data class ApplyRuleUiState(
    val isRunning: Boolean = false,
    val isPreparingPreview: Boolean = false,
    val progress: Pair<Int, Int>? = null,
    val result: BatchApplyResult? = null,
    val diagnostics: QuickKeywordRuleMatcher.BatchStats? = null,
)

@HiltViewModel
class QuickKeywordRulesViewModel @Inject constructor(
    private val ruleRepository: RuleRepository,
    private val transactionRepository: TransactionRepository,
    private val applyRulesToPastTransactionsUseCase: ApplyRulesToPastTransactionsUseCase,
    private val keywordRuleBatchUndoRepository: KeywordRuleBatchUndoRepository,
) : ViewModel() {

    val quickRules: StateFlow<List<TransactionRule>> = ruleRepository.getAllRules()
        .map { rules -> rules.filter { QuickKeywordRuleCompiler.isQuickKeywordRule(it) } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    private val _applyState = MutableStateFlow(ApplyRuleUiState())
    val applyState: StateFlow<ApplyRuleUiState> = _applyState.asStateFlow()

    private val _batchPreview = MutableStateFlow<QuickKeywordBatchPreview?>(null)
    val batchPreview: StateFlow<QuickKeywordBatchPreview?> = _batchPreview.asStateFlow()

    private var pendingBatchApply: PendingKeywordBatchApply? = null

    private val _undoSession = MutableStateFlow<KeywordBatchUndoSession?>(null)
    val undoSession: StateFlow<KeywordBatchUndoSession?> = _undoSession.asStateFlow()

    init {
        refreshUndoSession()
    }

    fun refreshUndoSession() {
        _undoSession.value = keywordRuleBatchUndoRepository.getActiveSession()
    }

    fun saveQuickRule(
        input: QuickKeywordRuleCompiler.QuickKeywordRuleInput,
        existingRule: TransactionRule? = null,
        forceRunNow: Boolean = false,
        applyScope: QuickKeywordApplyScope = QuickKeywordApplyScope.AllTime,
        onComplete: (SaveQuickRuleOutcome) -> Unit = {},
    ) {
        if (!input.validate()) {
            onComplete(SaveQuickRuleOutcome(success = false))
            return
        }
        viewModelScope.launch {
            try {
                val compiled = QuickKeywordRuleCompiler.compile(
                    input = input,
                    existingId = existingRule?.id,
                    createdAt = existingRule?.createdAt ?: System.currentTimeMillis(),
                )
                if (existingRule != null) {
                    ruleRepository.updateRule(compiled)
                } else {
                    ruleRepository.insertRule(compiled)
                }

                val shouldRun = compiled.isActive && (forceRunNow || input.runOnPastWhenSaved)
                if (shouldRun) {
                    prepareBatchApply(compiled.name, input, applyScope)
                    onComplete(
                        SaveQuickRuleOutcome(
                            success = true,
                            savedRule = compiled,
                            awaitingBatchConfirm = true,
                        ),
                    )
                    return@launch
                }

                onComplete(
                    SaveQuickRuleOutcome(
                        success = true,
                        savedRule = compiled,
                    ),
                )
            } catch (e: Exception) {
                Log.e(QuickKeywordRuleMatcher.LOG_TAG, "saveQuickRule failed", e)
                _applyState.value = ApplyRuleUiState(isRunning = false)
                onComplete(SaveQuickRuleOutcome(success = false))
            }
        }
    }

    fun toggleRule(ruleId: String, isActive: Boolean) {
        viewModelScope.launch {
            runCatching { ruleRepository.setRuleActive(ruleId, isActive) }
        }
    }

    fun deleteRule(ruleId: String) {
        viewModelScope.launch {
            runCatching { ruleRepository.deleteRule(ruleId) }
        }
    }

    fun prepareBatchApplyFromRule(
        rule: TransactionRule,
        uncategorizedOnly: Boolean,
        applyScope: QuickKeywordApplyScope = QuickKeywordApplyScope.AllTime,
    ) {
        viewModelScope.launch {
            val input = QuickKeywordRuleCompiler.decompile(rule)
            if (input == null) {
                Log.w(QuickKeywordRuleMatcher.LOG_TAG, "prepareBatchApply: could not decompile ${rule.id}")
                return@launch
            }
            prepareBatchApply(
                rule.name,
                input.copy(applyUncategorizedOnly = uncategorizedOnly),
                applyScope,
            )
        }
    }

    fun prepareBatchApplyFromInput(
        ruleName: String,
        input: QuickKeywordRuleCompiler.QuickKeywordRuleInput,
        applyScope: QuickKeywordApplyScope,
    ) {
        if (!input.validate()) return
        viewModelScope.launch {
            prepareBatchApply(ruleName, input, applyScope)
        }
    }

    fun dismissBatchPreview() {
        pendingBatchApply = null
        _batchPreview.value = null
        _applyState.value = _applyState.value.copy(isPreparingPreview = false)
    }

    fun confirmPendingBatchApply() {
        val pending = pendingBatchApply ?: return
        viewModelScope.launch {
            runBatchApplyFromPreview(pending)
        }
    }

    fun undoLastBatchApply(onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val snapshots = keywordRuleBatchUndoRepository.consumeSnapshotsForUndo()
            if (snapshots == null) {
                refreshUndoSession()
                onComplete(false)
                return@launch
            }
            try {
                snapshots.forEach { transactionRepository.updateTransaction(it) }
                refreshUndoSession()
                onComplete(true)
            } catch (e: Exception) {
                Log.e(QuickKeywordRuleMatcher.LOG_TAG, "undoLastBatchApply failed", e)
                onComplete(false)
            }
        }
    }

    private suspend fun prepareBatchApply(
        ruleName: String,
        input: QuickKeywordRuleCompiler.QuickKeywordRuleInput,
        applyScope: QuickKeywordApplyScope,
    ) {
        _applyState.value = ApplyRuleUiState(isPreparingPreview = true)
        try {
            val preview = buildBatchPreview(ruleName, input, applyScope)
            pendingBatchApply = PendingKeywordBatchApply(
                ruleName = ruleName,
                input = input,
                applyScope = applyScope,
                preview = preview,
            )
            _batchPreview.value = preview
        } catch (e: Exception) {
            Log.e(QuickKeywordRuleMatcher.LOG_TAG, "prepareBatchApply failed", e)
            pendingBatchApply = null
            _batchPreview.value = null
        } finally {
            _applyState.value = _applyState.value.copy(isPreparingPreview = false)
        }
    }

    private suspend fun buildBatchPreview(
        ruleName: String,
        input: QuickKeywordRuleCompiler.QuickKeywordRuleInput,
        applyScope: QuickKeywordApplyScope,
    ): QuickKeywordBatchPreview {
        val allInRange = loadTransactionsForScope(applyScope)
        val pool = if (input.applyUncategorizedOnly) {
            allInRange.filter { it.category.isBlank() || it.category == "Others" }
        } else {
            allInRange
        }

        var keywordMatched = 0
        var alreadyLabeled = 0
        val pendingChanges = mutableListOf<QuickKeywordBatchChange>()

        pool.forEach { transaction ->
            if (transaction.isDeleted) return@forEach

            val smsBody = transaction.smsBody
            val diagnosis = QuickKeywordRuleMatcher.diagnose(transaction, smsBody, input)
            if (!diagnosis.matches) return@forEach

            keywordMatched++
            val patched = QuickKeywordRuleMatcher.applyOverwrites(transaction, input)
            val shouldUpdate = input.forceOverwriteExisting ||
                QuickKeywordRuleMatcher.hasPendingOverwrites(transaction, input)

            if (shouldUpdate) {
                pendingChanges.add(
                    QuickKeywordBatchChange(
                        transactionId = transaction.id,
                        amount = transaction.amount,
                        dateTime = transaction.dateTime,
                        beforeMerchant = transaction.merchantName,
                        afterMerchant = patched.merchantName,
                        beforeCategory = transaction.category,
                        afterCategory = patched.category,
                        beforeType = transaction.transactionType,
                        afterType = patched.transactionType,
                        matchedKeyword = diagnosis.matchedKeyword,
                        before = transaction,
                        after = patched,
                    ),
                )
            } else {
                alreadyLabeled++
            }
        }

        val samples = pendingChanges.shuffled().take(SAMPLE_PREVIEW_COUNT)
        return QuickKeywordBatchPreview(
            ruleName = ruleName,
            applyScope = applyScope,
            poolSize = pool.size,
            keywordMatched = keywordMatched,
            willUpdate = pendingChanges.size,
            alreadyLabeled = alreadyLabeled,
            sampleChanges = samples,
            pendingChanges = pendingChanges,
        )
    }

    private suspend fun runBatchApplyFromPreview(pending: PendingKeywordBatchApply) {
        val preview = pending.preview
        val input = pending.input
        _applyState.value = ApplyRuleUiState(isRunning = true)
        dismissBatchPreview()

        Log.i(
            QuickKeywordRuleMatcher.LOG_TAG,
            "Applying confirmed batch for \"${pending.ruleName}\" — ${preview.willUpdate} updates",
        )

        var typeOverwritten = 0
        val undoSnapshots = mutableListOf<com.pennywiseai.tracker.data.database.entity.TransactionEntity>()
        val total = preview.pendingChanges.size

        preview.pendingChanges.forEachIndexed { index, change ->
            _applyState.value = ApplyRuleUiState(
                isRunning = true,
                progress = (index + 1) to total,
            )
            transactionRepository.updateTransaction(change.after)
            undoSnapshots.add(change.before)
            if (change.typeChanges) typeOverwritten++
        }

        if (undoSnapshots.isNotEmpty()) {
            keywordRuleBatchUndoRepository.saveUndoSession(
                ruleName = pending.ruleName,
                beforeSnapshots = undoSnapshots,
            )
            refreshUndoSession()
        }

        val stats = QuickKeywordRuleMatcher.BatchStats(
            ruleName = pending.ruleName,
            keywords = input.keywords,
            poolSize = preview.poolSize,
            keywordMatched = preview.keywordMatched,
            updated = preview.willUpdate,
            alreadyHadLabels = preview.alreadyLabeled,
            typeOverwritten = typeOverwritten,
            uncategorizedOnly = input.applyUncategorizedOnly,
            applyScope = pending.applyScope,
            transactionsInRange = preview.poolSize,
            withSmsBody = 0,
        )
        stats.logSummary()

        val batchResult = BatchApplyResult(
            totalProcessed = preview.poolSize,
            totalUpdated = preview.willUpdate,
        )
        _applyState.value = ApplyRuleUiState(
            isRunning = false,
            result = batchResult,
            diagnostics = stats,
        )
        pendingBatchApply = null
    }

    private suspend fun loadTransactionsForScope(
        scope: QuickKeywordApplyScope,
    ): List<com.pennywiseai.tracker.data.database.entity.TransactionEntity> {
        val range = scope.resolveDateTimeRange()
        return if (range == null) {
            transactionRepository.getAllTransactionsList()
        } else {
            transactionRepository.getTransactionsBetweenDatesList(range.first, range.second)
        }
    }

    fun clearApplyState() {
        _applyState.value = ApplyRuleUiState()
    }

    suspend fun previewRule(rule: TransactionRule): DryRunResult =
        applyRulesToPastTransactionsUseCase.previewRuleApplication(rule)

    companion object {
        private const val SAMPLE_PREVIEW_COUNT = 5
    }
}
