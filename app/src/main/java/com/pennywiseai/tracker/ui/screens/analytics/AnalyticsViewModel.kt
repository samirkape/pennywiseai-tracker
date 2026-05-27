package com.pennywiseai.tracker.ui.screens.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.SalaryMonthOverrideRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.pennywiseai.tracker.presentation.common.TimePeriod
import com.pennywiseai.tracker.presentation.common.TransactionTypeFilter
import com.pennywiseai.tracker.presentation.common.defaultTimePeriod
import com.pennywiseai.tracker.presentation.common.PaymentMode
import com.pennywiseai.tracker.presentation.common.matchesAnalyticsSpendingFilter
import com.pennywiseai.tracker.presentation.common.paymentMode
import com.pennywiseai.tracker.presentation.common.getDateRangeForPeriod
import com.pennywiseai.tracker.presentation.common.getDateRangeForYearMonthNavigation
import com.pennywiseai.tracker.presentation.common.resolveDateRangeForSelection
import com.pennywiseai.tracker.utils.CurrencyUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import com.pennywiseai.tracker.ui.components.BalancePoint

enum class ChartType { LINE, BAR, HEATMAP }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val currencyConversionService: CurrencyConversionService,
    private val salaryMonthOverrideRepository: SalaryMonthOverrideRepository,
    private val savedStateHandle: androidx.lifecycle.SavedStateHandle
) : ViewModel() {
    
    private val _selectedPeriod = MutableStateFlow(TimePeriod.THIS_MONTH)
    val selectedPeriod: StateFlow<TimePeriod> = _selectedPeriod.asStateFlow()
    
    private val _transactionTypeFilter = MutableStateFlow(TransactionTypeFilter.EXPENSE)
    val transactionTypeFilter: StateFlow<TransactionTypeFilter> = _transactionTypeFilter.asStateFlow()

    private val _selectedCurrency = MutableStateFlow("")
    val selectedCurrency: StateFlow<String> = _selectedCurrency.asStateFlow()

    private val _categoryFilter = MutableStateFlow<String?>(null)
    val categoryFilter: StateFlow<String?> = _categoryFilter.asStateFlow()

    private val _isUnifiedMode = MutableStateFlow(false)
    val isUnifiedMode: StateFlow<Boolean> = _isUnifiedMode.asStateFlow()

    val useFinancialMonth: StateFlow<Boolean> = userPreferencesRepository.useFinancialMonth
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _selectedChartType = MutableStateFlow(ChartType.LINE)
    val selectedChartType: StateFlow<ChartType> = _selectedChartType.asStateFlow()

    private val _periodAnchorMonthKey = savedStateHandle.getStateFlow<String?>("periodAnchorMonth", null)

    /** True when month scrubber follows calendar months; false for pay-month boundaries. */
    private val periodNavUsesCalendarKey = "periodNavUsesCalendar"

    /** Nominal month key for pay-month / calendar-month scrubbing (may span two calendar months). */
    val periodAnchorMonth: StateFlow<YearMonth?> = _periodAnchorMonthKey
        .map { key -> key?.let { YearMonth.parse(it) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            val default = defaultTimePeriod(userPreferencesRepository.useFinancialMonth.first())
            _selectedPeriod.value = default
            updatePeriodAnchorForChip(default)
        }

        viewModelScope.launch {
            userPreferencesRepository.getAnalyticsChartType().collect { saved ->
                if (saved != null) {
                    try {
                        _selectedChartType.value = ChartType.valueOf(saved)
                    } catch (_: IllegalArgumentException) { }
                }
            }
        }

        // Load unified mode and baseCurrency preferences
        viewModelScope.launch {
            val baseCurrency = userPreferencesRepository.baseCurrency.first()
            _selectedCurrency.value = baseCurrency
            combine(
                userPreferencesRepository.unifiedCurrencyMode,
                userPreferencesRepository.displayCurrency
            ) { unifiedMode, displayCurrency ->
                unifiedMode to displayCurrency
            }.collect { (unifiedMode, displayCurrency) ->
                _isUnifiedMode.value = unifiedMode
                if (unifiedMode) {
                    _selectedCurrency.value = displayCurrency
                }
            }
        }
    }

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

    private val _availableCurrencies = MutableStateFlow<List<String>>(emptyList())
    val availableCurrencies: StateFlow<List<String>> = _availableCurrencies.asStateFlow()

    // Reactive UI state that automatically updates when any filter changes
    // Uses flatMapLatest to cancel previous data loads when filters change (prevents race conditions)
    val uiState: StateFlow<AnalyticsUiState> = combine(
        combine(
            _selectedPeriod,
            customDateRange,
            _transactionTypeFilter,
            _selectedCurrency,
        ) { period, customRange, typeFilter, currency ->
            listOf(period, customRange, typeFilter, currency)
        },
        combine(
            _isUnifiedMode,
            _categoryFilter,
        ) { isUnified, catFilter ->
            listOf(isUnified, catFilter)
        },
        combine(
            userPreferencesRepository.monthStartDay,
            userPreferencesRepository.useFinancialMonth,
            userPreferencesRepository.useFixedBudgetPeriodEnd,
            userPreferencesRepository.budgetPeriodEndDay,
            salaryMonthOverrideRepository.overridesMap,
        ) { monthStartDay, useFinancialMonth, useFixedEnd, endDom, overrides ->
            listOf(monthStartDay, useFinancialMonth, useFixedEnd, endDom, overrides)
        },
    ) { periodPack, filterPack, prefsPack ->
        @Suppress("UNCHECKED_CAST")
        val pl = periodPack as List<Any?>
        @Suppress("UNCHECKED_CAST")
        val fl = filterPack as List<Any?>
        @Suppress("UNCHECKED_CAST")
        val pr = prefsPack as List<Any?>
        FilterState(
            period = pl[0] as TimePeriod,
            customRange = pl[1] as Pair<LocalDate, LocalDate>?,
            typeFilter = pl[2] as TransactionTypeFilter,
            currency = pl[3] as String,
            isUnifiedMode = fl[0] as Boolean,
            categoryFilter = fl[1] as String?,
            monthStartDay = pr[0] as Int,
            useFinancialMonth = pr[1] as Boolean,
            monthStartOverrides = pr[4] as Map<String, Int>,
            useFixedBudgetPeriodEnd = pr[2] as Boolean,
            budgetPeriodEndDay = pr[3] as Int,
        )
    }.flatMapLatest { filterState ->
        var dateRange = resolveDateRangeForSelection(
            period = filterState.period,
            customRange = filterState.customRange,
            monthStartDay = filterState.monthStartDay,
            useFinancialMonth = filterState.useFinancialMonth,
            monthStartOverrides = filterState.monthStartOverrides,
            useFixedBudgetPeriodEnd = filterState.useFixedBudgetPeriodEnd,
            budgetPeriodEndDay = filterState.budgetPeriodEndDay,
        )
        if (dateRange == null && filterState.period == TimePeriod.CUSTOM) {
            val fallbackPeriod = defaultTimePeriod(filterState.useFinancialMonth)
            android.util.Log.e(
                "AnalyticsViewModel",
                "CUSTOM period selected but no date range set - falling back to $fallbackPeriod",
            )
            _selectedPeriod.value = fallbackPeriod
            dateRange = resolveDateRangeForSelection(
                period = fallbackPeriod,
                customRange = null,
                monthStartDay = filterState.monthStartDay,
                useFinancialMonth = filterState.useFinancialMonth,
                monthStartOverrides = filterState.monthStartOverrides,
                useFixedBudgetPeriodEnd = filterState.useFixedBudgetPeriodEnd,
                budgetPeriodEndDay = filterState.budgetPeriodEndDay,
            )
        }

        if (dateRange == null) {
            // No valid date range, return empty state
            flowOf(AnalyticsUiState(isLoading = false))
        } else {
            // First load all transactions for the date range to get available currencies
            transactionRepository.getTransactionsBetweenDates(
                startDate = dateRange.first,
                endDate = dateRange.second
            ).flatMapLatest { allTransactions ->
                // Update available currencies using standard sorting (INR first, then alphabetical)
                val allCurrencies = CurrencyUtils.sortCurrencies(
                    allTransactions.map { it.currency }.distinct()
                )
                _availableCurrencies.value = allCurrencies

                // Auto-select primary currency if not already selected or if current currency no longer exists
                val currentSelectedCurrency = filterState.currency
                if (!allCurrencies.contains(currentSelectedCurrency) && allCurrencies.isNotEmpty()) {
                    val baseCurrency = _selectedCurrency.value.ifEmpty { allCurrencies.first() }
                    _selectedCurrency.value = if (allCurrencies.contains(baseCurrency)) baseCurrency else allCurrencies.first()
                }

                // Use database-level filtering for better performance
                // Load transactions with splits for proper category breakdown
                if (filterState.isUnifiedMode) {
                    // Unified mode: load ALL currencies
                    transactionRepository.getTransactionsWithSplitsFiltered(
                        startDate = dateRange.first,
                        endDate = dateRange.second
                    ).map { txs -> Triple(txs, filterState.typeFilter, true) }
                } else {
                    transactionRepository.getTransactionsWithSplitsFiltered(
                        startDate = dateRange.first,
                        endDate = dateRange.second,
                        currency = filterState.currency
                    ).map { txs -> Triple(txs, filterState.typeFilter, false) }
                }
            }.mapLatest { (allTransactionsWithSplits, transactionTypeFilter, isUnified) ->
                // Filter by transaction type in memory (splits are already loaded)
                // Exclude loan repayments — they are fixed obligations, not discretionary spending
                // EXPENSE filter includes CREDIT card transactions — both represent money spent
                val filteredTransactionsWithSplits = (when (transactionTypeFilter) {
                    TransactionTypeFilter.ALL -> allTransactionsWithSplits
                    TransactionTypeFilter.EXPENSE -> allTransactionsWithSplits.filter {
                        it.transaction.matchesAnalyticsSpendingFilter()
                    }
                    TransactionTypeFilter.INCOME -> allTransactionsWithSplits.filter {
                        it.transaction.transactionType == com.pennywiseai.tracker.data.database.entity.TransactionType.INCOME
                    }
                    TransactionTypeFilter.CREDIT -> allTransactionsWithSplits.filter {
                        it.transaction.transactionType == com.pennywiseai.tracker.data.database.entity.TransactionType.CREDIT
                    }
                    TransactionTypeFilter.TRANSFER -> allTransactionsWithSplits.filter {
                        it.transaction.transactionType == com.pennywiseai.tracker.data.database.entity.TransactionType.TRANSFER
                    }
                    TransactionTypeFilter.INVESTMENT -> allTransactionsWithSplits.filter {
                        it.transaction.transactionType == com.pennywiseai.tracker.data.database.entity.TransactionType.INVESTMENT
                    }
                }).filter { it.transaction.loanId == null }

                // Compute available categories BEFORE applying category filter
                val allCategoryNames = filteredTransactionsWithSplits
                    .flatMap { txWithSplits -> txWithSplits.getAmountByCategory().keys }
                    .map { it.ifEmpty { "Others" } }
                    .distinct()
                    .sorted()

                // Apply category filter
                val categoryFilteredWithSplits = filterState.categoryFilter?.let { cat ->
                    filteredTransactionsWithSplits.filter { txWithSplits ->
                        txWithSplits.getAmountByCategory().keys
                            .map { it.ifEmpty { "Others" } }
                            .contains(cat)
                    }
                } ?: filteredTransactionsWithSplits

                val filteredTransactions = categoryFilteredWithSplits.map { it.transaction }
                val displayCurrency = _selectedCurrency.value

                // Calculate total — convert if unified mode
                var totalSpending = BigDecimal.ZERO
                if (isUnified) {
                    for (tx in filteredTransactions) {
                        totalSpending += currencyConversionService.convertAmount(tx.amount, tx.currency, displayCurrency)
                    }
                } else {
                    totalSpending = filteredTransactions.sumOf { it.amount.toDouble() }.toBigDecimal()
                }

                // Build category breakdown considering splits
                val categoryAmounts = mutableMapOf<String, BigDecimal>()
                val categoryTransactionCounts = mutableMapOf<String, Int>()

                for (txWithSplits in categoryFilteredWithSplits) {
                    val fromCurrency = txWithSplits.transaction.currency
                    txWithSplits.getAmountByCategory().forEach { (category, amount) ->
                        val categoryName = category.ifEmpty { "Others" }
                        val converted = if (isUnified) {
                            currencyConversionService.convertAmount(amount, fromCurrency, displayCurrency)
                        } else {
                            amount
                        }
                        categoryAmounts[categoryName] = (categoryAmounts[categoryName] ?: BigDecimal.ZERO) + converted
                        categoryTransactionCounts[categoryName] = (categoryTransactionCounts[categoryName] ?: 0) + 1
                    }
                }

                val categoryBreakdown = categoryAmounts.map { (categoryName, categoryTotal) ->
                    CategoryData(
                        name = categoryName,
                        amount = categoryTotal,
                        percentage = if (totalSpending > BigDecimal.ZERO) {
                            (categoryTotal.divide(totalSpending, 4, java.math.RoundingMode.HALF_UP) * BigDecimal(100)).toFloat()
                        } else 0f,
                        transactionCount = categoryTransactionCounts[categoryName] ?: 0
                    )
                }.sortedByDescending { it.amount }

                // Group by merchant — convert if unified
                val merchantBreakdown = filteredTransactions
                    .groupBy { it.merchantName }
                    .entries
                    .map { (merchant, txns) ->
                        val merchantAmount = if (isUnified) {
                            var sum = BigDecimal.ZERO
                            for (tx in txns) {
                                sum += currencyConversionService.convertAmount(tx.amount, tx.currency, displayCurrency)
                            }
                            sum
                        } else {
                            txns.sumOf { it.amount.toDouble() }.toBigDecimal()
                        }
                        MerchantData(
                            name = merchant,
                            amount = merchantAmount,
                            transactionCount = txns.size,
                            isSubscription = txns.any { it.isRecurring }
                        )
                    }
                    .sortedByDescending { it.amount }
                    .take(10)

                // Calculate average amount
                val averageAmount = if (filteredTransactions.isNotEmpty()) {
                    totalSpending.divide(BigDecimal(filteredTransactions.size), 2, java.math.RoundingMode.HALF_UP)
                } else {
                    BigDecimal.ZERO
                }

                // Get top category info
                val topCategory = categoryBreakdown.firstOrNull()

                // Tags on transactions (for filter/insights only — not budget accounting).
                val categoriesByTx: Map<Long, List<String>> = categoryFilteredWithSplits
                    .filter { it.transaction.tags.isNotBlank() }
                    .associate { twSplits ->
                        twSplits.transaction.id to twSplits.transaction.tags
                            .split(",").filter { it.isNotBlank() }
                    }

                // Insight 1: Category co-occurrence pairs
                val pairCounts = mutableMapOf<Pair<String, String>, Int>()
                for ((_, cats) in categoriesByTx) {
                    if (cats.size < 2) continue
                    val sorted = cats.sorted()
                    for (i in sorted.indices) {
                        for (j in i + 1 until sorted.size) {
                            val pair = sorted[i] to sorted[j]
                            pairCounts[pair] = (pairCounts[pair] ?: 0) + 1
                        }
                    }
                }
                val categoryOverlaps = pairCounts
                    .map { (pair, count) -> CategoryOverlapData(pair.first, pair.second, count) }
                    .sortedByDescending { it.coOccurrenceCount }
                    .take(10)

                // Insight 2: Transactions with 2+ junction-table categories
                val multiCategoryTransactions = categoriesByTx
                    .filter { (_, cats) -> cats.size >= 2 }
                    .mapNotNull { (txId, cats) ->
                        categoryFilteredWithSplits.find { it.transaction.id == txId }?.let { twSplits ->
                            MultiCategoryTransactionData(
                                transactionId = txId,
                                merchantName = twSplits.transaction.merchantName,
                                amount = twSplits.transaction.amount,
                                dateTime = twSplits.transaction.dateTime,
                                categories = cats,
                                currency = twSplits.transaction.currency
                            )
                        }
                    }
                    .sortedByDescending { it.dateTime }
                    .take(20)

                // Insight 3: Top tags by transaction count and total amount
                val tagCountMap = mutableMapOf<String, Int>()
                val tagAmountMap = mutableMapOf<String, BigDecimal>()
                for ((txId, tags) in categoriesByTx) {
                    val tx = categoryFilteredWithSplits.find { it.transaction.id == txId }?.transaction
                    val txAmount = tx?.amount ?: BigDecimal.ZERO
                    val txCurrency = tx?.currency ?: displayCurrency
                    val converted = if (isUnified) {
                        currencyConversionService.convertAmount(txAmount, txCurrency, displayCurrency)
                    } else txAmount
                    for (tag in tags) {
                        tagCountMap[tag] = (tagCountMap[tag] ?: 0) + 1
                        tagAmountMap[tag] = (tagAmountMap[tag] ?: BigDecimal.ZERO) + converted
                    }
                }
                val topTags = tagCountMap.entries
                    .sortedByDescending { it.value }
                    .take(10)
                    .map { (tag, count) ->
                        TagData(
                            name = tag,
                            transactionCount = count,
                            totalAmount = tagAmountMap[tag] ?: BigDecimal.ZERO
                        )
                    }

                val previousRange = previousDateRange(dateRange.first, dateRange.second)
                val previousPeriodTxs = if (filterState.isUnifiedMode) {
                    transactionRepository.getTransactionsWithSplitsFiltered(
                        startDate = previousRange.first,
                        endDate = previousRange.second,
                    ).first()
                } else {
                    transactionRepository.getTransactionsWithSplitsFiltered(
                        startDate = previousRange.first,
                        endDate = previousRange.second,
                        currency = filterState.currency,
                    ).first()
                }

                val periodOutflow = computePeriodOutflow(
                    allTransactionsWithSplits = allTransactionsWithSplits,
                    isUnified = isUnified,
                    displayCurrency = displayCurrency,
                )

                val investmentInsights = computeInvestmentInsights(
                    allTransactionsWithSplits = allTransactionsWithSplits,
                    previousPeriodTxs = previousPeriodTxs,
                    isUnified = isUnified,
                    displayCurrency = displayCurrency,
                )

                val paymentModeBreakdown = if (transactionTypeFilter == TransactionTypeFilter.EXPENSE) {
                    computePaymentModeBreakdown(
                        transactions = allTransactionsWithSplits.map { it.transaction },
                        totalSpending = totalSpending,
                        isUnified = isUnified,
                        displayCurrency = displayCurrency,
                    )
                } else {
                    null
                }

                AnalyticsUiState(
                    totalSpending = totalSpending,
                    categoryBreakdown = categoryBreakdown,
                    topMerchants = merchantBreakdown,
                    transactionCount = filteredTransactions.size,
                    averageAmount = averageAmount,
                    topCategory = topCategory?.name,
                    topCategoryPercentage = topCategory?.percentage ?: 0f,
                    currency = displayCurrency,
                    isLoading = false,
                    spendingTrend = calculateSpendingTrend(filteredTransactions, dateRange.first, dateRange.second),
                    availableCategories = allCategoryNames,
                    categoryOverlaps = categoryOverlaps,
                    multiCategoryTransactions = multiCategoryTransactions,
                    topTags = topTags,
                    periodStart = dateRange.first,
                    periodEnd = dateRange.second,
                    periodOutflow = periodOutflow,
                    investmentInsights = investmentInsights,
                    paymentModeBreakdown = paymentModeBreakdown,
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AnalyticsUiState(isLoading = true)
    )

    fun selectPeriod(period: TimePeriod) {
        if (period != TimePeriod.CUSTOM) {
            savedStateHandle["customDateRange"] = null
            updatePeriodAnchorForChip(period)
        }
        _selectedPeriod.value = period
    }

    private fun updatePeriodAnchorForChip(period: TimePeriod) {
        val anchor = when (period) {
            TimePeriod.THIS_MONTH, TimePeriod.CALENDAR_MONTH -> YearMonth.now()
            TimePeriod.LAST_MONTH -> YearMonth.now().minusMonths(1)
            TimePeriod.ALL, TimePeriod.CURRENT_FY, TimePeriod.CUSTOM -> null
        }
        savedStateHandle["periodAnchorMonth"] = anchor?.toString()
        when (period) {
            TimePeriod.CALENDAR_MONTH -> savedStateHandle[periodNavUsesCalendarKey] = true
            TimePeriod.THIS_MONTH, TimePeriod.LAST_MONTH -> savedStateHandle[periodNavUsesCalendarKey] = false
            TimePeriod.ALL, TimePeriod.CURRENT_FY -> savedStateHandle.remove<Boolean>(periodNavUsesCalendarKey)
            TimePeriod.CUSTOM -> Unit
        }
    }

    /** Whether scrubber steps calendar months vs pay months (from chip or persisted nav mode). */
    private fun resolveUsesCalendarMonthNavigation(): Boolean {
        savedStateHandle.get<Boolean>(periodNavUsesCalendarKey)?.let { return it }
        return _selectedPeriod.value == TimePeriod.CALENDAR_MONTH
    }

    fun setTransactionTypeFilter(filter: TransactionTypeFilter) {
        _transactionTypeFilter.value = filter
    }

    fun selectCurrency(currency: String) {
        _selectedCurrency.value = currency
    }

    fun setCategoryFilter(category: String) {
        _categoryFilter.value = category
    }

    fun clearCategoryFilter() {
        _categoryFilter.value = null
    }

    fun setChartType(type: ChartType) {
        _selectedChartType.value = type
        viewModelScope.launch {
            userPreferencesRepository.saveAnalyticsChartType(type.name)
        }
    }

    /**
     * Sets a custom date range filter and switches the period to CUSTOM.
     * Date range is persisted in SavedStateHandle to survive process death.
     *
     * @param startDate The start date (inclusive)
     * @param endDate The end date (inclusive)
     * @throws IllegalArgumentException if startDate > endDate
     */
    /**
     * @param anchorMonth When set (month scrubber), keeps prev/next navigation. Null for manual date-picker ranges.
     */
    fun setCustomDateRange(
        startDate: LocalDate,
        endDate: LocalDate,
        anchorMonth: YearMonth? = null,
    ) {
        require(startDate <= endDate) {
            "Start date ($startDate) must be before or equal to end date ($endDate)"
        }
        savedStateHandle["customDateRange"] = startDate.toEpochDay() to endDate.toEpochDay()
        savedStateHandle["periodAnchorMonth"] = anchorMonth?.toString()
        if (anchorMonth == null) {
            savedStateHandle.remove<Boolean>(periodNavUsesCalendarKey)
        }
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
                val default = defaultTimePeriod(userPreferencesRepository.useFinancialMonth.first())
                _selectedPeriod.value = default
                updatePeriodAnchorForChip(default)
            }
        }
    }

    /**
     * Steps to an adjacent pay-month or calendar-month anchor ([yearMonth] key).
     */
    fun navigateToMonth(yearMonth: YearMonth) {
        viewModelScope.launch {
            val monthStartDay = userPreferencesRepository.monthStartDay.first()
            val payPeriodEnabled = userPreferencesRepository.useFinancialMonth.first()
            val useFixedEnd = userPreferencesRepository.useFixedBudgetPeriodEnd.first()
            val endDom = userPreferencesRepository.budgetPeriodEndDay.first()
            val overrides = salaryMonthOverrideRepository.overridesMap.first()
            val usesCalendar = resolveUsesCalendarMonthNavigation()
            when {
                yearMonth == YearMonth.now() && usesCalendar ->
                    selectPeriod(TimePeriod.CALENDAR_MONTH)
                yearMonth == YearMonth.now() && !usesCalendar && payPeriodEnabled ->
                    selectPeriod(TimePeriod.THIS_MONTH)
                yearMonth == YearMonth.now() && !usesCalendar && !payPeriodEnabled ->
                    selectPeriod(TimePeriod.CALENDAR_MONTH)
                else -> {
                    val range = getDateRangeForYearMonthNavigation(
                        yearMonth = yearMonth,
                        useCalendarMonth = usesCalendar,
                        monthStartDay = monthStartDay,
                        monthStartOverrides = overrides,
                        useFixedBudgetPeriodEnd = useFixedEnd,
                        budgetPeriodEndDay = endDom,
                    )
                    setCustomDateRange(range.first, range.second, anchorMonth = yearMonth)
                }
            }
        }
    }

    private fun calculateSpendingTrend(
        transactions: List<com.pennywiseai.tracker.data.database.entity.TransactionEntity>,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<BalancePoint> {
        val selectedPeriod = _selectedPeriod.value
        val trend = mutableListOf<BalancePoint>()
        val currency = transactions.firstOrNull()?.currency ?: _selectedCurrency.value

        when {
            selectedPeriod == TimePeriod.ALL || selectedPeriod == TimePeriod.CURRENT_FY -> {
                val actualStartDate = if (selectedPeriod == TimePeriod.ALL && transactions.isNotEmpty()) {
                    val firstTxDate = transactions.minByOrNull { it.dateTime }?.dateTime?.toLocalDate() ?: startDate
                    if (firstTxDate.isAfter(startDate)) firstTxDate.withDayOfMonth(1) else startDate
                } else {
                    startDate
                }

                val yearsInRange = ChronoUnit.YEARS.between(actualStartDate, endDate)
                val aggregateByYear = selectedPeriod == TimePeriod.ALL && yearsInRange >= 2

                if (aggregateByYear) {
                    var currentYear = actualStartDate.withDayOfYear(1)
                    val lastYear = endDate.withDayOfYear(1)
                    while (!currentYear.isAfter(lastYear) && !currentYear.isAfter(LocalDate.now().withDayOfYear(1))) {
                        val endOfYear = currentYear.withDayOfYear(currentYear.lengthOfYear())
                        val totalAmount = transactions.filter {
                            !it.dateTime.toLocalDate().isBefore(currentYear) && !it.dateTime.toLocalDate().isAfter(endOfYear)
                        }.sumOf { it.amount.toDouble() }.toBigDecimal()
                        trend.add(BalancePoint(timestamp = currentYear.atStartOfDay(), balance = totalAmount, currency = currency))
                        currentYear = currentYear.plusYears(1)
                    }
                } else {
                    var currentMonth = actualStartDate.withDayOfMonth(1)
                    val lastMonth = endDate.withDayOfMonth(1)
                    while (!currentMonth.isAfter(lastMonth) && !currentMonth.isAfter(LocalDate.now().withDayOfMonth(1))) {
                        val endOfMonth = currentMonth.withDayOfMonth(currentMonth.lengthOfMonth())
                        val totalAmount = transactions.filter {
                            !it.dateTime.toLocalDate().isBefore(currentMonth) && !it.dateTime.toLocalDate().isAfter(endOfMonth)
                        }.sumOf { it.amount.toDouble() }.toBigDecimal()
                        trend.add(BalancePoint(timestamp = currentMonth.atStartOfDay(), balance = totalAmount, currency = currency))
                        currentMonth = currentMonth.plusMonths(1)
                    }
                }
            }
            else -> {
                val rangeDays = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
                val transactionsByDate = transactions.groupBy { it.dateTime.toLocalDate() }
                if (rangeDays > 14) {
                    // Aggregate into weekly buckets when range exceeds 2 weeks
                    val weekStart = startDate.minusDays(startDate.dayOfWeek.value.toLong() - 1)
                    var currentWeek = weekStart
                    while (!currentWeek.isAfter(endDate) && !currentWeek.isAfter(LocalDate.now())) {
                        val weekEnd = currentWeek.plusDays(6).coerceAtMost(endDate).coerceAtMost(LocalDate.now())
                        var d = currentWeek.coerceAtLeast(startDate)
                        var weekTotal = java.math.BigDecimal.ZERO
                        while (!d.isAfter(weekEnd)) {
                            weekTotal += (transactionsByDate[d] ?: emptyList()).sumOf { it.amount.toDouble() }.toBigDecimal()
                            d = d.plusDays(1)
                        }
                        trend.add(BalancePoint(timestamp = currentWeek.coerceAtLeast(startDate).atStartOfDay(), balance = weekTotal, currency = currency))
                        currentWeek = currentWeek.plusWeeks(1)
                    }
                } else {
                    var currentDate = startDate
                    while (!currentDate.isAfter(endDate) && !currentDate.isAfter(LocalDate.now())) {
                        val totalAmount = (transactionsByDate[currentDate] ?: emptyList()).sumOf { it.amount.toDouble() }.toBigDecimal()
                        trend.add(BalancePoint(timestamp = currentDate.atStartOfDay(), balance = totalAmount, currency = currency))
                        currentDate = currentDate.plusDays(1)
                    }
                }
            }
        }
        return trend
    }

    private suspend fun convertAmount(
        amount: BigDecimal,
        fromCurrency: String,
        toCurrency: String,
        isUnified: Boolean,
    ): BigDecimal = if (isUnified) {
        currencyConversionService.convertAmount(amount, fromCurrency, toCurrency)
    } else {
        amount
    }

    private suspend fun computePeriodOutflow(
        allTransactionsWithSplits: List<TransactionWithSplits>,
        isUnified: Boolean,
        displayCurrency: String,
    ): PeriodOutflowSummary? {
        var spending = BigDecimal.ZERO
        var invested = BigDecimal.ZERO
        var spendingCount = 0
        var investmentCount = 0

        for (item in allTransactionsWithSplits) {
            val tx = item.transaction
            if (tx.isExcludedFromTracking) continue
            val amount = convertAmount(tx.amount, tx.currency, displayCurrency, isUnified)
            when {
                tx.matchesAnalyticsSpendingFilter() -> {
                    spending += amount
                    spendingCount++
                }
                tx.transactionType == TransactionType.INVESTMENT -> {
                    invested += amount
                    investmentCount++
                }
            }
        }

        val total = spending + invested
        if (total <= BigDecimal.ZERO) return null

        return PeriodOutflowSummary(
            total = total,
            spending = spending,
            invested = invested,
            transactionCount = spendingCount + investmentCount,
            spendingTransactionCount = spendingCount,
            investmentTransactionCount = investmentCount,
            currency = displayCurrency,
        )
    }

    private suspend fun computeInvestmentInsights(
        allTransactionsWithSplits: List<TransactionWithSplits>,
        previousPeriodTxs: List<TransactionWithSplits>,
        isUnified: Boolean,
        displayCurrency: String,
    ): InvestmentInsights? {
        val investmentTxs = allTransactionsWithSplits.filter {
            it.transaction.transactionType == TransactionType.INVESTMENT &&
                !it.transaction.isExcludedFromTracking
        }
        if (investmentTxs.isEmpty()) return null

        var totalInvested = BigDecimal.ZERO
        for (item in investmentTxs) {
            val tx = item.transaction
            totalInvested += convertAmount(tx.amount, tx.currency, displayCurrency, isUnified)
        }
        val count = investmentTxs.size

        var previousTotal = BigDecimal.ZERO
        for (item in previousPeriodTxs) {
            val tx = item.transaction
            if (tx.transactionType == TransactionType.INVESTMENT && !tx.isExcludedFromTracking) {
                previousTotal += convertAmount(tx.amount, tx.currency, displayCurrency, isUnified)
            }
        }

        val deltaPercent = if (previousTotal > BigDecimal.ZERO) {
            val delta = totalInvested.subtract(previousTotal)
            (delta.divide(previousTotal, 4, java.math.RoundingMode.HALF_UP) * BigDecimal(100)).toFloat()
        } else {
            null
        }

        val topMerchants = investmentTxs
            .groupBy { it.transaction.merchantName }
            .map { (merchant, txns) ->
                var merchantTotal = BigDecimal.ZERO
                for (item in txns) {
                    val tx = item.transaction
                    merchantTotal += convertAmount(tx.amount, tx.currency, displayCurrency, isUnified)
                }
                MerchantData(
                    name = merchant,
                    amount = merchantTotal,
                    transactionCount = txns.size,
                    isSubscription = txns.any { it.transaction.isRecurring },
                )
            }
            .sortedByDescending { it.amount }
            .take(10)

        val recurringCount = investmentTxs.count { it.transaction.isRecurring }

        var largestInvestment = BigDecimal.ZERO
        for (item in investmentTxs) {
            val tx = item.transaction
            val converted = convertAmount(tx.amount, tx.currency, displayCurrency, isUnified)
            if (converted > largestInvestment) largestInvestment = converted
        }

        val categoryAmounts = mutableMapOf<String, BigDecimal>()
        val categoryCounts = mutableMapOf<String, Int>()
        for (item in investmentTxs) {
            val tx = item.transaction
            val splits = item.splits
            val amountsByCategory = if (splits.isNotEmpty()) {
                splits.associate { split ->
                    val cat = split.category.ifEmpty { "Others" }
                    cat to convertAmount(split.amount, tx.currency, displayCurrency, isUnified)
                }
            } else {
                val cat = tx.category.ifEmpty { "Others" }
                mapOf(cat to convertAmount(tx.amount, tx.currency, displayCurrency, isUnified))
            }
            amountsByCategory.forEach { (category, amount) ->
                categoryAmounts[category] = (categoryAmounts[category] ?: BigDecimal.ZERO) + amount
                categoryCounts[category] = (categoryCounts[category] ?: 0) + 1
            }
        }
        val topCategoryEntry = categoryAmounts.maxByOrNull { it.value }
        val topCategory = topCategoryEntry?.key
        val topCategoryPercentage = if (topCategory != null && totalInvested > BigDecimal.ZERO) {
            (topCategoryEntry.value.divide(totalInvested, 4, java.math.RoundingMode.HALF_UP) * BigDecimal(100)).toFloat()
        } else {
            0f
        }

        return InvestmentInsights(
            totalInvested = totalInvested,
            transactionCount = count,
            recurringCount = recurringCount,
            largestInvestment = largestInvestment,
            topCategory = topCategory,
            topCategoryPercentage = topCategoryPercentage,
            previousPeriodTotal = previousTotal,
            deltaPercent = deltaPercent,
            topMerchants = topMerchants,
            currency = displayCurrency,
        )
    }

    private suspend fun computePaymentModeBreakdown(
        transactions: List<TransactionEntity>,
        totalSpending: BigDecimal,
        isUnified: Boolean,
        displayCurrency: String,
    ): PaymentModeBreakdown? {
        val spendingTxs = transactions.filter {
            !it.isExcludedFromTracking && it.matchesAnalyticsSpendingFilter()
        }
        if (spendingTxs.isEmpty()) return null

        var creditTotal = BigDecimal.ZERO
        var creditCount = 0
        var bankTotal = BigDecimal.ZERO
        var bankCount = 0
        var cashTotal = BigDecimal.ZERO
        var cashCount = 0

        for (tx in spendingTxs) {
            val mode = tx.paymentMode() ?: continue
            val converted = convertAmount(tx.amount, tx.currency, displayCurrency, isUnified)
            when (mode) {
                PaymentMode.CREDIT_CARD -> {
                    creditTotal += converted
                    creditCount++
                }
                PaymentMode.BANK_ACCOUNT -> {
                    bankTotal += converted
                    bankCount++
                }
                PaymentMode.CASH -> {
                    cashTotal += converted
                    cashCount++
                }
            }
        }

        fun stat(mode: PaymentMode, total: BigDecimal, count: Int): PaymentModeStat? {
            if (total <= BigDecimal.ZERO) return null
            return PaymentModeStat(
                mode = mode,
                total = total,
                transactionCount = count,
                percentOfTotal = if (totalSpending > BigDecimal.ZERO) {
                    (total.divide(totalSpending, 4, java.math.RoundingMode.HALF_UP) * BigDecimal(100)).toFloat()
                } else {
                    0f
                },
            )
        }

        val creditCard = stat(PaymentMode.CREDIT_CARD, creditTotal, creditCount)
        val bankAccount = stat(PaymentMode.BANK_ACCOUNT, bankTotal, bankCount)
        val cash = stat(PaymentMode.CASH, cashTotal, cashCount)

        val cardAndBank = if (creditCard != null || bankAccount != null) {
            val creditTotal = creditCard?.total ?: BigDecimal.ZERO
            val bankTotal = bankAccount?.total ?: BigDecimal.ZERO
            val combined = creditTotal + bankTotal
            if (combined <= BigDecimal.ZERO) null else {
                CardAndBankSpendSummary(
                    total = combined,
                    transactionCount = (creditCard?.transactionCount ?: 0) + (bankAccount?.transactionCount ?: 0),
                    creditTotal = creditTotal,
                    creditCount = creditCard?.transactionCount ?: 0,
                    bankTotal = bankTotal,
                    bankCount = bankAccount?.transactionCount ?: 0,
                    percentOfSpending = if (totalSpending > BigDecimal.ZERO) {
                        (combined.divide(totalSpending, 4, java.math.RoundingMode.HALF_UP) * BigDecimal(100)).toFloat()
                    } else {
                        0f
                    },
                )
            }
        } else {
            null
        }

        if (cardAndBank == null && cash == null) return null

        return PaymentModeBreakdown(
            cardAndBank = cardAndBank,
            cash = cash,
            currency = displayCurrency,
        )
    }
}

private fun previousDateRange(start: LocalDate, end: LocalDate): Pair<LocalDate, LocalDate> {
    val days = ChronoUnit.DAYS.between(start, end) + 1
    val prevEnd = start.minusDays(1)
    val prevStart = prevEnd.minusDays(days - 1)
    return prevStart to prevEnd
}

/**
 * Internal state for combining all filter parameters.
 * Used in reactive Flow to trigger data reload when any filter changes.
 */
private data class FilterState(
    val period: TimePeriod,
    val customRange: Pair<LocalDate, LocalDate>?,
    val typeFilter: TransactionTypeFilter,
    val currency: String,
    val isUnifiedMode: Boolean = false,
    val categoryFilter: String? = null,
    val monthStartDay: Int = 1,
    val useFinancialMonth: Boolean = true,
    val monthStartOverrides: Map<String, Int> = emptyMap(),
    val useFixedBudgetPeriodEnd: Boolean = false,
    val budgetPeriodEndDay: Int = 31
)

data class AnalyticsUiState(
    val totalSpending: BigDecimal = BigDecimal.ZERO,
    val categoryBreakdown: List<CategoryData> = emptyList(),
    val topMerchants: List<MerchantData> = emptyList(),
    val transactionCount: Int = 0,
    val averageAmount: BigDecimal = BigDecimal.ZERO,
    val topCategory: String? = null,
    val topCategoryPercentage: Float = 0f,
    val currency: String = "",
    val isLoading: Boolean = true,
    val spendingTrend: List<BalancePoint> = emptyList(),
    val availableCategories: List<String> = emptyList(),
    val categoryOverlaps: List<CategoryOverlapData> = emptyList(),
    val multiCategoryTransactions: List<MultiCategoryTransactionData> = emptyList(),
    val topTags: List<TagData> = emptyList(),
    val periodStart: LocalDate? = null,
    val periodEnd: LocalDate? = null,
    val periodOutflow: PeriodOutflowSummary? = null,
    val investmentInsights: InvestmentInsights? = null,
    val paymentModeBreakdown: PaymentModeBreakdown? = null,
)

/** Spending plus investments for the active period (independent of type filter). */
data class PeriodOutflowSummary(
    val total: BigDecimal,
    val spending: BigDecimal,
    val invested: BigDecimal,
    val transactionCount: Int,
    val spendingTransactionCount: Int,
    val investmentTransactionCount: Int,
    val currency: String,
)

data class InvestmentInsights(
    val totalInvested: BigDecimal,
    val transactionCount: Int,
    val recurringCount: Int,
    val largestInvestment: BigDecimal,
    val topCategory: String?,
    val topCategoryPercentage: Float,
    val previousPeriodTotal: BigDecimal,
    val deltaPercent: Float?,
    val topMerchants: List<MerchantData>,
    val currency: String,
)

data class PaymentModeStat(
    val mode: PaymentMode,
    val total: BigDecimal,
    val transactionCount: Int,
    val percentOfTotal: Float,
)

data class CardAndBankSpendSummary(
    val total: BigDecimal,
    val transactionCount: Int,
    val creditTotal: BigDecimal,
    val creditCount: Int,
    val bankTotal: BigDecimal,
    val bankCount: Int,
    val percentOfSpending: Float,
)

data class PaymentModeBreakdown(
    val cardAndBank: CardAndBankSpendSummary?,
    val cash: PaymentModeStat?,
    val currency: String,
)

data class CategoryData(
    val name: String,
    val amount: BigDecimal,
    val percentage: Float,
    val transactionCount: Int
)

data class MerchantData(
    val name: String,
    val amount: BigDecimal,
    val transactionCount: Int,
    val isSubscription: Boolean
)

data class CategoryOverlapData(
    val categoryA: String,
    val categoryB: String,
    val coOccurrenceCount: Int
)

data class MultiCategoryTransactionData(
    val transactionId: Long,
    val merchantName: String,
    val amount: BigDecimal,
    val dateTime: java.time.LocalDateTime,
    val categories: List<String>,
    val currency: String
)

data class TagData(
    val name: String,
    val transactionCount: Int,
    val totalAmount: BigDecimal
)

