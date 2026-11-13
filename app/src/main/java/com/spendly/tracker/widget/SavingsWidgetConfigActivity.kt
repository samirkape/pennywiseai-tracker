package com.spendly.tracker.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spendly.tracker.R
import com.spendly.tracker.data.preferences.UserPreferencesRepository
import com.spendly.tracker.ui.screens.savings.SavingsGoalDefinition
import com.spendly.tracker.ui.screens.savings.SavingsGoalKind
import com.spendly.tracker.ui.screens.savings.SavingsGoals
import com.spendly.tracker.ui.theme.SpendlyTheme
import com.spendly.tracker.utils.CurrencyFormatter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * Launcher entry point for choosing which savings goal a widget should track.
 *
 * Reached when a widget is first added and again when the user reconfigures it, so the
 * saved choice is keyed by the widget id rather than by the app's active goal.
 */
@AndroidEntryPoint
class SavingsWidgetConfigActivity : ComponentActivity() {

    companion object {
        private const val INVALID_WIDGET_ID = AppWidgetManager.INVALID_APPWIDGET_ID
    }

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    private var appWidgetId = INVALID_WIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Any exit without pressing Save leaves the widget as it was.
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            INVALID_WIDGET_ID,
        ) ?: INVALID_WIDGET_ID
        if (appWidgetId == INVALID_WIDGET_ID) {
            finish()
            return
        }

        val existingGoalId = SavingsWidgetConfigStore.goalId(this, appWidgetId)

        setContent {
            SpendlyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    SavingsWidgetGoalPicker(
                        goalsFlow = { userPreferencesRepository.getSavingsGoals().first() },
                        currencyFlow = { userPreferencesRepository.baseCurrency.first() },
                        initialGoalId = existingGoalId,
                        onCancel = { finish() },
                        onSave = { goalId -> bindGoal(goalId) },
                    )
                }
            }
        }
    }

    private fun bindGoal(goalId: String) {
        SavingsWidgetConfigStore.setGoalId(this, appWidgetId, goalId)
        SavingsWidgetUpdateWorker.enqueueOneShot(this)
        setResult(
            RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
        )
        finish()
    }
}

private data class SavingsWidgetPickerState(
    val goals: SavingsGoals,
    val currency: String,
)

@Composable
private fun SavingsWidgetGoalPicker(
    goalsFlow: suspend () -> SavingsGoals,
    currencyFlow: suspend () -> String,
    initialGoalId: String?,
    onCancel: () -> Unit,
    onSave: (String) -> Unit,
) {
    val state by produceState<SavingsWidgetPickerState?>(initialValue = null) {
        value = SavingsWidgetPickerState(
            goals = goalsFlow(),
            currency = currencyFlow(),
        )
    }

    val loaded = state
    if (loaded == null) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val goals = loaded.goals
    val initial = initialGoalId?.takeIf { id -> goals.goals.any { it.id == id } }
        ?: goals.activeGoal?.id
    var selectedId by remember(goals) { mutableStateOf(initial) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Text(
            text = stringResource(R.string.savings_widget_config_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.savings_widget_config_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(goals.goals, key = { it.id }) { goal ->
                GoalOptionRow(
                    goal = goal,
                    currency = loaded.currency,
                    selected = goal.id == selectedId,
                    onClick = { selectedId = goal.id },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.common_cancel))
            }
            Button(
                onClick = { selectedId?.let(onSave) },
                enabled = selectedId != null,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.savings_widget_config_save))
            }
        }
    }
}

@Composable
private fun GoalOptionRow(
    goal: SavingsGoalDefinition,
    currency: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val month = formatGoalMonth(goal.endMonth)
    val detail = if (goal.kind == SavingsGoalKind.FIXED) {
        stringResource(
            R.string.savings_widget_config_fixed_detail,
            CurrencyFormatter.formatCurrencyWhole(goal.normalizedAmount, currency),
            month,
        )
    } else {
        stringResource(
            R.string.savings_widget_config_percent_detail,
            goal.normalizedPercent,
            month,
        )
    }

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = goal.name.ifBlank {
                        stringResource(R.string.savings_goal_default_name)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}

private fun formatGoalMonth(month: YearMonth): String =
    month.atEndOfMonth().format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))
