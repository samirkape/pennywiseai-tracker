package com.pennywiseai.tracker.ui.screens.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.SalaryMonthOverrideRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.pennywiseai.tracker.presentation.common.TimePeriod
import com.pennywiseai.tracker.presentation.common.TransactionTypeFilter
import com.pennywiseai.tracker.presentation.common.defaultTimePeriod
import com.pennywiseai.tracker.presentation.common.getDateRangeForPeriod
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

    init {
        viewModelScope.launch {
            _selectedPeriod.value = defaultTimePeriod(
                userPreferencesRepository.useFinancialMonth.first()
            )
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
        // Determine date range based on selected period
        val dateRange = if (filterState.period == TimePeriod.CUSTOM) {
            val customRange = filterState.customRange
            // Guard against invalid state: CUSTOM period must have a date range
            if (customRange == null) {
                val fallbackPeriod = defaultTimePeriod(filterState.useFinancialMonth)
                android.util.Log.e("AnalyticsViewModel",
                    "CUSTOM period selected but no date range set - falling back to $fallbackPeriod")
                _selectedPeriod.value = fallbackPeriod
                getDateRangeForPeriod(
                    fallbackPeriod,
                    filterState.monthStartDay,
                    filterState.useFinancialMonth,
                    filterState.monthStartOverrides,
                    filterState.useFixedBudgetPeriodEnd,
                    filterState.budgetPeriodEndDay
                )
            } else {
                customRange
            }
        } else {
            getDateRangeForPeriod(
                filterState.period,
                filterState.monthStartDay,
                filterState.useFinancialMonth,
                filterState.monthStartOverrides,
                filterState.useFixedBudgetPeriodEnd,
                filterState.budgetPeriodEndDay
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
                        it.transaction.transactionType == com.pennywiseai.tracker.data.database.entity.TransactionType.EXPENSE ||
                        it.transaction.transactionType == com.pennywiseai.tracker.data.database.entity.TransactionType.CREDIT
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

                // ---- Insight 3: Category trends over time (from splits data) ----
                val categoryTrends: Map<String, List<BalancePoint>> = run {
                    val monthsBetween = ChronoUnit.MONTHS.between(
                        dateRange.first.withDayOfMonth(1),
                        dateRange.second.withDayOfMonth(1)
                    )
                    if (monthsBetween < 1) {
                        emptyMap()
                    } else {
                        val topCatNames = categoryAmounts.entries
                            .sortedByDescending { it.value }
                            .take(5)
                            .map { it.key }

                        val monthMap = mutableMapOf<YearMonth, MutableMap<String, BigDecimal>>()
                        for (txWithSplits in categoryFilteredWithSplits) {
                            val ym = YearMonth.from(txWithSplits.transaction.dateTime)
                            val perMonth = monthMap.getOrPut(ym) { mutableMapOf() }
                            txWithSplits.getAmountByCategory().forEach { (cat, amt) ->
                                val catName = cat.ifEmpty { "Others" }
                                if (catName in topCatNames) {
                                    val converted = if (isUnified) {
                                        currencyConversionService.convertAmount(amt, txWithSplits.transaction.currency, displayCurrency)
                                    } else amt
                                    perMonth[catName] = (perMonth[catName] ?: BigDecimal.ZERO) + converted
                                }
                            }
                        }

                        topCatNames.associateWith { catName ->
                            monthMap.entries
                                .sortedBy { it.key }
                                .map { (ym, catAmts) ->
                                    BalancePoint(
                                        timestamp = ym.atDay(1).atStartOfDay(),
                                        balance = catAmts[catName] ?: BigDecimal.ZERO,
                                        currency = displayCurrency
                                    )
                                }
                        }.filter { (_, points) -> points.any { it.balance > BigDecimal.ZERO } }
                    }
                }

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
                    categoryTrends = categoryTrends,
                    categoryOverlaps = categoryOverlaps,
                    multiCategoryTransactions = multiCategoryTransactions
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
        }
        _selectedPeriod.value = period
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
                val transactionsByDate = transactions.groupBy { it.dateTime.toLocalDate() }
                var currentDate = startDate
                while (!currentDate.isAfter(endDate) && !currentDate.isAfter(LocalDate.now())) {
                    val totalAmount = (transactionsByDate[currentDate] ?: emptyList()).sumOf { it.amount.toDouble() }.toBigDecimal()
                    trend.add(BalancePoint(timestamp = currentDate.atStartOfDay(), balance = totalAmount, currency = currency))
                    currentDate = currentDate.plusDays(1)
                }
            }
        }
        return trend
    }
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
    val categoryTrends: Map<String, List<BalancePoint>> = emptyMap(),
    val categoryOverlaps: List<CategoryOverlapData> = emptyList(),
    val multiCategoryTransactions: List<MultiCategoryTransactionData> = emptyList()
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

