package com.spendly.tracker.presentation.prepaid

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.spendly.tracker.data.database.entity.PrepaidExpenseStatus
import com.spendly.tracker.ui.components.SpendlyScaffold
import com.spendly.tracker.ui.components.cards.SpendlyCardV2
import com.spendly.tracker.ui.theme.Dimensions
import com.spendly.tracker.ui.theme.Spacing
import com.spendly.tracker.utils.CurrencyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrepaidExpensesScreen(
    viewModel: PrepaidExpensesViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    SpendlyScaffold(
        title = "Prepaid Expenses",
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
                        "Add one from the \"Prepaid\" tab when creating a transaction",
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

    SpendlyCardV2(modifier = Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(plan.merchantName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${CurrencyFormatter.getCurrencySymbol(plan.currency)}${plan.totalAmount} total · " +
                        "${CurrencyFormatter.getCurrencySymbol(plan.currency)}$monthlyAmount/mo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${card.monthsElapsed} of ${plan.totalMonths} months · ${plan.status.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                LinearProgressIndicator(
                    progress = { (card.monthsElapsed.toFloat() / plan.totalMonths).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs)
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (plan.status == PrepaidExpenseStatus.ACTIVE) {
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
    }
}
