package com.pennywiseai.tracker.domain.service

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.utils.DateRangeUtils
import java.time.LocalDate
import java.time.YearMonth

/**
 * Finds a conservative pay-period start suggestion from likely salary credits.
 * Suggest-only: callers apply overrides after explicit user confirmation.
 */
object SalaryPayPeriodDetector {

    private val SALARY_TEXT_PATTERN = Regex(
        "(?i)(salary|stipend|wages|payroll|pay\\s*roll)",
    )

    /** Minimum [scoreCandidate] to treat a credit as salary-like. */
    const val MIN_SCORE = 30

    data class Suggestion(
        val yearMonth: YearMonth,
        val suggestedDay: Int,
        val salaryDate: LocalDate,
        val transactionId: Long,
    ) {
        val dismissToken: String = "${yearMonth}:${suggestedDay}"
    }

    /**
     * @param transactions Credits in the salary calendar month (typically month-to-date).
     * @param useFixedBudgetPeriodEnd When true, per-month overrides are ignored for ranges — no suggestion.
     */
    fun findSuggestion(
        transactions: List<TransactionEntity>,
        today: LocalDate = LocalDate.now(),
        useFinancialMonth: Boolean,
        useFixedBudgetPeriodEnd: Boolean,
        defaultStartDay: Int,
        overrides: Map<String, Int>,
        dismissedTokens: Set<String>,
    ): Suggestion? {
        if (!useFinancialMonth || useFixedBudgetPeriodEnd) return null

        val calendarMonth = YearMonth.from(today)
        val yearMonthKey = calendarMonth.toString()
        if (overrides.containsKey(yearMonthKey)) return null

        val monthStart = calendarMonth.atDay(1)
        val best = transactions
            .asSequence()
            .filter { txn ->
                val date = txn.dateTime.toLocalDate()
                !date.isBefore(monthStart) && !date.isAfter(today)
            }
            .mapNotNull { txn ->
                val score = scoreCandidate(txn)
                if (score < MIN_SCORE) null else txn to score
            }
            .maxWithOrNull(
                compareBy<Pair<TransactionEntity, Int>> { it.second }
                    .thenBy { it.first.amount },
            )
            ?.first ?: return null

        val salaryDate = best.dateTime.toLocalDate()
        val suggestedDay = salaryDate.dayOfMonth
        val effectiveDefaultDay = DateRangeUtils.resolveStartDay(calendarMonth, defaultStartDay)
        if (suggestedDay == effectiveDefaultDay) return null

        val token = "${yearMonthKey}:$suggestedDay"
        if (dismissedTokens.contains(token)) return null

        return Suggestion(
            yearMonth = calendarMonth,
            suggestedDay = suggestedDay,
            salaryDate = salaryDate,
            transactionId = best.id,
        )
    }

    internal fun scoreCandidate(transaction: TransactionEntity): Int {
        if (transaction.isDeleted || transaction.isExcludedFromTracking) return 0
        if (transaction.transactionType != TransactionType.INCOME) return 0

        var score = 0
        if (transaction.category.equals("Salary", ignoreCase = true)) {
            score += 40
        }

        val haystack = buildString {
            transaction.smsBody?.let { append(it); append(' ') }
            append(transaction.merchantName)
            transaction.description?.let { append(' '); append(it) }
        }

        if (SALARY_TEXT_PATTERN.containsMatchIn(haystack)) {
            score += 30
        } else if (transaction.merchantName.contains("salary", ignoreCase = true)) {
            score += 20
        }

        return score
    }
}
