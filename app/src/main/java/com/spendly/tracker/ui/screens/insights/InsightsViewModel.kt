package com.spendly.tracker.ui.screens.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.spendly.tracker.data.preferences.UserPreferencesRepository
import com.spendly.tracker.data.repository.SalaryMonthOverrideRepository
import com.spendly.tracker.data.repository.TransactionRepository
import com.spendly.tracker.domain.usecase.ComputeInsightsUseCase
import com.spendly.tracker.domain.model.SmartInsight
import com.spendly.tracker.presentation.common.getDateRangeForYearMonth
import com.spendly.tracker.utils.DateRangeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val computeInsightsUseCase: ComputeInsightsUseCase,
    transactionRepository: TransactionRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val salaryMonthOverrideRepository: SalaryMonthOverrideRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    // Captured before getStateFlow sets the key — true means we're restoring saved state
    // (e.g. process death), false means a fresh open that needs the correct anchor month.
    private val wasSelectedMonthSaved = savedStateHandle.contains("selectedMonth")

    private val selectedMonthKey = savedStateHandle.getStateFlow(
        key = "selectedMonth",
        initialValue = YearMonth.now().toString()
    )

    init {
        if (!wasSelectedMonthSaved) {
            viewModelScope.launch {
                val useFinancial = userPreferencesRepository.useFinancialMonth.first()
                if (!useFinancial) return@launch
                val startDay = userPreferencesRepository.monthStartDay.first()
                val useFixed = userPreferencesRepository.useFixedBudgetPeriodEnd.first()
                val endDay = userPreferencesRepository.budgetPeriodEndDay.first()
                val (periodStart, _) = DateRangeUtils.calculateBudgetPeriodRange(
                    LocalDate.now(), startDay, useFixed, endDay
                )
                val correctMonth = YearMonth.from(periodStart)
                if (correctMonth != YearMonth.now()) {
                    savedStateHandle["selectedMonth"] = correctMonth.toString()
                }
            }
        }
    }

    val selectedMonth: StateFlow<YearMonth> = selectedMonthKey
        .map { key -> YearMonth.parse(key) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = YearMonth.now()
        )

    private data class InsightsParams(
        val month: YearMonth,
        val monthStartDay: Int,
        val useFinancialMonth: Boolean,
        val monthStartOverrides: Map<String, Int>,
        val useFixedBudgetPeriodEnd: Boolean,
        val budgetPeriodEndDay: Int,
    )

    private val insightsParams = combine(
        selectedMonth,
        userPreferencesRepository.monthStartDay,
        userPreferencesRepository.useFinancialMonth,
        salaryMonthOverrideRepository.overridesMap,
        userPreferencesRepository.useFixedBudgetPeriodEnd,
        userPreferencesRepository.budgetPeriodEndDay,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        InsightsParams(
            month = values[0] as YearMonth,
            monthStartDay = values[1] as Int,
            useFinancialMonth = values[2] as Boolean,
            monthStartOverrides = values[3] as Map<String, Int>,
            useFixedBudgetPeriodEnd = values[4] as Boolean,
            budgetPeriodEndDay = values[5] as Int,
        )
    }

    /** The resolved start/end date for the currently selected period. */
    val activePeriodRange: StateFlow<Pair<LocalDate, LocalDate>> = insightsParams
        .map { p -> p.resolveDateRange() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = YearMonth.now().atDay(1) to YearMonth.now().atEndOfMonth(),
        )

    val insights: StateFlow<List<SmartInsight>> = insightsParams
        .flatMapLatest { p ->
            flow {
                val currentRange = p.resolveDateRange()
                val prevRange = p.resolvePreviousDateRange()
                emit(
                    computeInsightsUseCase.computeForPeriod(
                        anchorMonth = p.month,
                        dateRange = currentRange,
                        previousDateRange = prevRange,
                    )
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private fun InsightsParams.resolveDateRange(): Pair<LocalDate, LocalDate> =
        getDateRangeForYearMonth(
            yearMonth = month,
            monthStartDay = monthStartDay,
            useFinancialMonth = useFinancialMonth,
            monthStartOverrides = monthStartOverrides,
            useFixedBudgetPeriodEnd = useFixedBudgetPeriodEnd,
            budgetPeriodEndDay = budgetPeriodEndDay,
        )

    private fun InsightsParams.resolvePreviousDateRange(): Pair<LocalDate, LocalDate> =
        getDateRangeForYearMonth(
            yearMonth = month.minusMonths(1),
            monthStartDay = monthStartDay,
            useFinancialMonth = useFinancialMonth,
            monthStartOverrides = monthStartOverrides,
            useFixedBudgetPeriodEnd = useFixedBudgetPeriodEnd,
            budgetPeriodEndDay = budgetPeriodEndDay,
        )

    val uncategorizedTransactionPercentage: StateFlow<Int?> =
        transactionRepository.getUncategorizedTransactionSummary()
            .map { summary ->
                if (summary.totalCount == 0) {
                    null
                } else {
                    ((summary.uncategorizedCount.toDouble() / summary.totalCount) * 100).roundToInt()
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null,
            )

    fun navigateToPreviousMonth() {
        savedStateHandle["selectedMonth"] = selectedMonth.value.minusMonths(1).toString()
    }

    fun navigateToNextMonth() {
        val nextMonth = selectedMonth.value.plusMonths(1)
        if (!nextMonth.isAfter(YearMonth.now())) {
            savedStateHandle["selectedMonth"] = nextMonth.toString()
        }
    }
}
