package com.spendly.tracker.presentation.add

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.spendly.tracker.data.database.entity.BudgetImpactType
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.data.database.entity.TransferKind
import com.spendly.tracker.domain.model.displayName
import com.spendly.tracker.domain.model.getAccountType
import com.spendly.tracker.presentation.accounts.AccountType
import com.spendly.tracker.presentation.categories.CategoryEditDialog
import com.spendly.tracker.ui.components.SplitEditor
import com.spendly.tracker.ui.components.BrandIcon
import com.spendly.tracker.ui.theme.*
import com.spendly.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.util.Locale

// Reusable filled text field colors with no indicator
@Composable
private fun filledFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    disabledIndicatorColor = Color.Transparent,
    disabledLabelColor = MaterialTheme.colorScheme.primary,
    disabledTextColor = MaterialTheme.colorScheme.onSurface,
    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
)

private val topShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
private val bottomShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
private val middleShape = RoundedCornerShape(4.dp)
private val fullShape = RoundedCornerShape(16.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionTabContent(
    viewModel: AddViewModel,
    onSave: () -> Unit
) {
    val uiState by viewModel.transactionUiState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val filteredAccounts by viewModel.filteredAccounts.collectAsState()
    val applyToAllFromMerchant by viewModel.applyToAllFromMerchant.collectAsState()
    val tagSuggestions by viewModel.tagSuggestions.collectAsState()
    val merchantAutocompleteSuggestions by viewModel.merchantAutocompleteSuggestions.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showCreateCategoryDialog by remember { mutableStateOf(false) }
    var showAccountMenu by remember { mutableStateOf(false) }
    var showCurrencyMenu by remember { mutableStateOf(false) }
    var merchantDropdownExpanded by remember { mutableStateOf(false) }
    var tagInput by remember { mutableStateOf("") }
    var accountQuery by remember { mutableStateOf("") }

    // Sync accountQuery when selected account is set or cleared externally
    val selectedAccount = uiState.selectedAccount
    LaunchedEffect(selectedAccount) {
        accountQuery = selectedAccount
            ?.let { "${it.bankName} ••${it.accountLast4}" }
            ?: ""
        viewModel.updateAccountSearchQuery("")
    }
    // Reset search query when filter context changes and no account is selected
    val paymentChannel = uiState.paymentChannel
    val transactionType = uiState.transactionType
    LaunchedEffect(paymentChannel, transactionType) {
        if (uiState.selectedAccount == null) {
            accountQuery = ""
            viewModel.updateAccountSearchQuery("")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimensions.Padding.content, vertical = Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            if (uiState.fromUnrecognizedSms) {
                uiState.sourceSmsBody?.let { smsBody ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(
                            text = "Original SMS",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        uiState.sourceSmsSender?.let { sender ->
                            Text(
                                text = sender,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = smsBody,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Amount and merchant are guessed — please verify before saving.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                }
            }

            // ── Section: Basics ──
            Text(
                text = "Basics",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = Spacing.xs),
            )

            // ── Amount ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.Top
            ) {
                ExposedDropdownMenuBox(
                    expanded = showCurrencyMenu,
                    onExpandedChange = { showCurrencyMenu = it },
                    modifier = Modifier.width(130.dp)
                ) {
                    TextField(
                        value = "${CurrencyFormatter.getCurrencySymbol(uiState.currency)} ${uiState.currency}",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCurrencyMenu) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        singleLine = true,
                        shape = fullShape,
                        colors = filledFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = showCurrencyMenu,
                        onDismissRequest = { showCurrencyMenu = false }
                    ) {
                        CurrencyFormatter.getSupportedCurrencies().forEach { currency ->
                            DropdownMenuItem(
                                text = { Text("${CurrencyFormatter.getCurrencySymbol(currency)} $currency") },
                                onClick = {
                                    viewModel.updateTransactionCurrency(currency)
                                    showCurrencyMenu = false
                                }
                            )
                        }
                    }
                }

                TextField(
                    value = uiState.amount,
                    onValueChange = viewModel::updateTransactionAmount,
                    label = { Text("Amount *", fontWeight = FontWeight.SemiBold) },
                    textStyle = MaterialTheme.typography.headlineSmall,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = uiState.amountError != null,
                    supportingText = uiState.amountError?.let { { Text(it) } },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = fullShape,
                    colors = filledFieldColors()
                )
            }

            // ── Merchant + Notes (connected cards) ──
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(1.5.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = merchantDropdownExpanded && merchantAutocompleteSuggestions.isNotEmpty(),
                    onExpandedChange = { merchantDropdownExpanded = it },
                ) {
                    TextField(
                        value = uiState.merchant,
                        onValueChange = {
                            viewModel.updateTransactionMerchant(it)
                            merchantDropdownExpanded = it.isNotBlank()
                        },
                        label = { Text("Merchant", fontWeight = FontWeight.SemiBold) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryEditable),
                        shape = topShape,
                        leadingIcon = {
                            BrandIcon(
                                merchantName = uiState.merchant,
                                size = 24.dp,
                                showBackground = false
                            )
                        },
                        trailingIcon = {
                            if (merchantAutocompleteSuggestions.isNotEmpty()) {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = merchantDropdownExpanded)
                            }
                        },
                        isError = uiState.merchantError != null,
                        supportingText = uiState.merchantError?.let { { Text(it) } },
                        colors = filledFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = merchantDropdownExpanded && merchantAutocompleteSuggestions.isNotEmpty(),
                        onDismissRequest = { merchantDropdownExpanded = false },
                    ) {
                        merchantAutocompleteSuggestions.forEach { suggestion ->
                            DropdownMenuItem(
                                text = { Text(suggestion) },
                                onClick = {
                                    viewModel.updateTransactionMerchant(suggestion)
                                    merchantDropdownExpanded = false
                                },
                                leadingIcon = {
                                    BrandIcon(
                                        merchantName = suggestion,
                                        size = 20.dp,
                                        showBackground = false
                                    )
                                }
                            )
                        }
                    }
                }

                TextField(
                    value = uiState.notes,
                    onValueChange = viewModel::updateTransactionNotes,
                    label = { Text("Notes (Optional)", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = bottomShape,
                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                    colors = filledFieldColors()
                )
            }

            // ── Transaction Type chips ──
            val topLevelTypes = listOf(
                TransactionType.INCOME,
                TransactionType.EXPENSE,
                TransactionType.TRANSFER,
                TransactionType.INVESTMENT
            )
            val isExpenseSelected = uiState.transactionType == TransactionType.EXPENSE ||
                    uiState.transactionType == TransactionType.CREDIT
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                topLevelTypes.forEach { type ->
                    val selected = if (type == TransactionType.EXPENSE) isExpenseSelected
                                   else uiState.transactionType == type
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.updateTransactionType(type) },
                        label = {
                            Text(type.name.lowercase(Locale.getDefault())
                                .replaceFirstChar { it.titlecase(Locale.getDefault()) })
                        },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(Dimensions.Icon.small)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(0.7f),
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderWidth = 0.dp,
                            selected = selected,
                            enabled = true
                        )
                    )
                }
            }

            // ── Payment Channel sub-chips (Expense only) ──
            if (isExpenseSelected) {
                val channels = listOf(
                    PaymentChannel.ACCOUNT to "Account",
                    PaymentChannel.CASH to "Cash",
                    PaymentChannel.CREDIT_CARD to "Credit Card"
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    channels.forEach { (channel, label) ->
                        val channelSelected = uiState.paymentChannel == channel
                        FilterChip(
                            selected = channelSelected,
                            onClick = { viewModel.updatePaymentChannel(channel) },
                            label = { Text(label) },
                            leadingIcon = if (channelSelected) {
                                {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(Dimensions.Icon.small)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(0.7f),
                                labelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderWidth = 0.dp,
                                selected = channelSelected,
                                enabled = true
                            )
                        )
                    }
                }
            }

            // ── Transfer kind (Transfer only) ──
            if (uiState.transactionType == TransactionType.TRANSFER) {
                val transferKinds = listOf(
                    TransferKind.SELF_TRANSFER to "Self transfer",
                    TransferKind.OTHERS_TRANSFER to "To others",
                    TransferKind.CC_BILL_PAYMENT to "Credit card bill",
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    transferKinds.forEach { (kind, label) ->
                        val selected = uiState.transferKind == kind
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.updateTransferKind(kind) },
                            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = if (selected) {
                                {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(Dimensions.Icon.small)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(0.7f),
                                labelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderWidth = 0.dp,
                                selected = selected,
                                enabled = true
                            )
                        )
                    }
                }
            }

            // ── Date + Time row ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Date button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                        )
                        .padding(Spacing.sm)
                        .clickable(
                            onClick = { showDatePicker = true },
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.size(Spacing.sm))
                        Column {
                            Text(
                                text = uiState.date.format(DateTimeFormatter.ofPattern("yyyy")),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = uiState.date.format(DateTimeFormatter.ofPattern("dd MMMM")),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Time display
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.sm)
                        .clickable { showTimePicker = true },
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        val hour = if (uiState.date.hour % 12 == 0) 12 else uiState.date.hour % 12
                        val minute = uiState.date.minute
                        val amPm = if (uiState.date.hour < 12) "AM" else "PM"

                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(0.2f),
                                    shape = MaterialTheme.shapes.small
                                )
                        ) {
                            Text(
                                text = String.format("%02d", hour),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(5.dp)
                            )
                        }
                        Text(
                            text = ":",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.small
                                )
                        ) {
                            Text(
                                text = String.format("%02d", minute),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(5.dp)
                            )
                        }
                        Box(modifier = Modifier.padding(4.dp)) {
                            Text(
                                text = amPm,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ── Section: Details ──
            Spacer(modifier = Modifier.height(Spacing.xs))
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = "Details",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 2.dp, end = 2.dp),
            )

            // ── Account + Category (connected cards) ──
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(1.5.dp)
            ) {
                // Account field with autocomplete — hidden for cash transactions
                if (uiState.paymentChannel != PaymentChannel.CASH) {
                val accountInteractionSource = remember { MutableInteractionSource() }
                ExposedDropdownMenuBox(
                    expanded = showAccountMenu,
                    onExpandedChange = { showAccountMenu = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = accountQuery,
                        onValueChange = { newValue ->
                            // If user edits while an account is selected, clear the selection
                            val currentSelected = uiState.selectedAccount
                            if (currentSelected != null &&
                                newValue != "${currentSelected.bankName} ••${currentSelected.accountLast4}"
                            ) {
                                viewModel.updateSelectedAccount(null)
                            }
                            accountQuery = newValue
                            viewModel.updateAccountSearchQuery(newValue)
                            if (!showAccountMenu) showAccountMenu = true
                        },
                        label = { Text("Account (Optional)", fontWeight = FontWeight.SemiBold) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryEditable),
                        shape = topShape,
                        leadingIcon = {
                            Icon(
                                when (uiState.selectedAccount?.getAccountType()) {
                                    AccountType.CASH -> Icons.Default.Money
                                    AccountType.CREDIT -> Icons.Default.CreditCard
                                    AccountType.SAVINGS, AccountType.CURRENT -> Icons.Default.AccountBalance
                                    null -> Icons.Default.AccountBalance
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (uiState.selectedAccount != null || accountQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        viewModel.updateSelectedAccount(null)
                                        accountQuery = ""
                                        viewModel.updateAccountSearchQuery("")
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Icon(
                                    Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        colors = filledFieldColors()
                    )

                    ExposedDropdownMenu(
                        expanded = showAccountMenu,
                        onDismissRequest = { showAccountMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("No account (Manual Entry)")
                                    Text(
                                        "Won't affect account balance",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                viewModel.updateSelectedAccount(null)
                                accountQuery = ""
                                viewModel.updateAccountSearchQuery("")
                                showAccountMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) }
                        )
                        if (filteredAccounts.isNotEmpty()) {
                            HorizontalDivider()
                            val groupedAccounts = filteredAccounts.groupBy { it.getAccountType() }
                            groupedAccounts.forEach { (accountType, accountList) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = accountType.displayName(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    onClick = {},
                                    enabled = false
                                )
                                accountList.forEach { account ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("${account.bankName} ••${account.accountLast4}")
                                                Text(
                                                    CurrencyFormatter.formatCurrency(account.balance, account.currency),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        },
                                        onClick = {
                                            viewModel.updateSelectedAccount(account)
                                            accountQuery = "${account.bankName} ••${account.accountLast4}"
                                            viewModel.updateAccountSearchQuery("")
                                            showAccountMenu = false
                                        },
                                        leadingIcon = {
                                            Icon(
                                                when (accountType) {
                                                    AccountType.CASH -> Icons.Default.Money
                                                    AccountType.CREDIT -> Icons.Default.CreditCard
                                                    else -> Icons.Default.AccountBalance
                                                },
                                                contentDescription = null
                                            )
                                        },
                                        trailingIcon = {
                                            if (uiState.selectedAccount?.id == account.id) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    "Selected",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        } else if (accountQuery.isNotEmpty()) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "No matching accounts",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {},
                                enabled = false
                            )
                        }
                    }
                }
                } // end if (paymentChannel != CASH)

                // Category field
                ExposedDropdownMenuBox(
                    expanded = showCategoryMenu,
                    onExpandedChange = { showCategoryMenu = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = uiState.category,
                        onValueChange = {},
                        shape = if (uiState.paymentChannel == PaymentChannel.CASH) topShape else bottomShape,
                        label = { Text("Category", fontWeight = FontWeight.SemiBold) },
                        readOnly = true,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        leadingIcon = {
                            Icon(Icons.Default.Category, contentDescription = null)
                        },
                        trailingIcon = {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
                        },
                        isError = uiState.categoryError != null,
                        supportingText = uiState.categoryError?.let { { Text(it) } },
                        colors = filledFieldColors()
                    )

                    ExposedDropdownMenu(
                        expanded = showCategoryMenu,
                        onDismissRequest = { showCategoryMenu = false }
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    viewModel.updateTransactionCategory(category.name)
                                    showCategoryMenu = false
                                }
                            )
                        }
                        if (categories.isNotEmpty()) {
                            HorizontalDivider()
                        }
                        DropdownMenuItem(
                            text = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "Create new category",
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            onClick = {
                                showCategoryMenu = false
                                showCreateCategoryDialog = true
                            }
                        )
                    }
                }

                // Apply to all checkbox — only shown when a merchant name is entered
                if (uiState.merchant.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleApplyToAllFromMerchant() }
                            .padding(top = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = applyToAllFromMerchant,
                            onCheckedChange = { viewModel.toggleApplyToAllFromMerchant() }
                        )
                        Text(
                            text = "Apply category to all from ${uiState.merchant}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // ── Split Transaction (EXPENSE / CREDIT only) ──
            val canSplit = uiState.transactionType != TransactionType.TRANSFER &&
                    uiState.transactionType != TransactionType.INCOME &&
                    uiState.transactionType != TransactionType.INVESTMENT
            if (canSplit) {
                if (uiState.isSplitEnabled) {
                    SplitEditor(
                        totalAmount = uiState.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                        currency = uiState.currency,
                        splits = uiState.splits,
                        availableCategories = categories.map { it.name },
                        onSplitsChanged = viewModel::updateSplits,
                        onRemoveSplits = viewModel::toggleSplit,
                        onCreateCategory = { name, color, isIncome, icon ->
                            viewModel.createAndSelectTransactionCategory(name, color, isIncome, icon)
                        }
                    )
                } else {
                    OutlinedButton(
                        onClick = viewModel::toggleSplit,
                        enabled = uiState.amount.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } ?: false,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.CallSplit,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.Icon.small)
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("Split across categories")
                    }
                }
            }

            // ── Tags ──
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = "Tags (Optional)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (uiState.tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        uiState.tags.forEach { tag ->
                            FilterChip(
                                selected = true,
                                onClick = { viewModel.removeTag(tag) },
                                label = { Text(tag) },
                                leadingIcon = null,
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove tag",
                                        modifier = Modifier.size(Dimensions.Icon.small)
                                    )
                                }
                            )
                        }
                    }
                }
                if (tagSuggestions.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        tagSuggestions.take(10).forEach { suggestion ->
                            SuggestionChip(
                                onClick = {
                                    viewModel.addTag(suggestion)
                                    tagInput = ""
                                    viewModel.updateTagQuery("")
                                },
                                label = { Text(suggestion, style = MaterialTheme.typography.bodySmall) },
                                border = null,
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = {
                            tagInput = it
                            viewModel.updateTagQuery(it)
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Add tag…", style = MaterialTheme.typography.bodyMedium) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = {
                                if (tagInput.isNotBlank()) {
                                    viewModel.addTag(tagInput)
                                    tagInput = ""
                                    viewModel.updateTagQuery("")
                                }
                            }
                        )
                    )
                    TextButton(
                        onClick = {
                            if (tagInput.isNotBlank()) {
                                viewModel.addTag(tagInput)
                                tagInput = ""
                                viewModel.updateTagQuery("")
                            }
                        }
                    ) {
                        Text("Add")
                    }
                }
            }

            // ── Budget Impact (INCOME only) ──
            if (uiState.transactionType == TransactionType.INCOME) {
                val activeBudgetCategories by viewModel.activeBudgetCategories.collectAsState()
                AddBudgetImpactSection(
                    budgetImpactType = uiState.budgetImpactType,
                    budgetCategory = uiState.budgetCategory,
                    activeBudgetCategories = activeBudgetCategories,
                    onImpactTypeChange = viewModel::updateBudgetImpactType,
                    onCategoryChange = viewModel::updateBudgetCategory
                )
            }

            // ── Receipt ──
            ReceiptPickerSection(
                receiptUris = uiState.receiptUris,
                onReceiptAdded = { uri -> viewModel.addReceiptUri(uri) },
                onReceiptRemoved = { index -> viewModel.removeReceiptUri(index) },
                onCreateCameraUri = { viewModel.createCameraUri() }
            )

            // Bottom padding for save button overlay (button height + nav bar inset)
            val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            Spacer(modifier = Modifier.height(56.dp + navBarBottom))
        }

        // ── Sticky Save Button ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Button(
                onClick = { viewModel.saveTransaction(onSuccess = onSave) },
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = Dimensions.Padding.content)
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Dimensions.Icon.small),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Done, contentDescription = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Save", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.date
                .toLocalDate()
                .atStartOfDay()
                .toInstant(java.time.ZoneOffset.UTC)
                .toEpochMilli()
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            viewModel.updateTransactionDate(millis)
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Time Picker Dialog
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = uiState.date.hour,
            initialMinute = uiState.date.minute
        )

        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateTransactionTime(timePickerState.hour, timePickerState.minute)
                        showTimePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            }
        )
    }

    // Create Category Dialog
    if (showCreateCategoryDialog) {
        CategoryEditDialog(
            category = null,
            onDismiss = { showCreateCategoryDialog = false },
            onSave = { name, color, isIncome, icon ->
                viewModel.createAndSelectTransactionCategory(name, color, isIncome, icon)
                showCreateCategoryDialog = false
            }
        )
    }
}

@Composable
fun ReceiptPickerSection(
    receiptUris: List<android.net.Uri>,
    onReceiptAdded: (android.net.Uri) -> Unit,
    onReceiptRemoved: (Int) -> Unit,
    onCreateCameraUri: () -> android.net.Uri,
    showOptionalCaption: Boolean = true,
) {
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { onReceiptAdded(it) } }

    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success -> if (success) cameraUri?.let { onReceiptAdded(it) } }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        if (showOptionalCaption) {
            Text(
                text = "Receipt (Optional)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (receiptUris.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                itemsIndexed(receiptUris) { index, uri ->
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(MaterialTheme.shapes.medium)
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "Receipt ${index + 1}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        FilledIconButton(
                            onClick = { onReceiptRemoved(index) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(24.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove receipt",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            OutlinedButton(
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small))
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text("Gallery")
            }
            OutlinedButton(
                onClick = {
                    val uri = onCreateCameraUri()
                    cameraUri = uri
                    cameraLauncher.launch(uri)
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small))
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text("Camera")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBudgetImpactSection(
    budgetImpactType: BudgetImpactType?,
    budgetCategory: String?,
    activeBudgetCategories: List<String>,
    onImpactTypeChange: (BudgetImpactType?) -> Unit,
    onCategoryChange: (String?) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Text(
            text = "Budget impact",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = budgetImpactType == null,
                onClick = { onImpactTypeChange(null) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                label = { Text("None", style = MaterialTheme.typography.labelSmall) }
            )
            SegmentedButton(
                selected = budgetImpactType == BudgetImpactType.DEDUCT_SPENT,
                onClick = { onImpactTypeChange(BudgetImpactType.DEDUCT_SPENT) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                label = { Text("Refund", style = MaterialTheme.typography.labelSmall) }
            )
            SegmentedButton(
                selected = budgetImpactType == BudgetImpactType.ADD_TO_LIMIT,
                onClick = { onImpactTypeChange(BudgetImpactType.ADD_TO_LIMIT) },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                label = { Text("Extra budget", style = MaterialTheme.typography.labelSmall) }
            )
        }

        if (budgetImpactType != null) {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = budgetCategory ?: "Select category",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Budget category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    if (activeBudgetCategories.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No budget categories found") },
                            onClick = { expanded = false },
                            enabled = false
                        )
                    } else {
                        activeBudgetCategories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = {
                                    onCategoryChange(category)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
