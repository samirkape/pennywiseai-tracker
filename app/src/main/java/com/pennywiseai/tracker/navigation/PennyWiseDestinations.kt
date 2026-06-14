package com.pennywiseai.tracker.navigation

import kotlinx.serialization.Serializable

// Define navigation destinations using Kotlin Serialization
@Serializable
object AppLock

@Serializable
object OnBoarding

@Serializable
object Permission

@Serializable
data class Home(
    val category: String? = null,
    val merchant: String? = null,
    val period: String? = null,
    val currency: String? = null,
    val transactionType: String? = null,
    val startDateEpochDay: Long? = null,
    val endDateEpochDay: Long? = null,
    val paymentMode: String? = null,
    val bankName: String? = null,
    val accountLast4: String? = null,
)

@Serializable
object Transactions

@Serializable
object Settings

@Serializable
object Categories

@Serializable
object Analytics

@Serializable
object Insights

@Serializable
object QuickCategorize

@Serializable
object BehavioralStats

@Serializable
data class TransactionDetail(val transactionId: Long)

@Serializable
data class AddTransaction(
    val unrecognizedSmsId: Long = -1L,
)

@Serializable
data class AccountDetail(val bankName: String, val accountLast4: String)

@Serializable
object UnrecognizedSms

@Serializable
object MerchantAliases

@Serializable
object Faq

@Serializable
object Rules

@Serializable
data class CreateRule(val ruleId: String? = null)

@Serializable
object QuickKeywordRules

@Serializable
data class EditQuickKeywordRule(
    val ruleId: String? = null,
    val prefilledKeywords: String? = null,
    val prefilledName: String? = null,
)

@Serializable
object ExchangeRates

@Serializable
object BudgetGroups

@Serializable
object Subscriptions

@Serializable
data class BudgetGroupEdit(val groupId: Long = -1L)

@Serializable
object Loans

@Serializable
data class LoanDetail(val loanId: Long)

@Serializable
object TransactionGroups

@Serializable
data class TransactionGroupDetail(val groupId: Long)

@Serializable
object ImportStatement

@Serializable
object PayPeriodSettings

@Serializable
data class TransactionsWithFilter(
    val category: String,
    val period: String? = null,
    val currency: String? = null
)

@Serializable
data class TransactionsByMerchant(val merchant: String)

@Serializable
data class TransactionsByCategories(
    val categories: String,
    val period: String? = null,
    val currency: String? = null,
    val startDateEpochDay: Long? = null,
    val endDateEpochDay: Long? = null,
)
