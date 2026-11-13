package com.spendly.tracker.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal

private val Context.savingsWidgetDataStore by preferencesDataStore(name = "widget_savings_data")

/**
 * Caches the rendered values for each home-screen widget.
 *
 * Every key is scoped to an app widget id so two savings widgets can track different
 * goals without overwriting each other.
 */
object SavingsWidgetDataStore {

    private fun intKey(name: String, appWidgetId: Int) =
        intPreferencesKey("${name}_$appWidgetId")

    private fun floatKey(name: String, appWidgetId: Int) =
        floatPreferencesKey("${name}_$appWidgetId")

    private fun stringKey(name: String, appWidgetId: Int) =
        stringPreferencesKey("${name}_$appWidgetId")

    fun getData(context: Context, appWidgetId: Int): Flow<SavingsWidgetData> =
        context.savingsWidgetDataStore.data.map { prefs ->
            val saved = prefs[stringKey("saved", appWidgetId)]?.toBigDecimalOrNull()
                ?: BigDecimal.ZERO
            val required = prefs[stringKey("required", appWidgetId)]?.toBigDecimalOrNull()
                ?: BigDecimal.ZERO
            SavingsWidgetData(
                targetPercent = prefs[intKey("target_percent", appWidgetId)] ?: 70,
                saved = saved,
                required = required,
                monthlyNeeded = prefs[stringKey("monthly_needed", appWidgetId)]
                    ?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                // Data cached before this field existed falls back to target minus saved.
                remaining = prefs[stringKey("remaining", appWidgetId)]
                    ?.toBigDecimalOrNull()
                    ?: (required - saved).coerceAtLeast(BigDecimal.ZERO),
                progressPercent = prefs[floatKey("progress_percent", appWidgetId)] ?: 0f,
                currentPercent = prefs[stringKey("current_percent", appWidgetId)]
                    ?.toFloatOrNull(),
                endMonth = prefs[stringKey("end_month", appWidgetId)].orEmpty(),
                currency = prefs[stringKey("currency", appWidgetId)] ?: "INR",
                hasIncome = prefs[stringKey("has_income", appWidgetId)]
                    ?.toBooleanStrictOrNull() ?: false,
                goalName = prefs[stringKey("goal_name", appWidgetId)].orEmpty(),
                isFixedGoal = prefs[stringKey("is_fixed_goal", appWidgetId)]
                    ?.toBooleanStrictOrNull() ?: false,
                safeToSpendPerDay = prefs[stringKey("safe_to_spend_per_day", appWidgetId)]
                    ?.toBigDecimalOrNull(),
                daysToDeadline = prefs[stringKey("days_to_deadline", appWidgetId)]
                    ?.toLongOrNull() ?: 0L,
                isComplete = prefs[stringKey("is_complete", appWidgetId)]
                    ?.toBooleanStrictOrNull() ?: false,
            )
        }

    suspend fun update(context: Context, appWidgetId: Int, data: SavingsWidgetData) {
        context.savingsWidgetDataStore.edit { prefs ->
            prefs[intKey("target_percent", appWidgetId)] = data.targetPercent
            prefs[stringKey("saved", appWidgetId)] = data.saved.toPlainString()
            prefs[stringKey("required", appWidgetId)] = data.required.toPlainString()
            prefs[stringKey("monthly_needed", appWidgetId)] = data.monthlyNeeded.toPlainString()
            prefs[stringKey("remaining", appWidgetId)] = data.remaining.toPlainString()
            prefs[floatKey("progress_percent", appWidgetId)] = data.progressPercent
            prefs[stringKey("current_percent", appWidgetId)] =
                data.currentPercent?.toString() ?: ""
            prefs[stringKey("end_month", appWidgetId)] = data.endMonth
            prefs[stringKey("currency", appWidgetId)] = data.currency
            prefs[stringKey("has_income", appWidgetId)] = data.hasIncome.toString()
            prefs[stringKey("goal_name", appWidgetId)] = data.goalName
            prefs[stringKey("is_fixed_goal", appWidgetId)] = data.isFixedGoal.toString()
            prefs[stringKey("safe_to_spend_per_day", appWidgetId)] =
                data.safeToSpendPerDay?.toPlainString() ?: ""
            prefs[stringKey("days_to_deadline", appWidgetId)] = data.daysToDeadline.toString()
            prefs[stringKey("is_complete", appWidgetId)] = data.isComplete.toString()
        }
    }
}
