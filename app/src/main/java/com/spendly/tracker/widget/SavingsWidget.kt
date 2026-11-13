package com.spendly.tracker.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.spendly.tracker.MainActivity
import com.spendly.tracker.R
import com.spendly.tracker.utils.CurrencyFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class SavingsWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = try {
            withTimeoutOrNull(5000L) {
                val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
                SavingsWidgetDataStore.getData(context, appWidgetId).first()
            } ?: SavingsWidgetData()
        } catch (e: Exception) {
            android.util.Log.e("SavingsWidget", "Failed to load widget data", e)
            SavingsWidgetData()
        }

        provideContent {
            GlanceTheme(
                colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    GlanceTheme.colors
                else
                    SpendlyWidgetTheme.colors
            ) {
                SavingsWidgetContent(data)
            }
        }
    }

    @Composable
    private fun SavingsWidgetContent(data: SavingsWidgetData) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(16.dp)
                .background(GlanceTheme.colors.widgetBackground)
                .clickable(actionRunCallback<OpenSavingsAction>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!data.hasIncome) {
                EmptySavingsContent(data)
            } else {
                SavingsOverviewContent(data)
            }
        }
    }

    @Composable
    private fun EmptySavingsContent(data: SavingsWidgetData) {
        val context = LocalContext.current
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = data.goalName.ifBlank { context.getString(R.string.savings_widget_title) },
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = data.targetLabel(context),
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            Text(
                text = context.getString(R.string.savings_widget_no_income),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp,
                ),
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = context.getString(R.string.savings_widget_setup),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp,
                ),
            )
        }
    }

    @Composable
    private fun SavingsOverviewContent(data: SavingsWidgetData) {
        val context = LocalContext.current
        val size = LocalSize.current
        val isSmall = size.width < 280.dp
        val isTall = size.height >= 140.dp
        val statusColor = if (data.isComplete || (data.currentPercent ?: 0f) >= 0f) {
            GlanceTheme.colors.primary
        } else {
            GlanceTheme.colors.error
        }

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = data.goalName.ifBlank { context.getString(R.string.savings_widget_title) },
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = data.targetLabel(context),
                style = TextStyle(
                    color = statusColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }

        Spacer(modifier = GlanceModifier.height(10.dp))

        Text(
            text = CurrencyFormatter.formatCurrency(data.saved, data.currency),
            style = TextStyle(
                color = statusColor,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            ),
        )

        Spacer(modifier = GlanceModifier.height(8.dp))

        val progressWidth = ((size.width - 32.dp) * (data.progressPercent / 100f))
            .coerceAtLeast(4.dp)
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(8.dp)
                .cornerRadius(4.dp)
                .background(GlanceTheme.colors.surfaceVariant),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (data.progressPercent > 0f) {
                Spacer(
                    modifier = GlanceModifier
                        .width(progressWidth)
                        .height(8.dp)
                        .cornerRadius(4.dp)
                        .background(statusColor),
                )
            }
        }

        if (isSmall) {
            Spacer(modifier = GlanceModifier.height(6.dp))
            Text(
                text = data.remainingLabel(context),
                style = TextStyle(
                    color = statusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }

        if (!isSmall) {
            Spacer(modifier = GlanceModifier.height(8.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = data.remainingLabel(context),
                    style = TextStyle(
                        color = statusColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = context.getString(
                        R.string.savings_widget_per_month,
                        CurrencyFormatter.formatCurrencyWhole(data.monthlyNeeded, data.currency),
                    ),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }

            val deadline = formatWidgetMonth(data.endMonth)
            if (deadline != null) {
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = if (data.isFixedGoal) {
                        context.getString(R.string.savings_widget_deadline, deadline)
                    } else {
                        context.getString(
                            R.string.savings_widget_deadline_target,
                            deadline,
                            CurrencyFormatter.formatCurrencyWhole(data.required, data.currency),
                        )
                    },
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp,
                    ),
                )
            }
        }

        if (isTall && !data.isComplete) {
            Spacer(modifier = GlanceModifier.height(6.dp))
            Text(
                text = data.statusLine(context),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                ),
            )
        }
    }

    private fun SavingsWidgetData.targetLabel(context: Context): String =
        if (isFixedGoal) {
            context.getString(
                R.string.savings_widget_amount_target,
                CurrencyFormatter.formatCurrencyWhole(required, currency),
            )
        } else {
            context.getString(R.string.savings_widget_target, targetPercent)
        }

    private fun SavingsWidgetData.remainingLabel(context: Context): String =
        if (remaining.signum() <= 0) {
            context.getString(R.string.savings_widget_complete)
        } else {
            context.getString(
                R.string.savings_widget_remaining,
                CurrencyFormatter.formatCurrencyWhole(remaining, currency),
            )
        }

    private fun SavingsWidgetData.statusLine(context: Context): String = when {
        isComplete -> context.getString(R.string.savings_widget_complete)
        safeToSpendPerDay != null -> context.getString(
            R.string.savings_widget_safe_to_spend,
            CurrencyFormatter.formatCurrencyWhole(safeToSpendPerDay, currency),
        )
        else -> {
            val days = daysToDeadline.coerceAtLeast(0L).toInt()
            context.resources.getQuantityString(
                R.plurals.savings_widget_days_left,
                days,
                days,
            )
        }
    }

    private fun formatWidgetMonth(raw: String): String? =
        runCatching { YearMonth.parse(raw) }.getOrNull()?.atEndOfMonth()
            ?.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))
}

/** Opens the Savings page rather than the home tab when the widget is tapped. */
class OpenSavingsAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_SAVINGS, true)
        }
        context.startActivity(intent)
    }
}
