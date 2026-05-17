package com.pennywiseai.tracker.presentation.add

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.receipt.ReceiptManager
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.BudgetGroupRepository
import com.pennywiseai.tracker.domain.usecase.AddTransactionUseCase
import com.pennywiseai.tracker.domain.usecase.AddSubscriptionUseCase
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.usecase.GetCategoriesUseCase
import android.util.Log
import com.pennywiseai.tracker.data.database.entity.TransactionSplitEntity
import com.pennywiseai.tracker.ui.components.SplitItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class AddViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val addTransactionUseCase: AddTransactionUseCase,
    private val addSubscriptionUseCase: AddSubscriptionUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val budgetGroupRepository: BudgetGroupRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val receiptManager: ReceiptManager
) : ViewModel() {
    
    // General UI State
    private val _uiState = MutableStateFlow(AddUiState())
    val uiState: StateFlow<AddUiState> = _uiState.asStateFlow()
    
    // Transaction Tab State
    private val _transactionUiState = MutableStateFlow(TransactionUiState())
    val transactionUiState: StateFlow<TransactionUiState> = _transactionUiState.asStateFlow()
    
    // Subscription Tab State
    private val _subscriptionUiState = MutableStateFlow(SubscriptionUiState())
    val subscriptionUiState: StateFlow<SubscriptionUiState> = _subscriptionUiState.asStateFlow()
    
    
    init {
        // Load base currency and set as default for both transaction and subscription
        viewModelScope.launch {
            val baseCurrency = userPreferencesRepository.baseCurrency.first()
            _transactionUiState.update { it.copy(currency = baseCurrency) }
            _subscriptionUiState.update { it.copy(currency = baseCurrency) }
        }
    }

    // Categories for dropdowns
    val categories = getCategoriesUseCase.execute()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // All accounts for selection in manual transaction entry
    val accounts = accountBalanceRepository.getAllLatestBalances()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val activeBudgetCategories = budgetGroupRepository.getActiveGroups()
        .map { groups ->
            groups.flatMap { it.categories.map { cat -> cat.categoryName } }.distinct().sorted()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Transaction Tab Functions
    fun updateSelectedAccount(account: AccountBalanceEntity?) {
        _transactionUiState.update { currentState ->
            currentState.copy(selectedAccount = account)
        }
    }

    // Transaction Tab Functions
    fun updateTransactionAmount(amount: String) {
        val filtered = amount.filter { it.isDigit() || it == '.' }
        val decimalCount = filtered.count { it == '.' }
        val validAmount = if (decimalCount <= 1) filtered else _transactionUiState.value.amount
        
        _transactionUiState.update { currentState ->
            currentState.copy(
                amount = validAmount,
                amountError = validateAmount(validAmount)
            )
        }
    }
    
    fun updateTransactionType(type: TransactionType) {
        _transactionUiState.update { currentState ->
            currentState.copy(
                transactionType = type,
                paymentChannel = if (type != TransactionType.EXPENSE && type != TransactionType.CREDIT) {
                    PaymentChannel.ACCOUNT
                } else currentState.paymentChannel,
                category = when (type) {
                    TransactionType.INCOME -> "Income"
                    TransactionType.EXPENSE, TransactionType.CREDIT -> "Others"
                    TransactionType.INVESTMENT -> "Investment"
                    else -> currentState.category
                },
                budgetImpactType = if (type != TransactionType.INCOME) null else currentState.budgetImpactType,
                budgetCategory = if (type != TransactionType.INCOME) null else currentState.budgetCategory,
                isSplitEnabled = if (type == TransactionType.TRANSFER) false else currentState.isSplitEnabled,
                splits = if (type == TransactionType.TRANSFER) emptyList() else currentState.splits
            )
        }
    }

    fun updatePaymentChannel(channel: PaymentChannel) {
        _transactionUiState.update { currentState ->
            currentState.copy(
                paymentChannel = channel,
                transactionType = if (channel == PaymentChannel.CREDIT_CARD) {
                    TransactionType.CREDIT
                } else {
                    TransactionType.EXPENSE
                }
            )
        }
    }
    
    fun updateTransactionMerchant(merchant: String) {
        _transactionUiState.update { currentState ->
            currentState.copy(
                merchant = merchant,
                merchantError = validateMerchant(merchant)
            )
        }
    }
    
    private val _applyToAllFromMerchant = MutableStateFlow(false)
    val applyToAllFromMerchant: StateFlow<Boolean> = _applyToAllFromMerchant.asStateFlow()

    fun toggleApplyToAllFromMerchant() {
        _applyToAllFromMerchant.value = !_applyToAllFromMerchant.value
    }

    fun updateTransactionCategory(category: String) {
        _transactionUiState.update {
            it.copy(
                category = category,
                categoryError = validateCategory(category)
            )
        }
    }

    fun createAndSelectTransactionCategory(name: String, color: String, isIncome: Boolean) {
        viewModelScope.launch {
            categoryRepository.createCategory(name, color, isIncome)
            updateTransactionCategory(name)
        }
    }

    fun updateTransactionDate(dateMillis: Long) {
        val instant = Instant.ofEpochMilli(dateMillis)
        val localDate = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        val currentTime = _transactionUiState.value.date.toLocalTime()
        val newDateTime = LocalDateTime.of(localDate, currentTime)
        
        _transactionUiState.update { currentState ->
            currentState.copy(date = newDateTime)
        }
    }
    
    fun updateTransactionTime(hour: Int, minute: Int) {
        val currentDate = _transactionUiState.value.date.toLocalDate()
        val newDateTime = currentDate.atTime(hour, minute)
        
        _transactionUiState.update { currentState ->
            currentState.copy(date = newDateTime)
        }
    }
    
    fun updateTransactionNotes(notes: String) {
        _transactionUiState.update { currentState ->
            currentState.copy(notes = notes)
        }
    }
    
    fun updateTransactionRecurring(isRecurring: Boolean) {
        _transactionUiState.update { currentState ->
            currentState.copy(isRecurring = isRecurring)
        }
    }

    fun updateTransactionCurrency(currency: String) {
        _transactionUiState.update { currentState ->
            currentState.copy(currency = currency)
        }
    }

    fun addReceiptUri(uri: Uri) {
        _transactionUiState.update { it.copy(receiptUris = it.receiptUris + uri) }
    }

    fun removeReceiptUri(index: Int) {
        _transactionUiState.update {
            val updated = it.receiptUris.toMutableList().also { list ->
                if (index in list.indices) list.removeAt(index)
            }
            it.copy(receiptUris = updated)
        }
    }

    fun createCameraUri(): Uri = receiptManager.createCameraUri()
    
    fun updateBudgetImpactType(type: BudgetImpactType?) {
        _transactionUiState.update { it.copy(budgetImpactType = type, budgetCategory = if (type == null) null else it.budgetCategory) }
    }

    fun updateBudgetCategory(category: String?) {
        _transactionUiState.update { it.copy(budgetCategory = category) }
    }

    fun toggleSplit() {
        val state = _transactionUiState.value
        if (state.isSplitEnabled) {
            _transactionUiState.update { it.copy(isSplitEnabled = false, splits = emptyList()) }
        } else {
            val total = state.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
            val half = total.divide(BigDecimal("2"), 2, RoundingMode.HALF_UP)
            val remainder = total - half
            _transactionUiState.update {
                it.copy(
                    isSplitEnabled = true,
                    splits = listOf(
                        SplitItem(category = it.category, amount = half),
                        SplitItem(category = "Others", amount = remainder)
                    )
                )
            }
        }
    }

    fun updateSplits(splits: List<SplitItem>) {
        _transactionUiState.update { it.copy(splits = splits) }
    }

    fun saveTransaction(onSuccess: () -> Unit) {
        val state = _transactionUiState.value
        
        val amountError = validateAmount(state.amount)
        val merchantError = validateMerchant(state.merchant)
        val categoryError = validateCategory(state.category)
        
        if (amountError != null || merchantError != null || categoryError != null) {
            _transactionUiState.update { currentState ->
                currentState.copy(
                    amountError = amountError,
                    merchantError = merchantError,
                    categoryError = categoryError
                )
            }
            return
        }
        
        viewModelScope.launch {
            try {
                _transactionUiState.update { it.copy(isLoading = true) }

                val amount = BigDecimal(state.amount)
                val selectedAccount = state.selectedAccount

                val receiptPaths = receiptManager.saveReceipts(state.receiptUris)

                val transactionId = addTransactionUseCase.execute(
                    amount = amount,
                    merchant = state.merchant.trim(),
                    category = state.category,
                    type = state.transactionType,
                    date = state.date,
                    notes = state.notes.takeIf { it.isNotBlank() },
                    isRecurring = state.isRecurring,
                    bankName = selectedAccount?.bankName,
                    accountLast4 = selectedAccount?.accountLast4,
                    currency = state.currency,
                    receiptPaths = receiptPaths,
                    budgetCategory = state.budgetCategory,
                    budgetImpactType = state.budgetImpactType
                )

                if (state.isSplitEnabled && state.splits.size >= 2 && transactionId != -1L) {
                    val splitEntities = state.splits.map { split ->
                        TransactionSplitEntity(
                            transactionId = transactionId,
                            category = split.category,
                            amount = split.amount,
                            tags = split.tags.joinToString(",")
                        )
                    }
                    transactionRepository.saveSplits(transactionId, splitEntities)
                }

                if (_applyToAllFromMerchant.value) {
                    transactionRepository.updateCategoryForMerchant(state.merchant.trim(), state.category)
                    _applyToAllFromMerchant.value = false
                }

                com.pennywiseai.tracker.widget.RecentTransactionsWidgetUpdateWorker.enqueueOneShot(appContext)

                onSuccess()
            } catch (e: Exception) {
                _transactionUiState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to save transaction"
                    )
                }
            }
        }
    }
    
    // Subscription Tab Functions
    fun updateSubscriptionService(service: String) {
        _subscriptionUiState.update { currentState ->
            currentState.copy(
                serviceName = service,
                serviceError = if (service.isBlank()) "Service name is required" else null
            )
        }
    }
    
    fun updateSubscriptionAmount(amount: String) {
        val filtered = amount.filter { it.isDigit() || it == '.' }
        val decimalCount = filtered.count { it == '.' }
        val validAmount = if (decimalCount <= 1) filtered else _subscriptionUiState.value.amount
        
        _subscriptionUiState.update { currentState ->
            currentState.copy(
                amount = validAmount,
                amountError = validateAmount(validAmount)
            )
        }
    }
    
    fun updateSubscriptionBillingCycle(cycle: String) {
        _subscriptionUiState.update { currentState ->
            currentState.copy(
                billingCycle = cycle,
                billingCycleError = null
            )
        }
    }
    
    fun updateSubscriptionNextPaymentDate(dateMillis: Long) {
        val instant = Instant.ofEpochMilli(dateMillis)
        val localDate = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        
        _subscriptionUiState.update { currentState ->
            currentState.copy(nextPaymentDate = localDate)
        }
    }
    
    fun updateSubscriptionCategory(category: String) {
        _subscriptionUiState.update { currentState ->
            currentState.copy(
                category = category,
                categoryError = validateCategory(category)
            )
        }
    }

    fun createAndSelectSubscriptionCategory(name: String, color: String, isIncome: Boolean) {
        viewModelScope.launch {
            categoryRepository.createCategory(name, color, isIncome)
            updateSubscriptionCategory(name)
        }
    }

    fun updateSubscriptionNotes(notes: String) {
        _subscriptionUiState.update { currentState ->
            currentState.copy(notes = notes)
        }
    }

    fun updateSubscriptionCurrency(currency: String) {
        _subscriptionUiState.update { currentState ->
            currentState.copy(currency = currency)
        }
    }
    
    fun saveSubscription(onSuccess: () -> Unit) {
        val state = _subscriptionUiState.value
        Log.d("AddViewModel", "saveSubscription called with state: $state")
        
        // Validate all fields
        val serviceError = if (state.serviceName.isBlank()) "Service name is required" else null
        val amountError = validateAmount(state.amount)
        val categoryError = validateCategory(state.category)
        
        Log.d("AddViewModel", "Validation - serviceError: $serviceError, amountError: $amountError, categoryError: $categoryError")
        
        if (serviceError != null || amountError != null || categoryError != null) {
            _subscriptionUiState.update { currentState ->
                currentState.copy(
                    serviceError = serviceError,
                    amountError = amountError,
                    categoryError = categoryError
                )
            }
            return
        }
        
        viewModelScope.launch {
            try {
                Log.d("AddViewModel", "Starting to save subscription...")
                _subscriptionUiState.update { it.copy(isLoading = true) }
                
                val amount = BigDecimal(state.amount)
                Log.d("AddViewModel", "Calling addSubscriptionUseCase.execute with: " +
                    "merchantName=${state.serviceName.trim()}, amount=$amount, " +
                    "nextPaymentDate=${state.nextPaymentDate}, billingCycle=${state.billingCycle}, " +
                    "category=${state.category}")
                
                val subscriptionId = addSubscriptionUseCase.execute(
                    merchantName = state.serviceName.trim(),
                    amount = amount,
                    nextPaymentDate = state.nextPaymentDate,
                    billingCycle = state.billingCycle,
                    category = state.category,
                    autoRenewal = false, // Not implemented yet
                    paymentReminder = false, // Not implemented yet
                    notes = state.notes.takeIf { it.isNotBlank() },
                    currency = state.currency
                )
                
                Log.d("AddViewModel", "Subscription saved successfully with ID: $subscriptionId")
                onSuccess()
            } catch (e: Exception) {
                Log.e("AddViewModel", "Error saving subscription", e)
                e.printStackTrace()
                _subscriptionUiState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to save subscription"
                    )
                }
            } finally {
                _subscriptionUiState.update { it.copy(isLoading = false) }
            }
        }
    }
    
    
    // Validation helpers
    private fun validateAmount(amount: String): String? {
        return when {
            amount.isBlank() -> "Amount is required"
            amount.toDoubleOrNull() == null -> "Invalid amount"
            amount.toDouble() <= 0 -> "Amount must be greater than 0"
            else -> null
        }
    }
    
    private fun validateMerchant(merchant: String): String? {
        return when {
            merchant.isBlank() -> "Merchant/Description is required"
            merchant.length < 2 -> "Too short"
            else -> null
        }
    }
    
    private fun validateCategory(category: String): String? {
        return when {
            category.isBlank() -> "Category is required"
            else -> null
        }
    }
}

enum class PaymentChannel {
    CASH, CREDIT_CARD, ACCOUNT
}

// UI State Classes
data class AddUiState(
    val currentTab: Int = 0
)

data class TransactionUiState(
    val amount: String = "",
    val amountError: String? = null,
    val transactionType: TransactionType = TransactionType.EXPENSE,
    val paymentChannel: PaymentChannel = PaymentChannel.ACCOUNT,
    val merchant: String = "",
    val merchantError: String? = null,
    val category: String = "Others",
    val categoryError: String? = null,
    val date: LocalDateTime = LocalDateTime.now(),
    val notes: String = "",
    val isRecurring: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedAccount: AccountBalanceEntity? = null,
    val currency: String = "INR",
    val receiptUris: List<Uri> = emptyList(),
    val budgetImpactType: BudgetImpactType? = null,
    val budgetCategory: String? = null,
    val isSplitEnabled: Boolean = false,
    val splits: List<SplitItem> = emptyList()
) {
    private val areSplitsBalanced: Boolean
        get() {
            if (splits.size < 2) return false
            val total = amount.toBigDecimalOrNull() ?: return false
            val splitsSum = splits.fold(BigDecimal.ZERO) { acc, s -> acc + s.amount }
            return (total - splitsSum).abs() <= BigDecimal("0.01")
        }

    val isValid: Boolean
        get() = amount.isNotBlank() &&
                amount.toDoubleOrNull() != null &&
                amount.toDouble() > 0 &&
                merchant.isNotBlank() &&
                category.isNotBlank() &&
                amountError == null &&
                merchantError == null &&
                categoryError == null &&
                (budgetImpactType == null || budgetCategory != null) &&
                (!isSplitEnabled || areSplitsBalanced)
}

data class SubscriptionUiState(
    val serviceName: String = "",
    val serviceError: String? = null,
    val amount: String = "",
    val amountError: String? = null,
    val billingCycle: String = "Monthly",
    val billingCycleError: String? = null,
    val nextPaymentDate: LocalDate = LocalDate.now().plusMonths(1),
    val category: String = "Subscriptions",
    val categoryError: String? = null,
    val notes: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val currency: String = "INR"
) {
    val isValid: Boolean
        get() = serviceName.isNotBlank() &&
                amount.isNotBlank() &&
                amount.toDoubleOrNull() != null &&
                amount.toDouble() > 0 &&
                billingCycle.isNotBlank() &&
                category.isNotBlank() &&
                serviceError == null &&
                amountError == null &&
                categoryError == null
}
