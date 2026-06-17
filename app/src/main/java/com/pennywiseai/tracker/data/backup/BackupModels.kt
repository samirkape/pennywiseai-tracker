package com.pennywiseai.tracker.data.backup

import com.google.gson.annotations.SerializedName
import com.pennywiseai.tracker.data.database.entity.*
import java.time.LocalDateTime

/**
 * Root container for Spendly backup data
 */
data class PennyWiseBackup(
    @SerializedName("_format")
    val format: String = "Spendly Backup v1.0",
    
    @SerializedName("_warning")
    val warning: String = "Contains sensitive financial data. Keep this file secure.",
    
    @SerializedName("_created")
    val created: String = LocalDateTime.now().toString(),
    
    @SerializedName("metadata")
    val metadata: BackupMetadata,
    
    @SerializedName("database")
    val database: DatabaseSnapshot,
    
    @SerializedName("preferences")
    val preferences: PreferencesSnapshot
)

/**
 * Metadata about the backup
 */
data class BackupMetadata(
    @SerializedName("export_id")
    val exportId: String,
    
    @SerializedName("app_version")
    val appVersion: String,
    
    @SerializedName("database_version")
    val databaseVersion: Int,
    
    @SerializedName("device")
    val device: String,
    
    @SerializedName("android_version")
    val androidVersion: Int,
    
    @SerializedName("statistics")
    val statistics: BackupStatistics
)

/**
 * Statistics about the backup content
 */
data class BackupStatistics(
    @SerializedName("total_transactions")
    val totalTransactions: Int,
    
    @SerializedName("total_categories")
    val totalCategories: Int,
    
    @SerializedName("total_cards")
    val totalCards: Int,
    
    @SerializedName("total_subscriptions")
    val totalSubscriptions: Int,
    
    @SerializedName("total_rules")
    val totalRules: Int = 0,
    
    @SerializedName("total_rule_applications")
    val totalRuleApplications: Int = 0,
    
    @SerializedName("total_exchange_rates")
    val totalExchangeRates: Int = 0,
    
    @SerializedName("total_budgets")
    val totalBudgets: Int = 0,
    
    @SerializedName("total_budget_categories")
    val totalBudgetCategories: Int = 0,
    
    @SerializedName("total_transaction_splits")
    val totalTransactionSplits: Int = 0,
    
    @SerializedName("total_bank_notifications")
    val totalBankNotifications: Int = 0,
    
    @SerializedName("total_receipts")
    val totalReceipts: Int = 0,

    @SerializedName("total_loans")
    val totalLoans: Int = 0,

    @SerializedName("total_transaction_groups")
    val totalTransactionGroups: Int = 0,

    @SerializedName("total_profiles")
    val totalProfiles: Int = 0,

    @SerializedName("date_range")
    val dateRange: DateRange?
)

/**
 * Date range of transactions
 */
data class DateRange(
    @SerializedName("earliest")
    val earliest: String?,
    
    @SerializedName("latest")
    val latest: String?
)

/**
 * Complete database snapshot
 */
data class DatabaseSnapshot(
    @SerializedName("transactions")
    val transactions: List<TransactionEntity>,
    
    @SerializedName("categories")
    val categories: List<CategoryEntity>,
    
    @SerializedName("cards")
    val cards: List<CardEntity>,
    
    @SerializedName("account_balances")
    val accountBalances: List<AccountBalanceEntity>,
    
    @SerializedName("subscriptions")
    val subscriptions: List<SubscriptionEntity>,
    
    @SerializedName("merchant_mappings")
    val merchantMappings: List<MerchantMappingEntity>,

    @SerializedName("merchant_aliases")
    val merchantAliases: List<MerchantAliasEntity> = emptyList(),
    
    @SerializedName("unrecognized_sms")
    val unrecognizedSms: List<UnrecognizedSmsEntity>,
    
    @SerializedName("chat_messages")
    val chatMessages: List<ChatMessage>,
    
    @SerializedName("rules")
    val rules: List<RuleEntity> = emptyList(),
    
    @SerializedName("rule_applications")
    val ruleApplications: List<RuleApplicationEntity> = emptyList(),
    
    @SerializedName("exchange_rates")
    val exchangeRates: List<ExchangeRateEntity> = emptyList(),
    
    @SerializedName("budgets")
    val budgets: List<BudgetEntity> = emptyList(),
    
    @SerializedName("budget_categories")
    val budgetCategories: List<BudgetCategoryEntity> = emptyList(),
    
    @SerializedName("transaction_splits")
    val transactionSplits: List<TransactionSplitEntity> = emptyList(),
    
    @SerializedName("bank_notifications")
    val bankNotifications: List<BankNotificationEntity> = emptyList(),

    @SerializedName("salary_month_overrides")
    val salaryMonthOverrides: List<SalaryMonthOverrideEntity> = emptyList(),
    
    @SerializedName("transaction_receipts")
    val transactionReceipts: List<TransactionReceiptEntity> = emptyList(),

    @SerializedName("loans")
    val loans: List<LoanEntity> = emptyList(),

    @SerializedName("transaction_groups")
    val transactionGroups: List<TransactionGroupEntity> = emptyList(),

    @SerializedName("profiles")
    val profiles: List<ProfileEntity> = emptyList()
)

/**
 * User preferences snapshot
 */
data class PreferencesSnapshot(
    @SerializedName("theme")
    val theme: ThemePreferences,
    
    @SerializedName("sms")
    val sms: SmsPreferences,
    
    @SerializedName("developer")
    val developer: DeveloperPreferences,
    
    @SerializedName("app")
    val app: AppPreferences,

    @SerializedName("security")
    val security: SecurityPreferences = SecurityPreferences()
)

/**
 * Theme-related preferences
 */
data class ThemePreferences(
    @SerializedName("is_dark_theme_enabled")
    val isDarkThemeEnabled: Boolean?,
    
    @SerializedName("is_dynamic_color_enabled")
    val isDynamicColorEnabled: Boolean,

    @SerializedName("theme_style")
    val themeStyle: String? = null,

    @SerializedName("accent_color")
    val accentColor: String? = null,

    @SerializedName("is_amoled_mode")
    val isAmoledMode: Boolean = false,

    @SerializedName("app_font")
    val appFont: String? = null,

    @SerializedName("blur_effects_enabled")
    val blurEffectsEnabled: Boolean = true,

    @SerializedName("nav_bar_style")
    val navBarStyle: String? = null,

    @SerializedName("analytics_chart_type")
    val analyticsChartType: String? = null,

    @SerializedName("compact_analytics_cards")
    val compactAnalyticsCards: Boolean = true,

    @SerializedName("cover_style")
    val coverStyle: String? = null
)

/**
 * SMS-related preferences
 */
data class SmsPreferences(
    @SerializedName("has_skipped_sms_permission")
    val hasSkippedSmsPermission: Boolean,
    
    @SerializedName("sms_scan_months")
    val smsScanMonths: Int,

    @SerializedName("sms_scan_all_time")
    val smsScanAllTime: Boolean = true,

    @SerializedName("last_scan_timestamp")
    val lastScanTimestamp: Long?,
    
    @SerializedName("last_scan_period")
    val lastScanPeriod: Int?
)

/**
 * Developer mode preferences
 */
data class DeveloperPreferences(
    @SerializedName("is_developer_mode_enabled")
    val isDeveloperModeEnabled: Boolean,
    
    @SerializedName("system_prompt")
    val systemPrompt: String?
)

/**
 * App-related preferences
 */
data class AppPreferences(
    @SerializedName("has_shown_scan_tutorial")
    val hasShownScanTutorial: Boolean,
    
    @SerializedName("first_launch_time")
    val firstLaunchTime: Long?,
    
    @SerializedName("has_shown_review_prompt")
    val hasShownReviewPrompt: Boolean,
    
    @SerializedName("last_review_prompt_time")
    val lastReviewPromptTime: Long?,

    @SerializedName("base_currency")
    val baseCurrency: String? = null,

    @SerializedName("unified_currency_mode")
    val unifiedCurrencyMode: Boolean = false,

    @SerializedName("display_currency")
    val displayCurrency: String? = null,

    @SerializedName("monthly_budget_limit")
    val monthlyBudgetLimit: String? = null,

    @SerializedName("balance_hidden")
    val balanceHidden: Boolean = false,

    @SerializedName("user_name")
    val userName: String? = null,

    @SerializedName("profile_image_uri")
    val profileImageUri: String? = null,

    @SerializedName("profile_background_color")
    val profileBackgroundColor: Int = 0,

    @SerializedName("has_completed_onboarding")
    val hasCompletedOnboarding: Boolean = false,

    @SerializedName("main_account_key")
    val mainAccountKey: String? = null,

    @SerializedName("month_start_day")
    val monthStartDay: Int = 1,

    @SerializedName("use_financial_month")
    val useFinancialMonth: Boolean = true,

    @SerializedName("use_fixed_budget_period_end")
    val useFixedBudgetPeriodEnd: Boolean = false,

    @SerializedName("budget_period_end_day")
    val budgetPeriodEndDay: Int = 31,

    @SerializedName("dismissed_salary_suggestions")
    val dismissedSalarySuggestions: String? = null,

    @SerializedName("selected_profile_id")
    val selectedProfileId: Long? = null,

    @SerializedName("insights_data_window_months")
    val insightsDataWindowMonths: Int = 3
)

/**
 * Security-related preferences
 */
data class SecurityPreferences(
    @SerializedName("app_lock_enabled")
    val appLockEnabled: Boolean = false,

    @SerializedName("app_lock_timeout_minutes")
    val appLockTimeoutMinutes: Int = 1
)

/**
 * Import result
 */
sealed class ImportResult {
    data class Success(
        val importedTransactions: Int,
        val importedCategories: Int,
        val skippedDuplicates: Int,
        val latestTransactionTimestamp: Long? = null
    ) : ImportResult()

    data class Error(val message: String) : ImportResult()
}

/**
 * Export result
 */
sealed class ExportResult {
    data class Success(val file: java.io.File) : ExportResult()
    data class Error(val message: String) : ExportResult()
    data class Progress(val current: Int, val total: Int) : ExportResult()
}

/**
 * Import strategy options
 */
enum class ImportStrategy {
    REPLACE_ALL,    // Replace all existing data
    MERGE,          // Merge with existing data (skip duplicates)
    SELECTIVE       // User selects what to import
}

/**
 * Selective restore options — controls which data categories are restored
 * from a backup file. All flags default to `true` (restore everything).
 */
data class RestoreOptions(
    val transactions: Boolean = true,
    val categories: Boolean = true,
    val cards: Boolean = true,
    val subscriptions: Boolean = true,
    val budgets: Boolean = true,
    val rules: Boolean = true,
    val loans: Boolean = true,
    val transactionGroups: Boolean = true,
    val merchantMappings: Boolean = true,
    val exchangeRates: Boolean = true,
    val chatMessages: Boolean = true,
    val bankNotifications: Boolean = true,
    val unrecognizedSms: Boolean = true,
    val salaryOverrides: Boolean = true,
    val profiles: Boolean = true,
    val preferences: Boolean = true,
) {
    /** Returns `true` when every category is selected — the fast path. */
    val isAllSelected: Boolean
        get() = transactions && categories && cards && subscriptions && budgets &&
            rules && loans && transactionGroups && merchantMappings && exchangeRates &&
            chatMessages && bankNotifications && unrecognizedSms && salaryOverrides &&
            profiles && preferences
}

/**
 * Privacy level for export
 */
enum class ExportPrivacy {
    FULL,          // Export everything as-is
    MASKED,        // Mask sensitive data like account numbers
    ANONYMOUS      // Remove merchant names and descriptions
}