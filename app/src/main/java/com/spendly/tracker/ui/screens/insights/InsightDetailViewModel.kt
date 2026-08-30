package com.spendly.tracker.ui.screens.insights

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.data.preferences.UserPreferencesRepository
import com.spendly.tracker.data.repository.TransactionRepository
import com.spendly.tracker.domain.model.InsightType
import com.spendly.tracker.domain.model.SmartInsight
import com.spendly.tracker.domain.usecase.ComputeInsightsUseCase
import com.spendly.tracker.utils.CurrencyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class InsightDetailUiState(
    val isLoading: Boolean = true,
    val insight: SmartInsight? = null,
    val narrative: String = "",
    val howWeKnow: String = "",
    val breakdownTitle: String = "",
    val breakdownItems: List<Pair<String, String>> = emptyList(), // label to value/amount
    val contributingTransactions: List<TransactionEntity> = emptyList(),
    val feedback: String? = null, // "up" / "down" / null
)

@HiltViewModel
class InsightDetailViewModel @Inject constructor(
    private val computeInsightsUseCase: ComputeInsightsUseCase,
    private val transactionRepository: TransactionRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val insightId: String = checkNotNull(savedStateHandle["insightId"])
    private val anchorMonth: YearMonth = YearMonth.parse(checkNotNull(savedStateHandle["anchorMonth"]) as String)

    private val _uiState = MutableStateFlow(InsightDetailUiState())
    val uiState: StateFlow<InsightDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val isLifetime = insightId.startsWith("lifetime_")
            val insights = if (isLifetime) {
                computeInsightsUseCase.computeLifetime()
            } else {
                computeInsightsUseCase.computeForMonth(anchorMonth)
            }
            val insight = insights.firstOrNull { it.id == insightId }
            val feedback = userPreferencesRepository.insightFeedback.first()[insightId]

            if (insight == null) {
                _uiState.value = InsightDetailUiState(isLoading = false, insight = null, feedback = feedback)
                return@launch
            }

            val periodTransactions = fetchPeriodTransactions(insight)
            val contributing = resolveContributingTransactions(insight, periodTransactions)
            val breakdown = insight.metadata["topItems"]
                ?.split("|")
                ?.filter { it.isNotBlank() }
                ?.take(7)
                ?.map { entry ->
                    val parts = entry.split(":")
                    parts[0] to (parts.getOrNull(1) ?: "")
                }
                ?: emptyList()

            _uiState.value = InsightDetailUiState(
                isLoading = false,
                insight = insight,
                narrative = buildNarrative(insight),
                howWeKnow = buildHowWeKnow(insight),
                breakdownTitle = breakdownTitleFor(insight.type),
                breakdownItems = breakdown,
                contributingTransactions = contributing,
                feedback = feedback,
            )
        }
    }

    /** Loads every transaction in the insight's underlying date range, once, for local filtering. */
    private suspend fun fetchPeriodTransactions(insight: SmartInsight): List<TransactionEntity> {
        val startEpoch = insight.metadata["startDate"]?.toLongOrNull()
        val endEpoch = insight.metadata["endDate"]?.toLongOrNull()
        val (start, end) = if (startEpoch != null && endEpoch != null) {
            LocalDate.ofEpochDay(startEpoch) to LocalDate.ofEpochDay(endEpoch)
        } else {
            anchorMonth.atDay(1) to anchorMonth.atEndOfMonth()
        }
        return transactionRepository.getTransactionsBetweenDatesList(
            start.atStartOfDay(),
            end.plusDays(1).atStartOfDay().minusNanos(1)
        )
    }

    /**
     * Picks the transactions that back this insight: explicit txnIds when the generator recorded
     * them (category anomaly), otherwise falls back to filtering the period by category/merchant,
     * and finally to the largest transactions in the period for aggregate insights.
     */
    private fun resolveContributingTransactions(
        insight: SmartInsight,
        periodTransactions: List<TransactionEntity>,
    ): List<TransactionEntity> {
        val txnIds = insight.metadata["txnIds"]
            ?.split(",")
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
        if (!txnIds.isNullOrEmpty()) {
            return periodTransactions.filter { it.id in txnIds }.sortedByDescending { it.amount }
        }

        val category = insight.metadata["category"]
        val merchant = insight.metadata["merchant"]
        val filtered = when {
            category != null -> periodTransactions.filter { it.category == category }
            merchant != null -> periodTransactions.filter { it.merchantName == merchant }
            insight.type in AGGREGATE_TYPES -> periodTransactions.filter {
                it.transactionType in transactionTypesFor(insight.type)
            }
            else -> emptyList()
        }
        return filtered.sortedByDescending { it.amount }.take(15)
    }

    /**
     * Aggregate insights are computed from a specific slice of transaction types (e.g. spend-only
     * totals exclude income/investments). The transaction list shown in the detail view must match
     * that same slice, otherwise income shows up inside a "total spend" breakdown.
     */
    private fun transactionTypesFor(type: InsightType): Set<TransactionType> = when (type) {
        InsightType.INCOME_VS_EXPENSE ->
            setOf(TransactionType.INCOME, TransactionType.EXPENSE, TransactionType.CREDIT)
        InsightType.INVESTMENT_RATIO -> setOf(TransactionType.INVESTMENT)
        else -> setOf(TransactionType.EXPENSE, TransactionType.CREDIT)
    }

    private fun buildNarrative(insight: SmartInsight): String {
        val m = insight.metadata
        val category = m["category"]
        val percent = m["percent"]
        val categoryTotal = m["categoryTotal"]
        val deficit = m["deficit"]?.toIntOrNull()
        val topLabel = m["topItems"]?.split("|")?.firstOrNull()?.substringBefore(":")

        return when (insight.type) {
            InsightType.ANOMALY -> buildString {
                if (category != null && percent != null && categoryTotal != null) {
                    append("Your $category category totalled $categoryTotal this period")
                    if (topLabel != null) append(", driven largely by $topLabel")
                    append(". This represents $percent% of your total expenses — an unusually high concentration in one category.")
                    if (deficit != null && deficit < 0) {
                        append(" Combined with your income this period, this has resulted in a net cashflow deficit of ${CurrencyFormatter.formatCurrency(Math.abs(deficit).toBigDecimal())}. ")
                        append("Review these large payments to see if any can be optimized or staggered.")
                    }
                } else {
                    append("${insight.title}. ${insight.secondaryText}")
                }
            }
            InsightType.TOP_GROWER -> {
                val lastTotal = m["categoryLastTotal"]
                val comparison = if (lastTotal != null) {
                    "compared to ${formatRupeeString(lastTotal)} last period"
                } else {
                    "compared to last period"
                }
                "${insight.secondaryText} $comparison. Rapid category growth like this is worth a closer look — " +
                    "it's often driven by a small number of large or recurring payments rather than everyday spending."
            }
            InsightType.MERCHANT_JUMP -> "${insight.title}: ${insight.secondaryText}. These are the merchants you spent the most with this period, ranked by total amount."
            InsightType.PACE -> "At your current daily spending rate, you're projected to reach ${insight.primaryValue} by the end of this period. " +
                "${insight.secondaryText}. Use this to gauge whether you're on track for the month."
            InsightType.RECURRING_RATIO -> "${insight.secondaryText}. Recurring merchants are places you paid more than once this period — often subscriptions, " +
                "bills, or regular habits. A high ratio here means less of your spend is discretionary."
            InsightType.SAVINGS_WIN -> "${insight.secondaryText}. Spending less than last period in this category is a genuine win — keep an eye on whether it holds next period too."
            InsightType.TOP_CATEGORIES -> "${insight.secondaryText}. These are the categories that made up most of your spend this period."
            InsightType.LARGEST_EXPENSE -> "${insight.secondaryText}. A single transaction this far above your typical spend is worth double-checking — " +
                "make sure it was expected and correctly categorized."
            InsightType.WEEKEND_SPEND -> "${insight.secondaryText}. Comparing weekend and weekday averages highlights whether leisure spending is outpacing routine spending."
            InsightType.INCOME_VS_EXPENSE -> "${insight.secondaryText}. This compares everything you earned against everything you spent this period."
            InsightType.INVESTMENT_RATIO -> "${insight.secondaryText}. This tracks how much of your money moved into investments rather than being spent."
            InsightType.NEW_MERCHANTS -> "${insight.secondaryText}. These are merchants you didn't transact with last period — useful for spotting new habits early."
            InsightType.MERCHANT_LOYALTY -> "${insight.secondaryText}. Frequent visits to the same merchant can be a good target for negotiating discounts or switching to a cheaper alternative."
            InsightType.TRANSACTION_FREQUENCY -> "${insight.secondaryText}. Tracks how often you're transacting, which can reveal impulse spending patterns."
            InsightType.MONTHLY_COMPARISON -> {
                val driverCategory = m["driverCategory"]
                val driverDelta = m["driverDelta"]
                val isIncrease = m["isIncrease"] == "true"
                buildString {
                    append("${insight.secondaryText}. ")
                    if (driverCategory != null && driverDelta != null) {
                        append(
                            "The biggest driver was your $driverCategory spending, which " +
                                "${if (isIncrease) "rose" else "fell"} by ${formatRupeeString(driverDelta)} compared to last period."
                        )
                    } else {
                        append("A direct comparison of this period's total spend against the previous one.")
                    }
                }
            }
            InsightType.ZERO_SPEND_DAYS -> "${insight.secondaryText}. Days with no spending at all — a useful signal of how consistently you're spending versus bursts of activity."
            InsightType.PEAK_SPEND_DAY -> "${insight.secondaryText}. Knowing which day of the week you spend the most can help you plan ahead or set a soft limit."
            InsightType.SPEND_SPLIT -> "${insight.secondaryText}. Shows how your spending splits across the period."
            InsightType.LIFETIME_TOP_MERCHANT -> "${insight.secondaryText}. This is the merchant you've spent the most with across your entire transaction history, not just this period."
            InsightType.LIFETIME_TOTAL_SPEND -> "${insight.secondaryText}. This is everything you've spent since you started tracking with Spendly."
            InsightType.LIFETIME_NO_SPEND_STREAK -> "${insight.secondaryText}. A long streak like this shows real discipline — worth aiming to beat."
            InsightType.LIFETIME_HIGHEST_MONTH -> "${insight.secondaryText}. Comparing any month against this record high gives useful perspective."
            InsightType.LIFETIME_AVG_MONTHLY_SPEND -> "${insight.secondaryText}. Use this as a baseline to judge whether any given month is above or below your norm."
            InsightType.LIFETIME_MERCHANT_LOYALTY -> "${insight.secondaryText}. This merchant relationship spans your entire tracked history — a good candidate for negotiating discounts or loyalty perks."
            InsightType.LIFETIME_MEMBER_SINCE -> "${insight.secondaryText}. That's how long Spendly has been tracking your spending."
        }
    }

    private fun buildHowWeKnow(insight: SmartInsight): String {
        val m = insight.metadata
        val txnCount = m["transactionCount"]
        val countSuffix = if (txnCount != null) " across $txnCount transactions" else ""

        return when (insight.type) {
            InsightType.ANOMALY -> {
                val percent = m["percent"]
                val categoryTotal = m["categoryTotal"]
                val category = m["category"]
                if (category != null && percent != null && categoryTotal != null) {
                    "We grouped this period's spending by category. $category totalled $categoryTotal$countSuffix — " +
                        "that's $percent% of your total spend, well above a typical single-category share. " +
                        "The list below shows the transactions that contributed most."
                } else {
                    "We compared this transaction against your average spend and flagged it as significantly larger than usual."
                }
            }
            InsightType.TOP_GROWER, InsightType.SAVINGS_WIN -> "We compared this period's category totals against last period's and ranked the biggest percentage changes."
            InsightType.MERCHANT_JUMP, InsightType.NEW_MERCHANTS, InsightType.MERCHANT_LOYALTY ->
                "We grouped this period's transactions by merchant and ranked them by total spend / visit count."
            InsightType.PACE -> "We divided your total spend so far by the number of days elapsed, then projected that daily rate across the rest of the period."
            InsightType.RECURRING_RATIO -> "We flagged merchants you paid more than once this period as recurring, then compared that total against your overall spend."
            InsightType.TOP_CATEGORIES -> "We grouped this period's transactions by category and ranked them by total amount."
            InsightType.LARGEST_EXPENSE -> "We compared every transaction this period against your average transaction size."
            InsightType.WEEKEND_SPEND, InsightType.PEAK_SPEND_DAY -> "We split this period's spending by day of week and compared daily averages."
            InsightType.INCOME_VS_EXPENSE -> "We summed all income transactions and all expense transactions recorded in this period."
            InsightType.INVESTMENT_RATIO -> "We summed transactions categorized as investments and compared them against your total transaction volume."
            InsightType.TRANSACTION_FREQUENCY -> "We counted the number of transactions recorded this period$countSuffix and compared it to your usual pace."
            InsightType.MONTHLY_COMPARISON -> {
                val driverCategory = m["driverCategory"]
                if (driverCategory != null) {
                    "We compared this period's total spend against the previous period, then broke both down by category to find $driverCategory as the largest mover."
                } else {
                    "We compared this period's total spend against the previous period."
                }
            }
            InsightType.ZERO_SPEND_DAYS -> "We counted the calendar days in this period with no recorded transactions."
            InsightType.SPEND_SPLIT -> "We split this period in half and compared total spend in each half."
            InsightType.LIFETIME_TOP_MERCHANT, InsightType.LIFETIME_MERCHANT_LOYALTY ->
                "We grouped every transaction in your full history by merchant and ranked them by total spend / visit count."
            InsightType.LIFETIME_TOTAL_SPEND -> "We summed every expense transaction recorded since you started using Spendly."
            InsightType.LIFETIME_NO_SPEND_STREAK -> "We walked every calendar day from your first recorded transaction to today and found the longest run with no spending."
            InsightType.LIFETIME_HIGHEST_MONTH -> "We grouped your entire history by calendar month and found the one with the highest total spend."
            InsightType.LIFETIME_AVG_MONTHLY_SPEND -> "We averaged your total spend across every month you've been tracked."
            InsightType.LIFETIME_MEMBER_SINCE -> "We used the date of your earliest recorded transaction to calculate how long you've been tracking."
        }
    }

    fun submitFeedback(helpful: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setInsightFeedback(insightId, helpful)
            _uiState.value = _uiState.value.copy(feedback = if (helpful) "up" else "down")
        }
    }

    /** Formats a raw integer-string amount (e.g. from `driverDelta` metadata) as ₹ with thousand
     *  separators; falls back to a plain ₹ prefix if the value isn't parseable. */
    private fun formatRupeeString(rawAmount: String): String =
        rawAmount.toLongOrNull()?.let { CurrencyFormatter.formatCurrency(it.toBigDecimal()) } ?: "₹$rawAmount"

    private companion object {
        /** Insight types with no natural category/merchant filter — show top transactions overall. */
        val AGGREGATE_TYPES = setOf(
            InsightType.PACE,
            InsightType.INCOME_VS_EXPENSE,
            InsightType.INVESTMENT_RATIO,
            InsightType.MONTHLY_COMPARISON,
            InsightType.TRANSACTION_FREQUENCY,
            InsightType.SPEND_SPLIT,
            InsightType.LIFETIME_TOTAL_SPEND,
            InsightType.LIFETIME_HIGHEST_MONTH,
            InsightType.LIFETIME_AVG_MONTHLY_SPEND,
            InsightType.LIFETIME_NO_SPEND_STREAK,
            InsightType.LIFETIME_MEMBER_SINCE,
        )
    }
}

private fun breakdownTitleFor(type: InsightType): String = when (type) {
    InsightType.ANOMALY -> "Top merchants in this category"
    InsightType.TOP_GROWER -> "Growing categories"
    InsightType.MERCHANT_JUMP -> "Top merchants"
    InsightType.PACE -> "Spend pattern"
    InsightType.RECURRING_RATIO -> "Recurring merchants"
    InsightType.SAVINGS_WIN -> "Savings breakdown"
    InsightType.TOP_CATEGORIES -> "Top categories"
    InsightType.LARGEST_EXPENSE -> "Expense context"
    InsightType.WEEKEND_SPEND -> "Daily averages"
    InsightType.INCOME_VS_EXPENSE -> "Money flow"
    InsightType.INVESTMENT_RATIO -> "Top investments"
    InsightType.NEW_MERCHANTS -> "New merchants"
    InsightType.MERCHANT_LOYALTY -> "Visit frequency"
    InsightType.TRANSACTION_FREQUENCY -> "Activity"
    InsightType.MONTHLY_COMPARISON -> "Period comparison"
    InsightType.ZERO_SPEND_DAYS -> "Activity"
    InsightType.PEAK_SPEND_DAY -> "Spending by weekday"
    InsightType.SPEND_SPLIT -> "Period split"
    InsightType.LIFETIME_TOP_MERCHANT -> "Top merchants, all-time"
    InsightType.LIFETIME_TOTAL_SPEND -> "Tracking summary"
    InsightType.LIFETIME_NO_SPEND_STREAK -> "Spending history"
    InsightType.LIFETIME_HIGHEST_MONTH -> "Spending history"
    InsightType.LIFETIME_AVG_MONTHLY_SPEND -> "Spending history"
    InsightType.LIFETIME_MERCHANT_LOYALTY -> "Visit frequency, all-time"
    InsightType.LIFETIME_MEMBER_SINCE -> "Tracking summary"
}
