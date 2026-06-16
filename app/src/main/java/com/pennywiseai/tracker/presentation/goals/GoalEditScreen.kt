package com.pennywiseai.tracker.presentation.goals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.data.database.entity.GoalTrackingMode
import com.pennywiseai.tracker.data.database.entity.GoalType
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.theme.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditScreen(
    onNavigateBack: () -> Unit = {},
    onSaved: () -> Unit = {},
    viewModel: GoalEditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) onSaved()
    }

    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy") }

    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = if (uiState.isEditMode) "Edit Goal" else "New Goal",
                hasBackButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                hasActionButton = true,
                actionContent = {
                    IconButton(
                        onClick = { viewModel.saveGoal() },
                        enabled = !uiState.isSaving
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = "Save")
                        }
                    }
                },
                hazeState = hazeState
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + Spacing.xl
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = { viewModel.updateName(it) },
                    label = { Text("Goal name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = uiState.errorMessage?.contains("name") == true
                )
            }

            item {
                OutlinedTextField(
                    value = uiState.description,
                    onValueChange = { viewModel.updateDescription(it) },
                    label = { Text("Description (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text(
                    text = "Goal Type",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                GoalTypeSelector(
                    selected = uiState.goalType,
                    onSelect = { viewModel.updateGoalType(it) }
                )
            }

            item {
                OutlinedTextField(
                    value = uiState.targetAmountText,
                    onValueChange = { viewModel.updateTargetAmount(it) },
                    label = { Text("Target amount (${uiState.currency})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = uiState.errorMessage?.contains("amount") == true
                )
            }

            item {
                OutlinedTextField(
                    value = uiState.targetDate.format(dateFormatter),
                    onValueChange = {},
                    label = { Text("Target date") },
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    enabled = false
                )
            }

            item {
                Text(
                    text = "How to track progress",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                TrackingModeSelector(
                    selected = uiState.trackingMode,
                    onSelect = { viewModel.updateTrackingMode(it) }
                )
            }

            if (uiState.trackingMode == GoalTrackingMode.CATEGORY_AUTO && uiState.availableCategories.isNotEmpty()) {
                item {
                    Text(
                        text = "Count income from categories",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    CategoryMultiSelector(
                        categories = uiState.availableCategories,
                        selectedCategories = uiState.autoTrackCategories,
                        onToggle = { viewModel.toggleAutoTrackCategory(it) }
                    )
                }
            }

            uiState.errorMessage?.let { msg ->
                item {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            item {
                Button(
                    onClick = { viewModel.saveGoal() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSaving
                ) {
                    Text(if (uiState.isEditMode) "Save Changes" else "Create Goal")
                }
            }
        }
    }

    if (showDatePicker) {
        GoalDatePickerDialog(
            initialDate = uiState.targetDate,
            onDismiss = { showDatePicker = false },
            onDateSelected = { date ->
                showDatePicker = false
                viewModel.updateTargetDate(date)
            }
        )
    }
}

@Composable
private fun GoalTypeSelector(
    selected: GoalType,
    onSelect: (GoalType) -> Unit
) {
    val types = GoalType.entries
    val rows = types.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                row.forEach { type ->
                    FilterChip(
                        selected = type == selected,
                        onClick = { onSelect(type) },
                        label = { Text(type.shortName(), style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size < 3) {
                    repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun TrackingModeSelector(
    selected: GoalTrackingMode,
    onSelect: (GoalTrackingMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        GoalTrackingMode.entries.forEach { mode ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(mode) },
                colors = CardDefaults.cardColors(
                    containerColor = if (mode == selected)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(Spacing.sm)) {
                    Text(
                        text = mode.displayName(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = mode.description(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryMultiSelector(
    categories: List<String>,
    selectedCategories: List<String>,
    onToggle: (String) -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        categories.forEach { category ->
            FilterChip(
                selected = selectedCategories.contains(category),
                onClick = { onToggle(category) },
                label = { Text(category, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    val date = java.time.Instant.ofEpochMilli(millis)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                    onDateSelected(date)
                }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

private fun GoalType.shortName(): String = when (this) {
    GoalType.SAVINGS -> "Savings"
    GoalType.EMERGENCY_FUND -> "Emergency"
    GoalType.PURCHASE -> "Purchase"
    GoalType.VACATION -> "Vacation"
    GoalType.DEBT_PAYOFF -> "Debt Payoff"
    GoalType.INVESTMENT -> "Investment"
    GoalType.CUSTOM -> "Custom"
}

private fun GoalTrackingMode.displayName(): String = when (this) {
    GoalTrackingMode.CATEGORY_AUTO -> "Automatic"
    GoalTrackingMode.MANUAL_DEPOSIT -> "Manual"
}

private fun GoalTrackingMode.description(): String = when (this) {
    GoalTrackingMode.CATEGORY_AUTO -> "Income from selected categories counts automatically"
    GoalTrackingMode.MANUAL_DEPOSIT -> "Log your savings yourself — or link individual transactions"
}
