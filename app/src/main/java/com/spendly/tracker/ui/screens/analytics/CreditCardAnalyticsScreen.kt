package com.spendly.tracker.ui.screens.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.ui.screens.analytics.CreditCardAnalyticsViewModel.ViewMode
import com.spendly.tracker.ui.theme.Dimensions
import com.spendly.tracker.ui.theme.Spacing
import com.spendly.tracker.utils.CurrencyFormatter
import com.spendly.tracker.utils.DateRangeUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class PickerTarget { GLOBAL, CARD }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditCardAnalyticsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTransaction: (Long) -> Unit = {},
    viewModel: CreditCardAnalyticsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pickerTarget by rememberSaveable { mutableStateOf<PickerTarget?>(null) }

    val activeRange: Pair<LocalDate, LocalDate> = remember(
        uiState.view, uiState.payPeriodStart, uiState.payPeriodEnd,
        uiState.billingCycleStart, uiState.billingCycleEnd,
    ) {
        val billingStart = uiState.billingCycleStart
        val billingEnd = uiState.billingCycleEnd
        if (uiState.view == ViewMode.BILLING_CYCLE && billingStart != null && billingEnd != null) {
            billingStart to billingEnd
        } else {
            uiState.payPeriodStart to uiState.payPeriodEnd
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Credit Card",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = paddingValues.calculateTopPadding() + Spacing.sm,
                bottom = Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                ViewToggle(selected = uiState.view, onSelect = viewModel::setView)
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DateRangeChip(start = activeRange.first, end = activeRange.second)
                    IconButton(
                        onClick = {
                            pickerTarget = if (uiState.selectedCardKey != null) PickerTarget.CARD else PickerTarget.GLOBAL
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit billing cycle date",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (uiState.view == ViewMode.BILLING_CYCLE && uiState.effectiveBillingCycleDay == 0) {
                item {
                    BillingCycleSetupPrompt(
                        forCard = uiState.selectedCardKey?.let { key ->
                            uiState.availableCards.find { it.key == key }?.displayName
                        },
                        onClick = {
                            pickerTarget = if (uiState.selectedCardKey != null) PickerTarget.CARD else PickerTarget.GLOBAL
                        },
                    )
                }
            }

            if (uiState.availableCards.size > 1) {
                item {
                    CardSelectorRow(
                        cards = uiState.availableCards,
                        selectedKey = uiState.selectedCardKey,
                        perCardOverrides = uiState.perCardBillingCycleDays,
                        onSelect = viewModel::setSelectedCardKey,
                    )
                }
            }

            item {
                SpendingSummaryHeader(
                    total = CurrencyFormatter.formatCurrency(uiState.totalAmount, uiState.currency),
                    count = uiState.transactions.size,
                )
            }

            if (uiState.transactions.isEmpty() && !uiState.isLoading) {
                item { EmptyCreditCardState() }
            } else {
                items(uiState.transactions, key = { it.id }) { txn ->
                    CreditCardTransactionItem(
                        transaction = txn,
                        currency = uiState.currency,
                        onClick = { onNavigateToTransaction(txn.id) },
                    )
                }
            }
        }
    }

    when (pickerTarget) {
        PickerTarget.GLOBAL -> BillingCycleDayDialog(
            title = "Default billing cycle",
            subtitle = "Applies to all cards unless overridden individually.",
            currentDay = uiState.globalBillingCycleDay,
            onConfirm = { day ->
                viewModel.updateGlobalBillingCycleDay(day)
                pickerTarget = null
            },
            onDismiss = { pickerTarget = null },
        )
        PickerTarget.CARD -> {
            val cardKey = uiState.selectedCardKey
            val card = cardKey?.let { k -> uiState.availableCards.find { it.key == k } }
            if (card != null) {
                BillingCycleDayDialog(
                    title = card.displayName,
                    subtitle = "Override the default billing cycle day for this card only.",
                    currentDay = uiState.perCardBillingCycleDays[cardKey] ?: 0,
                    showClearOption = uiState.selectedCardHasOverride,
                    onConfirm = { day ->
                        viewModel.updateCardBillingCycleDay(cardKey, day)
                        pickerTarget = null
                    },
                    onClear = {
                        viewModel.clearCardBillingCycleOverride(cardKey)
                        pickerTarget = null
                    },
                    onDismiss = { pickerTarget = null },
                )
            }
        }
        null -> Unit
    }
}

@Composable
private fun ViewToggle(selected: ViewMode, onSelect: (ViewMode) -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ViewMode.entries.forEach { mode ->
                val isSelected = selected == mode
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.weight(1f).clickable { onSelect(mode) },
                ) {
                    Text(
                        text = if (mode == ViewMode.PAY_PERIOD) "Pay Period" else "Billing Cycle",
                        modifier = Modifier.padding(vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun DateRangeChip(start: LocalDate, end: LocalDate) {
    val label = remember(start, end) { DateRangeUtils.formatDateRange(start, end) }
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun CardSelectorRow(
    cards: List<CreditCardAnalyticsViewModel.CardOption>,
    selectedKey: String?,
    perCardOverrides: Map<String, Int>,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = selectedKey == null,
            onClick = { onSelect(null) },
            label = { Text("All") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )
        cards.forEach { card ->
            val isSelected = selectedKey == card.key
            val hasOverride = perCardOverrides.containsKey(card.key)
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(if (isSelected) null else card.key) },
                label = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(card.displayName)
                        if (hasOverride) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    text = "Custom",
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun SpendingSummaryHeader(total: String, count: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = total,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "$count transaction${if (count != 1) "s" else ""}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CreditCardTransactionItem(
    transaction: TransactionEntity,
    currency: String,
    onClick: () -> Unit,
) {
    val dateLabel = remember(transaction.dateTime) {
        transaction.dateTime.format(DateTimeFormatter.ofPattern("d MMM, h:mm a"))
    }
    val cardLabel = remember(transaction.bankName, transaction.accountNumber) {
        buildString {
            transaction.bankName?.let { append(it) }
            transaction.accountNumber?.takeLast(4)?.let { append(" ••$it") }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.CreditCard,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = transaction.merchantName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (cardLabel.isNotBlank()) "$cardLabel · $dateLabel" else dateLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = CurrencyFormatter.formatCurrency(transaction.amount, currency),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun BillingCycleSetupPrompt(forCard: String?, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Column {
                Text(
                    text = if (forCard != null) "Set billing cycle for $forCard" else "Set default billing cycle date",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Tap to configure when your card statement closes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyCreditCardState() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.CreditCard,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            Text(
                text = "No credit card transactions",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "for this period",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun BillingCycleDayDialog(
    title: String,
    subtitle: String,
    currentDay: Int,
    showClearOption: Boolean = false,
    onConfirm: (Int) -> Unit,
    onClear: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var input by rememberSaveable(currentDay) {
        mutableStateOf(if (currentDay > 0) currentDay.toString() else "")
    }
    val parsed = input.toIntOrNull()
    val isValid = parsed != null && parsed in 1..28

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { new -> if (new.length <= 2 && new.all { it.isDigit() }) input = new },
                    label = { Text("Day of month (1–28)") },
                    singleLine = true,
                    isError = input.isNotEmpty() && !isValid,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (isValid) onConfirm(parsed!!) }, enabled = isValid) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (showClearOption && onClear != null) {
                    TextButton(onClick = onClear) {
                        Text("Use default", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
