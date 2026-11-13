package com.spendly.tracker.ui.screens.savings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendly.tracker.R
import com.spendly.tracker.core.Constants
import com.spendly.tracker.ui.components.SpendlyStandardScaffold
import com.spendly.tracker.ui.components.cards.SectionHeaderV2
import com.spendly.tracker.ui.components.cards.SpendlyCardV2
import com.spendly.tracker.ui.effects.overScrollVertical
import com.spendly.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.spendly.tracker.ui.screens.analytics.SavingsRateCard
import com.spendly.tracker.ui.theme.Dimensions
import com.spendly.tracker.ui.theme.Spacing
import com.spendly.tracker.utils.CurrencyFormatter
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsScreen(
    onNavigateBack: () -> Unit = {},
    openGoalEditor: Boolean = false,
    onGoalEditorOpened: () -> Unit = {},
    viewModel: SavingsViewModel = hiltViewModel(),
) {
    val savingsRate by viewModel.savingsRate.collectAsStateWithLifecycle()
    val savingsCustomMonths by viewModel.savingsCustomMonths.collectAsStateWithLifecycle()
    val savingsProjection by viewModel.savingsProjection.collectAsStateWithLifecycle()
    val savingsProjectionHorizon by viewModel.savingsProjectionHorizon.collectAsStateWithLifecycle()
    val goalPlan by viewModel.goalPlan.collectAsStateWithLifecycle()
    val goalForecast by viewModel.goalForecast.collectAsStateWithLifecycle()
    val goalSimulation by viewModel.goalSimulation.collectAsStateWithLifecycle()
    val extraMonthlySavings by viewModel.extraMonthlySavings.collectAsStateWithLifecycle()
    val reminderEnabled by viewModel.savingsGoalReminderEnabled.collectAsStateWithLifecycle()
    val reminderDays by viewModel.savingsReminderDays.collectAsStateWithLifecycle()
    val reminderHour by viewModel.savingsReminderHour.collectAsStateWithLifecycle()
    val savingsHistory by viewModel.savingsHistory.collectAsStateWithLifecycle()
    val savingsGoals by viewModel.savingsGoals.collectAsStateWithLifecycle()
    val savingsGoalsLoaded by viewModel.savingsGoalsLoaded.collectAsStateWithLifecycle()
    val celebrationMilestone by viewModel.celebrationMilestone.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    var showGoalDetails by remember { mutableStateOf(false) }
    var goalBeingEdited by remember { mutableStateOf<SavingsGoalDefinition?>(null) }
    var editingNewGoal by remember { mutableStateOf(false) }
    var goalTab by remember { mutableStateOf(GoalTab.PLAN) }
    val enhancementsEnabled = Constants.Features.SAVINGS_GOAL_ENHANCEMENTS_ENABLED

    LaunchedEffect(openGoalEditor, savingsGoals.activeGoalId, savingsGoalsLoaded) {
        if (openGoalEditor && savingsGoalsLoaded) {
            goalBeingEdited = savingsGoals.activeGoal
            editingNewGoal = false
            onGoalEditorOpened()
        }
    }

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.setSavingsGoalReminderEnabled(true)
    }
    val onToggleReminder: (Boolean) -> Unit = { desired ->
        if (!desired) {
            viewModel.setSavingsGoalReminderEnabled(false)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setSavingsGoalReminderEnabled(true)
        }
    }

    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    SpendlyStandardScaffold(
        title = stringResource(R.string.savings_title),
        onNavigateBack = onNavigateBack,
        scrollBehavior = scrollBehavior,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = paddingValues.calculateTopPadding() + Spacing.md,
                bottom = Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            flingBehavior = rememberOverscrollFlingBehavior { listState },
        ) {
            item {
                SavingsRateCard(
                    summary = savingsRate,
                    onWindowSelected = viewModel::applySavingsWindow,
                    customMonths = savingsCustomMonths,
                    onCustomMonthsChanged = viewModel::saveSavingsCustomMonths,
                    onRangeSelected = viewModel::applySavingsRange,
                    projection = savingsProjection,
                    projectionHorizon = savingsProjectionHorizon,
                    onProjectionHorizonSelected = viewModel::saveSavingsProjectionHorizon,
                )
            }

            item {
                SectionHeaderV2(title = stringResource(R.string.savings_goal_section))
            }

            if (savingsGoals.hasMultiple || enhancementsEnabled) {
                item {
                    SavingsGoalSelector(
                        goals = savingsGoals,
                        onSelect = viewModel::selectSavingsGoal,
                        onAdd = {
                            goalBeingEdited = SavingsGoalDefinition(
                                id = newSavingsGoalId(),
                                name = "",
                                kind = SavingsGoalKind.PERCENT,
                                targetPercent = goalPlan.targetPercent,
                                targetAmount = BigDecimal.ZERO,
                                endMonth = goalPlan.endMonth,
                            )
                            editingNewGoal = true
                        },
                    )
                }
            }

            if (enhancementsEnabled) {
                item {
                    GoalTabRow(selected = goalTab, onSelect = { goalTab = it })
                }
            }

            item {
                if (!enhancementsEnabled || goalTab == GoalTab.PLAN) {
                    SavingsGoalPlannerCard(
                        plan = goalPlan,
                        currency = currency,
                        contributions = savingsGoals.activeGoal?.contributions.orEmpty(),
                        onRemoveContribution = { atMillis ->
                            savingsGoals.activeGoal?.let { goal ->
                                viewModel.removeSavingsContributionEntry(goal.id, atMillis)
                            }
                        },
                        onPercentChange = viewModel::setGoalPercent,
                        onEndMonthChange = viewModel::setGoalEndMonth,
                        onRollover = viewModel::rolloverGoalToNextYear,
                        onEditGoal = {
                            goalBeingEdited = savingsGoals.activeGoal
                            editingNewGoal = false
                        },
                        onAddContribution = { amount ->
                            savingsGoals.activeGoal?.let { goal ->
                                viewModel.addSavingsContribution(goal.id, amount)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = context.getString(
                                            R.string.savings_goal_contribution_added,
                                            CurrencyFormatter.formatCurrencyWhole(amount, currency),
                                        ),
                                        actionLabel = context.getString(
                                            R.string.savings_goal_contribution_undo,
                                        ),
                                        withDismissAction = true,
                                        duration = SnackbarDuration.Long,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.removeSavingsContribution(goal.id, amount)
                                    }
                                }
                            }
                        },
                        showDetails = enhancementsEnabled,
                        onShowDetails = { showGoalDetails = true },
                    )
                } else if (goalTab == GoalTab.FORECAST) {
                    SavingsGoalForecastCard(
                        forecast = goalForecast,
                        plan = goalPlan,
                        currency = currency,
                    )
                } else if (goalTab == GoalTab.WHAT_IF) {
                    SavingsWhatIfCard(
                        plan = goalPlan,
                        simulation = goalSimulation,
                        extraPerMonth = extraMonthlySavings,
                        currency = currency,
                        onExtraChange = viewModel::setExtraMonthlySavings,
                    )
                } else {
                    SavingsGoalHistoryCard(
                        history = savingsHistory,
                        targetPercent = goalPlan.targetPercent,
                        currency = currency,
                        onClearHistory = viewModel::clearSavingsHistory,
                    )
                }
            }

            item {
                SavingsReminderCard(
                    enabled = reminderEnabled,
                    selectedDays = reminderDays
                        .map { DayOfWeek.of(it.coerceIn(1, 7)) }
                        .toSet(),
                    hour = reminderHour,
                    onToggle = onToggleReminder,
                    onDayToggle = viewModel::toggleSavingsGoalReminderDay,
                    onHourChange = viewModel::setSavingsGoalReminderHour,
                )
            }
        }
    }

    if (enhancementsEnabled && showGoalDetails) {
        SavingsGoalDetailsSheet(
            plan = goalPlan,
            forecast = goalForecast,
            currency = currency,
            onDismiss = { showGoalDetails = false },
        )
    }

    goalBeingEdited?.let { goal ->
        SavingsGoalEditorDialog(
            initial = goal,
            isNew = editingNewGoal,
            canDelete = savingsGoals.hasMultiple,
            currency = currency,
            onDismiss = { goalBeingEdited = null },
            onSave = { updated ->
                goalBeingEdited = null
                viewModel.saveSavingsGoal(updated)
            },
            onDelete = {
                goalBeingEdited = null
                viewModel.deleteSavingsGoal(goal.id)
            },
        )
    }

    celebrationMilestone?.let { milestone ->
        MilestoneCelebrationDialog(
            milestone = milestone,
            saved = goalPlan.actualSaved,
            currency = currency,
            onDismiss = viewModel::dismissCelebration,
        )
    }
}

@Composable
private fun SavingsGoalSelector(
    goals: SavingsGoals,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val selectorDescription = stringResource(R.string.savings_goal_selector_cd)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .semantics {
                contentDescription = selectorDescription
            },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        goals.goals.forEach { goal ->
            GoalChip(
                label = goalDisplayName(goal),
                selected = goal.id == goals.activeGoal?.id,
                onClick = { onSelect(goal.id) },
            )
        }
        Surface(
            onClick = onAdd,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Text(
                text = stringResource(R.string.savings_goal_selector_add),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
        }
    }
}

/** Which part of the goal experience the tabs below the selector are showing. */
private enum class GoalTab { PLAN, FORECAST, WHAT_IF, HISTORY }

private fun GoalTab.labelRes(): Int = when (this) {
    GoalTab.PLAN -> R.string.savings_goal_tab_plan
    GoalTab.FORECAST -> R.string.savings_goal_tab_forecast
    GoalTab.WHAT_IF -> R.string.savings_goal_tab_what_if
    GoalTab.HISTORY -> R.string.savings_goal_tab_history
}

@Composable
private fun GoalTabRow(selected: GoalTab, onSelect: (GoalTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GoalTab.entries.forEach { tab ->
            GoalChip(
                label = stringResource(tab.labelRes()),
                selected = tab == selected,
                onClick = { onSelect(tab) },
            )
        }
    }
}

@Composable
private fun goalDisplayName(goal: SavingsGoalDefinition): String =
    goal.name.ifBlank { stringResource(R.string.savings_goal_default_name) }

/**
 * Add/edit sheet for a goal. Percent goals keep the slider from the planner; fixed goals
 * switch to an amount field so the target is an explicit rupee figure.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavingsGoalEditorDialog(
    initial: SavingsGoalDefinition,
    isNew: Boolean,
    canDelete: Boolean,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (SavingsGoalDefinition) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var kind by remember(initial.id) { mutableStateOf(initial.kind) }
    var percent by remember(initial.id) { mutableFloatStateOf(initial.targetPercent.toFloat()) }
    var amountText by remember(initial.id) {
        mutableStateOf(
            initial.normalizedAmount.takeIf { it.signum() > 0 }?.toPlainString().orEmpty(),
        )
    }
    var endMonth by remember(initial.id) { mutableStateOf(initial.endMonth) }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val amount = amountText.trim().toBigDecimalOrNull()
    val isValid = kind == SavingsGoalKind.PERCENT ||
        (amount != null && amount.signum() > 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (isNew) R.string.savings_goal_new_title else R.string.savings_goal_edit_title,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(R.string.savings_goal_name_label)) },
                    placeholder = {
                        Text(stringResource(R.string.savings_goal_name_placeholder))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    GoalChip(
                        label = stringResource(R.string.savings_goal_kind_percent),
                        selected = kind == SavingsGoalKind.PERCENT,
                        onClick = { kind = SavingsGoalKind.PERCENT },
                    )
                    GoalChip(
                        label = stringResource(R.string.savings_goal_kind_fixed),
                        selected = kind == SavingsGoalKind.FIXED,
                        onClick = { kind = SavingsGoalKind.FIXED },
                    )
                }

                if (kind == SavingsGoalKind.PERCENT) {
                    Slider(
                        value = percent,
                        onValueChange = { percent = (it / 5f).roundToInt() * 5f },
                        valueRange = 0f..100f,
                        steps = 19,
                    )
                    Text(
                        text = stringResource(
                            R.string.savings_goal_target_label,
                            percent.roundToInt(),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { char -> char.isDigit() } },
                        label = { Text(stringResource(R.string.savings_goal_amount_label)) },
                        placeholder = {
                            Text(stringResource(R.string.savings_goal_amount_placeholder))
                        },
                        prefix = { Text(currencySymbol(currency)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = amountText.isNotBlank() && !isValid,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (amountText.isNotBlank() && !isValid) {
                        Text(
                            text = stringResource(R.string.savings_goal_amount_invalid),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                Text(
                    text = stringResource(
                        R.string.savings_goal_planner_deadline,
                        formatGoalMonth(endMonth),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    GoalChip(
                        label = stringResource(R.string.savings_goal_preset_this_year),
                        selected = endMonth == defaultSavingsGoalEndMonth(),
                        onClick = { endMonth = defaultSavingsGoalEndMonth() },
                    )
                    GoalChip(
                        label = stringResource(R.string.savings_goal_preset_next_year),
                        selected = endMonth == defaultSavingsGoalEndMonth().plusYears(1),
                        onClick = { endMonth = defaultSavingsGoalEndMonth().plusYears(1) },
                    )
                    GoalChip(
                        label = stringResource(R.string.savings_goal_preset_custom),
                        selected = !isPresetGoalMonth(endMonth),
                        onClick = { showDatePicker = true },
                    )
                }

                if (canDelete) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(
                            text = stringResource(R.string.savings_goal_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            kind = kind,
                            targetPercent = percent.roundToInt().coerceIn(0, 100),
                            targetAmount = amount ?: BigDecimal.ZERO,
                            endMonth = endMonth,
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.savings_goal_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )

    if (showDatePicker) {
        GoalEndMonthPicker(
            initial = endMonth,
            onDismiss = { showDatePicker = false },
            onConfirm = { picked ->
                endMonth = picked
                showDatePicker = false
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.savings_goal_delete_title)) },
            text = { Text(stringResource(R.string.savings_goal_delete_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.savings_goal_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

/** Currency glyph for the amount field prefix; falls back to the ISO code. */
private fun currencySymbol(currency: String): String = when (currency.uppercase()) {
    "INR" -> "₹"
    "USD" -> "$"
    "EUR" -> "€"
    "GBP" -> "£"
    else -> currency
}

@Composable
private fun MilestoneCelebrationDialog(
    milestone: Int,
    saved: BigDecimal,
    currency: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.savings_celebration_title)) },
        text = {
            Text(
                if (milestone >= 100) {
                    stringResource(
                        R.string.savings_celebration_complete,
                        CurrencyFormatter.formatCurrencyWhole(saved, currency),
                    )
                } else {
                    stringResource(R.string.savings_celebration_text, milestone)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.savings_celebration_continue))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavingsGoalPlannerCard(
    plan: SavingsGoalPlan,
    currency: String,
    contributions: List<SavingsContribution>,
    onRemoveContribution: (Long) -> Unit,
    onPercentChange: (Int) -> Unit,
    onEndMonthChange: (YearMonth) -> Unit,
    onRollover: () -> Unit,
    onEditGoal: () -> Unit,
    onAddContribution: (BigDecimal) -> Unit,
    showDetails: Boolean,
    onShowDetails: () -> Unit,
) {
    var sliderValue by remember { mutableFloatStateOf(plan.targetPercent.toFloat()) }
    LaunchedEffect(plan.targetPercent) { sliderValue = plan.targetPercent.toFloat() }
    var showDatePicker by remember { mutableStateOf(false) }
    var showContributionDialog by remember { mutableStateOf(false) }

    SpendlyCardV2(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (plan.isFixedAmount) {
                    stringResource(
                        R.string.savings_goal_planner_title_fixed,
                        CurrencyFormatter.formatCurrencyWhole(plan.targetAmount, currency),
                    )
                } else {
                    stringResource(R.string.savings_goal_planner_title, plan.targetPercent)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onEditGoal) {
                Text(stringResource(R.string.savings_goal_edit_action))
            }
        }
        Text(
            text = stringResource(
                R.string.savings_goal_planner_deadline,
                formatGoalMonth(plan.periodEnd),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            GoalChip(
                label = stringResource(R.string.savings_goal_preset_this_year),
                selected = plan.endMonth == defaultSavingsGoalEndMonth(),
                onClick = { onEndMonthChange(defaultSavingsGoalEndMonth()) },
            )
            GoalChip(
                label = stringResource(R.string.savings_goal_preset_next_year),
                selected = plan.endMonth == defaultSavingsGoalEndMonth().plusYears(1),
                onClick = { onEndMonthChange(defaultSavingsGoalEndMonth().plusYears(1)) },
            )
            GoalChip(
                label = stringResource(R.string.savings_goal_preset_custom),
                selected = !isPresetGoalMonth(plan.endMonth),
                onClick = { showDatePicker = true },
            )
        }

        if (!plan.isFixedAmount) {
            Spacer(modifier = Modifier.height(Spacing.md))
            Slider(
                value = sliderValue,
                onValueChange = { value ->
                    val rounded = (value / 5f).roundToInt() * 5
                    sliderValue = rounded.toFloat()
                    if (rounded != plan.targetPercent) onPercentChange(rounded)
                },
                valueRange = 0f..100f,
                steps = 19,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.savings_goal_zero_percent),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.savings_goal_target_label, plan.targetPercent),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.savings_goal_full_percent),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))
        // The bar measures progress toward the target amount, not the income savings rate
        // shown above it. Fall back to the rate only when no target amount exists.
        val progressPercent = plan.goalProgressPercent ?: plan.currentPercent
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = if (plan.isFixedAmount) {
                    stringResource(
                        R.string.savings_goal_saved_amount_so_far,
                        CurrencyFormatter.formatCurrencyWhole(plan.actualSaved, currency),
                        CurrencyFormatter.formatCurrencyWhole(plan.targetAmount, currency),
                    )
                } else {
                    when {
                        plan.currentPercent == null ->
                            stringResource(R.string.savings_goal_no_income_period)
                        plan.currentPercent < 0f -> stringResource(
                            R.string.savings_goal_overspent_so_far,
                            abs(plan.currentPercent.roundToInt()),
                        )
                        else -> stringResource(
                            R.string.savings_goal_savings_rate,
                            plan.currentPercent.roundToInt(),
                        )
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (plan.isFixedAmount) {
                FilledTonalButton(
                    onClick = { showContributionDialog = true },
                    contentPadding = PaddingValues(
                        horizontal = Spacing.md,
                        vertical = Spacing.xs,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.savings_goal_add_contribution),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        val progressDescription = if (plan.isFixedAmount) {
            stringResource(
                R.string.savings_goal_progress_cd_fixed,
                (progressPercent ?: 0f).coerceIn(0f, 100f).roundToInt(),
                CurrencyFormatter.formatCurrencyWhole(plan.targetAmount, currency),
            )
        } else {
            stringResource(
                R.string.savings_goal_progress_cd,
                (progressPercent ?: 0f).coerceIn(0f, 100f).roundToInt(),
            )
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
        LinearProgressIndicator(
            progress = { (progressPercent ?: 0f).coerceIn(0f, 100f) / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .semantics {
                    contentDescription = progressDescription
                },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            // The end-cap dot reads as a draggable handle; this is a read-only bar.
            drawStopIndicator = {},
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (plan.requiredTotalSavings.signum() > 0) {
                    stringResource(
                        R.string.savings_goal_progress_of_target,
                        (progressPercent ?: 0f).coerceIn(0f, 100f).roundToInt(),
                        CurrencyFormatter.formatCurrencyWhole(plan.requiredTotalSavings, currency),
                    )
                } else if (plan.isFixedAmount) {
                    stringResource(
                        R.string.savings_goal_target_amount_short,
                        CurrencyFormatter.formatCurrencyWhole(plan.targetAmount, currency),
                    )
                } else {
                    stringResource(R.string.savings_goal_target_short, plan.targetPercent)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (plan.remainingMonths > 0) {
                    pluralStringResource(
                        R.plurals.savings_months_left,
                        plan.remainingMonths,
                        plan.remainingMonths,
                    )
                } else {
                    stringResource(R.string.savings_goal_deadline_passed)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (Constants.Features.SAVINGS_GOAL_ENHANCEMENTS_ENABLED &&
            plan.remainingToSave.signum() > 0
        ) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = stringResource(
                    R.string.savings_goal_remaining_to_save,
                    CurrencyFormatter.formatCurrencyWhole(plan.remainingToSave, currency),
                ),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PlanStat(
                label = stringResource(R.string.savings_goal_save_per_month_label),
                value = CurrencyFormatter.formatCurrencyWhole(plan.monthlySavingsNeeded, currency),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            PlanStat(
                label = stringResource(R.string.savings_goal_spend_per_month_label),
                value = if (plan.hasIncome) {
                    CurrencyFormatter.formatCurrencyWhole(plan.spendablePerMonth, currency)
                } else {
                    "—"
                },
                color = if (plan.hasIncome && plan.spendablePerMonth.signum() == 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.secondary
                },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(Spacing.sm))
        val status = when {
            !plan.hasIncome ->
                stringResource(R.string.savings_goal_status_no_income)
            plan.isGoalDatePassed ->
                stringResource(R.string.savings_goal_status_passed)
            plan.shortfallPerMonth.signum() > 0 -> if (plan.otherMonthlySavings.signum() > 0) {
                stringResource(
                    R.string.savings_goal_status_shortfall_split,
                    CurrencyFormatter.formatCurrencyWhole(plan.totalMonthlySavings, currency),
                    CurrencyFormatter.formatCurrencyWhole(plan.monthlySavingsNeeded, currency),
                    CurrencyFormatter.formatCurrencyWhole(plan.otherMonthlySavings, currency),
                )
            } else {
                stringResource(
                    R.string.savings_goal_status_shortfall,
                    CurrencyFormatter.formatCurrencyWhole(plan.totalMonthlySavings, currency),
                )
            }
            plan.cutPerMonth.signum() > 0 -> stringResource(
                R.string.savings_goal_status_cut,
                CurrencyFormatter.formatCurrencyWhole(plan.cutPerMonth, currency),
            )
            else -> stringResource(
                R.string.savings_goal_status_on_track,
                CurrencyFormatter.formatCurrencyWhole(plan.monthlySavingsNeeded, currency),
            )
        }
        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = when {
                !plan.hasIncome -> MaterialTheme.colorScheme.onSurfaceVariant
                plan.isGoalDatePassed -> MaterialTheme.colorScheme.error
                plan.shortfallPerMonth.signum() > 0 -> MaterialTheme.colorScheme.error
                plan.cutPerMonth.signum() > 0 -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            },
        )

        val timeToGoal = computeTimeToGoal(plan)
        val timeToGoalText = when {
            timeToGoal.isComplete -> stringResource(R.string.savings_time_to_goal_complete)
            timeToGoal.isUnreachable -> stringResource(R.string.savings_time_to_goal_unreachable)
            timeToGoal.targetMonth == null -> null
            timeToGoal.targetMonth.isAfter(plan.endMonth) ->
                if (plan.isFixedAmount) {
                    stringResource(
                        R.string.savings_time_to_goal_fixed,
                        formatGoalMonth(timeToGoal.targetMonth),
                        CurrencyFormatter.formatCurrencyWhole(plan.monthlySavingsNeeded, currency),
                    )
                } else {
                    stringResource(
                        R.string.savings_time_to_goal,
                        formatGoalMonth(timeToGoal.targetMonth),
                    )
                }
            timeToGoal.targetMonth == plan.endMonth -> stringResource(
                R.string.savings_time_to_goal_on_schedule,
                formatGoalMonth(plan.endMonth),
            )
            else ->
                if (plan.isFixedAmount) {
                    stringResource(
                        R.string.savings_time_to_goal_fixed_ahead,
                        formatGoalMonth(timeToGoal.targetMonth),
                        CurrencyFormatter.formatCurrencyWhole(plan.monthlySavingsNeeded, currency),
                    )
                } else {
                    stringResource(
                        R.string.savings_time_to_goal_ahead,
                        formatGoalMonth(timeToGoal.targetMonth),
                    )
                }
        }
        timeToGoalText?.let {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        computeSafeToSpend(plan)?.let { safe ->
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = stringResource(
                    R.string.savings_safe_to_spend,
                    CurrencyFormatter.formatCurrencyWhole(safe.perDay, currency),
                    safe.daysRemaining,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        if (plan.isGoalDatePassed || showDetails) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (plan.isGoalDatePassed) {
                    TextButton(onClick = onRollover) {
                        Text(stringResource(R.string.savings_goal_plan_next_year))
                    }
                }
                if (showDetails) {
                    TextButton(onClick = onShowDetails) {
                        Text(stringResource(R.string.savings_goal_how_calculated))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = stringResource(
                R.string.savings_goal_planning_period,
                formatGoalMonth(plan.periodStart),
                formatGoalMonth(plan.periodEnd),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (plan.isFixedAmount || plan.hasIncome) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = incomeBasisText(plan, currency),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showDatePicker) {
        GoalEndMonthPicker(
            initial = plan.endMonth,
            onDismiss = { showDatePicker = false },
            onConfirm = { month ->
                showDatePicker = false
                onEndMonthChange(month)
            },
        )
    }

    if (showContributionDialog) {
        SavingsContributionDialog(
            currency = currency,
            contributions = contributions,
            onDismiss = { showContributionDialog = false },
            onAdd = { amount ->
                showContributionDialog = false
                onAddContribution(amount)
            },
            onRemove = onRemoveContribution,
        )
    }
}

@Composable
private fun SavingsContributionDialog(
    currency: String,
    contributions: List<SavingsContribution>,
    onDismiss: () -> Unit,
    onAdd: (BigDecimal) -> Unit,
    onRemove: (Long) -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    val amount = amountText.trim().toBigDecimalOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.savings_goal_contribution_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = stringResource(R.string.savings_goal_contribution_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text(stringResource(R.string.savings_goal_contribution_amount_label)) },
                    prefix = { Text(currencySymbol(currency)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amountText.isNotBlank() && (amount == null || amount.signum() <= 0),
                )
                if (amountText.isNotBlank() && (amount == null || amount.signum() <= 0)) {
                    Text(
                        text = stringResource(R.string.savings_goal_contribution_invalid),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (contributions.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(top = Spacing.sm),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )
                    Text(
                        text = stringResource(R.string.savings_goal_contribution_history),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    contributions.takeLast(3).reversed().forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = formatContributionDate(entry.atMillis),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = CurrencyFormatter.formatCurrencyWhole(
                                    entry.amount,
                                    currency,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            TextButton(
                                onClick = { onRemove(entry.atMillis) },
                                contentPadding = PaddingValues(horizontal = Spacing.sm),
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.savings_goal_contribution_remove,
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount != null && amount.signum() > 0,
                onClick = { amount?.let(onAdd) },
            ) { Text(stringResource(R.string.savings_goal_contribution_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalEndMonthPicker(
    initial: YearMonth,
    onDismiss: () -> Unit,
    onConfirm: (YearMonth) -> Unit,
) {
    // Epoch-day millis keep the picked day stable across timezones.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atEndOfMonth().toEpochDay() * 86_400_000L,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val picked = LocalDate.ofEpochDay(utcTimeMillis / 86_400_000L)
                return !YearMonth.from(picked).isBefore(YearMonth.now())
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    state.selectedDateMillis?.let {
                        onConfirm(YearMonth.from(LocalDate.ofEpochDay(it / 86_400_000L)))
                    }
                },
            ) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    ) {
        DatePicker(
            state = state,
            title = {
                Text(
                    text = stringResource(R.string.savings_goal_picker_title),
                    modifier = Modifier.padding(
                        start = Spacing.lg,
                        end = Spacing.sm,
                        top = Spacing.md,
                    ),
                )
            },
        )
    }
}

@Composable
private fun SavingsReminderCard(
    enabled: Boolean,
    selectedDays: Set<DayOfWeek>,
    hour: Int,
    onToggle: (Boolean) -> Unit,
    onDayToggle: (Int) -> Unit,
    onHourChange: (Int) -> Unit,
) {
    val locale = Locale.getDefault()
    val orderedDays = DayOfWeek.values().filter { it in selectedDays }
    val dayNames = orderedDays.joinToString(", ") {
        it.getDisplayName(JavaTextStyle.SHORT, locale)
    }
    val timeText = formatReminderTime(hour)
    SpendlyCardV2(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.savings_reminder_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (enabled) {
                        stringResource(
                            R.string.savings_reminder_card_subtitle,
                            dayNames,
                            timeText,
                        )
                    } else {
                        stringResource(R.string.savings_reminder_card_subtitle_off)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
            )
        }

        if (enabled) {
            Spacer(modifier = Modifier.height(Spacing.md))
            Text(
                text = stringResource(R.string.savings_reminder_day_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            ReminderDayPicker(
                selected = selectedDays,
                locale = locale,
                onToggle = { onDayToggle(it.value) },
            )

            Spacer(modifier = Modifier.height(Spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.savings_reminder_time_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            val reminderTimeDescription = stringResource(
                R.string.savings_reminder_time_cd,
                timeText,
            )
            Slider(
                value = hour.toFloat(),
                onValueChange = { onHourChange(it.roundToInt()) },
                valueRange = 0f..23f,
                steps = 22,
                modifier = Modifier.semantics {
                    contentDescription = reminderTimeDescription
                },
            )
        }
    }
}

@Composable
private fun ReminderDayPicker(
    selected: Set<DayOfWeek>,
    locale: Locale,
    onToggle: (DayOfWeek) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        DayOfWeek.values().forEach { day ->
            val isSelected = day in selected
            Surface(
                onClick = { onToggle(day) },
                shape = CircleShape,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainer,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                border = if (isSelected) {
                    null
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics { this.selected = isSelected },
            ) {
                Text(
                    text = day.getDisplayName(JavaTextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(vertical = Spacing.sm),
                )
            }
        }
    }
}

private fun formatReminderTime(hour: Int): String =
    LocalTime.of(hour.coerceIn(0, 23), 0)
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

@Composable
private fun GoalChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
    }
}

@Composable
private fun PlanStat(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
        )
    }
}

private fun isPresetGoalMonth(month: YearMonth): Boolean =
    month == defaultSavingsGoalEndMonth() || month == defaultSavingsGoalEndMonth().plusYears(1)

@Composable
private fun incomeBasisText(plan: SavingsGoalPlan, currency: String): String {
    if (plan.isFixedAmount) {
        return stringResource(
            R.string.savings_goal_contribution_basis,
            CurrencyFormatter.formatCurrencyWhole(plan.actualSaved, currency),
            CurrencyFormatter.formatCurrencyWhole(plan.monthlySavingsNeeded, currency),
        )
    }
    val monthly = CurrencyFormatter.formatCurrencyWhole(plan.projectedMonthlyIncome, currency)
    return when (plan.incomeBasis) {
        SavingsIncomeBasis.SALARY ->
            stringResource(R.string.savings_goal_income_basis_salary, monthly)
        SavingsIncomeBasis.AVERAGE -> stringResource(
            R.string.savings_goal_income_basis_average,
            monthly,
            formatGoalMonth(plan.periodStart),
        )
        SavingsIncomeBasis.PROJECTION ->
            stringResource(R.string.savings_goal_income_basis_projection, monthly)
        SavingsIncomeBasis.NONE -> stringResource(R.string.savings_goal_income_basis_none)
    }
}

private fun formatGoalMonth(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))

private fun formatGoalMonth(month: YearMonth): String =
    month.atEndOfMonth().format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))

private fun formatContributionDate(atMillis: Long): String =
    Instant.ofEpochMilli(atMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

/**
 * Actual-then-projected savings-rate line with the goal target and milestones.
 * Past months are solid; months after today are dashed and estimated.
 */
@Composable
private fun SavingsGoalForecastCard(
    forecast: SavingsGoalForecast,
    plan: SavingsGoalPlan,
    currency: String,
) {
    SpendlyCardV2(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.savings_forecast_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = if (plan.isFixedAmount) {
                stringResource(
                    R.string.savings_forecast_subtitle_fixed,
                    CurrencyFormatter.formatCurrencyWhole(plan.targetAmount, currency),
                    formatGoalMonth(plan.periodEnd),
                )
            } else {
                stringResource(
                    R.string.savings_forecast_subtitle,
                    plan.targetPercent,
                    formatGoalMonth(plan.periodEnd),
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(Spacing.sm))
        if (!forecast.hasData) {
            Text(
                text = stringResource(R.string.savings_forecast_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            var selectedIndex by remember(forecast) { mutableStateOf<Int?>(null) }
            ForecastReadout(
                point = selectedIndex?.let { forecast.points.getOrNull(it) },
                currency = currency,
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            SavingsGoalForecastChart(
                forecast = forecast,
                selectedIndex = selectedIndex,
                showTarget = !plan.isFixedAmount,
                onSelect = { index ->
                    selectedIndex = if (selectedIndex == index) null else index
                },
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            ForecastLegend(
                targetPercent = forecast.targetPercent,
                showTarget = !plan.isFixedAmount,
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.savings_forecast_milestones_label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (plan.requiredTotalSavings.signum() > 0) {
                        stringResource(
                            R.string.savings_forecast_milestones_target,
                            CurrencyFormatter.formatCurrencyWhole(
                                plan.requiredTotalSavings,
                                currency,
                            ),
                        )
                    } else {
                        stringResource(R.string.savings_forecast_milestones_no_target)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
            forecast.milestones.forEachIndexed { index, milestone ->
                GoalMilestoneRow(
                    milestone = milestone,
                    currency = currency,
                    todayMonth = forecast.todayMonth,
                    isFirst = index == 0,
                    isLast = index == forecast.milestones.lastIndex,
                )
            }
        }
    }
}

/** Selected-month readout; keeps a stable height so the card does not jump on tap. */
@Composable
private fun ForecastReadout(point: GoalForecastPoint?, currency: String) {
    // Keep the slot height stable, but drop the filled card until a month is selected so
    // the hint reads as a caption instead of a second surface.
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (point == null) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = 52.dp)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                .semantics { liveRegion = LiveRegionMode.Polite },
            contentAlignment = Alignment.CenterStart,
        ) {
            if (point == null) {
                Text(
                    text = stringResource(R.string.savings_forecast_tap_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(
                            text = buildString {
                                append(formatGoalMonth(point.month))
                                if (point.isProjected) {
                                    append(" · ")
                                    append(
                                        stringResource(R.string.savings_forecast_legend_projected),
                                    )
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = when {
                                point.savedPercent == null ->
                                    stringResource(R.string.savings_history_no_income)
                                point.savedPercent < 0f -> stringResource(
                                    R.string.savings_history_overspent,
                                    abs(point.savedPercent.roundToInt()),
                                )
                                else -> stringResource(
                                    R.string.savings_history_rate,
                                    point.savedPercent.roundToInt(),
                                )
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = forecastRateColor(point.savedPercent),
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(
                            text = stringResource(R.string.savings_forecast_selected_cumulative),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = CurrencyFormatter.formatCurrencyWhole(point.savedAmount, currency),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun forecastRateColor(percent: Float?): Color = when {
    percent == null -> MaterialTheme.colorScheme.onSurfaceVariant
    percent < 0f -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.primary
}

@Composable
private fun SavingsGoalForecastChart(
    forecast: SavingsGoalForecast,
    selectedIndex: Int?,
    showTarget: Boolean,
    onSelect: (Int?) -> Unit,
) {
    val textMeasurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val actualColor = colors.primary
    val projectedColor = colors.tertiary
    val targetColor = colors.onSurfaceVariant
    val gridColor = colors.onSurface.copy(alpha = 0.08f)
    val todayColor = colors.onSurface.copy(alpha = 0.25f)
    val selectionColor = colors.primary.copy(alpha = 0.35f)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        color = colors.onSurface.copy(alpha = 0.7f),
    )

    val rates = forecast.points.mapNotNull { it.savedPercent }
    val description = forecastDescription(forecast)
    val high = max(
        if (showTarget) forecast.targetPercent.toFloat() else 0f,
        rates.maxOrNull() ?: 0f,
    ).coerceAtLeast(25f)
    val low = minOf(0f, rates.minOrNull() ?: 0f)
    val axisMax = ceil(high / 25f) * 25f
    val axisMin = floor(low / 25f) * 25f

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .pointerInput(forecast.points.size) {
                detectTapGestures { offset ->
                    onSelect(
                        forecastIndexAt(
                            x = offset.x,
                            width = size.width.toFloat(),
                            left = 34.dp.toPx(),
                            right = size.width - 4.dp.toPx(),
                            count = forecast.points.size,
                        ),
                    )
                }
            }
            .pointerInput(forecast.points.size) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        forecastIndexAt(
                            x = offset.x,
                            width = size.width.toFloat(),
                            left = 34.dp.toPx(),
                            right = size.width - 4.dp.toPx(),
                            count = forecast.points.size,
                        )?.let(onSelect)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        forecastIndexAt(
                            x = change.position.x,
                            width = size.width.toFloat(),
                            left = 34.dp.toPx(),
                            right = size.width - 4.dp.toPx(),
                            count = forecast.points.size,
                        )?.let(onSelect)
                    },
                )
            }
            .semantics { contentDescription = description },
    ) {
        val left = 34.dp.toPx()
        val right = size.width - 4.dp.toPx()
        val top = 10.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        val chartWidth = right - left
        val chartHeight = bottom - top
        if (chartWidth <= 0f || chartHeight <= 0f) return@Canvas

        val span = (axisMax - axisMin).coerceAtLeast(1f)
        fun yFor(value: Float) = top + (axisMax - value) / span * chartHeight
        fun xFor(index: Int): Float {
            val count = forecast.points.size
            if (count <= 1) return left + chartWidth / 2f
            return left + chartWidth * index / (count - 1)
        }

        var tick = axisMin
        while (tick <= axisMax + 0.01f) {
            val y = yFor(tick)
            drawLine(
                color = gridColor,
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = 1f,
            )
            val label = textMeasurer.measure("${tick.roundToInt()}%", labelStyle)
            drawText(
                label,
                topLeft = Offset(left - label.size.width - 6.dp.toPx(), y - label.size.height / 2f),
            )
            tick += 25f
        }

        val targetY = yFor(forecast.targetPercent.toFloat().coerceIn(axisMin, axisMax))
        if (showTarget) {
            drawLine(
                color = targetColor,
                start = Offset(left, targetY),
                end = Offset(right, targetY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f),
            )
        }

        val todayIndex = forecast.points.indexOfFirst { it.month == forecast.todayMonth }
        if (todayIndex >= 0) {
            val x = xFor(todayIndex)
            drawLine(
                color = todayColor,
                start = Offset(x, top),
                end = Offset(x, bottom),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 5f), 0f),
            )
        }

        fun drawSeries(indices: List<Int>, color: Color, dashed: Boolean) {
            val visible = indices.filter { forecast.points[it].savedPercent != null }
            if (visible.isEmpty()) return
            val path = Path()
            visible.forEachIndexed { position, index ->
                val point = forecast.points[index]
                val x = xFor(index)
                val y = yFor(point.savedPercent!!.coerceIn(axisMin, axisMax))
                if (position == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = if (dashed) {
                        PathEffect.dashPathEffect(floatArrayOf(10f, 7f), 0f)
                    } else {
                        null
                    },
                ),
            )
            visible.forEach { index ->
                val point = forecast.points[index]
                val x = xFor(index)
                val y = yFor(point.savedPercent!!.coerceIn(axisMin, axisMax))
                drawCircle(color = color, radius = 3.dp.toPx(), center = Offset(x, y))
            }
        }

        val actualIndices = forecast.points.indices.filter { !forecast.points[it].isProjected }
        val projectedIndices = forecast.points.indices.filter { forecast.points[it].isProjected }
        drawSeries(actualIndices, actualColor, dashed = false)
        if (projectedIndices.isNotEmpty()) {
            // Anchor the projected line to the last actual point so there is no visual gap.
            val anchor = actualIndices.lastOrNull { forecast.points[it].savedPercent != null }
            drawSeries(
                indices = listOfNotNull(anchor) + projectedIndices,
                color = projectedColor,
                dashed = true,
            )
        }

        // Ring each milestone month so it is easy to match the list below to the line.
        forecast.milestones.forEach { milestone ->
            val index = milestone.month?.let { month ->
                forecast.points.indexOfFirst { it.month == month }
            } ?: -1
            if (index < 0) return@forEach
            val percent = forecast.points[index].savedPercent ?: return@forEach
            val center = Offset(
                xFor(index),
                yFor(percent.coerceIn(axisMin, axisMax)),
            )
            drawCircle(
                color = colors.tertiary.copy(alpha = 0.7f),
                radius = 5.dp.toPx(),
                center = center,
                style = Stroke(width = 1.2.dp.toPx()),
            )
        }

        // Month labels: start, today and deadline keep the axis readable on narrow screens.
        val labelIndices = listOfNotNull(
            forecast.points.indices.firstOrNull(),
            todayIndex.takeIf { it >= 0 },
            forecast.points.indices.lastOrNull(),
        ).distinct()
        labelIndices.forEach { index ->
            val label = textMeasurer.measure(
                forecast.points[index].month.format(
                    DateTimeFormatter.ofPattern("MMM", Locale.getDefault()),
                ),
                labelStyle,
            )
            val x = xFor(index)
            val leftEdge = (x - label.size.width / 2f)
                .coerceIn(0f, size.width - label.size.width)
            drawText(
                label,
                topLeft = Offset(leftEdge, size.height - label.size.height - 2.dp.toPx()),
            )
        }

        // Tapped or scrubbed month: guide line plus a highlighted point.
        selectedIndex?.takeIf { it in forecast.points.indices }?.let { index ->
            val x = xFor(index)
            drawLine(
                color = selectionColor,
                start = Offset(x, top),
                end = Offset(x, bottom),
                strokeWidth = 1.5.dp.toPx(),
            )
            forecast.points[index].savedPercent?.let { percent ->
                val center = Offset(x, yFor(percent.coerceIn(axisMin, axisMax)))
                val pointColor = if (forecast.points[index].isProjected) {
                    projectedColor
                } else {
                    actualColor
                }
                drawCircle(color = colors.surface, radius = 6.dp.toPx(), center = center)
                drawCircle(color = pointColor, radius = 4.dp.toPx(), center = center)
                drawCircle(color = colors.surface, radius = 1.5.dp.toPx(), center = center)
            }
        }
    }
}

/** Maps a touch x-position to the nearest forecast month, or null when it is outside the plot. */
private fun forecastIndexAt(
    x: Float,
    width: Float,
    left: Float,
    right: Float,
    count: Int,
): Int? {
    if (count <= 0) return null
    val plotWidth = right - left
    if (plotWidth <= 0f) return null
    val edgeSlack = (width - right).coerceAtLeast(24f)
    if (x < left - edgeSlack || x > right + edgeSlack) return null
    if (count == 1) return 0
    return ((x - left) / plotWidth * (count - 1))
        .roundToInt()
        .coerceIn(0, count - 1)
}

@Composable
private fun ForecastLegend(targetPercent: Int, showTarget: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendSwatch(
            color = MaterialTheme.colorScheme.primary,
            label = stringResource(R.string.savings_forecast_legend_actual),
            dashed = false,
        )
        LegendSwatch(
            color = MaterialTheme.colorScheme.tertiary,
            label = stringResource(R.string.savings_forecast_legend_projected),
            dashed = true,
        )
        if (showTarget) {
            LegendSwatch(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                label = stringResource(R.string.savings_forecast_legend_target, targetPercent),
                dashed = true,
            )
        }
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String, dashed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Canvas(Modifier.size(width = 14.dp, height = 3.dp)) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = size.height,
                pathEffect = if (dashed) {
                    PathEffect.dashPathEffect(floatArrayOf(5f, 4f), 0f)
                } else {
                    null
                },
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GoalMilestoneRow(
    milestone: GoalMilestone,
    currency: String,
    todayMonth: YearMonth,
    isFirst: Boolean,
    isLast: Boolean,
) {
    val month = milestone.month
    val reached = month != null
    // A milestone dated after today is planned rather than banked, so it gets an open dot.
    val isProjected = month != null && month.isAfter(todayMonth)
    val dotColor = if (reached) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.outline
    }
    val connectorColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        Canvas(
            modifier = Modifier
                .width(20.dp)
                .fillMaxHeight(),
        ) {
            val centerX = size.width / 2f
            val dotY = 18.dp.toPx()
            val dotRadius = 4.dp.toPx()
            if (!isFirst) {
                drawLine(
                    color = connectorColor,
                    start = Offset(centerX, 0f),
                    end = Offset(centerX, dotY - dotRadius - 1.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            if (!isLast) {
                drawLine(
                    color = connectorColor,
                    start = Offset(centerX, dotY + dotRadius + 1.dp.toPx()),
                    end = Offset(centerX, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            if (reached && !isProjected) {
                drawCircle(color = dotColor, radius = dotRadius, center = Offset(centerX, dotY))
            } else {
                drawCircle(
                    color = dotColor,
                    radius = 3.5.dp.toPx(),
                    center = Offset(centerX, dotY),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${milestone.percent}%",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (reached) dotColor
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(48.dp),
            )
            Text(
                text = CurrencyFormatter.formatCurrencyWhole(milestone.amount, currency),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (reached) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = month?.let { formatGoalMonth(it) }
                    ?: stringResource(R.string.savings_forecast_not_reached),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun forecastDescription(forecast: SavingsGoalForecast): String {
    val first = forecast.points.firstOrNull()
        ?: return stringResource(R.string.savings_forecast_cd)
    val last = forecast.points.lastOrNull() ?: first
    return buildString {
        append(
            stringResource(
                R.string.savings_forecast_cd_range,
                formatGoalMonth(first.month),
                formatGoalMonth(last.month),
            )
        )
        append(" ")
        first.savedPercent?.let {
            append(stringResource(R.string.savings_forecast_cd_actual, it.roundToInt()))
            append(" ")
        }
        last.savedPercent?.let {
            append(stringResource(R.string.savings_forecast_cd_projected, it.roundToInt()))
            append(" ")
        }
        append(stringResource(R.string.savings_forecast_cd_target, forecast.targetPercent))
    }
}

/** Lets the user try a higher monthly saving and see the new completion month. */
@Composable
private fun SavingsWhatIfCard(
    plan: SavingsGoalPlan,
    simulation: SavingsGoalSimulation,
    extraPerMonth: BigDecimal,
    currency: String,
    onExtraChange: (BigDecimal) -> Unit,
) {
    val maxExtra = max(
        500f,
        ceil((plan.monthlySavingsNeeded.toFloat() * 0.6f).coerceAtLeast(500f) / 500f) * 500f,
    )
    var sliderValue by remember { mutableFloatStateOf(extraPerMonth.toFloat().coerceIn(0f, maxExtra)) }
    LaunchedEffect(extraPerMonth, maxExtra) {
        sliderValue = extraPerMonth.toFloat().coerceIn(0f, maxExtra)
    }

    SpendlyCardV2(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.savings_what_if_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.savings_what_if_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = stringResource(
                R.string.savings_what_if_extra,
                CurrencyFormatter.formatCurrencyWhole(
                    BigDecimal.valueOf(sliderValue.toLong()),
                    currency,
                ),
            ),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        val extraDescription = stringResource(
            R.string.savings_what_if_cd,
            CurrencyFormatter.formatCurrencyWhole(
                BigDecimal.valueOf(sliderValue.toLong()),
                currency,
            ),
        )
        Slider(
            value = sliderValue,
            onValueChange = { value ->
                val rounded = (value / 100f).roundToInt() * 100f
                sliderValue = rounded
                onExtraChange(BigDecimal.valueOf(rounded.toLong()))
            },
            valueRange = 0f..maxExtra,
            modifier = Modifier.semantics {
                contentDescription = extraDescription
            },
        )

        val outcome = when {
            plan.remainingToSave.signum() <= 0 ->
                stringResource(R.string.savings_what_if_already_done)
            simulation.monthsToGoal == null ->
                stringResource(R.string.savings_what_if_unreachable)
            else -> {
                val months = simulation.monthsToGoal
                buildString {
                    append(
                        stringResource(
                            R.string.savings_what_if_complete,
                            simulation.completionMonth?.let { formatGoalMonth(it) } ?: "—",
                        )
                    )
                    append(" · ")
                    append(
                        pluralStringResource(
                            R.plurals.savings_month_count,
                            months,
                            months,
                        )
                    )
                }
            }
        }
        Text(
            text = outcome,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = buildString {
                append(
                    stringResource(
                        R.string.savings_what_if_total,
                        CurrencyFormatter.formatCurrencyWhole(
                            simulation.newMonthlySavings,
                            currency,
                        ),
                    )
                )
                simulation.newPercentAtDeadline?.let { percent ->
                    if (!plan.isFixedAmount) {
                        append(
                            stringResource(
                                R.string.savings_what_if_total_rate,
                                percent.roundToInt(),
                            )
                        )
                    }
                }
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Month-by-month snapshots, newest first, so progress is visible over time. */
@Composable
private fun SavingsGoalHistoryCard(
    history: List<SavingsMonthSnapshot>,
    targetPercent: Int,
    currency: String,
    onClearHistory: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    val ordered = history.sortedByDescending { it.month }
    val change = latestSavingsRateChange(ordered)
    val streak = computeSavingsStreak(ordered, targetPercent)
    val trackingSince = savingsTrackingSince(ordered)

    SpendlyCardV2(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.savings_history_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.savings_history_subtitle, targetPercent),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        trackingSince?.let {
            Text(
                text = stringResource(
                    R.string.savings_history_tracking_since,
                    formatGoalMonth(it),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(Spacing.sm))
        if (ordered.isEmpty()) {
            Text(
                text = stringResource(R.string.savings_history_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            change?.let { attribution ->
                val previousLabel = formatGoalMonthShort(attribution.previousMonth)
                val driver = stringResource(
                    when (attribution.driver) {
                        SavingsChangeDriver.SPENDING ->
                            R.string.savings_attribution_driver_spending
                        SavingsChangeDriver.INCOME ->
                            R.string.savings_attribution_driver_income
                        SavingsChangeDriver.BOTH ->
                            R.string.savings_attribution_driver_both
                        SavingsChangeDriver.NONE ->
                            R.string.savings_attribution_driver_none
                    }
                )
                Text(
                    text = when {
                        attribution.deltaPoints > 1f -> stringResource(
                            R.string.savings_attribution_up,
                            attribution.deltaPoints.roundToInt(),
                            previousLabel,
                            driver,
                        )
                        attribution.deltaPoints < -1f -> stringResource(
                            R.string.savings_attribution_down,
                            abs(attribution.deltaPoints).roundToInt(),
                            previousLabel,
                            driver,
                        )
                        else -> stringResource(
                            R.string.savings_attribution_flat,
                            previousLabel,
                        )
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (streak.best > 0) {
                val current = pluralStringResource(
                    R.plurals.savings_streak_months,
                    streak.current,
                    streak.current,
                )
                val best = pluralStringResource(
                    R.plurals.savings_streak_months,
                    streak.best,
                    streak.best,
                )
                Text(
                    text = stringResource(R.string.savings_streak_line, current, best),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xs))
            ordered.take(HISTORY_LIMIT).forEachIndexed { index, snapshot ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                    )
                }
                SavingsHistoryRow(
                    snapshot = snapshot,
                    targetPercent = targetPercent,
                    currency = currency,
                )
            }

            TextButton(onClick = { confirmClear = true }) {
                Text(
                    text = stringResource(R.string.savings_history_clear),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.savings_history_clear_title)) },
            text = { Text(stringResource(R.string.savings_history_clear_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClearHistory()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.savings_history_clear),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun SavingsHistoryRow(
    snapshot: SavingsMonthSnapshot,
    targetPercent: Int,
    currency: String,
) {
    val rate = snapshot.ratePercent
    val color = when {
        rate == null -> MaterialTheme.colorScheme.onSurfaceVariant
        rate < 0f -> MaterialTheme.colorScheme.error
        rate >= targetPercent -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.tertiary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatGoalMonth(snapshot.month),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (snapshot.targetPercent != targetPercent) {
                Text(
                    text = stringResource(
                        R.string.savings_history_target_note,
                        snapshot.targetPercent,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = CurrencyFormatter.formatCurrencyWhole(snapshot.saved, currency),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
            Text(
                text = when {
                    rate == null -> stringResource(R.string.savings_history_no_income)
                    rate < 0f -> stringResource(
                        R.string.savings_history_overspent,
                        abs(rate.roundToInt()),
                    )
                    else -> stringResource(
                        R.string.savings_history_rate,
                        rate.roundToInt(),
                    )
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatGoalMonthShort(month: YearMonth): String =
    month.atEndOfMonth().format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault()))

private const val HISTORY_LIMIT = 6

/** Transparent breakdown of the plan and forecast inputs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavingsGoalDetailsSheet(
    plan: SavingsGoalPlan,
    forecast: SavingsGoalForecast,
    currency: String,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimensions.Padding.content)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(R.string.savings_details_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.savings_details_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            GoalDetailRow(
                stringResource(R.string.savings_details_target),
                if (plan.isFixedAmount) {
                    CurrencyFormatter.formatCurrencyWhole(plan.targetAmount, currency)
                } else {
                    stringResource(R.string.savings_details_target_value, plan.targetPercent)
                },
            )
            GoalDetailRow(
                stringResource(R.string.savings_details_period),
                stringResource(
                    R.string.savings_details_period_value,
                    formatGoalMonth(plan.periodStart),
                    formatGoalMonth(plan.periodEnd),
                ),
            )
            GoalDetailRow(
                stringResource(R.string.savings_details_income_basis),
                incomeBasisText(plan, currency),
            )
            GoalDetailRow(
                stringResource(R.string.savings_details_income_so_far),
                CurrencyFormatter.formatCurrencyWhole(plan.actualIncome, currency),
            )
            GoalDetailRow(
                stringResource(R.string.savings_details_spent_so_far),
                CurrencyFormatter.formatCurrencyWhole(plan.actualSpent, currency),
            )
            GoalDetailRow(
                stringResource(R.string.savings_details_saved_so_far),
                CurrencyFormatter.formatCurrencyWhole(plan.actualSaved, currency),
            )
            GoalDetailRow(
                stringResource(R.string.savings_details_projected_income),
                CurrencyFormatter.formatCurrencyWhole(plan.projectedTotalIncome, currency),
            )
            GoalDetailRow(
                stringResource(R.string.savings_details_required_savings),
                CurrencyFormatter.formatCurrencyWhole(plan.requiredTotalSavings, currency),
            )
            GoalDetailRow(
                stringResource(R.string.savings_details_still_to_save),
                CurrencyFormatter.formatCurrencyWhole(plan.remainingToSave, currency),
            )
            GoalDetailRow(
                stringResource(R.string.savings_details_save_per_month),
                CurrencyFormatter.formatCurrencyWhole(plan.monthlySavingsNeeded, currency),
            )
            GoalDetailRow(
                stringResource(R.string.savings_details_spend_per_month),
                if (plan.hasIncome) {
                    CurrencyFormatter.formatCurrencyWhole(plan.spendablePerMonth, currency)
                } else {
                    "—"
                },
            )
            forecast.points.lastOrNull { it.savedPercent != null }?.let { last ->
                GoalDetailRow(
                    stringResource(R.string.savings_details_projected_rate),
                    stringResource(
                        R.string.savings_details_projected_rate_value,
                        last.savedPercent!!.roundToInt(),
                        formatGoalMonth(last.month),
                    ),
                )
            }
            GoalDetailRow(
                stringResource(R.string.savings_details_deadline),
                if (plan.isGoalDatePassed) {
                    stringResource(R.string.savings_details_deadline_passed)
                } else {
                    pluralStringResource(
                        R.plurals.savings_months_left,
                        plan.remainingMonths,
                        plan.remainingMonths,
                    )
                },
            )

            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.savings_details_footnote),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GoalDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
