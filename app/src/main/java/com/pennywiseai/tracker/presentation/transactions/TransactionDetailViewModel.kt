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
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionSplitEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.ui.components.SplitItem
import com.pennywiseai.tracker.data.receipt.ReceiptManager
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.BudgetGroupRepository
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.LoanRepository
import com.pennywiseai.tracker.data.repository.MerchantAliasRepository
import com.pennywiseai.tracker.data.repository.MerchantMappingRepository
import com.pennywiseai.tracker.data.repository.TransactionGroupRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.model.TransactionRenameCandidate
import com.pennywiseai.tracker.data.database.entity.TransactionGroupEntity
import com.pennywiseai.tracker.core.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDateTime
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val merchantMappingRepository: MerchantMappingRepository,
    private val merchantAliasRepository: MerchantAliasRepository,
    private val categoryRepository: CategoryRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val loanRepository: LoanRepository,
    private val budgetGroupRepository: BudgetGroupRepository,
    private val transactionGroupRepository: TransactionGroupRepository,
    private val currencyConversionService: CurrencyConversionService,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val receiptManager: ReceiptManager,
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
    
    private val _applyToAllFromMerchant = MutableStateFlow(false)
    val applyToAllFromMerchant: StateFlow<Boolean> = _applyToAllFromMerchant.asStateFlow()
    
    private val _updateExistingTransactions = MutableStateFlow(false)
    val updateExistingTransactions: StateFlow<Boolean> = _updateExistingTransactions.asStateFlow()
    
    private val _existingTransactionCount = MutableStateFlow(0)

    private val _originalMerchantNameOnEdit = MutableStateFlow<String?>(null)
    private val _suggestedMerchantRename = MutableStateFlow<String?>(null)
    val suggestedMerchantRename: StateFlow<String?> = _suggestedMerchantRename.asStateFlow()

    private val _merchantRenameReview = MutableStateFlow<MerchantRenameReviewState?>(null)
    val merchantRenameReview: StateFlow<MerchantRenameReviewState?> = _merchantRenameReview.asStateFlow()
    
    private val _showDeleteDialog = MutableStateFlow(false)
    val showDeleteDialog: StateFlow<Boolean> = _showDeleteDialog.asStateFlow()
    
    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    private val _deleteSuccess = MutableStateFlow(false)
    val deleteSuccess: StateFlow<Boolean> = _deleteSuccess.asStateFlow()
    val existingTransactionCount: StateFlow<Int> = _existingTransactionCount.asStateFlow()

    // Budget impact state (for INCOME transactions only)
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
            viewModelScope.launch {
                _existingTransactionCount.value = transactionRepository.getOtherTransactionCountForMerchant(
                    txn.merchantName,
                    txn.id,
                )
                loadMerchantRenameSuggestion(txn.merchantName)
                loadMerchantCategorySuggestions(txn.merchantName, txn.id)
            }
        }
    }

    private suspend fun loadMerchantRenameSuggestion(sourceMerchant: String) {
        val knownMerchants = transactionRepository.getDistinctMerchantNames()
        val suggested = merchantAliasRepository.suggestDisplayName(
            sourceMerchant = sourceMerchant,
            knownMerchants = knownMerchants
        )
        _suggestedMerchantRename.value = suggested?.takeIf {
            !it.equals(sourceMerchant, ignoreCase = true)
        }
    }

    fun applyMerchantRenameSuggestion() {
        val suggested = _suggestedMerchantRename.value ?: return
        _editableTransaction.update { current ->
            current?.copy(merchantName = suggested)
        }
    }

    private suspend fun loadMerchantCategorySuggestions(merchantName: String, transactionId: Long) {
        val mappingCategory = merchantMappingRepository.getCategoryForMerchant(merchantName)
        _merchantSuggestionCategories.value = transactionRepository.getSuggestedCategoriesForMerchant(
            merchantName = merchantName,
            excludeTransactionId = transactionId,
            merchantMappingCategory = mappingCategory
        )
    }
    
    fun exitEditMode() {
        _editableTransaction.value = null
        _isEditMode.value = false
        _errorMessage.value = null
        _applyToAllFromMerchant.value = false
        _updateExistingTransactions.value = false
        _existingTransactionCount.value = 0
        _pendingReceiptUris.value = emptyList()
        _removedReceiptIds.value = emptySet()
        _pendingTags.value = emptyList()
        _tagQuery.value = ""
        _allUsedTags.value = emptyList()
        _merchantSuggestionCategories.value = emptyList()
        _originalMerchantNameOnEdit.value = null
        _suggestedMerchantRename.value = null
        _merchantRenameReview.value = null

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

    fun toggleApplyToAllFromMerchant() {
        _applyToAllFromMerchant.value = !_applyToAllFromMerchant.value
    }
    
    fun toggleUpdateExistingTransactions() {
        _updateExistingTransactions.value = !_updateExistingTransactions.value
    }
    
    fun updateMerchantName(name: String) {
        _editableTransaction.update { current ->
            current?.copy(merchantName = name)
        }
        validateMerchantName(name)
    }
    
    fun updateAmount(amountStr: String) {
        val amount = amountStr.toBigDecimalOrNull()
        if (amount != null && amount > BigDecimal.ZERO) {
            _editableTransaction.update { current ->
                current?.copy(amount = amount)
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
                        it == com.pennywiseai.tracker.data.database.entity.TransferKind.OTHERS_TRANSFER
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
            current?.copy(transferKind = kind)
        }
    }

    fun updateCategory(category: String) {
        _editableTransaction.update { current ->
            current?.copy(category = category.ifEmpty { "Others" })
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

    fun saveChanges() {
        val toSave = _editableTransaction.value ?: return

        // Validate before saving
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
            _isSaving.value = true
            try {
                // Handle receipt deletions
                val removedIds = _removedReceiptIds.value
                for (id in removedIds) {
                    val receipt = _existingReceipts.value.find { it.id == id }
                    receipt?.let {
                        receiptManager.deleteReceipt(it.path)
                        transactionRepository.deleteReceipt(id)
                    }
                }

                // Save new pending receipts
                val newPaths = receiptManager.saveReceipts(_pendingReceiptUris.value)
                if (newPaths.isNotEmpty()) {
                    transactionRepository.insertReceipts(toSave.id, newPaths)
                }

                // Normalize merchant name and persist tags
                val normalizedTransaction = toSave.copy(
                    merchantName = normalizeMerchantName(toSave.merchantName),
                    tags = _pendingTags.value.joinToString(",")
                )

                transactionRepository.updateTransaction(normalizedTransaction)

                // Update account balance if account was changed or added
                val originalTxn = _transaction.value
                val oldBank = originalTxn?.bankName
                val oldAccount = originalTxn?.accountNumber
                val newBank = normalizedTransaction.bankName
                val newAccount = normalizedTransaction.accountNumber
                val accountChanged = oldBank != newBank || oldAccount != newAccount

                if (accountChanged && newBank != null && newAccount != null) {
                    val currentBalance = accountBalanceRepository.getLatestBalance(newBank, newAccount)
                    if (currentBalance != null) {
                        val balanceChange = when (normalizedTransaction.transactionType) {
                            TransactionType.INCOME -> normalizedTransaction.amount
                            TransactionType.EXPENSE, TransactionType.CREDIT -> -normalizedTransaction.amount
                            TransactionType.TRANSFER -> -normalizedTransaction.amount
                            TransactionType.INVESTMENT -> -normalizedTransaction.amount
                        }
                        accountBalanceRepository.insertBalance(
                            currentBalance.copy(
                                id = 0,
                                balance = currentBalance.balance + balanceChange,
                                timestamp = normalizedTransaction.dateTime,
                                transactionId = normalizedTransaction.id,
                                sourceType = "TRANSACTION",
                                smsSource = null
                            )
                        )
                    }
                }

                // Save or remove splits
                val currentSplits = _splits.value
                if (_showSplitEditor.value && currentSplits.isNotEmpty()) {
                    // Convert SplitItems to entities and save
                    val splitEntities = currentSplits.map { item ->
                        TransactionSplitEntity(
                            id = item.id,
                            transactionId = normalizedTransaction.id,
                            category = item.category,
                            amount = item.amount,
                            tags = item.tags.joinToString(",")
                        )
                    }
                    transactionRepository.saveSplits(normalizedTransaction.id, splitEntities)
                    _hasSplits.value = true
                    _originalSplits.value = currentSplits
                } else if (_originalSplits.value.isNotEmpty()) {
                    // Splits were removed, delete them from database
                    transactionRepository.removeSplits(normalizedTransaction.id)
                    _hasSplits.value = false
                    _originalSplits.value = emptyList()
                }

                // Save merchant mapping if checkbox is checked
                if (_applyToAllFromMerchant.value) {
                    merchantMappingRepository.setMapping(
                        normalizedTransaction.merchantName,
                        normalizedTransaction.category
                    )
                }

                // Update existing transactions if checkbox is checked
                if (_updateExistingTransactions.value) {
                    transactionRepository.updateCategoryForMerchant(
                        normalizedTransaction.merchantName,
                        normalizedTransaction.category
                    )
                }

                val originalMerchant = _originalMerchantNameOnEdit.value
                var pendingRenameReview = false
                if (originalMerchant != null &&
                    !normalizedTransaction.merchantName.equals(originalMerchant, ignoreCase = true)
                ) {
                    merchantAliasRepository.setAlias(
                        originalMerchant,
                        normalizedTransaction.merchantName
                    )
                    val similarTransactions = transactionRepository.findSimilarTransactionsForRename(
                        originalMerchant = originalMerchant,
                        newMerchantName = normalizedTransaction.merchantName,
                        excludeTransactionId = normalizedTransaction.id,
                    )
                    if (similarTransactions.isNotEmpty()) {
                        _merchantRenameReview.value = MerchantRenameReviewState(
                            newMerchantName = normalizedTransaction.merchantName,
                            transactions = similarTransactions,
                        )
                        pendingRenameReview = true
                    }
                }

                _transaction.value = normalizedTransaction
                _pendingReceiptUris.value = emptyList()
                _removedReceiptIds.value = emptySet()
                loadReceiptUris(normalizedTransaction)
                _pendingTags.value = emptyList()
                if (!pendingRenameReview) {
                    _saveSuccess.value = true
                }
                _isEditMode.value = false
                _editableTransaction.value = null
                _errorMessage.value = null
                _applyToAllFromMerchant.value = false
                _updateExistingTransactions.value = false
                _existingTransactionCount.value = 0
                _originalMerchantNameOnEdit.value = null
                _suggestedMerchantRename.value = null
                _budgetImpactType.value = normalizedTransaction.budgetImpactType
                _budgetCategory.value = normalizedTransaction.budgetCategory
            } catch (e: Exception) {
                _errorMessage.value = "Failed to save changes: ${e.message}"
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
        _saveSuccess.value = true
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
            _saveSuccess.value = true
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
                _saveSuccess.value = true
            }
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
     * Normalizes merchant name to consistent format.
     * Converts all-caps to proper case, preserves already mixed case.
     */
    private fun normalizeMerchantName(name: String): String {
        val trimmed = name.trim()
        
        // If it's all uppercase, convert to proper case
        return if (trimmed == trimmed.uppercase()) {
            trimmed.lowercase().split(" ").joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
        } else {
            // Already has mixed case, keep as is
            trimmed
        }
    }
    
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

