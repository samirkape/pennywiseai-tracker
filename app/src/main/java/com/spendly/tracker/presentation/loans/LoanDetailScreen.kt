package com.spendly.tracker.presentation.loans

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendly.tracker.data.database.entity.LoanDirection
import com.spendly.tracker.data.database.entity.LoanStatus
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.ui.components.CustomTitleTopAppBar
import com.spendly.tracker.ui.components.cards.PennyWiseCardV2
import com.spendly.tracker.ui.effects.overScrollVertical
import com.spendly.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.spendly.tracker.ui.theme.*
import com.spendly.tracker.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanDetailScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToTransactionDetail: (Long) -> Unit = {},
    viewModel: LoanDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }

    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) onNavigateBack()
    }

    val loan = uiState.loan

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = loan?.personName ?: "Loan",
                hasBackButton = true,
                hasActionButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actionContent = {
                    if (loan != null) {
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            if (loan.status == LoanStatus.ACTIVE) {
                                DropdownMenuItem(
                                    text = { Text("Set expected return") },
                                    onClick = { showMenu = false; viewModel.showEditAmountDialog() },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Settle") },
                                    onClick = { showMenu = false; viewModel.showSettleDialog() },
                                    leadingIcon = { Icon(Icons.Default.CheckCircle, null) }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Reopen") },
                                    onClick = { showMenu = false; viewModel.reopenLoan() },
                                    leadingIcon = { Icon(Icons.Default.Refresh, null) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = { showMenu = false; viewModel.showDeleteDialog() },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                                }
                            )
                        }
                    }
                },
                hazeState = hazeState
            )
        },
        floatingActionButton = {
            if (loan?.status == LoanStatus.ACTIVE) {
                FloatingActionButton(
                    onClick = { viewModel.showRecordPayment() },
                    containerColor = if (isSystemInDarkTheme()) loan_dark else loan_light
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Record Payment")
                }
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading || loan == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val isDark = isSystemInDarkTheme()
        val directionColor = if (loan.direction == LoanDirection.LENT) {
            if (isDark) loan_dark else loan_light
        } else {
            if (isDark) income_dark else income_light
        }
        val progressColor = if (isDark) income_dark else income_light
        val progress = if (loan.originalAmount > BigDecimal.ZERO) {
            (BigDecimal.ONE - loan.remainingAmount.divide(
                loan.originalAmount, 2, java.math.RoundingMode.HALF_UP
            )).toFloat().coerceIn(0f, 1f)
        } else 0f
        val totalRepaid = loan.originalAmount - loan.remainingAmount

        val lazyListState = rememberLazyListState()

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .background(MaterialTheme.colorScheme.background)
                .overScrollVertical(),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            flingBehavior = rememberOverscrollFlingBehavior { lazyListState }
        ) {
            // Hero card
            item {
                LoanHeroCard(
                    personName = loan.personName,
                    direction = loan.direction,
                    status = loan.status,
                    remainingAmount = loan.remainingAmount,
                    originalAmount = loan.originalAmount,
                    totalRepaid = totalRepaid,
                    currency = loan.currency,
                    progress = progress,
                    directionColor = directionColor,
                    progressColor = progressColor
                )
            }

            // History section header
            item {
                Text(
                    "History",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs)
                )
            }

            if (uiState.linkedTransactions.isEmpty()) {
                item {
                    EmptyHistoryState(status = loan.status)
                }
            } else {
                items(uiState.linkedTransactions, key = { it.id }) { txn ->
                    val isOriginal = if (loan.direction == LoanDirection.LENT)
                        txn.transactionType == TransactionType.EXPENSE
                    else txn.transactionType == TransactionType.INCOME
                    LoanTransactionItem(
                        transaction = txn,
                        isOriginal = isOriginal,
                        loanDirection = loan.direction,
                        onClick = { onNavigateToTransactionDetail(txn.id) },
                        onUnlink = { viewModel.unlinkTransaction(txn.id) }
                    )
                }
            }
        }
    }

    // Settle dialog
    if (uiState.showSettleDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideSettleDialog() },
            title = { Text("Settle Loan") },
            text = { Text("Mark this loan as settled? Any remaining balance will be forgiven.") },
            confirmButton = {
                TextButton(onClick = { viewModel.settleLoan() }) { Text("Settle") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideSettleDialog() }) { Text("Cancel") }
            }
        )
    }

    // Edit amount dialog
    if (uiState.showEditAmountDialog && loan != null) {
        var editAmount by remember { mutableStateOf(loan.originalAmount.toPlainString()) }
        AlertDialog(
            onDismissRequest = { viewModel.hideEditAmountDialog() },
            title = { Text("Expected Return") },
            text = {
                TextField(
                    value = editAmount,
                    onValueChange = { value ->
                        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
                            editAmount = value
                        }
                    },
                    label = { Text("Amount expected back") },
                    prefix = { Text(CurrencyFormatter.getCurrencySymbol(loan.currency)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        editAmount.toBigDecimalOrNull()?.let { viewModel.updateLoanAmount(it) }
                    },
                    enabled = editAmount.toBigDecimalOrNull()
                        ?.let { it > java.math.BigDecimal.ZERO } == true
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideEditAmountDialog() }) { Text("Cancel") }
            }
        )
    }

    // Delete dialog
    if (uiState.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteDialog() },
            title = { Text("Delete Loan") },
            text = { Text("Delete this loan? Linked transactions will be unlinked but not deleted.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteLoan() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteDialog() }) { Text("Cancel") }
            }
        )
    }

    // Record Payment bottom sheet
    if (uiState.showRecordPaymentSheet && loan != null) {
        RecordPaymentBottomSheet(
            personName = loan.personName,
            remainingAmount = loan.remainingAmount,
            currency = loan.currency,
            recentUnlinkedTransactions = uiState.recentUnlinkedTransactions,
            onDismiss = { viewModel.hideRecordPayment() },
            onLinkTransaction = { viewModel.linkTransactionAsRepayment(it) },
            onManualPayment = { viewModel.recordManualRepayment(it) }
        )
    }
}

@Composable
private fun LoanHeroCard(
    personName: String,
    direction: LoanDirection,
    status: LoanStatus,
    remainingAmount: BigDecimal,
    originalAmount: BigDecimal,
    totalRepaid: BigDecimal,
    currency: String,
    progress: Float,
    directionColor: Color,
    progressColor: Color,
    modifier: Modifier = Modifier
) {
    val isSettled = status == LoanStatus.SETTLED
    val heroBackground = if (isSettled)
        MaterialTheme.colorScheme.surfaceVariant
    else
        directionColor.copy(alpha = 0.10f)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = heroBackground,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Avatar + name + pills row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(directionColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        personName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = directionColor
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        personName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Direction pill
                        Box(
                            modifier = Modifier
                                .background(
                                    color = directionColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(50)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (direction == LoanDirection.LENT) "Lent" else "Borrowed",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = directionColor
                            )
                        }
                        // Settled badge
                        if (isSettled) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(50)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "Settled",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }

            // Dominant amount
            Text(
                text = if (isSettled) "Fully settled"
                else CurrencyFormatter.formatCurrency(remainingAmount, currency),
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = if (isSettled) MaterialTheme.colorScheme.onSurfaceVariant else directionColor
            )
            Text(
                text = if (isSettled)
                    "of ${CurrencyFormatter.formatCurrency(originalAmount, currency)}"
                else
                    "remaining of ${CurrencyFormatter.formatCurrency(originalAmount, currency)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Progress bar + repaid label — active loans only
            if (!isSettled && originalAmount > BigDecimal.ZERO) {
                val barShape = RoundedCornerShape(50)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(barShape)
                        .background(progressColor.copy(alpha = 0.18f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = progress)
                            .fillMaxHeight()
                            .clip(barShape)
                            .background(progressColor)
                    )
                }
                val repaidLabel = if (totalRepaid > BigDecimal.ZERO)
                    "${CurrencyFormatter.formatCurrency(totalRepaid, currency)} repaid · ${(progress * 100).toInt()}% done"
                else
                    "No payments recorded yet"
                Text(
                    repaidLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyHistoryState(
    status: LoanStatus,
    modifier: Modifier = Modifier
) {
    val message = if (status == LoanStatus.ACTIVE)
        "No payments recorded yet — tap + to add one."
    else
        "No transaction history linked to this loan."

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xl),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LoanTransactionItem(
    transaction: TransactionEntity,
    isOriginal: Boolean,
    loanDirection: LoanDirection,
    onClick: () -> Unit,
    onUnlink: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val color = when (transaction.transactionType) {
        TransactionType.EXPENSE -> if (isDark) expense_dark else expense_light
        TransactionType.INCOME -> if (isDark) income_dark else income_light
        else -> MaterialTheme.colorScheme.onSurface
    }
    val sign = when (transaction.transactionType) {
        TransactionType.EXPENSE -> "-"
        TransactionType.INCOME -> "+"
        else -> ""
    }

    PennyWiseCardV2(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text(
                        transaction.merchantName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (isOriginal) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(50)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "Original",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Text(
                    transaction.dateTime.format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = "$sign${CurrencyFormatter.formatCurrency(transaction.amount, transaction.currency)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = color
                )
                IconButton(
                    onClick = onUnlink,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.LinkOff,
                        contentDescription = "Unlink transaction",
                        modifier = Modifier.size(Dimensions.Icon.small),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordPaymentBottomSheet(
    personName: String,
    remainingAmount: BigDecimal,
    currency: String,
    recentUnlinkedTransactions: List<TransactionEntity>,
    onDismiss: () -> Unit,
    onLinkTransaction: (Long) -> Unit,
    onManualPayment: (BigDecimal) -> Unit
) {
    var manualAmount by remember { mutableStateOf("") }
    var useManualEntry by remember { mutableStateOf(recentUnlinkedTransactions.isEmpty()) }
    val isDark = isSystemInDarkTheme()
    val loanColor = if (isDark) loan_dark else loan_light
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.Padding.content)
                .padding(bottom = Spacing.xl)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                "Record payment from $personName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "${CurrencyFormatter.formatCurrency(remainingAmount, currency)} remaining",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Segmented mode selector — only show when there are transactions to link
            if (recentUnlinkedTransactions.isNotEmpty()) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !useManualEntry,
                        onClick = { useManualEntry = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        label = { Text("Link existing", style = MaterialTheme.typography.labelSmall) }
                    )
                    SegmentedButton(
                        selected = useManualEntry,
                        onClick = { useManualEntry = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        label = { Text("Enter manually", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            if (!useManualEntry && recentUnlinkedTransactions.isNotEmpty()) {
                recentUnlinkedTransactions.take(5).forEach { txn ->
                    PennyWiseCardV2(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onLinkTransaction(txn.id) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(txn.merchantName, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    txn.dateTime.format(DateTimeFormatter.ofPattern("d MMM")),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                CurrencyFormatter.formatCurrency(txn.amount, txn.currency),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = manualAmount,
                        onValueChange = { value ->
                            if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
                                manualAmount = value
                            }
                        },
                        label = { Text("Amount") },
                        prefix = { Text(CurrencyFormatter.getCurrencySymbol(currency)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(16.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                    Button(
                        onClick = {
                            manualAmount.toBigDecimalOrNull()?.let { onManualPayment(it) }
                        },
                        enabled = manualAmount.toBigDecimalOrNull()
                            ?.let { it > BigDecimal.ZERO } == true,
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                        colors = ButtonDefaults.buttonColors(containerColor = loanColor)
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}
