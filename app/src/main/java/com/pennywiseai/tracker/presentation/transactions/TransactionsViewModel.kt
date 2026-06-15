package com.pennywiseai.tracker.presentation.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.dao.TransactionSplitDao
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionSplitEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.SalaryMonthOverrideRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.presentation.common.PaymentMode
import com.pennywiseai.tracker.presentation.common.PaymentModeGroup
import com.pennywiseai.tracker.presentation.common.matchesPaymentModeGroup
import com.pennywiseai.tracker.presentation.common.TimePeriod
import com.pennywiseai.tracker.presentation.common.matchesAnalyticsSpendingFilter
import com.pennywiseai.tracker.presentation.common.paymentMode
import com.pennywiseai.tracker.presentation.common.TransactionTypeFilter
import com.pennywiseai.tracker.presentation.common.defaultTimePeriod
import com.pennywiseai.tracker.presentation.common.getDateRangeForPeriod
import com.pennywiseai.tracker.presentation.common.getDateRangeForYearMonth
import com.pennywiseai.tracker.presentation.common.isCcBillPayment
import com.pennywiseai.tracker.presentation.common.parseYearMonthNavPeriod
import com.pennywiseai.tracker.presentation.common.CurrencyGroupedTotals
import com.pennywiseai.tracker.presentation.common.CurrencyTotals
import com.pennywiseai.tracker.presentation.common.buildProfileAccountKeys
import com.pennywiseai.tracker.presentation.common.filterTransactionsByProfile
import com.pennywiseai.tracker.core.Constants
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.ProfileRepository
import com.pennywiseai.tracker.utils.CurrencyUtils
import com.pennywiseai.tracker.utils.TransactionSearchMatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionSplitDao: TransactionSplitDao,
    private val userPreferencesRepository: com.pennywiseai.tracker.data.preferences.UserPreferencesRepository,
    private val salaryMonthOverrideRepository: SalaryMonthOverrideRepository,
    private val currencyConversionService: CurrencyConversionService,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val profileRepository: ProfileRepository,
    private val savedStateHandle: androidx.lifecycle.SavedStateHandle,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _selectedPeriod = MutableStateFlow(TimePeriod.THIS_MONTH)
    val selectedPeriod: StateFlow<TimePeriod> = _selectedPeriod.asStateFlow()

    val useFinancialMonth: StateFlow<Boolean> = userPreferencesRepository.useFinancialMonth
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    
    private val _categoryFilter = MutableStateFlow<String?>(null)
    val categoryFilter: StateFlow<String?> = _categoryFilter.asStateFlow()

    // Multiple categories filter (for budget navigation)
    private val _categoriesFilter = MutableStateFlow<List<String>?>(null)
    val categoriesFilter: StateFlow<List<String>?> = _categoriesFilter.asStateFlow()

    private val _transactionTypeFilter = MutableStateFlow(TransactionTypeFilter.ALL)
    val transactionTypeFilter: StateFlow<TransactionTypeFilter> = _transactionTypeFilter.asStateFlow()

    private val _paymentModeFilter = MutableStateFlow<PaymentMode?>(null)
    private val _paymentModeGroupFilter = MutableStateFlow<PaymentModeGroup?>(null)

    private val _bankNameFilter = MutableStateFlow<String?>(null)
    private val _accountLast4Filter = MutableStateFlow<String?>(null)

    private val _includeExcluded = MutableStateFlow(false)
    val includeExcluded: StateFlow<Boolean> = _includeExcluded.asStateFlow()

    private val _selectedProfileId = MutableStateFlow<Long?>(null)
    val selectedProfileId: StateFlow<Long?> = _selectedProfileId.asStateFlow()

    // Account/Card filtering - replace profile filtering
    private val _selectedAccountKey = MutableStateFlow<String?>(null) // "bankName_accountLast4"
    val selectedAccountKey: StateFlow<String?> = _selectedAccountKey.asStateFlow()

    private val _profileAccountKeys = MutableStateFlow<Map<Long, Set<String>>>(emptyMap())
    val profileAccountKeys: StateFlow<Map<Long, Set<String>>> = _profileAccountKeys.asStateFlow()

    // Available accounts for filtering
    private val _availableAccounts = MutableStateFlow<List<AccountBalanceEntity>>(emptyList())
    val availableAccounts: StateFlow<List<AccountBalanceEntity>> = _availableAccounts.asStateFlow()

    private val _availableAccountKeys = MutableStateFlow<Set<String>>(emptySet())
    val availableAccountKeys: StateFlow<Set<String>> = _availableAccountKeys.asStateFlow()

    val profiles: StateFlow<List<ProfileEntity>> = profileRepository.observeAllProfiles()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _sortOption = MutableStateFlow(SortOption.DATE_NEWEST)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _selectedCurrency = MutableStateFlow("INR") // Will be initialized from preferences
    val selectedCurrency: StateFlow<String> = _selectedCurrency.asStateFlow()

    private val _isUnifiedMode = MutableStateFlow(false)
    val isUnifiedMode: StateFlow<Boolean> = _isUnifiedMode.asStateFlow()

    // Map of transactionId -> converted amount in display currency (for unified mode)
    private val _convertedAmounts = MutableStateFlow<Map<Long, BigDecimal>>(emptyMap())
    val convertedAmounts: StateFlow<Map<Long, BigDecimal>> = _convertedAmounts.asStateFlow()

    /** Per-tx amount for the active category filter when only a split portion applies (display currency). */
    private val _categoryDisplayAmounts = MutableStateFlow<Map<Long, BigDecimal>>(emptyMap())
    val categoryDisplayAmounts: StateFlow<Map<Long, BigDecimal>> = _categoryDisplayAmounts.asStateFlow()

    // Store custom date range as epoch days to survive process death
    // Stored as Pair<Long, Long> (startEpochDay, endEpochDay) in SavedStateHandle
    private val _customDateRangeEpochDays = savedStateHandle.getStateFlow<Pair<Long, Long>?>("customDateRange", null)

    // Expose as LocalDate pair for convenience
    val customDateRange: StateFlow<Pair<LocalDate, LocalDate>?> = _customDateRangeEpochDays
        .map { epochDays ->
            epochDays?.let { (startEpochDay, endEpochDay) ->
                LocalDate.ofEpochDay(startEpochDay) to LocalDate.ofEpochDay(endEpochDay)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()
    
    private val _currencyGroupedTotals = MutableStateFlow(CurrencyGroupedTotals())
    val currencyGroupedTotals: StateFlow<CurrencyGroupedTotals> = _currencyGroupedTotals.asStateFlow()

    // Available currencies for the selected time period
    val availableCurrencies: StateFlow<List<String>> = combine(
        selectedPeriod,
        customDateRange,
        combine(
            userPreferencesRepository.monthStartDay,
            userPreferencesRepository.useFinancialMonth,
            userPreferencesRepository.useFixedBudgetPeriodEnd,
            userPreferencesRepository.budgetPeriodEndDay,
            salaryMonthOverrideRepository.overridesMap
        ) { day, useFin, useFixed, endDom, overrides ->
            listOf(day, useFin, useFixed, endDom, overrides)
        }
    ) { period, customRange, prefs ->
        Triple(period, customRange, prefs)
    }.flatMapLatest { (period, customRange, prefs) ->
        val monthStartDay = prefs[0] as Int
        val useFinancialMonth = prefs[1] as Boolean
        val useFixedBudgetPeriodEnd = prefs[2] as Boolean
        val budgetPeriodEndDay = prefs[3] as Int
        @Suppress("UNCHECKED_CAST")
        val overrides = prefs[4] as Map<String, Int>
        if (period == TimePeriod.ALL) {
            transactionRepository.getAllCurrencies()
        } else if (period == TimePeriod.CUSTOM && customRange != null) {
            val (startDate, endDate) = customRange
            val startDateTime = startDate.atStartOfDay()
            val endDateTime = endDate.atTime(23, 59, 59)
            transactionRepository.getCurrenciesForPeriod(startDateTime, endDateTime)
        } else {
            val dateRange = getDateRangeForPeriod(
                period,
                monthStartDay,
                useFinancialMonth,
                overrides,
                useFixedBudgetPeriodEnd,
                budgetPeriodEndDay
            )
            if (dateRange != null) {
                val (startDate, endDate) = dateRange
                val startDateTime = startDate.atStartOfDay()
                val endDateTime = endDate.atTime(23, 59, 59)
                transactionRepository.getCurrenciesForPeriod(startDateTime, endDateTime)
            } else {
                transactionRepository.getAllCurrencies()
            }
        }
    }
        .combine(userPreferencesRepository.baseCurrency) { currencies, base ->
            currencies.sortedWith { a, b ->
                when {
                    a == base -> -1
                    b == base -> 1
                    else -> a.compareTo(b)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Computed property for current selected currency totals
    val filteredTotals: StateFlow<FilteredTotals> = combine(
        _currencyGroupedTotals,
        _selectedCurrency,
        _isUnifiedMode
    ) { groupedTotals, currency, isUnified ->
        Triple(groupedTotals, currency, isUnified)
    }.mapLatest { (groupedTotals, currency, isUnified) ->
        if (isUnified && groupedTotals.totalsByCurrency.size > 1) {
            // Aggregate all currencies converted to display currency
            var income = BigDecimal.ZERO
            var expenses = BigDecimal.ZERO
            var credit = BigDecimal.ZERO
            var transfer = BigDecimal.ZERO
            var investment = BigDecimal.ZERO
            var count = 0
            for ((cur, totals) in groupedTotals.totalsByCurrency) {
                if (cur == currency) {
                    income += totals.income
                    expenses += totals.expenses
                    credit += totals.credit
                    transfer += totals.transfer
                    investment += totals.investment
                } else {
                    income += currencyConversionService.convertAmount(totals.income, cur, currency)
                    expenses += currencyConversionService.convertAmount(totals.expenses, cur, currency)
                    credit += currencyConversionService.convertAmount(totals.credit, cur, currency)
                    transfer += currencyConversionService.convertAmount(totals.transfer, cur, currency)
                    investment += currencyConversionService.convertAmount(totals.investment, cur, currency)
                }
                count += totals.transactionCount
            }
            val netBalance = income - expenses - credit - transfer - investment
            FilteredTotals(income, expenses, credit, transfer, investment, netBalance, count)
        } else {
            val currencyTotals = groupedTotals.getTotalsForCurrency(currency)
            FilteredTotals(
                income = currencyTotals.income,
                expenses = currencyTotals.expenses,
                credit = currencyTotals.credit,
                transfer = currencyTotals.transfer,
                investment = currencyTotals.investment,
                netBalance = currencyTotals.netBalance,
                transactionCount = currencyTotals.transactionCount
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FilteredTotals()
    )
    
    private val _deletedTransaction = MutableStateFlow<TransactionEntity?>(null)
    val deletedTransaction: StateFlow<TransactionEntity?> = _deletedTransaction.asStateFlow()
    
    // Track if initial filters have been applied to prevent resetting on back navigation
    private var hasAppliedInitialFilters = false

    // Track the navigation params that were initially applied, to detect actual navigation changes
    private var appliedNavigationParams: NavigationParams? = null

    // Track budget navigation params similarly
    private var appliedBudgetParams: BudgetParams? = null

    // Set to true (synchronously, before viewModelScope.launch) whenever any navigation-supplied
    // filter is being applied. The init block checks this flag so it doesn't override filters
    // that were already set by navigation after the async DataStore reads complete.
    private var hasAppliedNavigationFilters = false
    
    // Categories flow - will be used to map category names to colors
    val categories: StateFlow<Map<String, CategoryEntity>> = categoryRepository.getAllCategories()
        .map { categoryList ->
            categoryList.associateBy { it.name }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )
    
    // Available categories for the current period (before category filter is applied)
    // Used for the category filter chips row
    private val _availableCategories = MutableStateFlow<List<String>>(emptyList())
    val availableCategories: StateFlow<List<String>> = _availableCategories.asStateFlow()

    // SMS scan period for info banner
    val smsScanMonths: StateFlow<Int> = userPreferencesRepository.smsScanMonths
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 3
        )

    private val smsScanAllTime: StateFlow<Boolean> = userPreferencesRepository.smsScanAllTime
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val showSmsDataLimitBanner: StateFlow<Boolean> = combine(
        selectedPeriod,
        customDateRange,
        smsScanMonths,
        smsScanAllTime,
    ) { period, customRange, scanMonthsValue, scanAllTime ->
        if (scanAllTime) return@combine false

        when (period) {
            TimePeriod.ALL -> false
            TimePeriod.CURRENT_FY -> {
                val dateRange = getDateRangeForPeriod(TimePeriod.CURRENT_FY)
                if (dateRange != null) {
                    val (fyStart, _) = dateRange
                    val scanStart = LocalDate.now().minusMonths(scanMonthsValue.toLong())
                    fyStart.isBefore(scanStart)
                } else {
                    false
                }
            }
            TimePeriod.CUSTOM -> {
                if (customRange != null) {
                    val (startDate, _) = customRange
                    val scanStart = LocalDate.now().minusMonths(scanMonthsValue.toLong())
                    startDate.isBefore(scanStart)
                } else {
                    false
                }
            }
            else -> false
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false,
    )
    
    init {
        viewModelScope.launch {
            val defaultPeriod = defaultTimePeriod(
                userPreferencesRepository.useFinancialMonth.first()
            )
            // Only apply the default if navigation filters haven't already set the period
            if (!hasAppliedNavigationFilters) {
                _selectedPeriod.value = defaultPeriod
            }
        }

        // Observe selected profile from preferences and cache profile account keys
        viewModelScope.launch {
            userPreferencesRepository.selectedProfileId.collect { profileId ->
                _selectedProfileId.value = profileId
            }
        }
        viewModelScope.launch {
            accountBalanceRepository.getAllLatestBalances().collect { balances ->
                _profileAccountKeys.value = buildProfileAccountKeys(balances)
                _availableAccounts.value = balances
            }
        }

        // Load unified mode preferences
        viewModelScope.launch {
            val baseCurrency = userPreferencesRepository.baseCurrency.first()
            combine(
                userPreferencesRepository.unifiedCurrencyMode,
                userPreferencesRepository.displayCurrency
            ) { unifiedMode, displayCurrency ->
                unifiedMode to displayCurrency
            }.collect { (unifiedMode, displayCurrency) ->
                _isUnifiedMode.value = unifiedMode
                if (unifiedMode) {
                    _selectedCurrency.value = displayCurrency
                } else if (!hasAppliedNavigationFilters) {
                    // Only set the default currency if navigation hasn't already set one
                    _selectedCurrency.value = baseCurrency
                }
            }
        }

        // Compute available categories from transactions filtered by period only (no category filter).
        // Also respects the active transaction type filter so income categories don't appear when
        // Spending is selected and vice versa.
        merge(
            selectedPeriod.map { "period" },
            categoriesFilter.map { "categories" },
            transactionTypeFilter.map { "typeFilter" },
            customDateRange.map { "customDate" },
            userPreferencesRepository.monthStartDay.map { "monthStartDay" },
            userPreferencesRepository.useFinancialMonth.map { "useFinancialMonth" },
            userPreferencesRepository.useFixedBudgetPeriodEnd.map { "useFixedBudgetPeriodEnd" },
            userPreferencesRepository.budgetPeriodEndDay.map { "budgetPeriodEndDay" },
            salaryMonthOverrideRepository.overridesMap.map { "salaryOverrides" }
        )
            .transformLatest { _ ->
                val period = selectedPeriod.value
                val multiCategories = categoriesFilter.value
                val typeFilter = transactionTypeFilter.value
                val monthStartDay = userPreferencesRepository.monthStartDay.first()
                val useFinancialMonth = userPreferencesRepository.useFinancialMonth.first()
                val useFixedBudgetPeriodEnd = userPreferencesRepository.useFixedBudgetPeriodEnd.first()
                val budgetPeriodEndDay = userPreferencesRepository.budgetPeriodEndDay.first()
                val salaryOverrides = salaryMonthOverrideRepository.overridesMap.first()
                // Get all transactions without category filter applied
                getFilteredTransactions(
                    "",
                    period,
                    null,
                    multiCategories,
                    TransactionTypeFilter.ALL,
                    monthStartDay,
                    useFinancialMonth,
                    salaryOverrides,
                    useFixedBudgetPeriodEnd,
                    budgetPeriodEndDay
                )
                    .collect { transactions ->
                        val txIds = transactions.map { it.id }
                        val splitsByTxId = if (txIds.isNotEmpty()) {
                            transactionSplitDao.getSplitsForTransactions(txIds).groupBy { it.transactionId }
                        } else emptyMap()
                        val categoryEntityMap = categories.value
                        val allCats = transactions.flatMap { tx ->
                            val splits = splitsByTxId[tx.id]
                            if (!splits.isNullOrEmpty()) splits.map { it.category }
                            else listOf(tx.category)
                        }.map { it.ifEmpty { "Others" } }.distinct().sorted()
                        // Filter category chips to match the active type filter so that, for example,
                        // income categories are hidden when Spending/Credit is selected.
                        val visibleCats = when (typeFilter) {
                            TransactionTypeFilter.INCOME ->
                                allCats.filter { name ->
                                    categoryEntityMap[name]?.isIncome != false
                                }
                            TransactionTypeFilter.EXPENSE, TransactionTypeFilter.CREDIT, TransactionTypeFilter.CC_BILL_PAYMENT ->
                                allCats.filter { name ->
                                    categoryEntityMap[name]?.isIncome != true
                                }
                            TransactionTypeFilter.EXCLUDED -> allCats
                            else -> allCats
                        }
                        emit(visibleCats)
                    }
            }
            .onEach { filteredCategories ->
                _availableCategories.value = filteredCategories
                // Auto-clear category filter if the selected category no longer exists in available categories
                val currentFilter = _categoryFilter.value
                if (currentFilter != null && currentFilter !in filteredCategories) {
                    _categoryFilter.value = null
                }
            }
            .launchIn(viewModelScope)

        // Compute account chips independently so the disabled state reflects the selected period
        // rather than whichever category/account filter is currently active.
        merge(
            selectedPeriod.map { "period" },
            customDateRange.map { "customDate" },
            userPreferencesRepository.monthStartDay.map { "monthStartDay" },
            userPreferencesRepository.useFinancialMonth.map { "useFinancialMonth" },
            userPreferencesRepository.useFixedBudgetPeriodEnd.map { "useFixedBudgetPeriodEnd" },
            userPreferencesRepository.budgetPeriodEndDay.map { "budgetPeriodEndDay" },
            salaryMonthOverrideRepository.overridesMap.map { "salaryOverrides" },
            _includeExcluded.map { "includeExcluded" },
        )
            .flatMapLatest {
                val period = selectedPeriod.value
                val monthStartDay = userPreferencesRepository.monthStartDay.first()
                val useFinancialMonth = userPreferencesRepository.useFinancialMonth.first()
                val useFixedBudgetPeriodEnd = userPreferencesRepository.useFixedBudgetPeriodEnd.first()
                val budgetPeriodEndDay = userPreferencesRepository.budgetPeriodEndDay.first()
                val salaryOverrides = salaryMonthOverrideRepository.overridesMap.first()
                val includeExcluded = _includeExcluded.value

                getFilteredTransactions(
                    "",
                    period,
                    null,
                    null,
                    TransactionTypeFilter.ALL,
                    monthStartDay,
                    useFinancialMonth,
                    salaryOverrides,
                    useFixedBudgetPeriodEnd,
                    budgetPeriodEndDay,
                    includeExcluded,
                ).map { transactions ->
                    transactions
                        .asSequence()
                        .mapNotNull { tx ->
                            val bankName = tx.bankName?.trim().orEmpty()
                            val accountLast4 = tx.accountNumber?.trim().orEmpty()
                            if (bankName.isBlank() || accountLast4.isBlank()) {
                                null
                            } else {
                                "${bankName}_${accountLast4}"
                            }
                        }
                        .toSet()
                }
            }
            .onEach { accountKeys ->
                _availableAccountKeys.value = accountKeys
            }
            .launchIn(viewModelScope)

        // Manually combine all flows using transformLatest
        merge(
            searchQuery.debounce(300).map { "search" },
            selectedPeriod.map { "period" },
            categoryFilter.map { "category" },
            categoriesFilter.map { "categories" },
            transactionTypeFilter.map { "typeFilter" },
            _selectedProfileId.map { "profileFilter" },
            _profileAccountKeys.map { "profileAccountKeys" },
            selectedCurrency.map { "currency" },
            _isUnifiedMode.map { "unifiedMode" },
            sortOption.map { "sort" },
            customDateRange.map { "customDate" },
            userPreferencesRepository.monthStartDay.map { "monthStartDay" },
            userPreferencesRepository.useFinancialMonth.map { "useFinancialMonth" },
            userPreferencesRepository.useFixedBudgetPeriodEnd.map { "useFixedBudgetPeriodEnd" },
            userPreferencesRepository.budgetPeriodEndDay.map { "budgetPeriodEndDay" },
            salaryMonthOverrideRepository.overridesMap.map { "salaryOverrides" },
            _includeExcluded.map { "includeExcluded" },
            _paymentModeFilter.map { "paymentMode" },
            _paymentModeGroupFilter.map { "paymentModeGroup" },
            _bankNameFilter.map { "bankName" },
            _accountLast4Filter.map { "accountLast4" },
        )
            .transformLatest { trigger ->
                // Get current values from all StateFlows
                val query = searchQuery.value
                val period = selectedPeriod.value
                val category = categoryFilter.value
                val categories = categoriesFilter.value
                val typeFilter = transactionTypeFilter.value
                val paymentModeFilter = _paymentModeFilter.value
                val paymentModeGroupFilter = _paymentModeGroupFilter.value
                val bankNameFilter = _bankNameFilter.value
                val accountLast4Filter = _accountLast4Filter.value
                val sort = sortOption.value
                val isUnified = _isUnifiedMode.value
                val monthStartDay = userPreferencesRepository.monthStartDay.first()
                val useFinancialMonth = userPreferencesRepository.useFinancialMonth.first()
                val useFixedBudgetPeriodEnd = userPreferencesRepository.useFixedBudgetPeriodEnd.first()
                val budgetPeriodEndDay = userPreferencesRepository.budgetPeriodEndDay.first()
                val salaryOverrides = salaryMonthOverrideRepository.overridesMap.first()
                val includeExcluded = _includeExcluded.value

                val profileId = _selectedProfileId.value

                // Get filtered transactions (without currency filter first)
                getFilteredTransactions(
                    query,
                    period,
                    category,
                    categories,
                    typeFilter,
                    monthStartDay,
                    useFinancialMonth,
                    salaryOverrides,
                    useFixedBudgetPeriodEnd,
                    budgetPeriodEndDay,
                    includeExcluded,
                    paymentModeFilter,
                    paymentModeGroupFilter,
                    bankNameFilter,
                    accountLast4Filter,
                )
                    .collect { allTransactions ->
                        // Apply profile filter
                        val transactions = filterByProfile(allTransactions, profileId)
                        if (isUnified) {
                            // Show all transactions regardless of currency
                            emit(sortTransactions(transactions, sort))
                        } else {
                            // Calculate available currencies from ALL filtered transactions (before currency filtering)
                            val allAvailableCurrencies = CurrencyUtils.sortCurrencies(
                                transactions.map { it.currency }.distinct()
                            )

                            // Auto-select primary currency if current currency doesn't exist in available currencies
                            val currentCurrency = selectedCurrency.value
                            val finalCurrency =
                                if (allAvailableCurrencies.isNotEmpty() && !allAvailableCurrencies.contains(
                                        currentCurrency
                                    )
                                ) {
                                    // Auto-select: prefer baseCurrency from preferences, then first available
                                    val baseCurrency =
                                        userPreferencesRepository.baseCurrency.first()
                                    val newCurrency =
                                        if (allAvailableCurrencies.contains(baseCurrency)) {
                                            baseCurrency
                                        } else {
                                            allAvailableCurrencies.first()
                                        }
                                    _selectedCurrency.value = newCurrency
                                    newCurrency
                                } else {
                                    currentCurrency
                                }

                            // Now filter by the selected currency (which may have just been auto-selected)
                            val currencyFilteredTransactions = transactions.filter {
                                it.currency.equals(finalCurrency, ignoreCase = true)
                            }

                            emit(sortTransactions(currencyFilteredTransactions, sort))
                        }
                    }
            }
            .onEach { transactions ->
                val currentSort = sortOption.value
                val isDateSort = currentSort == SortOption.DATE_NEWEST || currentSort == SortOption.DATE_OLDEST
                _uiState.value = _uiState.value.copy(
                    transactions = transactions,
                    groupedTransactions = if (isDateSort) groupTransactionsByDate(transactions) else emptyMap(),
                    isLoading = false
                )
                // Load splits for split-aware total calculation (when category filter is active,
                // only the split portion assigned to that category should count toward the total,
                // matching the budget's per-category split accounting).
                val txIds = transactions.map { it.id }
                val splitsByTxId = if (txIds.isNotEmpty()) {
                    transactionSplitDao.getSplitsForTransactions(txIds).groupBy { it.transactionId }
                } else emptyMap()
                val activeCatFilter = _categoryFilter.value
                _categoryDisplayAmounts.value = computeCategoryDisplayAmounts(
                    transactions, splitsByTxId, activeCatFilter
                )
                _currencyGroupedTotals.value = calculateCurrencyGroupedTotals(
                    transactions, splitsByTxId, activeCatFilter
                )

                // Auto-select primary currency if not already selected or if current currency no longer exists
                if (!_isUnifiedMode.value) {
                    val currentCurrency = selectedCurrency.value
                    if (!_currencyGroupedTotals.value.availableCurrencies.contains(currentCurrency) && _currencyGroupedTotals.value.hasAnyCurrency()) {
                        val baseCurrency = userPreferencesRepository.baseCurrency.first()
                        _selectedCurrency.value = _currencyGroupedTotals.value.getPrimaryCurrency(baseCurrency)
                    }
                    _convertedAmounts.value = emptyMap()
                } else {
                    // Build converted amounts map for transactions in foreign currencies
                    val displayCurrency = _selectedCurrency.value
                    val converted = mutableMapOf<Long, BigDecimal>()
                    for (tx in transactions) {
                        if (!tx.currency.equals(displayCurrency, ignoreCase = true)) {
                            converted[tx.id] = currencyConversionService.convertAmount(
                                tx.amount, tx.currency, displayCurrency
                            )
                        }
                    }
                    _convertedAmounts.value = converted
                }
            }
            .launchIn(viewModelScope)
    }
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun selectPeriod(period: TimePeriod) {
        if (period != TimePeriod.CUSTOM) {
            savedStateHandle["customDateRange"] = null
        }
        _selectedPeriod.value = period
    }
    
    fun setCategoryFilter(category: String) {
        _categoriesFilter.value = null
        _categoryFilter.value = category
    }
    
    fun clearCategoryFilter() {
        _categoryFilter.value = null
    }
    
    fun setTransactionTypeFilter(filter: TransactionTypeFilter) {
        _transactionTypeFilter.value = filter
    }

    fun setIncludeExcluded(include: Boolean) {
        _includeExcluded.value = include
    }
    
    fun setSelectedProfile(profileId: Long?) {
        _selectedProfileId.value = profileId
        viewModelScope.launch {
            userPreferencesRepository.updateSelectedProfileId(profileId)
        }
    }

    fun setSelectedAccount(accountKey: String?) {
        _selectedAccountKey.value = accountKey
        if (accountKey.isNullOrBlank()) {
            _bankNameFilter.value = null
            _accountLast4Filter.value = null
            return
        }

        val parts = accountKey.split("_", limit = 2)
        if (parts.size == 2) {
            _bankNameFilter.value = parts[0]
            _accountLast4Filter.value = parts[1]
        } else {
            _bankNameFilter.value = null
            _accountLast4Filter.value = null
        }
    }

    fun clearSelectedAccount() {
        _selectedAccountKey.value = null
        _bankNameFilter.value = null
        _accountLast4Filter.value = null
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun selectCurrency(currency: String) {
        _selectedCurrency.value = currency
    }

    /**
     * Sets a custom date range filter and switches the period to CUSTOM.
     * Date range is persisted in SavedStateHandle to survive process death.
     *
     * @param startDate The start date (inclusive)
     * @param endDate The end date (inclusive)
     * @throws IllegalArgumentException if startDate > endDate
     */
    fun setCustomDateRange(startDate: LocalDate, endDate: LocalDate) {
        require(startDate <= endDate) {
            "Start date ($startDate) must be before or equal to end date ($endDate)"
        }
        // Store as epoch days for process death survival
        savedStateHandle["customDateRange"] = startDate.toEpochDay() to endDate.toEpochDay()
        _selectedPeriod.value = TimePeriod.CUSTOM
    }

    /**
     * Clears the custom date range and resets to THIS_MONTH period.
     * Always safe to call - ensures we never have CUSTOM period with null dates.
     */
    fun clearCustomDateRange() {
        savedStateHandle["customDateRange"] = null
        if (_selectedPeriod.value == TimePeriod.CUSTOM) {
            viewModelScope.launch {
                _selectedPeriod.value = defaultTimePeriod(
                    userPreferencesRepository.useFinancialMonth.first()
                )
            }
        }
    }

    /**
     * Navigates to a specific [yearMonth], selecting the appropriate period chip for the
     * current month or setting a custom date range for historical months.
     */
    fun navigateToMonth(yearMonth: YearMonth) {
        viewModelScope.launch {
            val monthStartDay = userPreferencesRepository.monthStartDay.first()
            val payPeriodEnabled = userPreferencesRepository.useFinancialMonth.first()
            val useFixedEnd = userPreferencesRepository.useFixedBudgetPeriodEnd.first()
            val endDom = userPreferencesRepository.budgetPeriodEndDay.first()
            val overrides = salaryMonthOverrideRepository.overridesMap.first()
            when {
                yearMonth == YearMonth.now() && payPeriodEnabled ->
                    selectPeriod(TimePeriod.THIS_MONTH)
                yearMonth == YearMonth.now() && !payPeriodEnabled ->
                    selectPeriod(TimePeriod.THIS_MONTH)
                else -> {
                    val range = com.pennywiseai.tracker.presentation.common.getDateRangeForYearMonthNavigation(
                        yearMonth = yearMonth,
                        useCalendarMonth = false,
                        monthStartDay = monthStartDay,
                        monthStartOverrides = overrides,
                        useFixedBudgetPeriodEnd = useFixedEnd,
                        budgetPeriodEndDay = endDom,
                    )
                    setCustomDateRange(range.first, range.second)
                }
            }
        }
    }

    fun toggleExcludedFromTracking(transaction: TransactionEntity) {
        viewModelScope.launch {
            transactionRepository.updateExcludedFromTracking(transaction.id, !transaction.isExcludedFromTracking)
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            _deletedTransaction.value = transaction
            transactionRepository.deleteTransaction(transaction)
        }
    }
    
    fun undoDelete() {
        _deletedTransaction.value?.let { transaction ->
            viewModelScope.launch {
                transactionRepository.undoDeleteTransaction(transaction)
                _deletedTransaction.value = null
            }
        }
    }
    
    fun undoDeleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            transactionRepository.undoDeleteTransaction(transaction)
        }
    }
    
    fun clearDeletedTransaction() {
        _deletedTransaction.value = null
    }

    fun resetFilters() {
        hasAppliedInitialFilters = false
        appliedNavigationParams = null
        appliedBudgetParams = null
        clearCategoryFilter()
        clearCategoriesFilter()
        updateSearchQuery("")
        clearCustomDateRange()
        viewModelScope.launch {
            selectPeriod(
                defaultTimePeriod(userPreferencesRepository.useFinancialMonth.first())
            )
        }
        setTransactionTypeFilter(TransactionTypeFilter.ALL)
        _selectedProfileId.value = null  // reset local state only; does not update the shared DataStore preference
        clearSelectedAccount()
        setSortOption(SortOption.DATE_NEWEST)
        _includeExcluded.value = false
        // Don't reset currency as it might be user preference
    }
    
    fun applyInitialFilters(
        category: String?,
        merchant: String?,
        period: String?,
        currency: String?
    ) {
        if (!hasAppliedInitialFilters) {
            // Prevent init async defaults from overriding these filters after DataStore reads resume
            if (
                (period != null && period != TimePeriod.CUSTOM.name) ||
                category != null ||
                merchant != null ||
                currency != null
            ) {
                hasAppliedNavigationFilters = true
            }
            viewModelScope.launch {
                // Only apply filters once, when first navigating to the screen
                clearCategoryFilter()
                updateSearchQuery("")
                setTransactionTypeFilter(TransactionTypeFilter.ALL)
                setSortOption(SortOption.DATE_NEWEST)

                category?.let {
                    val decoded = if (it.contains("+") || it.contains("%")) {
                        java.net.URLDecoder.decode(it, "UTF-8")
                    } else it
                    setCategoryFilter(decoded)
                }

                merchant?.let {
                    val decoded = if (it.contains("+") || it.contains("%")) {
                        java.net.URLDecoder.decode(it, "UTF-8")
                    } else it
                    updateSearchQuery(decoded)
                }

                if (period != null) {
                    applyPeriodFromNavigation(period)
                } else {
                    selectPeriod(
                        defaultTimePeriod(userPreferencesRepository.useFinancialMonth.first())
                    )
                }

                currency?.let { selectCurrency(it) }

                hasAppliedInitialFilters = true
            }
        }
    }

    fun applyNavigationFilters(
        category: String?,
        merchant: String?,
        period: String?,
        currency: String?,
        transactionType: String? = null,
        periodStartEpochDay: Long? = null,
        periodEndEpochDay: Long? = null,
        paymentMode: String? = null,
        bankName: String? = null,
        accountLast4: String? = null,
    ) {
        // Create current params to compare
        val currentParams = NavigationParams(
            category,
            merchant,
            period,
            currency,
            transactionType,
            periodStartEpochDay,
            periodEndEpochDay,
            paymentMode,
            bankName,
            accountLast4,
        )

        // Only apply navigation filters if:
        // 1. This is the first time (appliedNavigationParams is null)
        // 2. OR the navigation params have actually changed (new navigation, not returning from detail)
        if (appliedNavigationParams != null && appliedNavigationParams == currentParams) {
            // Same params, user is returning from detail screen - don't reset their filters
            return
        }

        // Store the current navigation params
        appliedNavigationParams = currentParams
        // Set flag synchronously before the launch so init coroutines see it on resume
        hasAppliedNavigationFilters = true

        viewModelScope.launch {
            // Reset filters for new navigation
            clearCategoryFilter()
            updateSearchQuery("")
            setTransactionTypeFilter(TransactionTypeFilter.ALL)
            _paymentModeFilter.value = null
            _paymentModeGroupFilter.value = null
            _bankNameFilter.value = null
            _accountLast4Filter.value = null
            _selectedAccountKey.value = null
            setSortOption(SortOption.DATE_NEWEST)

            category?.let {
                val decoded = if (it.contains("+") || it.contains("%")) {
                    java.net.URLDecoder.decode(it, "UTF-8")
                } else it
                setCategoryFilter(decoded)
            }

            merchant?.let {
                val decoded = if (it.contains("+") || it.contains("%")) {
                    java.net.URLDecoder.decode(it, "UTF-8")
                } else it
                updateSearchQuery(decoded)
            }

            applyPeriodAndRangeFromNavigation(period, periodStartEpochDay, periodEndEpochDay)

            currency?.let { selectCurrency(it) }

            transactionType?.let { typeName ->
                val filter = TransactionTypeFilter.entries.firstOrNull { it.name == typeName }
                filter?.let { setTransactionTypeFilter(it) }
            }

            paymentMode?.let { modeName ->
                when (modeName) {
                    PaymentModeGroup.CARD_AND_BANK.name ->
                        _paymentModeGroupFilter.value = PaymentModeGroup.CARD_AND_BANK
                    else ->
                        PaymentMode.entries.firstOrNull { it.name == modeName }?.let {
                            _paymentModeFilter.value = it
                        }
                }
            }

            bankName?.let {
                val decoded = if (it.contains("+") || it.contains("%")) {
                    java.net.URLDecoder.decode(it, "UTF-8")
                } else it
                _bankNameFilter.value = decoded
            }
            accountLast4?.let {
                val decoded = if (it.contains("+") || it.contains("%")) {
                    java.net.URLDecoder.decode(it, "UTF-8")
                } else it
                _accountLast4Filter.value = decoded
            }
            if (bankName != null && accountLast4 != null) {
                val decodedBank = if (bankName.contains("+") || bankName.contains("%")) {
                    java.net.URLDecoder.decode(bankName, "UTF-8")
                } else bankName
                val decodedLast4 = if (accountLast4.contains("+") || accountLast4.contains("%")) {
                    java.net.URLDecoder.decode(accountLast4, "UTF-8")
                } else accountLast4
                setSelectedAccount("${decodedBank}_${decodedLast4}")
            }
        }
    }

    private suspend fun applyPeriodAndRangeFromNavigation(
        period: String?,
        periodStartEpochDay: Long?,
        periodEndEpochDay: Long?,
    ) {
        when {
            periodStartEpochDay != null && periodEndEpochDay != null ->
                applyDateRangeFromNavigation(
                    LocalDate.ofEpochDay(periodStartEpochDay),
                    LocalDate.ofEpochDay(periodEndEpochDay),
                )
            period != null && period != TimePeriod.CUSTOM.name ->
                applyPeriodFromNavigation(period)
            period == TimePeriod.CUSTOM.name -> Unit
            else ->
                selectPeriod(defaultTimePeriod(userPreferencesRepository.useFinancialMonth.first()))
        }
    }

    /**
     * Apply filters for budget transactions navigation.
     * This sets a custom date range, categories filter, and transaction type.
     */
    fun applyBudgetFilters(
        startDateEpochDay: Long,
        endDateEpochDay: Long,
        currency: String?,
        categories: String?,  // Comma-separated
        transactionType: String?
    ) {
        // Create current params to compare
        val currentParams = BudgetParams(startDateEpochDay, endDateEpochDay, currency, categories, transactionType)

        // Only apply budget filters if:
        // 1. This is the first time (appliedBudgetParams is null)
        // 2. OR the budget params have actually changed (new navigation, not returning from detail)
        if (appliedBudgetParams != null && appliedBudgetParams == currentParams) {
            // Same params, user is returning from detail screen - don't reset their filters
            return
        }

        // Store the current budget params
        appliedBudgetParams = currentParams
        // Set flag synchronously before the launch so init coroutines see it on resume
        hasAppliedNavigationFilters = true

        viewModelScope.launch {
            // Clear existing filters first
            clearCategoryFilter()
            updateSearchQuery("")
            setSortOption(SortOption.DATE_NEWEST)

            applyDateRangeFromNavigation(
                LocalDate.ofEpochDay(startDateEpochDay),
                LocalDate.ofEpochDay(endDateEpochDay)
            )

            currency?.let { selectCurrency(it) }

            transactionType?.let {
                try {
                    val filter = TransactionTypeFilter.valueOf(it)
                    setTransactionTypeFilter(filter)
                } catch (e: IllegalArgumentException) {
                    // Ignore invalid transaction type
                }
            }

            categories?.let { cats ->
                val categoryList = cats.split(",").map { cat ->
                    if (cat.contains("+") || cat.contains("%")) {
                        java.net.URLDecoder.decode(cat, "UTF-8")
                    } else {
                        cat
                    }
                }.filter { it.isNotBlank() }

                if (categoryList.isNotEmpty()) {
                    _categoriesFilter.value = categoryList
                }
            }
        }
    }

    /**
     * Clears the multiple categories filter.
     */
    fun clearCategoriesFilter() {
        _categoriesFilter.value = null
    }

    /**
     * Applies a multi-category filter from analytics navigation.
     * Categories is a comma-separated, URL-encoded string.
     * When period is CUSTOM and epoch days are provided, applies the custom date range.
     */
    fun applyMultiCategoryFilter(categoriesEncoded: String, period: String?, periodStartEpochDay: Long? = null, periodEndEpochDay: Long? = null) {
        clearCategoryFilter()
        updateSearchQuery("")
        setSortOption(SortOption.DATE_NEWEST)
        // Set flag synchronously before the launch so init coroutines see it on resume
        hasAppliedNavigationFilters = true

        viewModelScope.launch {
            applyPeriodAndRangeFromNavigation(period, periodStartEpochDay, periodEndEpochDay)
        }

        val categoryList = categoriesEncoded.split(",").map { cat ->
            if (cat.contains("+") || cat.contains("%")) {
                java.net.URLDecoder.decode(cat, "UTF-8")
            } else cat
        }.filter { it.isNotBlank() }

        if (categoryList.isNotEmpty()) {
            _categoriesFilter.value = categoryList
        }
    }

    private fun filterByProfile(
        transactions: List<TransactionEntity>,
        profileId: Long?
    ): List<TransactionEntity> {
        var filtered = filterTransactionsByProfile(transactions, profileId, _profileAccountKeys.value)

        // Apply account/card filter if selected
        val selectedAccountKey = _selectedAccountKey.value
        if (selectedAccountKey != null) {
            val (bankName, accountLast4) = selectedAccountKey.split("_", limit = 2)
            filtered = filtered.filter { tx ->
                tx.bankName == bankName && tx.accountNumber == accountLast4
            }
        }

        return filtered
    }

    private fun getFilteredTransactions(
        searchQuery: String,
        period: TimePeriod,
        category: String?,
        categories: List<String>?,
        typeFilter: TransactionTypeFilter,
        monthStartDay: Int = 1,
        useFinancialMonth: Boolean = true,
        monthStartOverrides: Map<String, Int> = emptyMap(),
        useFixedBudgetPeriodEnd: Boolean = false,
        budgetPeriodEndDay: Int = 31,
        includeExcluded: Boolean = false,
        paymentModeFilter: PaymentMode? = null,
        paymentModeGroupFilter: PaymentModeGroup? = null,
        bankNameFilter: String? = null,
        accountLast4Filter: String? = null,
    ): Flow<List<TransactionEntity>> {
        // Category filter matches budget accounting: primary category or split lines only (not tags).
        val baseFlow = if (category != null) {
            transactionRepository.getAllTransactions().transformLatest { allTxs ->
                val txIds = allTxs.map { it.id }
                val allSplits = if (txIds.isNotEmpty()) {
                    transactionSplitDao.getSplitsForTransactions(txIds)
                } else emptyList()
                val splitMatchIds = allSplits.filter { it.category == category }.map { it.transactionId }.toSet()
                // Transactions with ANY splits have their primary category overridden by splits.
                // Only include them if at least one split matches the filter category.
                val txsWithAnySplits = allSplits.map { it.transactionId }.toSet()
                emit(allTxs.filter { tx ->
                    if (tx.id in txsWithAnySplits) tx.id in splitMatchIds
                    else tx.category == category
                })
            }
        } else {
            transactionRepository.getAllTransactions()
        }

        // By default, hide transactions excluded from tracking; show them only when opted in.
        // "Excluded" type filter needs the full list so excluded-only rows are visible.
        val excludedBaseFlow = if (includeExcluded || typeFilter == TransactionTypeFilter.EXCLUDED) {
            baseFlow
        } else {
            baseFlow.map { txs -> txs.filter { !it.isExcludedFromTracking } }
        }

        // Multiple categories: match primary or split lines only (same rules as budgets).
        val categoriesFilteredFlow = if (categories != null && categories.isNotEmpty()) {
            excludedBaseFlow.transformLatest { transactions ->
                val txIds = transactions.map { it.id }

                val allSplits = if (txIds.isNotEmpty()) {
                    transactionSplitDao.getSplitsForTransactions(txIds)
                } else emptyList()
                val splitsByTxId = allSplits.groupBy { it.transactionId }

                val filtered = transactions.filter { tx ->
                    tx.category in categories ||
                        splitsByTxId[tx.id]?.any { it.category in categories } == true
                }
                emit(filtered)
            }
        } else {
            excludedBaseFlow
        }
        
        // Apply period filter
        val periodFilteredFlow = when (period) {
            TimePeriod.ALL -> categoriesFilteredFlow
            TimePeriod.CUSTOM -> {
                val customRange = customDateRange.value
                // Guard against invalid state: CUSTOM period must have a date range
                // This should never happen due to clearCustomDateRange() logic, but be defensive
                if (customRange == null) {
                    val fallbackPeriod = defaultTimePeriod(useFinancialMonth)
                    android.util.Log.e("TransactionsViewModel",
                        "CUSTOM period selected but no date range set - falling back to $fallbackPeriod")
                    _selectedPeriod.value = fallbackPeriod
                    val (startDate, endDate) = getDateRangeForPeriod(
                        fallbackPeriod,
                        monthStartDay,
                        useFinancialMonth,
                        monthStartOverrides,
                        useFixedBudgetPeriodEnd,
                        budgetPeriodEndDay
                    )!!
                    val startDateTime = startDate.atStartOfDay()
                    val endDateTime = endDate.atTime(23, 59, 59)
                    categoriesFilteredFlow.map { transactions ->
                        transactions.filter { it.dateTime in startDateTime..endDateTime }
                    }
                } else {
                    val (startDate, endDate) = customRange
                    val startDateTime = startDate.atStartOfDay()
                    val endDateTime = endDate.atTime(23, 59, 59)

                    categoriesFilteredFlow.map { transactions ->
                        transactions.filter { it.dateTime in startDateTime..endDateTime }
                    }
                }
            }
            else -> {
                val dateRange = getDateRangeForPeriod(
                    period,
                    monthStartDay,
                    useFinancialMonth,
                    monthStartOverrides,
                    useFixedBudgetPeriodEnd,
                    budgetPeriodEndDay
                )
                android.util.Log.d("PWDebug", "=== TransactionsVM date range ===")
                android.util.Log.d("PWDebug", "period=$period useFinancialMonth=$useFinancialMonth monthStartDay=$monthStartDay range=$dateRange category=$category")
                if (dateRange != null) {
                    val (startDate, endDate) = dateRange
                    val startDateTime = startDate.atStartOfDay()
                    val endDateTime = endDate.atTime(23, 59, 59)

                    categoriesFilteredFlow.map { transactions ->
                        transactions.filter { it.dateTime in startDateTime..endDateTime }
                    }
                } else {
                    categoriesFilteredFlow
                }
            }
        }
        
        // Apply transaction type filter
        val typeFilteredFlow = periodFilteredFlow.map { transactions ->
            when (typeFilter) {
                TransactionTypeFilter.ALL -> transactions
                TransactionTypeFilter.INCOME -> transactions.filter { it.transactionType == TransactionType.INCOME }
                TransactionTypeFilter.EXPENSE -> transactions.filter { it.matchesAnalyticsSpendingFilter() }
                TransactionTypeFilter.CREDIT -> transactions.filter { it.transactionType == TransactionType.CREDIT && it.loanId == null }
                TransactionTypeFilter.TRANSFER -> transactions.filter { it.transactionType == TransactionType.TRANSFER }
                TransactionTypeFilter.CC_BILL_PAYMENT -> transactions.filter { it.isCcBillPayment() }
                TransactionTypeFilter.INVESTMENT -> transactions.filter { it.transactionType == TransactionType.INVESTMENT }
                TransactionTypeFilter.EXCLUDED -> transactions.filter { it.isExcludedFromTracking }
            }
        }

        val paymentModeFilteredFlow = when {
            paymentModeGroupFilter != null -> typeFilteredFlow.map { transactions ->
                transactions.filter { it.matchesPaymentModeGroup(paymentModeGroupFilter) }
            }
            paymentModeFilter != null -> typeFilteredFlow.map { transactions ->
                transactions.filter { it.paymentMode() == paymentModeFilter }
            }
            else -> typeFilteredFlow
        }

        val bankAccountFilteredFlow = when {
            !bankNameFilter.isNullOrBlank() && !accountLast4Filter.isNullOrBlank() -> paymentModeFilteredFlow.map { transactions ->
                transactions.filter {
                    it.bankName.equals(bankNameFilter, ignoreCase = true) &&
                        it.accountNumber?.endsWith(accountLast4Filter) == true
                }
            }
            !bankNameFilter.isNullOrBlank() -> paymentModeFilteredFlow.map { transactions ->
                transactions.filter { it.bankName.equals(bankNameFilter, ignoreCase = true) }
            }
            !accountLast4Filter.isNullOrBlank() -> paymentModeFilteredFlow.map { transactions ->
                transactions.filter { it.accountNumber?.endsWith(accountLast4Filter) == true }
            }
            else -> paymentModeFilteredFlow
        }

        // Apply search filter
        return if (searchQuery.isBlank()) {
            bankAccountFilteredFlow
        } else {
            bankAccountFilteredFlow.map { transactions ->
                transactions.filter { TransactionSearchMatcher.matches(it, searchQuery) }
            }
        }
    }
    
    private fun sortTransactions(transactions: List<TransactionEntity>, sortOption: SortOption): List<TransactionEntity> {
        return when (sortOption) {
            SortOption.DATE_NEWEST -> transactions.sortedByDescending { it.dateTime }
            SortOption.DATE_OLDEST -> transactions.sortedBy { it.dateTime }
            SortOption.AMOUNT_HIGHEST -> transactions.sortedByDescending { it.amount }
            SortOption.AMOUNT_LOWEST -> transactions.sortedBy { it.amount }
            SortOption.MERCHANT_AZ -> transactions.sortedBy { it.merchantName.lowercase() }
            SortOption.MERCHANT_ZA -> transactions.sortedByDescending { it.merchantName.lowercase() }
        }
    }
    
    private fun groupTransactionsByDate(
        transactions: List<TransactionEntity>
    ): Map<DateGroup, List<TransactionEntity>> {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val weekStart = today.minusWeeks(1)
        
        return transactions.groupBy { transaction ->
            val transactionDate = transaction.dateTime.toLocalDate()
            when {
                transactionDate == today -> DateGroup.TODAY
                transactionDate == yesterday -> DateGroup.YESTERDAY
                transactionDate > weekStart -> DateGroup.THIS_WEEK
                else -> DateGroup.EARLIER
            }
        }
    }
    
    private suspend fun applyPeriodFromNavigation(period: String?) {
        period ?: return
        when (period) {
            "THIS_MONTH" -> selectPeriod(TimePeriod.THIS_MONTH)
            "CALENDAR_MONTH" -> selectPeriod(TimePeriod.THIS_MONTH)
            "LAST_MONTH" -> selectPeriod(TimePeriod.LAST_MONTH)
            "CURRENT_FY" -> selectPeriod(TimePeriod.CURRENT_FY)
            "ALL" -> selectPeriod(TimePeriod.ALL)
            else -> {
                val yearMonth = parseYearMonthNavPeriod(period)
                if (yearMonth != null) {
                    val monthStartDay = userPreferencesRepository.monthStartDay.first()
                    val useFinancialMonth = userPreferencesRepository.useFinancialMonth.first()
                    val useFixedEnd = userPreferencesRepository.useFixedBudgetPeriodEnd.first()
                    val endDom = userPreferencesRepository.budgetPeriodEndDay.first()
                    val overrides = salaryMonthOverrideRepository.overridesMap.first()

                    when {
                        useFinancialMonth && yearMonth == YearMonth.now() ->
                            selectPeriod(TimePeriod.THIS_MONTH)
                        !useFinancialMonth && yearMonth == YearMonth.now() ->
                            selectPeriod(TimePeriod.THIS_MONTH)
                        else -> {
                            val range = getDateRangeForYearMonth(
                                yearMonth,
                                monthStartDay,
                                useFinancialMonth,
                                overrides,
                                useFixedEnd,
                                endDom
                            )
                            setCustomDateRange(range.first, range.second)
                        }
                    }
                }
            }
        }
    }

    /** Maps an explicit date range to the best matching period chip (pay-month when enabled). */
    private suspend fun applyDateRangeFromNavigation(start: LocalDate, end: LocalDate) {
        val monthStartDay = userPreferencesRepository.monthStartDay.first()
        val useFinancialMonth = userPreferencesRepository.useFinancialMonth.first()
        val useFixedEnd = userPreferencesRepository.useFixedBudgetPeriodEnd.first()
        val endDom = userPreferencesRepository.budgetPeriodEndDay.first()
        val overrides = salaryMonthOverrideRepository.overridesMap.first()

        val payMonthRange = getDateRangeForPeriod(
            TimePeriod.THIS_MONTH,
            monthStartDay,
            useFinancialMonth,
            overrides,
            useFixedEnd,
            endDom
        )
        val calendarMonthRange = getDateRangeForPeriod(
            TimePeriod.THIS_MONTH,
            monthStartDay,
            useFinancialMonth = false,
            monthStartOverrides = overrides,
            useFixedBudgetPeriodEnd = useFixedEnd,
            budgetPeriodEndDay = endDom
        )

        when {
            useFinancialMonth && payMonthRange != null && start == payMonthRange.first && end == payMonthRange.second ->
                selectPeriod(TimePeriod.THIS_MONTH)
            !useFinancialMonth && calendarMonthRange != null &&
                start == calendarMonthRange.first && end == calendarMonthRange.second ->
                selectPeriod(TimePeriod.THIS_MONTH)
            else -> setCustomDateRange(start, end)
        }
    }

    /**
     * When filtering by category, returns display amounts for split transactions where only
     * a portion of the total belongs to that category.
     */
    private suspend fun computeCategoryDisplayAmounts(
        transactions: List<TransactionEntity>,
        splitsByTxId: Map<Long, List<TransactionSplitEntity>>,
        categoryFilter: String?
    ): Map<Long, BigDecimal> {
        if (categoryFilter == null) return emptyMap()
        val isUnified = _isUnifiedMode.value
        val displayCurrency = _selectedCurrency.value
        val tolerance = BigDecimal("0.01")
        val result = mutableMapOf<Long, BigDecimal>()
        for (tx in transactions) {
            val splits = splitsByTxId[tx.id] ?: continue
            if (splits.isEmpty()) continue
            val portion = splits
                .filter { it.category == categoryFilter }
                .fold(BigDecimal.ZERO) { acc, split -> acc + split.amount }
            if (portion <= BigDecimal.ZERO) continue
            if ((portion - tx.amount).abs() <= tolerance) continue
            result[tx.id] = if (isUnified) {
                currencyConversionService.convertAmount(portion, tx.currency, displayCurrency)
            } else {
                portion
            }
        }
        return result
    }

    /** Split lines use per-category amounts; otherwise the full amount for the primary category. */
    private fun effectiveAmountForCategoryFilter(
        tx: TransactionEntity,
        splits: List<TransactionSplitEntity>?,
        categoryFilter: String?
    ): Double {
        if (categoryFilter == null) return tx.amount.toDouble()
        if (!splits.isNullOrEmpty()) {
            return splits.filter { it.category == categoryFilter }.sumOf { it.amount.toDouble() }
        }
        return tx.amount.toDouble()
    }

    private fun calculateCurrencyGroupedTotals(
        transactions: List<TransactionEntity>,
        splitsByTxId: Map<Long, List<TransactionSplitEntity>> = emptyMap(),
        categoryFilter: String? = null
    ): CurrencyGroupedTotals {
        // Group transactions by currency
        val transactionsByCurrency = transactions.groupBy { it.currency }

        val totalsByCurrency = transactionsByCurrency.mapValues { (currency, currencyTransactions) ->
            val income = currencyTransactions
                .filter { it.transactionType == TransactionType.INCOME }
                .sumOf { it.amount.toDouble() }
                .toBigDecimal()

            val expenses = currencyTransactions
                .filter { it.matchesAnalyticsSpendingFilter() }
                .sumOf { tx ->
                    effectiveAmountForCategoryFilter(tx, splitsByTxId[tx.id], categoryFilter)
                }
                .toBigDecimal()

            val credit = BigDecimal.ZERO

            val transfer = currencyTransactions
                .filter { it.transactionType == TransactionType.TRANSFER }
                .sumOf { it.amount.toDouble() }
                .toBigDecimal()

            val investment = currencyTransactions
                .filter { it.transactionType == TransactionType.INVESTMENT }
                .sumOf { tx ->
                    effectiveAmountForCategoryFilter(tx, splitsByTxId[tx.id], categoryFilter)
                }
                .toBigDecimal()

            CurrencyTotals(
                currency = currency,
                income = income,
                expenses = expenses,
                credit = credit,
                transfer = transfer,
                investment = investment,
                transactionCount = currencyTransactions.size
            )
        }

        // Note: availableCurrencies are now provided by the separate availableCurrencies StateFlow
        // We'll keep the old behavior for compatibility but the UI should use availableCurrencies property
        // Use standard currency sorting (INR first, then alphabetical)
        val filteredAvailableCurrencies = CurrencyUtils.sortCurrencies(
            totalsByCurrency.keys.toList()
        )

        return CurrencyGroupedTotals(
            totalsByCurrency = totalsByCurrency,
            availableCurrencies = filteredAvailableCurrencies,
            transactionCount = transactions.size
        )
    }
    
    fun getReportUrl(transaction: TransactionEntity): String {
        // If we have the original SMS body, create report URL
        val smsBody = transaction.smsBody ?: ""
        // Use the original SMS sender if available
        val sender = transaction.smsSender ?: ""
        
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
        return "${Constants.Links.WEB_PARSER_URL}/#message=$encodedMessage&sender=$encodedSender&device=$encodedDeviceData&autoparse=true"
    }

    // ── Self-transfer review ──────────────────────────────────────────────────

    val pendingSelfTransferCount: StateFlow<Int> =
        transactionRepository.getPendingSelfTransferCount()
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, 0)

    private val _selfTransferReview = MutableStateFlow<SelfTransferReviewState?>(null)
    val selfTransferReview: StateFlow<SelfTransferReviewState?> = _selfTransferReview.asStateFlow()

    fun startSelfTransferReview() {
        viewModelScope.launch {
            val pending = transactionRepository.getPendingSelfTransfers().first()
            if (pending.isNotEmpty()) {
                _selfTransferReview.value = SelfTransferReviewState(pending)
            }
        }
    }

    fun confirmSelfTransfer() {
        advanceSelfTransferReview(confirm = true)
    }

    fun denySelfTransfer() {
        advanceSelfTransferReview(confirm = false)
    }

    fun dismissSelfTransferReview() {
        _selfTransferReview.value = null
    }

    private fun advanceSelfTransferReview(confirm: Boolean) {
        val state = _selfTransferReview.value ?: return
        val current = state.currentTransaction ?: return
        viewModelScope.launch {
            val newKind = if (confirm) {
                com.pennywiseai.tracker.data.database.entity.TransferKind.SELF_TRANSFER
            } else {
                com.pennywiseai.tracker.data.database.entity.TransferKind.OTHERS_TRANSFER
            }
            transactionRepository.updateTransferKind(current.id, newKind)

            val nextIndex = state.currentIndex + 1
            if (nextIndex >= state.totalCount) {
                _selfTransferReview.value = null
            } else {
                _selfTransferReview.value = state.copy(currentIndex = nextIndex)
            }
        }
    }
}

data class TransactionsUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val groupedTransactions: Map<DateGroup, List<TransactionEntity>> = emptyMap(),
    val isLoading: Boolean = true
)

data class FilterParams(
    val query: String,
    val period: TimePeriod,
    val category: String?,
    val typeFilter: TransactionTypeFilter
)

enum class DateGroup(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    THIS_WEEK("This Week"),
    EARLIER("Earlier")
}

enum class SortOption(val label: String) {
    DATE_NEWEST("Newest First"),
    DATE_OLDEST("Oldest First"),
    AMOUNT_HIGHEST("Highest Amount"),
    AMOUNT_LOWEST("Lowest Amount"),
    MERCHANT_AZ("Merchant (A-Z)"),
    MERCHANT_ZA("Merchant (Z-A)")
}

data class FilteredTotals(
    val income: BigDecimal = BigDecimal.ZERO,
    val expenses: BigDecimal = BigDecimal.ZERO,
    val credit: BigDecimal = BigDecimal.ZERO,
    val transfer: BigDecimal = BigDecimal.ZERO,
    val investment: BigDecimal = BigDecimal.ZERO,
    val netBalance: BigDecimal = BigDecimal.ZERO,
    val transactionCount: Int = 0
)

/**
 * Tracks the navigation parameters that were applied.
 * Used to detect if navigation params have actually changed vs
 * just returning from a detail screen with the same params.
 */
private data class NavigationParams(
    val category: String?,
    val merchant: String?,
    val period: String?,
    val currency: String?,
    val transactionType: String? = null,
    val periodStartEpochDay: Long? = null,
    val periodEndEpochDay: Long? = null,
    val paymentMode: String? = null,
    val bankName: String? = null,
    val accountLast4: String? = null,
)

/**
 * Tracks the budget navigation parameters that were applied.
 * Used to detect if budget params have actually changed vs
 * just returning from a detail screen with the same params.
 */
private data class BudgetParams(
    val startDateEpochDay: Long,
    val endDateEpochDay: Long,
    val currency: String?,
    val categories: String?,
    val transactionType: String?
)
