package com.pennywiseai.tracker.presentation.common

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.utils.DateRangeUtils
import java.time.LocalDate
import java.time.YearMonth

private val YEAR_MONTH_NAV_PATTERN = Regex("\\d{4}-\\d{2}")

/**
 * Date range for a budget/analytics month key ([YearMonth] or "YYYY-MM" navigation param).
 * Uses pay-month boundaries when [useFinancialMonth] is true.
 */
fun getDateRangeForYearMonth(
    yearMonth: YearMonth,
    monthStartDay: Int = 1,
    useFinancialMonth: Boolean = true,
    monthStartOverrides: Map<String, Int> = emptyMap(),
    useFixedBudgetPeriodEnd: Boolean = false,
    budgetPeriodEndDay: Int = 31,
): Pair<LocalDate, LocalDate> {
    if (!useFinancialMonth) {
        return yearMonth.atDay(1) to yearMonth.atEndOfMonth()
    }
    if (useFixedBudgetPeriodEnd) {
        return DateRangeUtils.customDomPeriodStartingInMonth(
            yearMonth,
            monthStartDay,
            budgetPeriodEndDay,
        )
    }
    return DateRangeUtils.financialMonthRangeFor(
        yearMonth,
        monthStartDay,
        monthStartOverrides,
    )
}

fun parseYearMonthNavPeriod(period: String): YearMonth? {
    if (!period.matches(YEAR_MONTH_NAV_PATTERN)) return null
    return YearMonth.parse(period)
}

/** Default period chip when pay-month mode is on vs off. */
fun defaultTimePeriod(useFinancialMonth: Boolean): TimePeriod =
    if (useFinancialMonth) TimePeriod.THIS_MONTH else TimePeriod.CALENDAR_MONTH

/** Navigation query value for [defaultTimePeriod]. */
fun defaultTimePeriodNavParam(useFinancialMonth: Boolean): String =
    defaultTimePeriod(useFinancialMonth).name

enum class TimePeriod(val label: String) {
    THIS_MONTH("Pay Month"),
    CALENDAR_MONTH("Calendar Month"),
    LAST_MONTH("Last Month"),
    CURRENT_FY("Current FY"),
    ALL("All Time"),
    CUSTOM("Custom Range")
}

enum class TransactionTypeFilter(val label: String) {
    ALL("All"),
    INCOME("Income"),
    EXPENSE("Expense"),
    CREDIT("Credit Card"),
    TRANSFER("Transfer"),
    INVESTMENT("Investment")
}

fun getDateRangeForPeriod(
    period: TimePeriod,
    monthStartDay: Int = 1,
    useFinancialMonth: Boolean = true,
    monthStartOverrides: Map<String, Int> = emptyMap(),
    useFixedBudgetPeriodEnd: Boolean = false,
    budgetPeriodEndDay: Int = 31
): Pair<LocalDate, LocalDate>? {
    val today = LocalDate.now()
    return when (period) {
        TimePeriod.THIS_MONTH -> {
            if (useFinancialMonth) {
                val (start, end) = DateRangeUtils.calculateBudgetPeriodRange(
                    today,
                    monthStartDay,
                    useFixedBudgetPeriodEnd,
                    budgetPeriodEndDay,
                    monthStartOverrides
                )
                start to end
            } else {
                val start = YearMonth.now().atDay(1)
                start to today
            }
        }
        TimePeriod.CALENDAR_MONTH -> {
            val start = YearMonth.now().atDay(1)
            start to today
        }
        TimePeriod.LAST_MONTH -> {
            val lastMonth = YearMonth.now().minusMonths(1)
            val start = lastMonth.atDay(1)
            val end = lastMonth.atEndOfMonth()
            start to end
        }
        TimePeriod.CURRENT_FY -> {
            // Indian Financial Year: April 1 to March 31
            val currentYear = today.year
            val currentMonth = today.monthValue
            val fyStart = if (currentMonth >= 4) {
                LocalDate.of(currentYear, 4, 1)  // Apr 1 of current year
            } else {
                LocalDate.of(currentYear - 1, 4, 1)  // Apr 1 of previous year
            }
            fyStart to today
        }
        TimePeriod.ALL -> {
            // Use a reasonable date range for "All Time" - 10 years back to today
            val start = today.minusYears(10)
            start to today
        }
        TimePeriod.CUSTOM -> {
            // Custom range is handled separately in ViewModel
            null
        }
    }
}

/**
 * Filters transactions by the selected profile.
 *
 * A transaction's effective profile is:
 *   - [TransactionEntity.profileId] if explicitly set
 *   - otherwise inherited from the account it belongs to (looked up via [profileAccountKeys])
 *
 * @param selectedProfileId null means "All profiles" (no filtering)
 * @param profileAccountKeys map of profileId → set of "bankName_accountLast4" keys
 */
fun filterTransactionsByProfile(
    transactions: List<TransactionEntity>,
    selectedProfileId: Long?,
    profileAccountKeys: Map<Long, Set<String>>
): List<TransactionEntity> {
    if (selectedProfileId == null) return transactions
    return transactions.filter { tx ->
        // Explicit override > account inheritance > default Personal
        val effectiveProfileId = tx.profileId ?: run {
            if (tx.bankName != null && tx.accountNumber != null) {
                val key = "${tx.bankName}_${tx.accountNumber}"
                profileAccountKeys.entries.firstOrNull { (_, keys) -> keys.contains(key) }?.key
            } else null
        } ?: ProfileEntity.PERSONAL_ID
        effectiveProfileId == selectedProfileId
    }
}

/**
 * Builds a map of profileId → set of "bankName_accountLast4" keys from account balances.
 */
fun buildProfileAccountKeys(accounts: List<AccountBalanceEntity>): Map<Long, Set<String>> {
    return accounts.groupBy { it.profileId }
        .mapValues { (_, accs) -> accs.map { "${it.bankName}_${it.accountLast4}" }.toSet() }
}

/**
 * Filters account balances by profile.
 *
 * @param selectedProfileId null means "All profiles" (no filtering)
 */
fun filterAccountsByProfile(
    accounts: List<AccountBalanceEntity>,
    hiddenAccounts: Set<String>,
    selectedProfileId: Long?
): List<AccountBalanceEntity> {
    return accounts.filter { account ->
        val key = "${account.bankName}_${account.accountLast4}"
        !hiddenAccounts.contains(key) &&
            (selectedProfileId == null || account.profileId == selectedProfileId)
    }
}
