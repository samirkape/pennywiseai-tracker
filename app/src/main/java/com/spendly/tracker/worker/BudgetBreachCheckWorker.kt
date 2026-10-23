package com.spendly.tracker.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.spendly.tracker.MainActivity
import com.spendly.tracker.R
import com.spendly.tracker.core.Constants
import com.spendly.tracker.data.currency.CurrencyConversionService
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.data.preferences.UserPreferencesRepository
import com.spendly.tracker.data.repository.BudgetGroupRepository
import com.spendly.tracker.data.repository.SalaryMonthOverrideRepository
import com.spendly.tracker.utils.CurrencyFormatter
import com.spendly.tracker.utils.DateRangeUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/**
 * Evaluates budget buckets for the current period after a new transaction is saved and
 * notifies the user once per bucket whenever it crosses from within-budget to over-budget.
 *
 * Triggered as a debounced one-shot after transaction ingestion (SMS or manual entry) so
 * the ingest path stays fast and bursts of transactions collapse into a single evaluation.
 * A bucket re-alerts only after it has recovered and breached again within the period.
 */
@HiltWorker
class BudgetBreachCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val budgetGroupRepository: BudgetGroupRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val currencyConversionService: CurrencyConversionService,
    private val salaryMonthOverrideRepository: SalaryMonthOverrideRepository
) : CoroutineWorker(appContext, workerParams) {

    private data class BucketUsage(
        val id: Long,
        val name: String,
        val budget: BigDecimal,
        val actual: BigDecimal
    ) {
        val isBreached: Boolean
            get() = budget > BigDecimal.ZERO && actual > budget
    }

    companion object {
        private const val WORK_NAME = "budget_breach_check"
        private const val CHANNEL_ID = "budget_alerts_channel"
        private const val CHANNEL_NAME = "Budget Alerts"
        private const val PREFS_NAME = "budget_breach_alerts"
        private const val MAX_LISTED_BREACHES = 3
        private const val NOTIFICATION_ID = 76123

        fun enqueueOneShot(context: Context) {
            if (!Constants.Features.BUDGET_BREACH_ALERTS_ENABLED) return
            val request = OneTimeWorkRequestBuilder<BudgetBreachCheckWorker>().build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val today = LocalDate.now()
            val useFinancialMonth = userPreferencesRepository.useFinancialMonth.first()
            val monthStartDay = userPreferencesRepository.monthStartDay.first()
            val useFixedEnd = userPreferencesRepository.useFixedBudgetPeriodEnd.first()
            val budgetPeriodEndDay = userPreferencesRepository.budgetPeriodEndDay.first()
            val overrides = salaryMonthOverrideRepository.overridesMap.first()
            val isUnifiedMode = userPreferencesRepository.unifiedCurrencyMode.first()
            val baseCurrency = userPreferencesRepository.baseCurrency.first()
            val currency = if (isUnifiedMode) {
                userPreferencesRepository.displayCurrency.first()
            } else {
                baseCurrency
            }

            val (periodStart, periodEnd) = if (useFinancialMonth) {
                DateRangeUtils.calculateBudgetPeriodRange(
                    today,
                    monthStartDay,
                    useFixedEnd,
                    budgetPeriodEndDay,
                    overrides
                )
            } else {
                val yearMonth = YearMonth.from(today)
                yearMonth.atDay(1) to yearMonth.atEndOfMonth()
            }

            val buckets = if (isUnifiedMode) {
                loadUnifiedBucketUsage(periodStart, periodEnd, baseCurrency, currency)
            } else {
                budgetGroupRepository
                    .getGroupSpending(periodStart, periodEnd, currency)
                    .first()
                    .groups
                    .map { group ->
                        BucketUsage(
                            id = group.group.budget.id,
                            name = group.group.budget.name,
                            budget = group.totalBudget,
                            actual = group.totalActual
                        )
                    }
            }

            evaluateBreaches(buckets, periodStart, currency)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    /**
     * Mirrors the unified-currency conversion used by the budget widget: bucket budgets
     * are stored in the base currency while per-category spend is summed from every
     * currency and converted to the display currency before comparison.
     */
    private suspend fun loadUnifiedBucketUsage(
        periodStart: LocalDate,
        periodEnd: LocalDate,
        baseCurrency: String,
        displayCurrency: String
    ): List<BucketUsage> {
        val raw = budgetGroupRepository
            .getGroupSpendingAllCurrencies(periodStart, periodEnd)
            .first()

        val categoryAmounts = mutableMapOf<String, BigDecimal>()
        raw.allTransactions.forEach { txWithSplits ->
            if (txWithSplits.transaction.transactionType != TransactionType.INCOME) {
                txWithSplits.getAmountByCategory().forEach { (category, amount) ->
                    val converted = currencyConversionService.convertAmount(
                        amount, txWithSplits.transaction.currency, displayCurrency
                    )
                    val categoryName = category.ifEmpty { "Others" }
                    categoryAmounts[categoryName] =
                        (categoryAmounts[categoryName] ?: BigDecimal.ZERO) + converted
                }
            }
        }

        return raw.budgetsWithCategories.map { group ->
            var totalBudget = BigDecimal.ZERO
            var totalActual = BigDecimal.ZERO
            group.categories.forEach { category ->
                totalBudget += currencyConversionService.convertAmount(
                    category.budgetAmount, baseCurrency, displayCurrency
                )
                totalActual += categoryAmounts[category.categoryName] ?: BigDecimal.ZERO
            }
            BucketUsage(
                id = group.budget.id,
                name = group.budget.name,
                budget = totalBudget,
                actual = totalActual
            )
        }
    }

    /**
     * Compares current bucket usage against the set of buckets already alerted for this
     * period. Newly breached buckets trigger one notification; buckets that recovered are
     * forgotten so a later re-breach within the same period alerts again.
     */
    private fun evaluateBreaches(
        buckets: List<BucketUsage>,
        periodStart: LocalDate,
        currency: String
    ) {
        val breached = buckets.filter { it.isBreached }

        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val periodKey = "notified_${periodStart.toEpochDay()}"
        val alreadyNotified = prefs.getStringSet(periodKey, emptySet())?.toSet() ?: emptySet()

        val breachedIds = breached.map { it.id.toString() }.toSet()
        val stillNotified = alreadyNotified.intersect(breachedIds)
        val newBreaches = breached.filter { it.id.toString() !in stillNotified }

        val updatedNotified = stillNotified + newBreaches.map { it.id.toString() }
        if (updatedNotified != alreadyNotified) {
            prefs.edit().putStringSet(periodKey, updatedNotified).apply()
        }

        if (newBreaches.isNotEmpty()) {
            showNotification(newBreaches, currency)
        }
    }

    private fun showNotification(breaches: List<BucketUsage>, currency: String) {
        val context = applicationContext

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alerts when a budget bucket goes over its limit"
        }
        notificationManager.createNotificationChannel(channel)

        val title = if (breaches.size == 1) {
            "${breaches.first().name} budget breached"
        } else {
            "${breaches.size} budgets breached"
        }
        val text = if (breaches.size == 1) {
            val breach = breaches.first()
            "You've spent ${CurrencyFormatter.formatCurrency(breach.actual, currency)} of your ${CurrencyFormatter.formatCurrency(breach.budget, currency)} budget."
        } else {
            val names = breaches.take(MAX_LISTED_BREACHES).joinToString(", ") { it.name }
            val more = if (breaches.size > MAX_LISTED_BREACHES) {
                " and ${breaches.size - MAX_LISTED_BREACHES} more"
            } else {
                ""
            }
            "$names$more are over budget."
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_BUDGETS, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            WORK_NAME.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
