package com.pennywiseai.tracker.presentation.common

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransferKind
import com.pennywiseai.tracker.utils.DateRangeUtils
import java.time.LocalDate
import java.time.YearMonth

private val YEAR_MONTH_NAV_PATTERN = Regex("\\d{4}-\\d{2}")

/**
 * Date range for a budget/analytics month key ([YearMonth] or "YYYY-MM" navigation param).
 * Uses pay-month boundaries when [useFinancialMonth] is true.
 */
/**
 * Date range for month scrubber navigation in Analytics / Transactions.
 *
 * @param useCalendarMonth When true, uses calendar month boundaries (1st through month-end, or today for current month).
 * When false, uses pay-month boundaries via [getDateRangeForYearMonth].
 */
fun getDateRangeForYearMonthNavigation(
    yearMonth: YearMonth,
    useCalendarMonth: Boolean,
    monthStartDay: Int = 1,
    monthStartOverrides: Map<String, Int> = emptyMap(),
    useFixedBudgetPeriodEnd: Boolean = false,
    budgetPeriodEndDay: Int = 31,
): Pair<LocalDate, LocalDate> {
    if (useCalendarMonth) {
        val start = yearMonth.atDay(1)
        val end = if (yearMonth == YearMonth.now()) {
            LocalDate.now()
        } else {
            yearMonth.atEndOfMonth()
        }
        return start to end
    }
    return getDateRangeForYearMonth(
        yearMonth,
        monthStartDay,
        useFinancialMonth = true,
        monthStartOverrides,
        useFixedBudgetPeriodEnd,
        budgetPeriodEndDay,
    )
}

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

/** Bank name used for manual / cash entries when no account is linked. */
const val MANUAL_ENTRY_BANK_NAME = "Manual Entry"

/** Matches Analytics "Expense" filter: debit spend plus card spend, excluding loan repayments. */
fun TransactionEntity.matchesAnalyticsSpendingFilter(): Boolean =
    (transactionType == TransactionType.EXPENSE || transactionType == TransactionType.CREDIT) &&
        loanId == null

enum class PaymentMode(val label: String) {
    CREDIT_CARD("Credit Card"),
    BANK_ACCOUNT("Bank Account"),
    CASH("Cash"),
}

/** Combined credit-card and bank-account spend (excludes cash). */
enum class PaymentModeGroup {
    CARD_AND_BANK,
}

fun TransactionEntity.paymentMode(): PaymentMode? = when {
    loanId != null -> null
    transactionType == TransactionType.CREDIT -> PaymentMode.CREDIT_CARD
    transactionType == TransactionType.EXPENSE ->
        if (bankName == MANUAL_ENTRY_BANK_NAME) PaymentMode.CASH else PaymentMode.BANK_ACCOUNT
    else -> null
}

fun TransactionEntity.matchesPaymentModeGroup(group: PaymentModeGroup): Boolean = when (group) {
    PaymentModeGroup.CARD_AND_BANK ->
        paymentMode() == PaymentMode.CREDIT_CARD || paymentMode() == PaymentMode.BANK_ACCOUNT
}

/** True when this row is a credit card bill payment (bank/card leg), not card spend. */
fun TransactionEntity.isCcBillPayment(): Boolean =
    transactionType == TransactionType.TRANSFER && transferKind == TransferKind.CC_BILL_PAYMENT

/**
 * Counts each linked CC bill pair once (see [com.pennywiseai.tracker.domain.usecase.CreditCardPaymentLinker]).
 */
fun TransactionEntity.countsOnceTowardCcBillPaymentTotal(): Boolean {
    if (!isCcBillPayment() || loanId != null || isExcludedFromTracking) return false
    val otherId = linkedTransactionId ?: return true
    return id < otherId
}

enum class TransactionTypeFilter(val label: String) {
    ALL("All"),
    INCOME("Income"),
    EXPENSE("Spending"),
    CREDIT("Credit Card"),
    TRANSFER("Transfer"),
    /** Card bill payments only (TRANSFER + CC_BILL_PAYMENT). */
    CC_BILL_PAYMENT("CC payment"),
    INVESTMENT("Investment"),
    /** Rows marked excluded from tracking (any type). */
    EXCLUDED("Excluded"),
}

/**
 * Resolves the active analytics/transactions date window for [period] and optional [customRange].
 */
fun resolveDateRangeForSelection(
    period: TimePeriod,
    customRange: Pair<LocalDate, LocalDate>?,
    monthStartDay: Int = 1,
    useFinancialMonth: Boolean = true,
    monthStartOverrides: Map<String, Int> = emptyMap(),
    useFixedBudgetPeriodEnd: Boolean = false,
    budgetPeriodEndDay: Int = 31,
): Pair<LocalDate, LocalDate>? {
    if (period == TimePeriod.CUSTOM) {
        return customRange
    }
    return getDateRangeForPeriod(
        period,
        monthStartDay,
        useFinancialMonth,
        monthStartOverrides,
        useFixedBudgetPeriodEnd,
        budgetPeriodEndDay,
    )
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
            if (useFinancialMonth) {
                // Return the previous pay period (one period before the current budget period)
                val currentPeriodStart = DateRangeUtils.calculateBudgetPeriodRange(
                    today,
                    monthStartDay,
                    useFixedBudgetPeriodEnd,
                    budgetPeriodEndDay,
                    monthStartOverrides
                ).first
                val prevPeriodEnd = currentPeriodStart.minusDays(1)
                DateRangeUtils.calculateBudgetPeriodRange(
                    prevPeriodEnd,
                    monthStartDay,
                    useFixedBudgetPeriodEnd,
                    budgetPeriodEndDay,
                    monthStartOverrides
                )
            } else {
                val lastMonth = YearMonth.now().minusMonths(1)
                lastMonth.atDay(1) to lastMonth.atEndOfMonth()
            }
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
