package com.spendly.tracker.worker

import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.currency.CurrencyConversionService
import com.spendly.tracker.data.preferences.UserPreferencesRepository
import com.spendly.tracker.data.repository.SalaryMonthOverrideRepository
import com.spendly.tracker.data.repository.TransactionRepository
import com.spendly.tracker.domain.service.SalaryPayPeriodDetector
import com.spendly.tracker.presentation.common.getDateRangeForYearMonthNavigation
import com.spendly.tracker.ui.screens.analytics.SavingsRateSummary
import com.spendly.tracker.ui.screens.analytics.computeMonthlySavingsRates
import com.spendly.tracker.ui.screens.savings.DEFAULT_SAVINGS_GOAL_ID
import com.spendly.tracker.ui.screens.savings.DEFAULT_SAVINGS_GOAL_PERCENT
import com.spendly.tracker.ui.screens.savings.SavingsGoalDefinition
import com.spendly.tracker.ui.screens.savings.SavingsGoalKind
import com.spendly.tracker.ui.screens.savings.SavingsGoalPlan
import com.spendly.tracker.ui.screens.savings.computeSavingsGoalPlan
import com.spendly.tracker.ui.screens.savings.defaultSavingsGoalEndMonth
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/** The goal inputs a background check needs, resolved once per worker run. */
data class SavingsGoalSnapshot(
    val plan: SavingsGoalPlan,
    val currency: String,
    val percent: Int,
    val endMonth: YearMonth,
    val goal: SavingsGoalDefinition,
)

/**
 * Loads the percentage savings goal the same way the Savings page does, for use by
 * background workers. Keeping the query in one place means the weekly reminder and the
 * pace/milestone alerts never disagree with what the user sees on screen.
 */
@Singleton
class SavingsGoalLoader @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val currencyConversionService: CurrencyConversionService,
    private val salaryMonthOverrideRepository: SalaryMonthOverrideRepository,
) {
    companion object {
        private const val DEFAULT_WINDOW_MONTHS = 6
    }

    suspend fun load(
        today: LocalDate = LocalDate.now(),
        goalId: String? = null,
    ): SavingsGoalSnapshot {
        val currency = userPreferencesRepository.baseCurrency.first()
        val unified = userPreferencesRepository.unifiedCurrencyMode.first()
        val goals = userPreferencesRepository.getSavingsGoals().first()
        // A widget can be bound to a specific goal; fall back to the active goal when the
        // chosen goal was deleted or the widget has no explicit selection.
        val goal = goals.goals.firstOrNull { it.id == goalId }
            ?: goals.activeGoal
            ?: com.spendly.tracker.ui.screens.savings.defaultSavingsGoal()
        val percent = goal.normalizedPercent
        val endMonth = goal.endMonth

        val periodStart = savingsPeriodStart(today)
        val periodEnd = minOf(today, endMonth.atEndOfMonth())
        val transactions = if (periodStart.isAfter(periodEnd)) {
            emptyList()
        } else {
            transactionRepository
                .getNetTransactionsBetweenDates(periodStart, periodEnd)
                .first()
        }
        val relevant = if (unified) {
            transactions
        } else {
            transactions.filter { it.currency == currency }
        }
        val amountOf: suspend (TransactionEntity) -> BigDecimal = { tx ->
            if (unified) {
                currencyConversionService.convertAmount(tx.amount, tx.currency, currency)
            } else {
                tx.amount
            }
        }

        val bucket = computeMonthlySavingsRates(
            transactions = relevant,
            ranges = listOf(YearMonth.from(periodEnd) to (periodStart to periodEnd)),
            currentMonth = YearMonth.now(),
            amountOf = amountOf,
        ).first()

        // Mirror the page's salary detection so the projected income basis matches.
        var salarySum = BigDecimal.ZERO
        var salaryCount = 0
        for (tx in relevant) {
            if (SalaryPayPeriodDetector.scoreCandidate(tx) < SalaryPayPeriodDetector.MIN_SCORE) {
                continue
            }
            salarySum += amountOf(tx)
            salaryCount++
        }
        val salaryAverage = if (salaryCount > 0) {
            salarySum.divide(BigDecimal(salaryCount), 2, RoundingMode.HALF_UP)
        } else {
            null
        }

        val plan = run {
            fun planFor(
                kind: SavingsGoalKind,
                goalPercent: Int,
                goalAmount: BigDecimal,
                contribution: BigDecimal,
                goalEndMonth: YearMonth,
                otherMonthlySavings: BigDecimal,
            ): SavingsGoalPlan = computeSavingsGoalPlan(
                targetPercent = goalPercent,
                periodStart = periodStart,
                endMonth = goalEndMonth,
                actualIncome = bucket.income,
                actualSpent = bucket.spent,
                salaryMonthlyIncome = salaryAverage,
                summary = SavingsRateSummary(),
                projection = null,
                currency = currency,
                today = today,
                goalKind = kind,
                targetAmount = goalAmount,
                contributedAmount = contribution,
                otherMonthlySavings = otherMonthlySavings,
            )

            val otherMonthlySavings = if (goal.id == DEFAULT_SAVINGS_GOAL_ID) {
                BigDecimal.ZERO
            } else {
                goals.goals
                    .filter { it.id != goal.id }
                    .fold(BigDecimal.ZERO) { total, other ->
                        total + planFor(
                            kind = other.kind,
                            goalPercent = other.normalizedPercent,
                            goalAmount = other.normalizedAmount,
                            contribution = other.normalizedContribution,
                            goalEndMonth = other.endMonth,
                            otherMonthlySavings = BigDecimal.ZERO,
                        ).monthlySavingsNeeded
                    }
            }

            planFor(
                kind = goal.kind,
                goalPercent = percent,
                goalAmount = goal.normalizedAmount,
                contribution = goal.normalizedContribution,
                goalEndMonth = endMonth,
                otherMonthlySavings = otherMonthlySavings,
            )
        }

        return SavingsGoalSnapshot(
            plan = plan,
            currency = currency,
            percent = percent,
            endMonth = endMonth,
            goal = goal,
        )
    }

    /**
     * Start of the savings-rate window shown on the tile, matching the Savings page:
     * a custom range start when set, otherwise the earliest month in the last-N-months
     * window, resolved through the same financial-month rules as the UI.
     */
    private suspend fun savingsPeriodStart(today: LocalDate): LocalDate {
        val range = userPreferencesRepository.getSavingsRateRange().first()
        val customStart = range?.first?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        val customEnd = range?.second?.let { runCatching { YearMonth.parse(it) }.getOrNull() }

        val todayMonth = YearMonth.from(today)
        val resolvedEnd = (customEnd ?: todayMonth).coerceAtMost(todayMonth)
        val window = if (customStart != null) {
            ChronoUnit.MONTHS.between(customStart, resolvedEnd).toInt().coerceAtLeast(0) + 1
        } else {
            userPreferencesRepository.getSavingsRateWindowMonths().first()
                ?: DEFAULT_WINDOW_MONTHS
        }
        val startMonth = customStart
            ?: resolvedEnd.minusMonths((window - 1).coerceAtLeast(0).toLong())

        val (start, _) = getDateRangeForYearMonthNavigation(
            yearMonth = startMonth,
            useCalendarMonth = !userPreferencesRepository.useFinancialMonth.first(),
            monthStartDay = userPreferencesRepository.monthStartDay.first(),
            monthStartOverrides = salaryMonthOverrideRepository.overridesMap.first(),
            useFixedBudgetPeriodEnd = userPreferencesRepository.useFixedBudgetPeriodEnd.first(),
            budgetPeriodEndDay = userPreferencesRepository.budgetPeriodEndDay.first(),
            salaryDayAdjustment = userPreferencesRepository.salaryDayAdjustment.first(),
        )
        return start
    }
}
