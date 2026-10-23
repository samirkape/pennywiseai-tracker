package com.spendly.tracker.presentation.budgetgroups

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendly.tracker.data.currency.CurrencyConversionService
import com.spendly.tracker.data.database.entity.BudgetGroupType
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.data.preferences.UserPreferencesRepository
import com.spendly.tracker.data.repository.BudgetCategorySpending
import com.spendly.tracker.data.repository.BudgetGroupRepository
import com.spendly.tracker.data.repository.BudgetGroupSpending
import com.spendly.tracker.data.repository.BudgetGroupSpendingRaw
import com.spendly.tracker.data.repository.BudgetOverallSummary
import com.spendly.tracker.data.repository.SalaryMonthOverrideRepository
import com.spendly.tracker.utils.DateRangeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class BudgetTrendPoint(
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    // Budget-tracked spend excluding investment transactions.
    val spent: BigDecimal,
    // Investment transactions counted against budgets; spent + invested == hero total.
    val invested: BigDecimal,
    val budget: BigDecimal
) {
    val total: BigDecimal
        get() = spent + invested

    val isOverBudget: Boolean
        get() = budget > BigDecimal.ZERO && total > budget
}

data class BudgetGroupsUiState(
    val summary: BudgetOverallSummary? = null,
    val trend: List<BudgetTrendPoint> = emptyList(),
    val isLoading: Boolean = true,
    val hasGroups: Boolean = false,
    val selectedYear: Int = LocalDate.now().year,
    val selectedMonth: Int = LocalDate.now().monthValue,
    val currency: String = "INR",
    val baseCurrency: String = "INR",
    val isUnifiedMode: Boolean = false,
    val useFinancialMonth: Boolean = true,
    val periodLabel: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetGroupsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val budgetGroupRepository: BudgetGroupRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val currencyConversionService: CurrencyConversionService,
    private val salaryMonthOverrideRepository: SalaryMonthOverrideRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetGroupsUiState())
    val uiState: StateFlow<BudgetGroupsUiState> = _uiState.asStateFlow()

    private val _selectedYearMonth = MutableStateFlow(YearMonth.now())

    private data class SpendingParams(
        val displayCurrency: String,
        val baseCurrency: String,
        val unifiedMode: Boolean,
        val yearMonth: YearMonth,
        val monthStartDay: Int,
        val useFinancial: Boolean,
        val useFixedEnd: Boolean,
        val endDom: Int,
        val overrides: Map<String, Int>
    )

    companion object {
        private const val TAG = "BudgetGroupsVM"
        private const val TREND_PERIODS = 6
    }

    init {
        loadBudgetData()
        observeActiveGroups()
    }

    private fun observeActiveGroups() {
        viewModelScope.launch {
            budgetGroupRepository.getActiveGroups().collect { groups ->
                _uiState.value = _uiState.value.copy(hasGroups = groups.isNotEmpty())
            }
        }
    }

    private fun loadBudgetData() {
        viewModelScope.launch {
            combine(
                userPreferencesRepository.baseCurrency,
                userPreferencesRepository.unifiedCurrencyMode,
                userPreferencesRepository.displayCurrency,
                _selectedYearMonth,
                userPreferencesRepository.useFinancialMonth,
                userPreferencesRepository.monthStartDay,
                userPreferencesRepository.useFixedBudgetPeriodEnd,
                userPreferencesRepository.budgetPeriodEndDay,
                salaryMonthOverrideRepository.overridesMap
            ) { args ->
                val baseCurrency = args[0] as String
                val unifiedMode = args[1] as Boolean
                val displayCurrency = args[2] as String
                val yearMonth = args[3] as YearMonth
                val useFinancial = args[4] as Boolean
                val monthStartDay = args[5] as Int
                val useFixedEnd = args[6] as Boolean
                val endDom = args[7] as Int
                @Suppress("UNCHECKED_CAST")
                val overrides = args[8] as Map<String, Int>
                data class Params(
                    val baseCurrency: String,
                    val unifiedMode: Boolean,
                    val displayCurrency: String,
                    val yearMonth: YearMonth,
                    val useFinancial: Boolean,
                    val monthStartDay: Int,
                    val useFixedEnd: Boolean,
                    val endDom: Int,
                    val overrides: Map<String, Int>
                )
                Params(
                    baseCurrency,
                    unifiedMode,
                    displayCurrency,
                    yearMonth,
                    useFinancial,
                    monthStartDay,
                    useFixedEnd,
                    endDom,
                    overrides
                )
            }.collect { params ->
                val currency = if (params.unifiedMode) params.displayCurrency else params.baseCurrency
                val isCurrentMonth = params.yearMonth == YearMonth.now()
                val periodLabel = if (params.useFinancial) {
                    val (start, end) = if (isCurrentMonth) {
                        DateRangeUtils.calculateBudgetPeriodRange(
                            LocalDate.now(),
                            params.monthStartDay,
                            params.useFixedEnd,
                            params.endDom,
                            params.overrides
                        )
                    } else if (params.useFixedEnd) {
                        DateRangeUtils.customDomPeriodStartingInMonth(params.yearMonth, params.monthStartDay, params.endDom)
                    } else {
                        DateRangeUtils.financialMonthRangeFor(params.yearMonth, params.monthStartDay, params.overrides)
                    }
                    DateRangeUtils.formatDateRange(start, end)
                } else {
                    params.yearMonth.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"))
                }
                _uiState.value = _uiState.value.copy(
                    currency = currency,
                    baseCurrency = params.baseCurrency,
                    isUnifiedMode = params.unifiedMode,
                    selectedYear = params.yearMonth.year,
                    selectedMonth = params.yearMonth.monthValue,
                    useFinancialMonth = params.useFinancial,
                    periodLabel = periodLabel
                )
            }
        }

        viewModelScope.launch {
            combine(
                userPreferencesRepository.baseCurrency,
                userPreferencesRepository.unifiedCurrencyMode,
                userPreferencesRepository.displayCurrency,
                _selectedYearMonth,
                userPreferencesRepository.monthStartDay,
                userPreferencesRepository.useFinancialMonth,
                userPreferencesRepository.useFixedBudgetPeriodEnd,
                userPreferencesRepository.budgetPeriodEndDay,
                salaryMonthOverrideRepository.overridesMap
            ) { args ->
                val baseCurrency = args[0] as String
                val unifiedMode = args[1] as Boolean
                val displayCurrency = args[2] as String
                val yearMonth = args[3] as YearMonth
                val monthStartDay = args[4] as Int
                val useFinancial = args[5] as Boolean
                val useFixedEnd = args[6] as Boolean
                val endDom = args[7] as Int
                @Suppress("UNCHECKED_CAST")
                val overrides = args[8] as Map<String, Int>
                SpendingParams(
                    if (unifiedMode) displayCurrency else baseCurrency,
                    baseCurrency,
                    unifiedMode,
                    yearMonth,
                    monthStartDay,
                    useFinancial,
                    useFixedEnd,
                    endDom,
                    overrides
                )
            }.flatMapLatest { params ->
                val isCurrentMonth = params.yearMonth == YearMonth.now()
                val summaryFlow: Flow<BudgetOverallSummary> = if (params.useFinancial) {
                    val (financialStart, financialEnd) = resolveSelectedRange(params)
                    android.util.Log.d("PWDebug", "=== BudgetGroupsVM date range (financial) ===")
                    android.util.Log.d("PWDebug", "start=$financialStart end=$financialEnd currency=${params.displayCurrency} monthStartDay=${params.monthStartDay}")
                    if (params.unifiedMode) {
                        budgetGroupRepository.getGroupSpendingAllCurrencies(financialStart, financialEnd)
                            .map { raw -> convertRawToSummary(raw, params.displayCurrency, params.baseCurrency) }
                    } else {
                        budgetGroupRepository.getGroupSpending(financialStart, financialEnd, params.displayCurrency)
                    }
                } else {
                    android.util.Log.d("PWDebug", "=== BudgetGroupsVM date range (calendar) ===")
                    android.util.Log.d("PWDebug", "yearMonth=${params.yearMonth} useFinancial=${params.useFinancial} isCurrentMonth=$isCurrentMonth")
                    if (params.unifiedMode) {
                        budgetGroupRepository.getGroupSpendingAllCurrencies(params.yearMonth.year, params.yearMonth.monthValue)
                            .map { raw -> convertRawToSummary(raw, params.displayCurrency, params.baseCurrency) }
                    } else {
                        budgetGroupRepository.getGroupSpending(params.yearMonth.year, params.yearMonth.monthValue, params.displayCurrency)
                    }
                }
                summaryFlow.map { summary ->
                    // Trend ends at the selected period; its last point always mirrors the
                    // live summary so the chart and hero can never disagree.
                    val (currentStart, currentEnd) = resolveSelectedRange(params)
                    val trend = buildPastTrend(params) + BudgetTrendPoint(
                        periodStart = currentStart,
                        periodEnd = currentEnd,
                        spent = summary.totalLimitSpent - summary.totalInvested,
                        invested = summary.totalInvested,
                        budget = summary.totalLimitBudget
                    )
                    summary to trend
                }
            }.collect { (summary, trend) ->
                _uiState.value = _uiState.value.copy(
                    summary = summary,
                    trend = trend,
                    isLoading = false
                )
            }
        }
    }

    /** Resolves the date range of the period currently selected in the UI. */
    private fun resolveSelectedRange(params: SpendingParams): Pair<LocalDate, LocalDate> {
        val yearMonth = params.yearMonth
        val isCurrentMonth = yearMonth == YearMonth.now()
        return if (params.useFinancial) {
            when {
                isCurrentMonth -> DateRangeUtils.calculateBudgetPeriodRange(
                    LocalDate.now(),
                    params.monthStartDay,
                    params.useFixedEnd,
                    params.endDom,
                    params.overrides
                )
                params.useFixedEnd -> DateRangeUtils.customDomPeriodStartingInMonth(
                    yearMonth, params.monthStartDay, params.endDom
                )
                else -> DateRangeUtils.financialMonthRangeFor(
                    yearMonth, params.monthStartDay, params.overrides
                )
            }
        } else {
            yearMonth.atDay(1) to yearMonth.atEndOfMonth()
        }
    }

    /**
     * Builds trend points for the [TREND_PERIODS] periods preceding the selected one,
     * oldest first. Historical periods resolve through month snapshots, so past budget
     * structure is preserved even after the user edits a bucket.
     */
    private suspend fun buildPastTrend(params: SpendingParams): List<BudgetTrendPoint> {
        return try {
            val pastRanges = mutableListOf<Pair<LocalDate, LocalDate>>()
            if (params.useFinancial) {
                var cursor = resolveSelectedRange(params).first.minusDays(1)
                repeat(TREND_PERIODS - 1) {
                    val range = DateRangeUtils.calculateBudgetPeriodRange(
                        cursor,
                        params.monthStartDay,
                        params.useFixedEnd,
                        params.endDom,
                        params.overrides
                    )
                    pastRanges.add(0, range)
                    cursor = range.first.minusDays(1)
                }
            } else {
                var cursor = params.yearMonth.minusMonths(1)
                repeat(TREND_PERIODS - 1) {
                    pastRanges.add(0, cursor.atDay(1) to cursor.atEndOfMonth())
                    cursor = cursor.minusMonths(1)
                }
            }

            pastRanges.map { (start, end) ->
                val summary = if (params.unifiedMode) {
                    convertRawToSummary(
                        budgetGroupRepository.getGroupSpendingAllCurrencies(start, end).first(),
                        params.displayCurrency,
                        params.baseCurrency
                    )
                } else {
                    budgetGroupRepository.getGroupSpending(start, end, params.displayCurrency).first()
                }
                BudgetTrendPoint(
                    periodStart = start,
                    periodEnd = end,
                    spent = summary.totalLimitSpent - summary.totalInvested,
                    invested = summary.totalInvested,
                    budget = summary.totalLimitBudget
                )
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to build budget trend: ${e.message}")
            emptyList()
        }
    }

    fun toggleMonthViewMode() {
        viewModelScope.launch {
            withContext(NonCancellable) {
                userPreferencesRepository.updateUseFinancialMonth(!_uiState.value.useFinancialMonth)
            }
        }
    }

    private suspend fun convertRawToSummary(
        raw: BudgetGroupSpendingRaw,
        displayCurrency: String,
        baseCurrency: String
    ): BudgetOverallSummary {
        val incomeTransactions = raw.allTransactions.filter {
            it.transaction.transactionType == TransactionType.INCOME
        }
        var totalIncome = BigDecimal.ZERO
        for (tx in incomeTransactions) {
            totalIncome = totalIncome.add(
                currencyConversionService.convertAmount(tx.transaction.amount, tx.transaction.currency, displayCurrency)
            )
        }

        // Primary category or split lines only; junction tags do not contribute amounts.
        val categoryAmounts = mutableMapOf<String, BigDecimal>()
        val investedCategoryAmounts = mutableMapOf<String, BigDecimal>()
        raw.allTransactions.forEach { txWithSplits ->
            val tx = txWithSplits.transaction
            if (tx.transactionType != TransactionType.INCOME) {
                val fromCurrency = tx.currency
                val isInvestment = tx.transactionType == TransactionType.INVESTMENT
                txWithSplits.getAmountByCategory().forEach { (category, amount) ->
                    val converted = currencyConversionService.convertAmount(amount, fromCurrency, displayCurrency)
                    val categoryName = category.ifEmpty { "Others" }
                    categoryAmounts[categoryName] = (categoryAmounts[categoryName] ?: BigDecimal.ZERO) + converted
                    if (isInvestment) {
                        investedCategoryAmounts[categoryName] =
                            (investedCategoryAmounts[categoryName] ?: BigDecimal.ZERO) + converted
                    }
                }
            }
        }

        // Pre-compute converted category amounts per transaction for pace chart
        val yearMonth = _selectedYearMonth.value
        val daysInMonth = yearMonth.lengthOfMonth()
        val effectiveDays = raw.daysElapsed.coerceAtMost(daysInMonth)

        data class ConvertedTxDay(val day: Int, val categoryAmounts: Map<String, Double>)
        val convertedTxDays = raw.allTransactions.mapNotNull { txWithSplits ->
            val tx = txWithSplits.transaction
            if (tx.transactionType == com.spendly.tracker.data.database.entity.TransactionType.INCOME ||
                tx.transactionType == com.spendly.tracker.data.database.entity.TransactionType.TRANSFER ||
                tx.transactionType == com.spendly.tracker.data.database.entity.TransactionType.INVESTMENT ||
                tx.loanId != null
            ) return@mapNotNull null
            val day = tx.dateTime.dayOfMonth.coerceIn(1, daysInMonth)
            val catAmounts = txWithSplits.getAmountByCategory().mapValues { (_, amount) ->
                currencyConversionService.convertAmount(amount, tx.currency, displayCurrency).toDouble()
            }.mapKeys { (cat, _) -> cat.ifEmpty { "Others" } }
            ConvertedTxDay(day, catAmounts)
        }

        fun buildGroupPaceUnified(
            categoryNames: Set<String>?,
            groupBudget: BigDecimal
        ): Pair<List<Double>, List<Double>> {
            if (effectiveDays < 1) return emptyList<Double>() to emptyList()
            val dailyAmounts = DoubleArray(daysInMonth)
            convertedTxDays.forEach { txDay ->
                if (categoryNames == null) {
                    dailyAmounts[txDay.day - 1] += txDay.categoryAmounts.values.sum()
                } else {
                    txDay.categoryAmounts.forEach { (cat, amount) ->
                        if (cat in categoryNames) dailyAmounts[txDay.day - 1] += amount
                    }
                }
            }
            val cumulative = mutableListOf<Double>()
            var running = 0.0
            for (i in 0 until effectiveDays) { running += dailyAmounts[i]; cumulative.add(running) }
            val pace = if (groupBudget > BigDecimal.ZERO) {
                val dp = groupBudget.toDouble() / daysInMonth
                (1..effectiveDays).map { it * dp }
            } else emptyList()
            return cumulative to pace
        }

        val groupSpendingList = raw.budgetsWithCategories.map { group ->
            val isTrackingAll = group.categories.isEmpty()
            android.util.Log.d("BudgetBucket", "--- group '${group.budget.name}' [${group.budget.groupType}] trackAll=$isTrackingAll categories=${group.categories.map { it.categoryName }} ---")
            val catSpending = group.categories.map { cat ->
                val actual = categoryAmounts[cat.categoryName] ?: BigDecimal.ZERO
                val convertedBudget = currencyConversionService.convertAmount(cat.budgetAmount, baseCurrency, displayCurrency)
                android.util.Log.d("BudgetBucket", "  cat='${cat.categoryName}' budget=$convertedBudget actual=$actual pct=${if (convertedBudget > BigDecimal.ZERO) (actual.toFloat() / convertedBudget.toFloat() * 100f) else 0f}%")
                val pctUsed = if (convertedBudget > BigDecimal.ZERO) {
                    (actual.toFloat() / convertedBudget.toFloat() * 100f).coerceAtLeast(0f)
                } else 0f
                val dailySpend = if (raw.daysElapsed > 0 && actual > BigDecimal.ZERO) {
                    actual.divide(BigDecimal(raw.daysElapsed), 0, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO
                BudgetCategorySpending(
                    categoryName = cat.categoryName,
                    budgetAmount = convertedBudget,
                    actualAmount = actual,
                    percentageUsed = pctUsed,
                    dailySpend = dailySpend
                )
            }
            val totalBudget = if (isTrackingAll) {
                currencyConversionService.convertAmount(group.budget.limitAmount, baseCurrency, displayCurrency)
            } else {
                catSpending.fold(BigDecimal.ZERO) { acc, c -> acc + c.budgetAmount }
            }
            val totalActual = if (isTrackingAll) {
                categoryAmounts.values.fold(BigDecimal.ZERO) { acc, v -> acc + v }
            } else {
                catSpending.fold(BigDecimal.ZERO) { acc, c -> acc + c.actualAmount }
            }
            val remaining = totalBudget - totalActual
            val pctUsed = if (totalBudget > BigDecimal.ZERO) {
                (totalActual.toFloat() / totalBudget.toFloat() * 100f).coerceAtLeast(0f)
            } else 0f
            val dailyAllowance = if (raw.daysRemaining > 0 && remaining > BigDecimal.ZERO) {
                remaining.divide(BigDecimal(raw.daysRemaining), 0, RoundingMode.HALF_UP)
            } else BigDecimal.ZERO

            val catNames = if (isTrackingAll) null else group.categories.map { it.categoryName }.toSet()
            val (cumSpending, budgetPace) = buildGroupPaceUnified(catNames, totalBudget)

            BudgetGroupSpending(
                group = group,
                categorySpending = if (isTrackingAll) emptyList() else catSpending,
                totalBudget = totalBudget,
                totalActual = totalActual,
                remaining = remaining,
                percentageUsed = pctUsed,
                dailyAllowance = dailyAllowance,
                daysRemaining = raw.daysRemaining,
                daysElapsed = raw.daysElapsed,
                isTrackingAllExpenses = isTrackingAll,
                dailyCumulativeSpending = cumSpending,
                dailyBudgetPace = budgetPace
            )
        }

        val limitGroups = groupSpendingList.filter { it.group.budget.groupType == BudgetGroupType.LIMIT }
        val targetGroups = groupSpendingList.filter { it.group.budget.groupType == BudgetGroupType.TARGET }
        val expectedGroups = groupSpendingList.filter { it.group.budget.groupType == BudgetGroupType.EXPECTED }

        val totalLimitBudget = limitGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalBudget }
        val totalLimitSpent = limitGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalActual }
        // Mirror totalLimitSpent's per-group scoping so spent - invested splits cleanly.
        val totalInvested = limitGroups.fold(BigDecimal.ZERO) { acc, g ->
            acc + if (g.isTrackingAllExpenses) {
                investedCategoryAmounts.values.fold(BigDecimal.ZERO) { a, v -> a + v }
            } else {
                g.group.categories.fold(BigDecimal.ZERO) { a, c ->
                    a + (investedCategoryAmounts[c.categoryName] ?: BigDecimal.ZERO)
                }
            }
        }
        val totalTargetGoal = targetGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalBudget }
        val totalTargetActual = targetGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalActual }
        val totalExpectedBudget = expectedGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalBudget }
        val totalExpectedActual = expectedGroups.fold(BigDecimal.ZERO) { acc, g -> acc + g.totalActual }

        val netSavings = totalIncome - totalLimitSpent
        val savingsRate = if (totalIncome > BigDecimal.ZERO) {
            (netSavings.toFloat() / totalIncome.toFloat() * 100f)
        } else 0f

        val limitRemaining = totalLimitBudget - totalLimitSpent
        val dailyAllowance = if (raw.daysRemaining > 0 && limitRemaining > BigDecimal.ZERO) {
            limitRemaining.divide(BigDecimal(raw.daysRemaining), 0, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        return BudgetOverallSummary(
            groups = groupSpendingList,
            totalIncome = totalIncome,
            totalLimitBudget = totalLimitBudget,
            totalLimitSpent = totalLimitSpent,
            totalInvested = totalInvested,
            totalTargetGoal = totalTargetGoal,
            totalTargetActual = totalTargetActual,
            totalExpectedBudget = totalExpectedBudget,
            totalExpectedActual = totalExpectedActual,
            netSavings = netSavings,
            savingsRate = savingsRate,
            dailyAllowance = dailyAllowance,
            daysRemaining = raw.daysRemaining,
            currency = displayCurrency
        )
    }

    fun selectPreviousMonth() {
        _selectedYearMonth.value = _selectedYearMonth.value.minusMonths(1)
        _uiState.value = _uiState.value.copy(isLoading = true)
    }

    fun selectNextMonth() {
        val next = _selectedYearMonth.value.plusMonths(1)
        if (!next.isAfter(YearMonth.now())) {
            _selectedYearMonth.value = next
            _uiState.value = _uiState.value.copy(isLoading = true)
        }
    }

    fun selectCurrentMonth() {
        _selectedYearMonth.value = YearMonth.now()
    }

    fun deleteGroup(budgetId: Long) {
        viewModelScope.launch {
            budgetGroupRepository.deleteGroup(budgetId)
            com.spendly.tracker.widget.BudgetWidgetUpdateWorker.enqueueOneShot(context)
        }
    }
    
    fun moveGroupUp(budgetId: Long) {
        viewModelScope.launch {
            budgetGroupRepository.moveGroupUp(budgetId)
            com.spendly.tracker.widget.BudgetWidgetUpdateWorker.enqueueOneShot(context)
        }
    }
    
    fun moveGroupDown(budgetId: Long) {
        viewModelScope.launch {
            budgetGroupRepository.moveGroupDown(budgetId)
            com.spendly.tracker.widget.BudgetWidgetUpdateWorker.enqueueOneShot(context)
        }
    }

    private var isCreatingSmartDefaults = false

    fun runSmartDefaults() {
        if (isCreatingSmartDefaults) return
        viewModelScope.launch {
            isCreatingSmartDefaults = true
            try {
                val baseCurrency = userPreferencesRepository.baseCurrency.first()
                budgetGroupRepository.createSmartDefaults(baseCurrency)
                com.spendly.tracker.widget.BudgetWidgetUpdateWorker.enqueueOneShot(context)
            } finally {
                isCreatingSmartDefaults = false
            }
        }
    }

}
