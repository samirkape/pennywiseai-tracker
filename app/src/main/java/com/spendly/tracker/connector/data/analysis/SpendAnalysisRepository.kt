package com.spendly.tracker.connector.data.analysis

import com.spendly.tracker.connector.domain.AnalysisWindow
import com.spendly.tracker.connector.domain.SpendAnalysis
import com.spendly.tracker.connector.domain.TransactionBrief
import com.spendly.tracker.connector.domain.WindowUnit
import com.spendly.tracker.data.currency.CurrencyConversionService
import com.spendly.tracker.data.database.entity.LoanDirection
import com.spendly.tracker.data.database.entity.LoanEntity
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionWithSplits
import com.spendly.tracker.data.preferences.UserPreferencesRepository
import com.spendly.tracker.data.repository.LoanRepository
import com.spendly.tracker.data.repository.SalaryMonthOverrideRepository
import com.spendly.tracker.data.repository.TransactionRepository
import com.spendly.tracker.utils.DateRangeUtils
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns raw transactions into the privacy-safe brief that is handed to a provider.
 *
 * Reads exactly the same rows, in exactly the same currency, as the Analytics screen:
 * user-excluded rows, loan-linked rows, transfers, credit card bill payments and
 * investments never reach the aggregator as spending. Only aggregate figures leave
 * this class — descriptions, SMS bodies, account numbers and bank names are never read.
 *
 * Unlike the Analytics screen the rows are read *gross*: refunds are not netted
 * out of their original expense here, because the brief has to show the gross
 * spend, the refund and the net spend side by side.
 */
@Singleton
class SpendAnalysisRepository @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val salaryMonthOverrideRepository: SalaryMonthOverrideRepository,
    private val currencyConversionService: CurrencyConversionService,
    private val loanRepository: LoanRepository
) {

    suspend fun buildAnalysis(
        window: AnalysisWindow,
        today: LocalDate = LocalDate.now(),
        useCalendarMonths: Boolean = false
    ): SpendAnalysis =
        buildBrief(window, today, includeTransactions = false, useCalendarMonths = useCalendarMonths).analysis

    /**
     * Aggregates for [window] plus, when requested, the per-transaction rows the user
     * chose to attach to the shared file. The provider payload stays aggregate-only.
     *
     * [useCalendarMonths] forces month windows onto calendar-month boundaries instead
     * of the user's pay cycle.
     */
    suspend fun buildBrief(
        window: AnalysisWindow,
        today: LocalDate = LocalDate.now(),
        includeTransactions: Boolean = false,
        useCalendarMonths: Boolean = false
    ): SpendBrief {
        val preferences = userPreferencesRepository.userPreferences.first()
        val isUnified = preferences.unifiedCurrencyMode
        val currency = if (isUnified) preferences.displayCurrency else preferences.baseCurrency

        val currentRange = resolveRange(window, today, useCalendarMonths = useCalendarMonths)
        val previousRange = resolvePreviousRange(window, today, currentRange, useCalendarMonths)

        val current = loadWindow(currentRange, isUnified, currency)
        val previous = loadWindow(previousRange, isUnified, currency)
        val loanDirections = loadLoanDirections()

        val analysis = SpendAnalysisBuilder.build(
            transactions = current.transactions,
            previousTransactions = previous.transactions,
            window = window,
            startDate = currentRange.first,
            endDate = currentRange.second,
            currency = currency,
            categorySplits = current.splitsByTransaction,
            loanDirections = loanDirections
        )
        return SpendBrief(
            analysis = analysis,
            transactions = if (includeTransactions) {
                TransactionBriefMapper.from(current.transactions, loanDirections)
            } else {
                emptyList()
            }
        )
    }

    /** Aggregate brief plus the optional per-transaction detail rows. */
    data class SpendBrief(
        val analysis: SpendAnalysis,
        val transactions: List<TransactionBrief>
    )

    /**
     * Month windows follow the user's pay cycle, exactly like the Analytics
     * "Last N Months" chips; day and year windows stay rolling.
     */
    private suspend fun resolveRange(
        window: AnalysisWindow,
        today: LocalDate,
        endDate: LocalDate = today,
        useCalendarMonths: Boolean = false
    ): Pair<LocalDate, LocalDate> {
        if (window.unit != WindowUnit.MONTHS) {
            return window.startDate(today) to window.endDate(today)
        }
        val useFinancialMonth = !useCalendarMonths &&
            userPreferencesRepository.useFinancialMonth.first()
        return if (useFinancialMonth) {
            DateRangeUtils.rollingPayPeriodRange(
                periods = window.count,
                endDate = endDate,
                startDay = userPreferencesRepository.monthStartDay.first(),
                useFixedEndDay = userPreferencesRepository.useFixedBudgetPeriodEnd.first(),
                fixedEndDayOfMonth = userPreferencesRepository.budgetPeriodEndDay.first(),
                overrides = salaryMonthOverrideRepository.overridesMap.first(),
                adjustment = userPreferencesRepository.salaryDayAdjustment.first()
            )
        } else {
            DateRangeUtils.rollingCalendarMonthRange(window.count.toLong(), endDate)
        }
    }

    private suspend fun resolvePreviousRange(
        window: AnalysisWindow,
        today: LocalDate,
        currentRange: Pair<LocalDate, LocalDate>,
        useCalendarMonths: Boolean = false
    ): Pair<LocalDate, LocalDate> =
        if (window.unit == WindowUnit.MONTHS) {
            resolveRange(window, today, currentRange.first.minusDays(1), useCalendarMonths)
        } else {
            window.startDateOfPrevious(today) to window.endDateOfPrevious(today)
        }

    /**
     * Loads the window the way Analytics does: filtered by the display currency, or
     * converted into it when Unified Currency Mode is on.
     *
     * Rows are read gross — refunds are not netted against their original expense —
     * so the brief can report gross spend, refunds and net spend separately.
     */
    private suspend fun loadWindow(
        range: Pair<LocalDate, LocalDate>,
        isUnified: Boolean,
        currency: String
    ): WindowRows {
        val rows = if (isUnified) {
            transactionRepository.getTransactionsWithSplitsAllCurrenciesIncludingExcluded(
                range.first,
                range.second
            ).first()
        } else {
            transactionRepository.getTransactionsWithSplitsFilteredIncludingExcluded(
                range.first,
                range.second,
                currency
            ).first()
        }
        val normalized = if (isUnified) convertTo(rows, currency) else rows
        return WindowRows(
            transactions = normalized.map { it.transaction },
            splitsByTransaction = normalized
                .filter { it.hasSplits }
                .associate { it.transaction.id to it.getAmountByCategory() }
        )
    }

    /**
     * Direction of every loan, so a loan-linked row can be told apart as either a
     * disbursement (the loan itself) or a payment (the repayment).
     */
    private suspend fun loadLoanDirections(): Map<Long, LoanDirection> =
        loanRepository.getAllLoans().first()
            .associate { loan: LoanEntity -> loan.id to loan.direction }

    private suspend fun convertTo(
        rows: List<TransactionWithSplits>,
        currency: String
    ): List<TransactionWithSplits> = rows.map { row ->
        val transaction = row.transaction
        if (transaction.currency.equals(currency, ignoreCase = true)) {
            row
        } else {
            row.copy(
                transaction = transaction.copy(
                    amount = convert(transaction.amount, transaction.currency, currency),
                    currency = currency
                ),
                splits = row.splits.map { split ->
                    split.copy(amount = convert(split.amount, transaction.currency, currency))
                }
            )
        }
    }

    private suspend fun convert(amount: BigDecimal, from: String, to: String): BigDecimal =
        currencyConversionService.convertAmount(amount, from, to)

    private data class WindowRows(
        val transactions: List<TransactionEntity>,
        val splitsByTransaction: Map<Long, Map<String, BigDecimal>>
    )
}
