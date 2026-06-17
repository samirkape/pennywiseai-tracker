package com.pennywiseai.tracker.presentation.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import androidx.core.net.toUri
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.MerchantAliasEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionSplitEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.ui.components.SplitItem
import com.pennywiseai.tracker.data.receipt.ReceiptManager
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.database.entity.GoalContributionEntity
import com.pennywiseai.tracker.data.database.entity.GoalEntity
import com.pennywiseai.tracker.data.repository.BudgetGroupRepository
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.GoalRepository
import com.pennywiseai.tracker.data.repository.LoanRepository
import com.pennywiseai.tracker.data.repository.MerchantAliasRepository
import com.pennywiseai.tracker.data.repository.MerchantMappingRepository
import com.pennywiseai.tracker.data.repository.TransactionGroupRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.model.FutureParsingPromptState
import com.pennywiseai.tracker.domain.model.TransactionRenameCandidate
import com.pennywiseai.tracker.data.database.entity.TransactionGroupEntity
import com.pennywiseai.tracker.core.Constants
import com.pennywiseai.tracker.data.database.dao.BulkCategoryPreviewDaoRow
import com.pennywiseai.tracker.domain.usecase.UpdateTransactionRequest
import com.pennywiseai.tracker.domain.usecase.UpdateTransactionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import com.pennywiseai.tracker.utils.MerchantAliasAuditor
import com.pennywiseai.tracker.utils.MerchantNameMatcher
import com.pennywiseai.tracker.utils.SmsMerchantAliasHints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDateTime
import javax.inject.Inject

/** Limits which past transactions match bulk category propagation by `date_time`. */
enum class BulkCategoryDateScope {
    ALL_TIME,
    LAST_90_DAYS,
    LAST_365_DAYS,
}

data class BulkCategorySaveConfirmParams(
    val merchantName: String,
    val category: String,
    val otherCount: Int,
    val scope: BulkCategoryDateScope,
    val pastCategory: Boolean,
    val pastMerchant: Boolean,
    val pastType: Boolean,
)

/** State emitted on Save when the user has changed category or merchant and there are
 *  past/future transactions that can be bulk-updated. The UI shows a bottom sheet so
 *  the user can decide before the save actually commits. */
data class PreSaveBulkState(
    val existingCount: Int,
    val isSelfTransfer: Boolean,
    val categoryChanged: Boolean,
    val merchantChanged: Boolean,
    val typeChanged: Boolean,
    val merchantName: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val merchantMappingRepository: MerchantMappingRepository,
    private val merchantAliasRepository: MerchantAliasRepository,
    private val categoryRepository: CategoryRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val loanRepository: LoanRepository,
    private val goalRepository: GoalRepository,
    private val budgetGroupRepository: BudgetGroupRepository,
    private val transactionGroupRepository: TransactionGroupRepository,
    private val currencyConversionService: CurrencyConversionService,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val receiptManager: ReceiptManager,
    private val updateTransactionUseCase: UpdateTransactionUseCase,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {
    
    private val _transaction = MutableStateFlow<TransactionEntity?>(null)
    val transaction: StateFlow<TransactionEntity?> = _transaction.asStateFlow()

    private val _pendingTags = MutableStateFlow<List<String>>(emptyList())
    val pendingTags: StateFlow<List<String>> = _pendingTags.asStateFlow()

    private val _allUsedTags = MutableStateFlow<List<String>>(emptyList())
    private val _tagQuery = MutableStateFlow("")

    val tagSuggestions: StateFlow<List<String>> = combine(
        _allUsedTags,
        _tagQuery,
        _pendingTags,
    ) { all, query, pending ->
        val filtered = if (query.isBlank()) all else all.filter { it.contains(query.trim(), ignoreCase = true) }
        filtered.filter { it !in pending }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _primaryCurrency = MutableStateFlow("INR")
    val primaryCurrency: StateFlow<String> = _primaryCurrency.asStateFlow()

    private val _convertedAmount = MutableStateFlow<BigDecimal?>(null)
    val convertedAmount: StateFlow<BigDecimal?> = _convertedAmount.asStateFlow()

    private val _accountProfileId = MutableStateFlow<Long?>(null)
    val accountProfileId: StateFlow<Long?> = _accountProfileId.asStateFlow()

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()
    
    private val _editableTransaction = MutableStateFlow<TransactionEntity?>(null)
    val editableTransaction: StateFlow<TransactionEntity?> = _editableTransaction.asStateFlow()
    
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    
    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _existingTransactionCount = MutableStateFlow(0)

    private val _originalMerchantNameOnEdit = MutableStateFlow<String?>(null)
    private val _originalCategoryOnEdit = MutableStateFlow<String?>(null)
    private val _originalTypeOnEdit = MutableStateFlow<TransactionType?>(null)
    val originalMerchantAtEditStart: StateFlow<String?> = _originalMerchantNameOnEdit.asStateFlow()
    val originalCategoryAtEditStart: StateFlow<String?> = _originalCategoryOnEdit.asStateFlow()

    /** Past rows: overwrite category for same merchant match. */
    private val _bulkPastCategory = MutableStateFlow(false)
    val bulkPastCategory: StateFlow<Boolean> = _bulkPastCategory.asStateFlow()
    /** Past rows: rename merchant to match this edit. */
    private val _bulkPastMerchant = MutableStateFlow(false)
    val bulkPastMerchant: StateFlow<Boolean> = _bulkPastMerchant.asStateFlow()
    /** Past rows: set transaction type and transfer kind to match this edit. */
    private val _bulkPastType = MutableStateFlow(false)
    val bulkPastType: StateFlow<Boolean> = _bulkPastType.asStateFlow()

    /** Future SMS: save merchant→category mapping for the display name. */
    private val _bulkIncomingCategory = MutableStateFlow(false)
    val bulkIncomingCategory: StateFlow<Boolean> = _bulkIncomingCategory.asStateFlow()
    /** Future SMS: save raw→display merchant alias. */
    private val _bulkIncomingMerchant = MutableStateFlow(false)
    val bulkIncomingMerchant: StateFlow<Boolean> = _bulkIncomingMerchant.asStateFlow()

    /** Master toggles for the pre-save bulk sheet (Option 3 confirmation framing). */
    private val _applyToPast = MutableStateFlow(false)
    val applyToPast: StateFlow<Boolean> = _applyToPast.asStateFlow()
    private val _applyToFuture = MutableStateFlow(false)
    val applyToFuture: StateFlow<Boolean> = _applyToFuture.asStateFlow()

    private val _allKnownMerchants = MutableStateFlow<List<String>>(emptyList())
    private val _suggestedMerchantRenames = MutableStateFlow<List<String>>(emptyList())
    val suggestedMerchantRenames: StateFlow<List<String>> = _suggestedMerchantRenames.asStateFlow()
    private var merchantEditDebounceJob: Job? = null

    val merchantAutocompleteSuggestions: StateFlow<List<String>> = combine(
        _allKnownMerchants,
        _editableTransaction,
    ) { knownMerchants, txn ->
        MerchantNameMatcher.autocompleteMatches(
            query = txn?.merchantName.orEmpty(),
            knownMerchants = knownMerchants,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _merchantRenameReview = MutableStateFlow<MerchantRenameReviewState?>(null)
    val merchantRenameReview: StateFlow<MerchantRenameReviewState?> = _merchantRenameReview.asStateFlow()

    private val _futureParsingPrompt = MutableStateFlow<FutureParsingPromptState?>(null)
    val futureParsingPrompt: StateFlow<FutureParsingPromptState?> = _futureParsingPrompt.asStateFlow()
    private var pendingFutureParsingPrompt: FutureParsingPromptState? = null
    
    private val _showDeleteDialog = MutableStateFlow(false)
    val showDeleteDialog: StateFlow<Boolean> = _showDeleteDialog.asStateFlow()
    
    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    private val _deleteSuccess = MutableStateFlow(false)
    val deleteSuccess: StateFlow<Boolean> = _deleteSuccess.asStateFlow()
    val existingTransactionCount: StateFlow<Int> = _existingTransactionCount.asStateFlow()

    private val _bulkCategoryDateScope = MutableStateFlow(BulkCategoryDateScope.ALL_TIME)
    val bulkCategoryDateScope: StateFlow<BulkCategoryDateScope> = _bulkCategoryDateScope.asStateFlow()

    private val _bulkCategorySaveConfirm = MutableStateFlow<BulkCategorySaveConfirmParams?>(null)
    val bulkCategorySaveConfirm: StateFlow<BulkCategorySaveConfirmParams?> =
        _bulkCategorySaveConfirm.asStateFlow()

    private val _preSaveBulkState = MutableStateFlow<PreSaveBulkState?>(null)
    val preSaveBulkState: StateFlow<PreSaveBulkState?> = _preSaveBulkState.asStateFlow()

    private val _bulkCategoryPreviewRows = MutableStateFlow<List<BulkCategoryPreviewDaoRow>>(emptyList())
    val bulkCategoryPreviewRows: StateFlow<List<BulkCategoryPreviewDaoRow>> =
        _bulkCategoryPreviewRows.asStateFlow()

    private val _bulkCategoryUndoSnackCount = MutableStateFlow<Int?>(null)
    val bulkCategoryUndoSnackCount: StateFlow<Int?> = _bulkCategoryUndoSnackCount.asStateFlow()

    private val _merchantMappingCategoryHint = MutableStateFlow<String?>(null)
    val merchantMappingCategoryHint: StateFlow<String?> = _merchantMappingCategoryHint.asStateFlow()
    private val _budgetImpactType = MutableStateFlow<BudgetImpactType?>(null)
    val budgetImpactType: StateFlow<BudgetImpactType?> = _budgetImpactType.asStateFlow()

    private val _budgetCategory = MutableStateFlow<String?>(null)
    val budgetCategory: StateFlow<String?> = _budgetCategory.asStateFlow()

    val activeBudgetCategories: StateFlow<List<String>> = budgetGroupRepository.getActiveGroups()
        .map { groups ->
            groups.flatMap { it.categories.map { cat -> cat.categoryName } }.distinct().sorted()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Transaction group state
    val availableGroups: StateFlow<List<TransactionGroupEntity>> = transactionGroupRepository.getAllGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentGroup: StateFlow<TransactionGroupEntity?> = _transaction
        .flatMapLatest { tx ->
            val groupId = tx?.groupId ?: return@flatMapLatest kotlinx.coroutines.flow.flowOf(null)
            transactionGroupRepository.getAllGroups().map { groups -> groups.firstOrNull { it.id == groupId } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _showGroupSheet = MutableStateFlow(false)
    val showGroupSheet: StateFlow<Boolean> = _showGroupSheet.asStateFlow()

    fun showGroupSheet() { _showGroupSheet.value = true }
    fun hideGroupSheet() { _showGroupSheet.value = false }

    fun addToGroup(groupId: Long) {
        viewModelScope.launch {
            val txId = _transaction.value?.id ?: return@launch
            transactionGroupRepository.addTransactionToGroup(txId, groupId)
            _showGroupSheet.value = false
        }
    }

    fun removeFromGroup() {
        viewModelScope.launch {
            val txId = _transaction.value?.id ?: return@launch
            transactionGroupRepository.removeTransactionFromGroup(txId)
        }
    }

    fun createGroupAndAdd(name: String, note: String?) {
        viewModelScope.launch {
            val txId = _transaction.value?.id ?: return@launch
            transactionGroupRepository.createGroupWithTransaction(name, note, txId)
            _showGroupSheet.value = false
        }
    }

    // Split-related state
    private val _splits = MutableStateFlow<List<SplitItem>>(emptyList())
    val splits: StateFlow<List<SplitItem>> = _splits.asStateFlow()

    private val _originalSplits = MutableStateFlow<List<SplitItem>>(emptyList())

    private val _showSplitEditor = MutableStateFlow(false)
    val showSplitEditor: StateFlow<Boolean> = _showSplitEditor.asStateFlow()

    private val _hasSplits = MutableStateFlow(false)
    val hasSplits: StateFlow<Boolean> = _hasSplits.asStateFlow()
    
    private val todayCategories: StateFlow<List<String>> = transactionRepository.getTodayCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _merchantSuggestionCategories = MutableStateFlow<List<String>>(emptyList())

    val categorySuggestions: StateFlow<CategorySuggestionsState> = combine(
        _merchantSuggestionCategories,
        todayCategories,
        _editableTransaction,
        _transaction,
        _isEditMode,
    ) { merchantCats, todayCats, editable, transaction, isEdit ->
        if (!isEdit) return@combine CategorySuggestionsState()
        val txn = editable ?: transaction ?: return@combine CategorySuggestionsState()
        val selected = setOf(txn.category)
        val merchantFiltered = merchantCats.filter { it !in selected }
        if (merchantFiltered.isNotEmpty()) {
            CategorySuggestionsState(
                categories = merchantFiltered,
                source = CategorySuggestionSource.MERCHANT,
                merchantName = txn.merchantName
            )
        } else {
            val todayFiltered = todayCats.filter { it !in selected }
            CategorySuggestionsState(
                categories = todayFiltered,
                source = if (todayFiltered.isEmpty()) null else CategorySuggestionSource.USED_TODAY,
                merchantName = txn.merchantName
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CategorySuggestionsState()
    )

    // Categories should be based on transaction type
    val categories: StateFlow<List<CategoryEntity>> = combine(
        _editableTransaction,
        _transaction
    ) { editable, original ->
        val transaction = editable ?: original
        transaction?.transactionType == TransactionType.INCOME
    }.flatMapLatest { isIncome ->
        if (isIncome) {
            categoryRepository.getIncomeCategories()
        } else {
            categoryRepository.getExpenseCategories()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    // Available accounts for linking (excluding hidden accounts)
    private val sharedPrefs = context.getSharedPreferences("account_prefs", android.content.Context.MODE_PRIVATE)

    val availableAccounts = accountBalanceRepository.getAllLatestBalances()
        .map { balances ->
            val hiddenAccounts = sharedPrefs.getStringSet("hidden_accounts", emptySet()) ?: emptySet()
            balances
                .filter { balance ->
                    val key = "${balance.bankName}_${balance.accountLast4}"
                    !hiddenAccounts.contains(key)
                }
                .map { balance ->
                    AccountInfo(
                        bankName = balance.bankName,
                        accountLast4 = balance.accountLast4,
                        displayName = "${balance.bankName} ••••${balance.accountLast4}",
                        isCreditCard = balance.isCreditCard
                    )
                }
                .distinctBy { "${it.bankName}_${it.accountLast4}" }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    data class AccountInfo(
        val bankName: String,
        val accountLast4: String,
        val displayName: String,
        val isCreditCard: Boolean
    )
    
    fun loadTransaction(transactionId: Long) {
        viewModelScope.launch {
            val transaction = transactionRepository.getTransactionById(transactionId)
            _transaction.value = transaction
            transaction?.let {
                determinePrimaryCurrency(it)
                calculateConvertedAmount(it)
                loadSplits(transactionId)
                loadReceiptUris(it)
                it.loanId?.let { id -> loadLoan(id) }
                _budgetImpactType.value = it.budgetImpactType
                _budgetCategory.value = it.budgetCategory
                loadAccountProfileId(it)
                loadLinkedGoal(transactionId)
                loadSimilarTransactions(it.merchantName)
            }
        }
    }

    private suspend fun loadAccountProfileId(transaction: TransactionEntity) {
        val bankName = transaction.bankName ?: return
        val accountLast4 = transaction.accountNumber ?: return
        val balance = accountBalanceRepository.getLatestBalance(bankName, accountLast4)
        _accountProfileId.value = balance?.profileId
    }

    private suspend fun loadSplits(transactionId: Long) {
        val hasSplits = transactionRepository.hasSplits(transactionId)
        _hasSplits.value = hasSplits

        if (hasSplits) {
            transactionRepository.getSplitsForTransaction(transactionId)
                .collect { splitEntities ->
                    val splitItems = splitEntities.map { entity ->
                        SplitItem(
                            id = entity.id,
                            category = entity.category,
                            amount = entity.amount,
                            tags = if (entity.tags.isBlank()) emptyList()
                                   else entity.tags.split(",").filter { it.isNotBlank() }
                        )
                    }
                    _splits.value = splitItems
                    _originalSplits.value = splitItems
                    _showSplitEditor.value = true
                }
        } else {
            _splits.value = emptyList()
            _originalSplits.value = emptyList()
            _showSplitEditor.value = false
        }
    }

    private suspend fun determinePrimaryCurrency(transaction: TransactionEntity) {
        val isUnified = userPreferencesRepository.unifiedCurrencyMode.first()
        val primaryCurrency = if (isUnified) {
            userPreferencesRepository.displayCurrency.first()
        } else {
            val bankName = transaction.bankName
            if (!bankName.isNullOrEmpty()) {
                com.pennywiseai.tracker.utils.CurrencyFormatter.getBankBaseCurrency(bankName)
            } else {
                transaction.currency.takeIf { it.isNotEmpty() } ?: "INR"
            }
        }
        _primaryCurrency.value = primaryCurrency
    }

    private suspend fun calculateConvertedAmount(transaction: TransactionEntity) {
        val primaryCurrency = _primaryCurrency.value
        if (transaction.currency.isNotEmpty() && !transaction.currency.equals(primaryCurrency, ignoreCase = true)) {
            // Convert the amount to the primary currency
            val converted = currencyConversionService.convertAmount(
                amount = transaction.amount,
                fromCurrency = transaction.currency,
                toCurrency = primaryCurrency
            )
            _convertedAmount.value = converted
        } else {
            // No conversion needed if currencies are the same
            _convertedAmount.value = null
        }
    }

    fun enterEditMode() {
        _editableTransaction.value = _transaction.value?.copy()
        _isEditMode.value = true
        _errorMessage.value = null
        _pendingReceiptUris.value = emptyList()
        _removedReceiptIds.value = emptySet()
        _pendingTags.value = _transaction.value?.tags
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        _tagQuery.value = ""
        viewModelScope.launch {
            _allUsedTags.value = transactionRepository.getAllUsedTags()
        }

        // Restore split state from original splits
        if (_hasSplits.value) {
            _splits.value = _originalSplits.value
            _showSplitEditor.value = true
        }

        _transaction.value?.let { txn ->
            _originalMerchantNameOnEdit.value = txn.merchantName
            _originalCategoryOnEdit.value = txn.category
            _originalTypeOnEdit.value = txn.transactionType
            viewModelScope.launch {
                loadKnownMerchants()
                loadMerchantRenameSuggestion(txn.merchantName)
                refreshMerchantDependentEditData(txn.merchantName)
            }
        }
    }

    private suspend fun refreshMerchantDependentEditData(merchantName: String) {
        val txnId = _editableTransaction.value?.id ?: return
        val trimmed = merchantName.trim()
        if (trimmed.isEmpty()) {
            _existingTransactionCount.value = 0
            _merchantSuggestionCategories.value = emptyList()
            _merchantMappingCategoryHint.value = null
            clearPastBulkSelections()
            return
        }
        val keyForBulkCount = effectiveBulkCategoryMerchantKey(trimmed)
        _existingTransactionCount.value = transactionRepository.getOtherTransactionCountForMerchant(
            keyForBulkCount,
            txnId,
            notBeforeForCurrentBulkScope(),
        )
        if (_existingTransactionCount.value == 0 &&
            _bulkCategoryDateScope.value != BulkCategoryDateScope.ALL_TIME
        ) {
            _bulkCategoryDateScope.value = BulkCategoryDateScope.ALL_TIME
            _existingTransactionCount.value = transactionRepository.getOtherTransactionCountForMerchant(
                keyForBulkCount,
                txnId,
                null,
            )
        }
        if (_existingTransactionCount.value == 0) {
            clearPastBulkSelections()
        }
        loadMerchantCategorySuggestions(trimmed, txnId)
        updateMerchantMappingCategoryHint(trimmed)
    }

    private suspend fun updateMerchantMappingCategoryHint(merchantTrimmed: String) {
        if (merchantTrimmed.isEmpty()) {
            _merchantMappingCategoryHint.value = null
            return
        }
        val mapped = merchantMappingRepository.getCategoryForMerchant(merchantTrimmed)
        val currentCat = _editableTransaction.value?.category
        _merchantMappingCategoryHint.value =
            if (mapped != null && currentCat != null && !mapped.equals(currentCat, ignoreCase = true)) {
                mapped
            } else {
                null
            }
    }

    private suspend fun loadKnownMerchants() {
        val fromTransactions = transactionRepository.getDistinctMerchantNames()
        val fromAliases = merchantAliasRepository.getAllDisplayNames()
        _allKnownMerchants.value = (fromTransactions + fromAliases).distinct()
    }

    private suspend fun loadMerchantRenameSuggestion(sourceMerchant: String) {
        _suggestedMerchantRenames.value = merchantAliasRepository.suggestDisplayNames(
            sourceMerchant = sourceMerchant,
            knownMerchants = _allKnownMerchants.value,
        ).filter { !it.equals(sourceMerchant, ignoreCase = true) }
    }

    fun applyMerchantRenameSuggestion(suggestedName: String) {
        _editableTransaction.update { current ->
            current?.copy(merchantName = suggestedName)
        }
        _suggestedMerchantRenames.value = emptyList()
        clearPastBulkSelections()
        viewModelScope.launch {
            loadMerchantRenameSuggestion(suggestedName)
            refreshMerchantDependentEditData(suggestedName)
        }
    }

    private suspend fun loadMerchantCategorySuggestions(merchantName: String, transactionId: Long) {
        val trimmed = merchantName.trim()
        if (trimmed.isEmpty()) {
            _merchantSuggestionCategories.value = emptyList()
            return
        }
        val mappingCategory = merchantMappingRepository.getCategoryForMerchant(trimmed)
        _merchantSuggestionCategories.value = transactionRepository.getSuggestedCategoriesForMerchant(
            merchantName = trimmed,
            excludeTransactionId = transactionId,
            merchantMappingCategory = mappingCategory
        )
    }
    
    fun exitEditMode() {
        _editableTransaction.value = null
        _isEditMode.value = false
        _errorMessage.value = null
        clearAllBulkPropagationSelections()
        _existingTransactionCount.value = 0
        _bulkCategoryDateScope.value = BulkCategoryDateScope.ALL_TIME
        _bulkCategorySaveConfirm.value = null
        _bulkCategoryPreviewRows.value = emptyList()
        _merchantMappingCategoryHint.value = null
        _pendingReceiptUris.value = emptyList()
        _removedReceiptIds.value = emptySet()
        _pendingTags.value = emptyList()
        _tagQuery.value = ""
        _allUsedTags.value = emptyList()
        _merchantSuggestionCategories.value = emptyList()
        _originalMerchantNameOnEdit.value = null
        _originalCategoryOnEdit.value = null
        _allKnownMerchants.value = emptyList()
        _suggestedMerchantRenames.value = emptyList()
        _merchantRenameReview.value = null
        pendingFutureParsingPrompt = null
        _futureParsingPrompt.value = null

        // Reset split state to original values
        _splits.value = _originalSplits.value
        _showSplitEditor.value = _hasSplits.value
    }

    fun updateTagQuery(query: String) {
        _tagQuery.value = query
    }

    fun addPendingTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isNotBlank() && !_pendingTags.value.contains(trimmed)) {
            _pendingTags.value = _pendingTags.value + trimmed
        }
    }

    fun removePendingTag(tag: String) {
        _pendingTags.value = _pendingTags.value - tag
    }

    private fun clearPastBulkSelections() {
        _bulkPastCategory.value = false
        _bulkPastMerchant.value = false
        _bulkPastType.value = false
    }

    private fun clearAllBulkPropagationSelections() {
        clearPastBulkSelections()
        _bulkIncomingCategory.value = false
        _bulkIncomingMerchant.value = false
        _applyToPast.value = false
        _applyToFuture.value = false
    }

    fun toggleBulkPastCategory() {
        _bulkPastCategory.value = !_bulkPastCategory.value
    }

    fun toggleBulkPastMerchant() {
        _bulkPastMerchant.value = !_bulkPastMerchant.value
    }

    fun toggleBulkPastType() {
        _bulkPastType.value = !_bulkPastType.value
    }

    fun toggleBulkIncomingCategory() {
        _bulkIncomingCategory.value = !_bulkIncomingCategory.value
    }

    fun toggleBulkIncomingMerchant() {
        _bulkIncomingMerchant.value = !_bulkIncomingMerchant.value
    }

    fun toggleApplyToPast() {
        _applyToPast.value = !_applyToPast.value
    }

    fun toggleApplyToFuture() {
        _applyToFuture.value = !_applyToFuture.value
    }

    fun setBulkCategoryDateScope(scope: BulkCategoryDateScope) {
        _bulkCategoryDateScope.value = scope
        viewModelScope.launch {
            val name = _editableTransaction.value?.merchantName?.trim().orEmpty()
            if (name.isNotEmpty()) refreshMerchantDependentEditData(name)
        }
    }

    private fun notBeforeForCurrentBulkScope(): LocalDateTime? = when (_bulkCategoryDateScope.value) {
        BulkCategoryDateScope.ALL_TIME -> null
        BulkCategoryDateScope.LAST_90_DAYS -> LocalDateTime.now().minusDays(90)
        BulkCategoryDateScope.LAST_365_DAYS -> LocalDateTime.now().minusDays(365)
    }

    fun confirmBulkCategorySave() {
        _bulkCategorySaveConfirm.value = null
        _bulkCategoryPreviewRows.value = emptyList()
        saveChanges(requireBulkCategoryConfirm = false)
    }

    fun dismissBulkCategorySave() {
        _bulkCategorySaveConfirm.value = null
        _bulkCategoryPreviewRows.value = emptyList()
    }

    /** User tapped "Confirm & Save" in the pre-save bulk sheet. Translates master
     *  toggles into the individual per-field booleans read by saveChanges(). */
    fun saveWithBulkOptions() {
        val state = _preSaveBulkState.value
        if (state != null) {
            val applyPast = _applyToPast.value
            _bulkPastCategory.value = applyPast && state.categoryChanged && !state.isSelfTransfer
            _bulkPastMerchant.value = applyPast && state.merchantChanged
            _bulkPastType.value = applyPast && state.typeChanged
            val applyFuture = _applyToFuture.value
            _bulkIncomingCategory.value = applyFuture && state.categoryChanged && !state.isSelfTransfer
            _bulkIncomingMerchant.value = applyFuture && state.merchantChanged
        }
        _preSaveBulkState.value = null
        saveChanges(requireBulkCategoryConfirm = false, skipPreSaveSheet = true)
    }

    /** User tapped "Skip" in the pre-save bulk sheet — save without bulk operations. */
    fun saveWithoutBulkOptions() {
        clearAllBulkPropagationSelections()
        _preSaveBulkState.value = null
        saveChanges(requireBulkCategoryConfirm = false, skipPreSaveSheet = true)
    }

    fun dismissPreSaveBulkSheet() {
        _preSaveBulkState.value = null
        _isSaving.value = false
    }

    fun clearBulkCategoryUndoSnack() {
        _bulkCategoryUndoSnackCount.value = null
    }

    suspend fun undoBulkCategoryFromSnackSuspend() {
        transactionRepository.undoLastBulkCategoryUpdate()
        val id = _transaction.value?.id ?: return
        transactionRepository.getTransactionById(id)?.let { _transaction.value = it }
    }
    fun updateMerchantName(name: String) {
        val previousName = _editableTransaction.value?.merchantName
        _editableTransaction.update { current ->
            current?.copy(merchantName = name)
        }
        if (previousName != name) {
            clearAllBulkPropagationSelections()
        }
        validateMerchantName(name)
        merchantEditDebounceJob?.cancel()
        merchantEditDebounceJob = viewModelScope.launch {
            delay(250)
            loadMerchantRenameSuggestion(name)
            refreshMerchantDependentEditData(name)
        }
    }
    
    fun updateAmount(amountStr: String) {
        val amount = amountStr.toBigDecimalOrNull()
        if (amount != null && amount > BigDecimal.ZERO) {
            _editableTransaction.update { current ->
                current?.copy(amount = amount)
            }
            if (_showSplitEditor.value && _splits.value.size >= 2) {
                val currentSplits = _splits.value
                val sumExceptLast = currentSplits.dropLast(1).fold(BigDecimal.ZERO) { acc, s -> acc + s.amount }
                val lastAmt = (amount - sumExceptLast).coerceAtLeast(BigDecimal.ZERO)
                _splits.value = currentSplits.dropLast(1) + currentSplits.last().copy(amount = lastAmt)
            }
            _errorMessage.value = null
        } else if (amountStr.isNotEmpty()) {
            _errorMessage.value = "Amount must be a positive number"
        }
    }
    
    fun updateTransactionType(type: TransactionType) {
        if (type != TransactionType.INCOME) {
            _budgetImpactType.value = null
            _budgetCategory.value = null
            _editableTransaction.update { current ->
                // When switching to Transfer, default to SELF_TRANSFER sub-kind
                val newTransferKind = if (type == TransactionType.TRANSFER)
                    current?.transferKind?.takeIf {
                        it == com.pennywiseai.tracker.data.database.entity.TransferKind.SELF_TRANSFER ||
                        it == com.pennywiseai.tracker.data.database.entity.TransferKind.OTHERS_TRANSFER ||
                        it == com.pennywiseai.tracker.data.database.entity.TransferKind.CC_BILL_PAYMENT
                    } ?: com.pennywiseai.tracker.data.database.entity.TransferKind.SELF_TRANSFER
                else null
                current?.copy(transactionType = type, budgetImpactType = null, budgetCategory = null, transferKind = newTransferKind)
            }
        } else {
            _editableTransaction.update { current ->
                current?.copy(transactionType = type, transferKind = null)
            }
        }
    }

    fun updateTransferKind(kind: String) {
        _editableTransaction.update { current ->
            val c = current ?: return@update current
            val base = c.copy(transferKind = kind)
            if (kind == com.pennywiseai.tracker.data.database.entity.TransferKind.SELF_TRANSFER) {
                _bulkPastCategory.value = false
            }
            if (kind == com.pennywiseai.tracker.data.database.entity.TransferKind.CC_BILL_PAYMENT) {
                base.copy(category = "Credit Card Payment")
            } else {
                base
            }
        }
    }

    fun updateCategory(category: String) {
        _editableTransaction.update { current ->
            current?.copy(category = category.ifEmpty { "Others" })
        }
        val merchant = _editableTransaction.value?.merchantName?.trim().orEmpty()
        if (merchant.isNotEmpty()) {
            viewModelScope.launch { updateMerchantMappingCategoryHint(merchant) }
        } else {
            _merchantMappingCategoryHint.value = null
        }
    }
    
    fun updateDateTime(dateTime: LocalDateTime) {
        _editableTransaction.update { current ->
            current?.copy(dateTime = dateTime)
        }
    }
    
    fun updateDescription(description: String?) {
        _editableTransaction.update { current ->
            current?.copy(description = if (description.isNullOrEmpty()) null else description)
        }
    }
    
    fun updateRecurringStatus(isRecurring: Boolean) {
        _editableTransaction.update { current ->
            current?.copy(isRecurring = isRecurring)
        }
    }

    fun updateExcludedFromTracking(excluded: Boolean) {
        _editableTransaction.update { current ->
            current?.copy(isExcludedFromTracking = excluded)
        }
        // Immediately persist so the change isn't lost if the user navigates away without saving
        val transactionId = _transaction.value?.id ?: _editableTransaction.value?.id ?: return
        viewModelScope.launch {
            transactionRepository.updateExcludedFromTracking(transactionId, excluded)
            _transaction.update { it?.copy(isExcludedFromTracking = excluded) }
        }
    }

    fun updateAccountNumber(accountNumber: String?) {
        _editableTransaction.update { current ->
            current?.copy(accountNumber = if (accountNumber.isNullOrEmpty()) null else accountNumber)
        }
    }

    fun updateFromAccount(account: String?) {
        _editableTransaction.update { current ->
            current?.copy(fromAccount = if (account.isNullOrEmpty()) null else account)
        }
    }

    fun updateToAccount(account: String?) {
        _editableTransaction.update { current ->
            current?.copy(toAccount = if (account.isNullOrEmpty()) null else account)
        }
    }

    fun updateProfileId(profileId: Long?) {
        _editableTransaction.update { current ->
            current?.copy(profileId = profileId)
        }
    }

    fun updateBudgetImpactType(type: BudgetImpactType?) {
        _budgetImpactType.value = type
        if (type == null) _budgetCategory.value = null
        _editableTransaction.update { it?.copy(budgetImpactType = type, budgetCategory = if (type == null) null else it.budgetCategory) }
    }

    fun updateBudgetCategory(category: String?) {
        _budgetCategory.value = category
        _editableTransaction.update { it?.copy(budgetCategory = category) }
    }

    fun updateCurrency(currency: String) {
        _editableTransaction.update { current ->
            current?.copy(currency = currency)
        }
        // Recalculate converted amount when currency changes
        _editableTransaction.value?.let { transaction ->
            viewModelScope.launch {
                calculateConvertedAmount(transaction)
            }
        }
    }

    // ========== Split Management Methods ==========

    /**
     * Enables split mode for the current transaction.
     * Creates two initial splits: one with the current category and half the amount,
     * and another with "Others" and the remaining amount.
     */
    fun enableSplitMode() {
        val transaction = _editableTransaction.value ?: _transaction.value ?: return

        // Splits not supported for transfers
        if (transaction.transactionType == TransactionType.TRANSFER) {
            _errorMessage.value = "Splits are not available for transfers"
            return
        }

        val currentCategory = transaction.category
        val totalAmount = transaction.amount
        val halfAmount = totalAmount.divide(BigDecimal(2), 2, java.math.RoundingMode.HALF_UP)
        val remainingAmount = totalAmount - halfAmount

        val initialSplits = listOf(
            SplitItem(id = 0, category = currentCategory, amount = halfAmount),
            SplitItem(id = 0, category = "Others", amount = remainingAmount)
        )

        _splits.value = initialSplits
        _showSplitEditor.value = true
    }

    /**
     * Updates the splits list.
     */
    fun updateSplits(newSplits: List<SplitItem>) {
        _splits.value = newSplits
    }

    /**
     * Removes all splits from the transaction, reverting to single category.
     */
    fun removeSplits() {
        _splits.value = emptyList()
        _showSplitEditor.value = false
        _hasSplits.value = false
    }

    /**
     * Creates a new category and makes it immediately available in the split editor.
     * The [categories] StateFlow observes the DB and refreshes automatically.
     */
    fun createAndSelectSplitCategory(name: String, color: String, isIncome: Boolean, icon: String) {
        viewModelScope.launch {
            categoryRepository.createCategory(name, color, isIncome, icon)
            // categories StateFlow auto-refreshes via DB observation
        }
    }

    /**
     * Validates that splits sum equals the transaction total (within tolerance).
     * @return true if splits are valid, false otherwise
     */
    fun validateSplits(): Boolean {
        val transaction = _editableTransaction.value ?: _transaction.value ?: return true
        val currentSplits = _splits.value

        if (currentSplits.isEmpty()) return true

        // Minimum 2 splits required
        if (currentSplits.size < 2) {
            _errorMessage.value = "At least 2 splits are required"
            return false
        }

        // All splits must have positive amounts
        if (currentSplits.any { it.amount <= BigDecimal.ZERO }) {
            _errorMessage.value = "All split amounts must be positive"
            return false
        }

        // Splits must sum to transaction total (within 0.01 tolerance)
        val splitsTotal = currentSplits.sumOf { it.amount }
        val difference = (transaction.amount - splitsTotal).abs()
        val tolerance = BigDecimal("0.01")

        if (difference > tolerance) {
            _errorMessage.value = "Split amounts must equal the transaction total"
            return false
        }

        return true
    }

    /**
     * Checks if the amount field should be editable.
     * Amount is locked when splits exist.
     */
    fun isAmountEditable(): Boolean {
        return !_showSplitEditor.value || _splits.value.isEmpty()
    }

    fun saveChanges(requireBulkCategoryConfirm: Boolean = true, skipPreSaveSheet: Boolean = false) {
        val toSave = _editableTransaction.value ?: return

        // Validate before saving (fast feedback; full save continues in coroutine)
        if (toSave.merchantName.isBlank()) {
            _errorMessage.value = "Merchant name is required"
            return
        }

        if (toSave.amount <= BigDecimal.ZERO) {
            _errorMessage.value = "Amount must be positive"
            return
        }

        // Validate splits if present
        if (_showSplitEditor.value && _splits.value.isNotEmpty()) {
            if (!validateSplits()) {
                return
            }
        }

        // Validate self-transfer for TRANSFER transactions
        if (toSave.transactionType == TransactionType.TRANSFER &&
            toSave.fromAccount != null &&
            toSave.toAccount != null &&
            toSave.fromAccount == toSave.toAccount) {
            _errorMessage.value = "Source and destination accounts must be different"
            return
        }

        viewModelScope.launch {
            val current = _editableTransaction.value ?: return@launch
            if (current.merchantName.isBlank() || current.amount <= BigDecimal.ZERO) return@launch
            if (_showSplitEditor.value && _splits.value.isNotEmpty() && !validateSplits()) return@launch

            _isSaving.value = true
            try {
            // Intercept on first save attempt: if category or merchant changed and there are
            // past/future transactions, show the pre-save bulk options sheet.
            if (!skipPreSaveSheet) {
                val originalMerchant = _originalMerchantNameOnEdit.value
                val originalCategory = _originalCategoryOnEdit.value
                val originalType = _originalTypeOnEdit.value
                val categoryChanged = originalCategory != null && current.category != originalCategory
                val merchantChanged = originalMerchant != null &&
                    !current.merchantName.equals(originalMerchant, ignoreCase = true)
                val typeChanged = originalType != null && current.transactionType != originalType
                val existingCount = _existingTransactionCount.value
                if ((categoryChanged || merchantChanged || typeChanged) && existingCount > 0) {
                    val isSelf = current.transactionType == TransactionType.TRANSFER &&
                        current.transferKind == com.pennywiseai.tracker.data.database.entity.TransferKind.SELF_TRANSFER
                    val categoryMappingExists = if (categoryChanged && !isSelf) {
                        val mapped = withContext(Dispatchers.IO) {
                            merchantMappingRepository.getCategoryForMerchant(current.merchantName.trim())
                        }
                        mapped != null && mapped.equals(current.category, ignoreCase = true)
                    } else false
                    _preSaveBulkState.value = PreSaveBulkState(
                        existingCount = existingCount,
                        isSelfTransfer = isSelf,
                        categoryChanged = categoryChanged,
                        merchantChanged = merchantChanged,
                        typeChanged = typeChanged,
                        merchantName = current.merchantName,
                    )
                    _applyToPast.value = true
                    _applyToFuture.value = (categoryChanged && !isSelf && !categoryMappingExists) || merchantChanged
                    _isSaving.value = false
                    return@launch
                }
            }

            val wantsPastBulk =
                _bulkPastCategory.value || _bulkPastMerchant.value || _bulkPastType.value
            if (requireBulkCategoryConfirm && wantsPastBulk) {
                val merchantForMatch = current.merchantName.trim()
                if (merchantForMatch.isNotEmpty()) {
                    refreshMerchantDependentEditData(merchantForMatch)
                }
                if (_existingTransactionCount.value > 0) {
                    val merchantKeyForPreview =
                        effectiveBulkCategoryMerchantKey(current.merchantName.trim())
                    val notBefore = notBeforeForCurrentBulkScope()
                    _bulkCategoryPreviewRows.value = if (_bulkPastCategory.value) {
                        transactionRepository.getBulkCategoryPreviewForMerchant(
                            merchantName = merchantKeyForPreview,
                            excludeId = current.id,
                            notBefore = notBefore,
                        )
                    } else {
                        emptyList()
                    }
                    _bulkCategorySaveConfirm.value = BulkCategorySaveConfirmParams(
                        merchantName = merchantKeyForPreview,
                        category = current.category,
                        otherCount = _existingTransactionCount.value,
                        scope = _bulkCategoryDateScope.value,
                        pastCategory = _bulkPastCategory.value,
                        pastMerchant = _bulkPastMerchant.value,
                        pastType = _bulkPastType.value,
                    )
                    _isSaving.value = false
                    return@launch
                }
            }

            val latest = _editableTransaction.value ?: run {
                _isSaving.value = false
                return@launch
            }
            val originalTxn = _transaction.value
            val normalizedTransaction = latest.copy(
                merchantName = normalizeMerchantName(latest.merchantName),
                tags = _pendingTags.value.joinToString(",")
            )

            val hadSplitLinesSnapshot = _showSplitEditor.value && _splits.value.isNotEmpty()
            val snapshotOriginalMerchant = _originalMerchantNameOnEdit.value
            val snapshotOriginalCategory = _originalCategoryOnEdit.value
            val snapshotIncomingCategory = _bulkIncomingCategory.value
            val snapshotIncomingMerchant = _bulkIncomingMerchant.value

            val removedIds = _removedReceiptIds.value.toList()
            val removedPaths = removedIds.mapNotNull { id ->
                _existingReceipts.value.find { it.id == id }?.path
            }

            val splitEntities = _splits.value.map { s ->
                TransactionSplitEntity(
                    id = s.id,
                    transactionId = normalizedTransaction.id,
                    category = s.category,
                    amount = s.amount,
                    tags = s.tags.joinToString(","),
                )
            }

            val bulkNotBefore = if (_bulkPastCategory.value || _bulkPastMerchant.value || _bulkPastType.value) {
                notBeforeForCurrentBulkScope()
            } else {
                null
            }

            val updateRequest = UpdateTransactionRequest(
                original = originalTxn ?: normalizedTransaction,
                updated = normalizedTransaction,
                pendingReceiptUris = _pendingReceiptUris.value,
                removedReceiptIds = removedIds,
                removedReceiptPaths = removedPaths,
                updateCategoryForMerchant = _bulkPastCategory.value,
                bulkCategoryNotBefore = bulkNotBefore,
                bulkCategoryMerchantName = effectiveBulkCategoryMerchantKey(latest.merchantName.trim()),
                bulkSyncMerchantName = _bulkPastMerchant.value,
                bulkSyncTransactionType = _bulkPastType.value,
                showSplitEditor = _showSplitEditor.value,
                hasOriginalSplits = _originalSplits.value.isNotEmpty(),
                splits = splitEntities,
            )

            // Execute all DB mutations — DB is fully committed when this returns
            val result = withContext(Dispatchers.IO) {
                updateTransactionUseCase.execute(updateRequest)
            }

            // Update in-memory state to reflect committed DB values
            _transaction.value = normalizedTransaction
            _pendingReceiptUris.value = emptyList()
            _removedReceiptIds.value = emptySet()
            _pendingTags.value = emptyList()
            _budgetImpactType.value = normalizedTransaction.budgetImpactType
            _budgetCategory.value = normalizedTransaction.budgetCategory

            // Clear edit state
            _isEditMode.value = false
            _editableTransaction.value = null
            _errorMessage.value = null
            clearAllBulkPropagationSelections()
            _existingTransactionCount.value = 0
            _bulkCategoryDateScope.value = BulkCategoryDateScope.ALL_TIME
            _bulkCategorySaveConfirm.value = null
            _bulkCategoryPreviewRows.value = emptyList()
            _originalMerchantNameOnEdit.value = null
            _originalCategoryOnEdit.value = null
            _suggestedMerchantRenames.value = emptyList()

            // Show undo snackbar if bulk category was applied to other transactions
            if (result.bulkCategoryUndoCount > 0) {
                _bulkCategoryUndoSnackCount.value = result.bulkCategoryUndoCount
            }

            // Apply incoming SMS rules (merchant→category mapping, raw→display alias)
            try {
                if (snapshotIncomingCategory) {
                    merchantMappingRepository.setMapping(
                        normalizedTransaction.merchantName.trim(),
                        normalizedTransaction.category,
                    )
                }
                if (snapshotIncomingMerchant &&
                    snapshotOriginalMerchant != null &&
                    !normalizedTransaction.merchantName.equals(snapshotOriginalMerchant, ignoreCase = true)
                ) {
                    merchantAliasRepository.setAlias(
                        snapshotOriginalMerchant.trim(),
                        normalizedTransaction.merchantName.trim(),
                    )
                    val bodyExtras = SmsMerchantAliasHints.deriveExtraAliasSources(
                        smsBody = normalizedTransaction.smsBody,
                        rawMerchant = snapshotOriginalMerchant.trim(),
                        displayMerchant = normalizedTransaction.merchantName.trim(),
                    )
                    for (src in bodyExtras.take(2)) {
                        val audit = MerchantAliasAuditor.audit(
                            MerchantAliasEntity(
                                sourceMerchant = src,
                                displayName = normalizedTransaction.merchantName.trim(),
                            ),
                        )
                        if (audit.risk == MerchantAliasAuditor.RiskLevel.OK) {
                            merchantAliasRepository.setAlias(src, normalizedTransaction.merchantName.trim())
                        }
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Saved, but failed to update future SMS rules: ${e.message}"
            }

            pendingFutureParsingPrompt = if (userPreferencesRepository.isFutureParsingPromptDisabledOnce()) {
                null
            } else {
                val mappedCategory = withContext(Dispatchers.IO) {
                    merchantMappingRepository.getCategoryForMerchant(normalizedTransaction.merchantName.trim())
                }
                val categoryMappingAlreadyExists = mappedCategory != null &&
                    mappedCategory.equals(normalizedTransaction.category, ignoreCase = true)
                buildFutureParsingPrompt(
                    saved = normalizedTransaction,
                    originalMerchant = snapshotOriginalMerchant,
                    originalCategoryOnEdit = snapshotOriginalCategory,
                    hadSplitLines = hadSplitLinesSnapshot,
                    appliedIncomingCategory = snapshotIncomingCategory,
                    appliedIncomingMerchant = snapshotIncomingMerchant,
                    categoryMappingAlreadyExists = categoryMappingAlreadyExists,
                )
            }

            // Scan for other transactions with a similar old merchant name to offer batch rename
            val needSimilarMerchantScan = snapshotOriginalMerchant != null &&
                !normalizedTransaction.merchantName.equals(snapshotOriginalMerchant, ignoreCase = true)
            if (needSimilarMerchantScan) {
                val origMerchantForScan = snapshotOriginalMerchant!!
                try {
                    val similar = withContext(Dispatchers.IO) {
                        transactionRepository.findSimilarTransactionsForRename(
                            originalMerchant = origMerchantForScan,
                            newMerchantName = normalizedTransaction.merchantName,
                            excludeTransactionId = normalizedTransaction.id,
                        )
                    }
                    if (similar.isNotEmpty()) {
                        _merchantRenameReview.value = MerchantRenameReviewState(
                            newMerchantName = normalizedTransaction.merchantName,
                            transactions = similar,
                        )
                    } else {
                        finishSaveFlow()
                    }
                } catch (e: Exception) {
                    _errorMessage.value = "Could not load similar transactions: ${e.message}"
                    finishSaveFlow()
                }
            } else {
                finishSaveFlow()
            }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Save failed"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun cancelEdit() {
        exitEditMode()
    }
    
    fun clearSaveSuccess() {
        _saveSuccess.value = false
    }

    fun approveCurrentRenameCandidate() {
        advanceRenameReview(approved = true)
    }

    fun skipCurrentRenameCandidate() {
        advanceRenameReview(approved = false)
    }

    fun applyAllRenameCandidates() {
        val state = _merchantRenameReview.value ?: return
        val remainingIds = state.transactions
            .drop(state.currentIndex)
            .map { it.transactionId }
        finishMerchantRenameReview(state.approvedTransactionIds + remainingIds)
    }

    fun dismissMerchantRenameReview() {
        _merchantRenameReview.value = null
        finishSaveFlow()
    }

    private fun advanceRenameReview(approved: Boolean) {
        val state = _merchantRenameReview.value ?: return
        val current = state.currentTransaction ?: return
        val updated = state.copy(
            currentIndex = state.currentIndex + 1,
            approvedTransactionIds = if (approved) {
                state.approvedTransactionIds + current.transactionId
            } else {
                state.approvedTransactionIds
            },
        )
        if (updated.isComplete) {
            finishMerchantRenameReview(updated.approvedTransactionIds)
        } else {
            _merchantRenameReview.value = updated
        }
    }

    private fun finishMerchantRenameReview(approvedTransactionIds: List<Long>) {
        val state = _merchantRenameReview.value ?: return
        if (approvedTransactionIds.isEmpty()) {
            _merchantRenameReview.value = null
            finishSaveFlow()
            return
        }
        viewModelScope.launch {
            _merchantRenameReview.value = state.copy(isApplying = true)
            try {
                val idToMerchant = state.transactions.associate { it.transactionId to it.currentMerchantName }
                for (id in approvedTransactionIds.distinct()) {
                    val oldName = idToMerchant[id] ?: continue
                    transactionRepository.updateMerchantNameForTransaction(id, state.newMerchantName)
                    merchantAliasRepository.setAlias(oldName, state.newMerchantName)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to rename some transactions: ${e.message}"
            } finally {
                _merchantRenameReview.value = null
                finishSaveFlow()
            }
        }
    }

    fun confirmFutureParsing(extraBodyAliasSources: Collection<String> = emptyList()) {
        val prompt = _futureParsingPrompt.value ?: return
        val allowedExtras = prompt.optionalBodyAliasSources.toSet()
        val extrasToSave = extraBodyAliasSources.map { it.trim() }.filter { it in allowedExtras }.distinct()
        viewModelScope.launch {
            try {
                if (prompt.merchantChanged) {
                    merchantAliasRepository.setAlias(
                        prompt.rawMerchantName,
                        prompt.displayMerchantName,
                    )
                    for (src in extrasToSave) {
                        merchantAliasRepository.setAlias(src, prompt.displayMerchantName)
                    }
                }
                merchantMappingRepository.setMapping(
                    prompt.displayMerchantName,
                    prompt.category,
                )
            } catch (e: Exception) {
                _errorMessage.value = "Failed to save parsing preferences: ${e.message}"
            } finally {
                _futureParsingPrompt.value = null
                _saveSuccess.value = true
            }
        }
    }

    fun dismissFutureParsing() {
        _futureParsingPrompt.value = null
        _saveSuccess.value = true
    }

    fun neverFutureParsing() {
        viewModelScope.launch {
            userPreferencesRepository.disableFutureParsingPrompt()
            _futureParsingPrompt.value = null
            _saveSuccess.value = true
        }
    }

    private fun buildFutureParsingPrompt(
        saved: TransactionEntity,
        originalMerchant: String?,
        originalCategoryOnEdit: String?,
        hadSplitLines: Boolean,
        appliedIncomingCategory: Boolean,
        appliedIncomingMerchant: Boolean,
        categoryMappingAlreadyExists: Boolean = false,
    ): FutureParsingPromptState? {
        if (hadSplitLines) return null

        val merchantChanged = originalMerchant != null &&
            !saved.merchantName.equals(originalMerchant, ignoreCase = true)
        val categoryChanged = originalCategoryOnEdit != null && saved.category != originalCategoryOnEdit
        val promptMerchant = merchantChanged && !appliedIncomingMerchant
        val promptCategory = categoryChanged && !appliedIncomingCategory && !categoryMappingAlreadyExists
        if (!promptMerchant && !promptCategory) return null

        val rawForHints = originalMerchant?.trim().orEmpty().ifEmpty { saved.merchantName }
        val snippet = SmsMerchantAliasHints.snippetForUi(saved.smsBody)
        val bodyExtras = if (promptMerchant && rawForHints.isNotEmpty()) {
            SmsMerchantAliasHints.deriveExtraAliasSources(
                smsBody = saved.smsBody,
                rawMerchant = rawForHints,
                displayMerchant = saved.merchantName,
            )
        } else {
            emptyList()
        }

        return FutureParsingPromptState(
            rawMerchantName = originalMerchant ?: saved.merchantName,
            displayMerchantName = saved.merchantName,
            category = saved.category,
            merchantChanged = promptMerchant,
            categoryChanged = promptCategory,
            smsSnippet = snippet,
            optionalBodyAliasSources = bodyExtras,
        )
    }

    private fun finishSaveFlow() {
        val prompt = pendingFutureParsingPrompt
        pendingFutureParsingPrompt = null
        if (prompt != null) {
            _futureParsingPrompt.value = prompt
        } else {
            _saveSuccess.value = true
        }
    }
    
    private fun validateMerchantName(name: String) {
        if (name.isBlank()) {
            _errorMessage.value = "Merchant name is required"
        } else {
            _errorMessage.value = null
        }
    }
    
    /**
     * Merchant key used to find sibling transactions for bulk updates.
     * Uses the name from when edit mode started when available so renames on the
     * current row still match historical rows.
     */
    private fun effectiveBulkCategoryMerchantKey(fieldMerchantTrimmed: String): String {
        val raw = _originalMerchantNameOnEdit.value?.trim()?.takeIf { it.isNotBlank() } ?: fieldMerchantTrimmed
        return normalizeMerchantName(raw)
    }

    private fun normalizeMerchantName(name: String): String = name.trim()
    
    fun getReportUrl(): String {
        val txn = _transaction.value ?: return ""
        
        // Use the original SMS body if available
        val smsBody = txn.smsBody ?: "Transaction: ${txn.merchantName} - ${txn.amount}"
        
        // Use the original SMS sender if available
        val sender = txn.smsSender ?: ""
        
        android.util.Log.d("TransactionDetailVM", "Generating report URL for transaction")
        
        // URL encode the parameters
        val encodedMessage = java.net.URLEncoder.encode(smsBody, "UTF-8")
        val encodedSender = java.net.URLEncoder.encode(sender, "UTF-8")
        
        // Encrypt device data for verification
        val encryptedDeviceData = com.pennywiseai.tracker.utils.DeviceEncryption.encryptDeviceData(context)
        val encodedDeviceData = if (encryptedDeviceData != null) {
            java.net.URLEncoder.encode(encryptedDeviceData, "UTF-8")
        } else {
            ""
        }
        
        // Create the report URL using hash fragment for privacy
        val url = "${Constants.Links.WEB_PARSER_URL}/#message=$encodedMessage&sender=$encodedSender&device=$encodedDeviceData&autoparse=true"
        android.util.Log.d("TransactionDetailVM", "Report URL: ${url.take(200)}...")
        
        return url
    }
    
    fun showDeleteDialog() {
        _showDeleteDialog.value = true
    }
    
    fun hideDeleteDialog() {
        _showDeleteDialog.value = false
    }
    
    fun deleteTransaction() {
        viewModelScope.launch {
            _transaction.value?.let { txn ->
                _isDeleting.value = true
                _showDeleteDialog.value = false

                try {
                    // Delete all receipt files before removing the transaction
                    _existingReceipts.value.forEach { receiptManager.deleteReceipt(it.path) }
                    txn.receiptPath?.let { receiptManager.deleteReceipt(it) } // legacy
                    transactionRepository.deleteTransaction(txn)
                    _deleteSuccess.value = true
                } catch (e: Exception) {
                    _errorMessage.value = "Failed to delete transaction"
                } finally {
                    _isDeleting.value = false
                }
            }
        }
    }

    // ========== Receipt Management ==========

    private val _existingReceipts = MutableStateFlow<List<ExistingReceipt>>(emptyList())
    val existingReceipts: StateFlow<List<ExistingReceipt>> = _existingReceipts.asStateFlow()

    private val _pendingReceiptUris = MutableStateFlow<List<Uri>>(emptyList())
    val pendingReceiptUris: StateFlow<List<Uri>> = _pendingReceiptUris.asStateFlow()

    private val _removedReceiptIds = MutableStateFlow<Set<Long>>(emptySet())
    val removedReceiptIds: StateFlow<Set<Long>> = _removedReceiptIds.asStateFlow()

    private val _fullScreenReceiptUri = MutableStateFlow<Uri?>(null)
    val fullScreenReceiptUri: StateFlow<Uri?> = _fullScreenReceiptUri.asStateFlow()

    fun showFullScreenReceipt(uri: Uri) { _fullScreenReceiptUri.value = uri }
    fun hideFullScreenReceipt() { _fullScreenReceiptUri.value = null }

    fun addPendingReceiptUri(uri: Uri) {
        _pendingReceiptUris.value = _pendingReceiptUris.value + uri
    }

    fun removePendingReceiptUri(index: Int) {
        val current = _pendingReceiptUris.value.toMutableList()
        if (index in current.indices) current.removeAt(index)
        _pendingReceiptUris.value = current
    }

    fun removeExistingReceipt(receiptId: Long) {
        _removedReceiptIds.value = _removedReceiptIds.value + receiptId
    }

    fun createCameraUri(): Uri = receiptManager.createCameraUri()

    private suspend fun loadReceiptUris(transaction: TransactionEntity) {
        val receipts = transactionRepository.getReceiptsForTransaction(transaction.id)
        val existing = receipts.mapNotNull { entity ->
            val file = receiptManager.getReceiptFile(entity.filePath)
            if (file.exists()) ExistingReceipt(entity.id, file.toUri(), entity.filePath) else null
        }
        _existingReceipts.value = existing
    }

    // ========== Loan Management ==========

    private val _loan = MutableStateFlow<LoanEntity?>(null)
    val loan: StateFlow<LoanEntity?> = _loan.asStateFlow()

    private val _showMarkAsLoanSheet = MutableStateFlow(false)
    val showMarkAsLoanSheet: StateFlow<Boolean> = _showMarkAsLoanSheet.asStateFlow()

    val recentPersonNames: StateFlow<List<String>> = loanRepository.getRecentPersonNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun showMarkAsLoanSheet() { _showMarkAsLoanSheet.value = true }
    fun hideMarkAsLoanSheet() { _showMarkAsLoanSheet.value = false }

    private fun loadLoan(loanId: Long) {
        viewModelScope.launch {
            _loan.value = loanRepository.getLoanById(loanId)
        }
    }

    fun createLoanFromTransaction(personName: String, direction: LoanDirection, note: String?) {
        val txn = _transaction.value ?: return
        viewModelScope.launch {
            try {
                // Check for existing loan in the OPPOSITE direction first (this is a repayment)
                val oppositeDirection = if (direction == LoanDirection.LENT) LoanDirection.BORROWED else LoanDirection.LENT
                val oppositeLoan = loanRepository.findActiveLoanForPerson(personName, oppositeDirection)

                if (oppositeLoan != null) {
                    // Record as repayment on the opposite loan
                    loanRepository.recordRepayment(oppositeLoan.id, txn.id)
                    _transaction.value = transactionRepository.getTransactionById(txn.id)
                    _loan.value = loanRepository.getLoanById(oppositeLoan.id)
                    _showMarkAsLoanSheet.value = false
                    return@launch
                }

                // Check if an active loan already exists for this person + same direction
                val existingLoan = loanRepository.findActiveLoanForPerson(personName, direction)
                val loanId = if (existingLoan != null) {
                    // Merge into existing loan
                    loanRepository.addToExistingLoan(existingLoan.id, txn.amount, txn.id)
                    existingLoan.id
                } else {
                    // Create new loan
                    loanRepository.createLoan(
                        personName = personName,
                        direction = direction,
                        amount = txn.amount,
                        currency = txn.currency,
                        note = note,
                        sourceTransactionId = txn.id
                    )
                }
                _transaction.value = transactionRepository.getTransactionById(txn.id)
                _loan.value = loanRepository.getLoanById(loanId)
                _showMarkAsLoanSheet.value = false
            } catch (e: Exception) {
                _errorMessage.value = "Failed to create loan: ${e.message}"
            }
        }
    }

    fun unlinkLoan() {
        val txn = _transaction.value ?: return
        val loanId = txn.loanId ?: return
        viewModelScope.launch {
            try {
                loanRepository.unlinkTransaction(txn.id, loanId)
                _transaction.value = transactionRepository.getTransactionById(txn.id)
                _loan.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to unlink loan: ${e.message}"
            }
        }
    }

    // ========== Goal Management ==========

    val availableGoals: StateFlow<List<GoalEntity>> = goalRepository.getActiveGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _linkedGoalContribution = MutableStateFlow<GoalContributionEntity?>(null)
    val linkedGoalContribution: StateFlow<GoalContributionEntity?> = _linkedGoalContribution.asStateFlow()

    private val _showLinkGoalSheet = MutableStateFlow(false)
    val showLinkGoalSheet: StateFlow<Boolean> = _showLinkGoalSheet.asStateFlow()

    fun showLinkGoalSheet() { _showLinkGoalSheet.value = true }
    fun hideLinkGoalSheet() { _showLinkGoalSheet.value = false }

    private fun loadLinkedGoal(transactionId: Long) {
        viewModelScope.launch {
            _linkedGoalContribution.value = goalRepository.getLinkedGoalForTransaction(transactionId)
        }
    }

    fun linkToGoal(goalId: Long) {
        val txn = _transaction.value ?: return
        viewModelScope.launch {
            try {
                goalRepository.linkTransaction(goalId, txn.id, txn.amount, null)
                _linkedGoalContribution.value = goalRepository.getLinkedGoalForTransaction(txn.id)
                _showLinkGoalSheet.value = false
            } catch (e: Exception) {
                _errorMessage.value = "Failed to link goal: ${e.message}"
            }
        }
    }

    fun unlinkFromGoal() {
        val contribution = _linkedGoalContribution.value ?: return
        viewModelScope.launch {
            try {
                goalRepository.unlinkTransaction(contribution.id)
                _linkedGoalContribution.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to unlink goal: ${e.message}"
            }
        }
    }

    // ========== Similar Transactions ==========

    private val _similarTransactions = MutableStateFlow<List<TransactionEntity>>(emptyList())
    val similarTransactions: StateFlow<List<TransactionEntity>> = _similarTransactions.asStateFlow()

    private fun loadSimilarTransactions(merchantName: String) {
        viewModelScope.launch {
            try {
                val similar = withContext(Dispatchers.IO) {
                    transactionRepository.getSimilarTransactionsForMerchant(
                        merchantName,
                        excludeTransactionId = _transaction.value?.id ?: -1L
                    )
                }.take(5)  // Limit to 5 similar transactions for display
                _similarTransactions.value = similar
            } catch (e: Exception) {
                _similarTransactions.value = emptyList()
            }
        }
    }
}

data class ExistingReceipt(val id: Long, val uri: Uri, val path: String)

enum class CategorySuggestionSource {
    MERCHANT,
    USED_TODAY
}

data class CategorySuggestionsState(
    val categories: List<String> = emptyList(),
    val source: CategorySuggestionSource? = null,
    val merchantName: String = ""
)

data class MerchantRenameReviewState(
    val newMerchantName: String,
    val transactions: List<TransactionRenameCandidate>,
    val currentIndex: Int = 0,
    val approvedTransactionIds: List<Long> = emptyList(),
    val isApplying: Boolean = false,
) {
    val currentTransaction: TransactionRenameCandidate?
        get() = transactions.getOrNull(currentIndex)

    val isComplete: Boolean
        get() = currentIndex >= transactions.size

    val totalCount: Int
        get() = transactions.size
}

