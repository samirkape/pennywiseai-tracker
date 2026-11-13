package com.spendly.tracker.ui.screens.analytics

import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.presentation.common.matchesAnalyticsSpendingFilter
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Month windows offered by the savings-rate card. */
val SAVINGS_RATE_WINDOW_OPTIONS = listOf(3, 6, 12)
const val SAVINGS_RATE_DEFAULT_WINDOW = 6

/** Upper bound on a date-picked window so the chart stays readable. */
const val SAVINGS_RATE_MAX_RANGE_MONTHS = 60

/** Horizons offered by the forward-looking projection. */
val SAVINGS_PROJECTION_HORIZON_OPTIONS = listOf(3, 6, 12)
const val SAVINGS_PROJECTION_DEFAULT_HORIZON = 3
const val SAVINGS_PROJECTION_MAX_HORIZON = 24

/**
 * A custom window picked by dates. [endMonth] null means "Now", so the window keeps growing
 * to the current month.
 */
data class SavingsCustomRange(
    val startMonth: YearMonth,
    val endMonth: YearMonth? = null,
) {
    fun resolvedEnd(now: YearMonth = YearMonth.now()): YearMonth = (endMonth ?: now).coerceAtMost(now)

    /** Month count covered, clamped to [2, SAVINGS_RATE_MAX_RANGE_MONTHS]. */
    fun windowMonths(now: YearMonth = YearMonth.now()): Int {
        val end = resolvedEnd(now)
        val months = java.time.temporal.ChronoUnit.MONTHS.between(startMonth, end).toInt() + 1
        return months.coerceIn(2, SAVINGS_RATE_MAX_RANGE_MONTHS)
    }
}

/** One bucket (calendar or pay month) of income vs. spending. */
data class MonthlySavingsRate(
    val month: YearMonth,
    val start: LocalDate,
    val end: LocalDate,
    val income: BigDecimal,
    val spent: BigDecimal,
    /** True for the bucket that is still in progress. */
    val isCurrent: Boolean,
) {
    val saved: BigDecimal get() = income - spent

    /** Percent of income saved; null when the month has no income. Negative when overspent. */
    val ratePercent: Float?
        get() = if (income.signum() > 0) {
            saved.divide(income, 4, RoundingMode.HALF_UP).toFloat() * 100f
        } else {
            null
        }
}

data class SavingsRateSummary(
    val months: List<MonthlySavingsRate> = emptyList(),
    val windowMonths: Int = SAVINGS_RATE_DEFAULT_WINDOW,
    val customRange: SavingsCustomRange? = null,
    val currency: String = "INR",
) {
    /** Months that have income, so a rate can be computed. */
    val ratedMonths: List<MonthlySavingsRate> get() = months.filter { it.ratePercent != null }

    /** Window rate = total saved / total income, not a mean of monthly percentages. */
    val averagePercent: Float?
        get() {
            val rated = ratedMonths
            val income = rated.fold(BigDecimal.ZERO) { acc, m -> acc + m.income }
            if (income.signum() <= 0) return null
            val saved = rated.fold(BigDecimal.ZERO) { acc, m -> acc + m.saved }
            return saved.divide(income, 4, RoundingMode.HALF_UP).toFloat() * 100f
        }

    val hasData: Boolean get() = ratedMonths.isNotEmpty()

    /** Date range covered by [months], used to tell whether the page is showing the same window. */
    val range: Pair<LocalDate, LocalDate>?
        get() = if (months.isEmpty()) null else months.minOf { it.start } to months.maxOf { it.end }
}

/**
 * Buckets [transactions] into [ranges] (oldest first) and computes income vs. spending.
 * Spending excludes loan repayments, investments and transfers; excluded rows are ignored.
 * [amountOf] lets the caller convert currencies.
 */
suspend fun computeMonthlySavingsRates(
    transactions: List<TransactionEntity>,
    ranges: List<Pair<YearMonth, Pair<LocalDate, LocalDate>>>,
    currentMonth: YearMonth,
    amountOf: suspend (TransactionEntity) -> BigDecimal,
): List<MonthlySavingsRate> {
    val tracked = transactions.filter { !it.isExcludedFromTracking }
    return ranges.map { (month, range) ->
        val (start, end) = range
        var income = BigDecimal.ZERO
        var spent = BigDecimal.ZERO
        for (tx in tracked) {
            val date = tx.dateTime.toLocalDate()
            if (date.isBefore(start) || date.isAfter(end)) continue
            when {
                tx.transactionType == TransactionType.INCOME && tx.loanId == null -> income += amountOf(tx)
                tx.matchesAnalyticsSpendingFilter() -> spent += amountOf(tx)
            }
        }
        MonthlySavingsRate(
            month = month,
            start = start,
            end = end,
            income = income,
            spent = spent,
            isCurrent = month == currentMonth,
        )
    }
}

/** One estimated month in the forward-looking projection. */
data class ProjectedSavingsMonth(
    val month: YearMonth,
    val income: BigDecimal,
    val spent: BigDecimal,
    val isCurrent: Boolean,
) {
    val saved: BigDecimal get() = income - spent

    /** Percent of income kept; null when the month has no projected income. */
    val ratePercent: Float?
        get() = if (income.signum() > 0) {
            saved.divide(income, 4, RoundingMode.HALF_UP).toFloat() * 100f
        } else {
            null
        }
}

/**
 * Estimate of how much of the next [horizonMonths] months' income will be kept.
 *
 * The current month is paced: actual spend so far plus the historical daily rate for the
 * remaining days. Future months use the average of completed months. The aggregate rate is
 * total projected saved / total projected income, mirroring [SavingsRateSummary.averagePercent].
 */
data class SavingsProjection(
    val months: List<ProjectedSavingsMonth> = emptyList(),
    val horizonMonths: Int = SAVINGS_PROJECTION_DEFAULT_HORIZON,
    val projectedIncome: BigDecimal = BigDecimal.ZERO,
    val projectedSpent: BigDecimal = BigDecimal.ZERO,
    val projectedSaved: BigDecimal = BigDecimal.ZERO,
    val projectedPercent: Float? = null,
    /** Completed months that supplied the baseline; 0 means the estimate leans on this month. */
    val basisMonths: Int = 0,
    val usesCurrentMonthPace: Boolean = false,
) {
    val hasData: Boolean get() = projectedPercent != null

    /** First and last projected month, used for the "Oct – Dec" caption. */
    val range: Pair<YearMonth, YearMonth>?
        get() = if (months.isEmpty()) null else months.first().month to months.last().month
}

/**
 * Projects the next [horizonMonths] months from [summary], including the current in-progress
 * month when the window covers today.
 *
 * Returns null when there is no income to anchor the estimate or too little data to pace the
 * current month. [today] is injectable so the math stays deterministic in tests.
 */
fun computeSavingsProjection(
    summary: SavingsRateSummary,
    horizonMonths: Int,
    today: LocalDate = LocalDate.now(),
): SavingsProjection? {
    if (!summary.hasData) return null

    val horizon = horizonMonths.coerceIn(1, SAVINGS_PROJECTION_MAX_HORIZON)
    val currentBucket = summary.months.firstOrNull {
        !today.isBefore(it.start) && !today.isAfter(it.end)
    }
    val completed = summary.months.filter { it.end.isBefore(today) && it.income.signum() > 0 }

    val baselineIncome = if (completed.isNotEmpty()) {
        completed.fold(BigDecimal.ZERO) { acc, m -> acc + m.income }
            .divide(BigDecimal(completed.size), 4, RoundingMode.HALF_UP)
    } else {
        currentBucket?.income?.takeIf { it.signum() > 0 } ?: return null
    }

    var spendBaseline: BigDecimal? = if (completed.isNotEmpty()) {
        completed.fold(BigDecimal.ZERO) { acc, m -> acc + m.spent }
            .divide(BigDecimal(completed.size), 4, RoundingMode.HALF_UP)
    } else {
        null
    }

    val projected = mutableListOf<ProjectedSavingsMonth>()
    var usesCurrentMonthPace = false

    if (currentBucket != null) {
        val totalDays = ChronoUnit.DAYS.between(currentBucket.start, currentBucket.end).toInt() + 1
        val elapsedDays = (ChronoUnit.DAYS.between(currentBucket.start, today).toInt() + 1)
            .coerceIn(1, totalDays)
        val remainingDays = (totalDays - elapsedDays).coerceAtLeast(0)

        if (spendBaseline == null) {
            // No completed month to learn from: pace this month only once it has some signal.
            if (elapsedDays < 3 || currentBucket.spent.signum() <= 0) return null
            spendBaseline = currentBucket.spent
                .divide(BigDecimal(elapsedDays), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal(totalDays))
        }

        val baselineSpent = spendBaseline!!
        val dailyBaseline = baselineSpent.divide(BigDecimal(totalDays), 4, RoundingMode.HALF_UP)
        val projectedSpend = currentBucket.spent + dailyBaseline.multiply(BigDecimal(remainingDays))
        val projectedIncome = currentBucket.income.takeIf { it.signum() > 0 } ?: baselineIncome

        projected += ProjectedSavingsMonth(
            month = YearMonth.from(today),
            income = projectedIncome.setScale(2, RoundingMode.HALF_UP),
            spent = projectedSpend.setScale(2, RoundingMode.HALF_UP),
            isCurrent = true,
        )
        usesCurrentMonthPace = true
    }

    val firstFutureOffset = if (currentBucket != null) 1 else 0
    val thisMonth = YearMonth.from(today)
    for (offset in firstFutureOffset until horizon) {
        projected += ProjectedSavingsMonth(
            month = thisMonth.plusMonths(offset.toLong()),
            income = baselineIncome.setScale(2, RoundingMode.HALF_UP),
            spent = spendBaseline!!.setScale(2, RoundingMode.HALF_UP),
            isCurrent = false,
        )
    }

    val totalIncome = projected.fold(BigDecimal.ZERO) { acc, m -> acc + m.income }
    val totalSpent = projected.fold(BigDecimal.ZERO) { acc, m -> acc + m.spent }
    val totalSaved = totalIncome - totalSpent
    val percent = if (totalIncome.signum() > 0) {
        totalSaved.divide(totalIncome, 4, RoundingMode.HALF_UP).toFloat() * 100f
    } else {
        null
    }

    return SavingsProjection(
        months = projected,
        horizonMonths = horizon,
        projectedIncome = totalIncome,
        projectedSpent = totalSpent,
        projectedSaved = totalSaved,
        projectedPercent = percent,
        basisMonths = completed.size,
        usesCurrentMonthPace = usesCurrentMonthPace,
    )
}
