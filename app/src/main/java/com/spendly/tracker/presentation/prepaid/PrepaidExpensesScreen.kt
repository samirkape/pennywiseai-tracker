package com.spendly.tracker.presentation.prepaid

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.spendly.tracker.data.database.entity.PrepaidExpenseStatus
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.presentation.transactions.MarkAsPrepaidBottomSheet
import com.spendly.tracker.ui.components.SpendlyScaffold
import com.spendly.tracker.ui.components.cards.SpendlyCardV2
import com.spendly.tracker.ui.theme.Dimensions
import com.spendly.tracker.ui.theme.Spacing
import com.spendly.tracker.utils.CurrencyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrepaidExpensesScreen(
    viewModel: PrepaidExpensesViewModel = hiltViewModel(),
    onAddPrepaidClick: () -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var selectedTxn by remember { mutableStateOf<TransactionEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage by viewModel.errorMessage.collectAsState()

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    SpendlyScaffold(
        title = "Prepaid Expenses",
        snackbarHost = { SnackbarHost(snackbarHostState) },
        actions = {
            IconButton(onClick = { showPicker = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add prepaid expense")
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.plans.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Receipt,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.large),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        "No prepaid expenses yet",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Tap + above to pick an existing expense or create a new one",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(Dimensions.Padding.content),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(uiState.plans, key = { it.plan.id }) { card ->
                    PrepaidExpenseRow(
                        card = card,
                        onCancel = { viewModel.cancelPlan(card.plan.id) },
                        onDelete = { pendingDeleteId = card.plan.id }
                    )
                }
            }
        }
    }

    if (showPicker) {
        TransactionPickerSheet(
            viewModel = viewModel,
            onDismiss = { showPicker = false },
            onCreateNew = {
                showPicker = false
                onAddPrepaidClick()
            },
            onSelect = {
                showPicker = false
                selectedTxn = it
            }
        )
    }

    selectedTxn?.let { txn ->
        MarkAsPrepaidBottomSheet(
            transactionAmount = txn.amount,
            transactionCurrency = txn.currency,
            onDismiss = { selectedTxn = null },
            onConfirm = { months ->
                viewModel.convertTransaction(txn.id, months) { selectedTxn = null }
            }
        )
    }

    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete prepaid plan?") },
            text = { Text("This deletes the plan, its source payment, and every monthly allocation. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlan(id)
                    pendingDeleteId = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PrepaidExpenseRow(
    card: PrepaidExpenseCard,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val plan = card.plan
    var showMenu by remember { mutableStateOf(false) }
    val monthlyAmount = plan.totalAmount.divide(
        java.math.BigDecimal(plan.totalMonths), 2, java.math.RoundingMode.HALF_UP
    )
    val monthFormatter = remember { java.time.format.DateTimeFormatter.ofPattern("MMM yyyy") }
    val isFinished = plan.status == PrepaidExpenseStatus.ACTIVE && card.monthsElapsed >= plan.totalMonths
    val (statusLabel, statusColor) = when {
        isFinished || plan.status == PrepaidExpenseStatus.COMPLETED ->
            "Completed" to MaterialTheme.colorScheme.tertiary
        plan.status == PrepaidExpenseStatus.ACTIVE -> "Active" to MaterialTheme.colorScheme.primary
        else -> plan.status.name.lowercase().replaceFirstChar { it.uppercase() } to
            MaterialTheme.colorScheme.onSurfaceVariant
    }

    SpendlyCardV2(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        plan.merchantName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        "${plan.startDate.format(monthFormatter)} – ${plan.endDate.format(monthFormatter)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        CurrencyFormatter.formatCurrency(plan.totalAmount, plan.currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${CurrencyFormatter.formatCurrency(monthlyAmount, plan.currency)}/mo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (plan.status == PrepaidExpenseStatus.ACTIVE && !isFinished) {
                            DropdownMenuItem(
                                text = { Text("Cancel") },
                                onClick = { showMenu = false; onCancel() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = { showMenu = false; onDelete() }
                        )
                    }
                }
            }

            LinearProgressIndicator(
                progress = { (card.monthsElapsed.toFloat() / plan.totalMonths).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp)),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                drawStopIndicator = {}
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${card.monthsElapsed} of ${plan.totalMonths} months",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    statusLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionPickerSheet(
    viewModel: PrepaidExpensesViewModel,
    onDismiss: () -> Unit,
    onCreateNew: () -> Unit,
    onSelect: (TransactionEntity) -> Unit
) {
    val query by viewModel.searchQuery.collectAsState()
    val candidates by viewModel.candidates.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimensions.Padding.content)) {
            Text("Add prepaid expense", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Spacing.sm))
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::updateSearchQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search existing expenses") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
            Spacer(Modifier.height(Spacing.sm))
            TextButton(onClick = onCreateNew) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(Spacing.xs))
                Text("Create new prepaid expense")
            }
            HorizontalDivider()
            if (candidates.isEmpty()) {
                Text(
                    "No matching expenses",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.lg)
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(candidates, key = { it.id }) { txn ->
                        ListItem(
                            modifier = Modifier.clickable { onSelect(txn) },
                            headlineContent = { Text(txn.merchantName) },
                            supportingContent = { Text("${txn.dateTime.toLocalDate()} · ${txn.category}") },
                            trailingContent = {
                                Text("${CurrencyFormatter.getCurrencySymbol(txn.currency)}${txn.amount}")
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}
