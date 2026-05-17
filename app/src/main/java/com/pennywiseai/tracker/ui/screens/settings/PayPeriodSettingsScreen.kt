package com.pennywiseai.tracker.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.DateRangeUtils
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val fullDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayPeriodSettingsScreen(
    onNavigateBack: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.budget_period_saved)
    val rangeErrorMessage = stringResource(R.string.budget_period_error_range)
    val implicitSavedMessage = stringResource(R.string.budget_period_implicit_saved)

    val monthStartDay by settingsViewModel.monthStartDay.collectAsStateWithLifecycle(initialValue = 1)
    val useFinancialMonth by settingsViewModel.useFinancialMonth.collectAsStateWithLifecycle(initialValue = true)
    val useFixedEnd by settingsViewModel.useFixedBudgetPeriodEnd.collectAsStateWithLifecycle(initialValue = false)
    val endDom by settingsViewModel.budgetPeriodEndDay.collectAsStateWithLifecycle(initialValue = 31)
    val overrides by settingsViewModel.salaryMonthOverrides.collectAsStateWithLifecycle(initialValue = emptyMap())

    var draftStart by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var draftEnd by remember { mutableStateOf(LocalDate.now()) }

    // Sync draft pickers when persisted prefs change — do not key on [overrides] (new map identity can thrash).
    LaunchedEffect(monthStartDay, useFinancialMonth, useFixedEnd, endDom) {
        if (!useFinancialMonth) return@LaunchedEffect
        val (s, e) = DateRangeUtils.calculateBudgetPeriodRange(
            LocalDate.now(),
            monthStartDay,
            useFixedEnd,
            endDom,
            overrides
        )
        draftStart = s
        draftEnd = e
    }

    val currentWindowLabel = if (useFinancialMonth) {
        val (s, e) = DateRangeUtils.calculateBudgetPeriodRange(
            LocalDate.now(),
            monthStartDay,
            useFixedEnd,
            endDom,
            overrides
        )
        DateRangeUtils.formatDateRange(s, e)
    } else {
        null
    }

    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pay_period_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Spacing.md)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Spacer(Modifier.height(Spacing.sm))

            Text(
                text = stringResource(R.string.pay_period_mode_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.pay_period_mode_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.period_type_pay_month),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = useFinancialMonth,
                    onCheckedChange = { settingsViewModel.setUseFinancialMonth(it) },
                )
            }

            if (useFinancialMonth) {
                Text(
                    text = stringResource(R.string.budget_period_repeating_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (currentWindowLabel != null) {
                    Text(
                        text = "${stringResource(R.string.budget_period_current_label)}: $currentWindowLabel",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                OutlinedButton(
                    onClick = { pickingStart = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "${stringResource(R.string.budget_period_start)} · ${draftStart.format(fullDateFormatter)}"
                    )
                }
                OutlinedButton(
                    onClick = { pickingEnd = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "${stringResource(R.string.budget_period_end)} · ${draftEnd.format(fullDateFormatter)}"
                    )
                }

                Button(
                    onClick = {
                        settingsViewModel.saveFixedPayPeriodFromDates(draftStart, draftEnd) { ok ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (ok) savedMessage else rangeErrorMessage,
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.budget_period_save_dates))
                }

                TextButton(
                    onClick = {
                        settingsViewModel.clearFixedBudgetPeriodEnd {
                            scope.launch {
                                snackbarHostState.showSnackbar(implicitSavedMessage)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.budget_period_implicit_end))
                }
            }
        }
    }

    if (pickingStart) {
        FullDatePickerDialog(
            initial = draftStart,
            onDismiss = { pickingStart = false },
            onConfirm = {
                draftStart = it
                pickingStart = false
            },
        )
    }
    if (pickingEnd) {
        FullDatePickerDialog(
            initial = draftEnd,
            onDismiss = { pickingEnd = false },
            onConfirm = {
                draftEnd = it
                pickingEnd = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullDatePickerDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val millis = initial.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = millis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = state.selectedDateMillis ?: millis
                    onConfirm(
                        Instant.ofEpochMilli(selected).atZone(ZoneId.systemDefault()).toLocalDate()
                    )
                },
            ) {
                Text(stringResource(R.string.pay_period_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.pay_period_cancel))
            }
        },
    ) {
        DatePicker(state = state)
    }
}
