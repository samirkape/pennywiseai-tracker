package com.spendly.tracker.presentation.loans

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendly.tracker.data.database.entity.LoanDirection
import com.spendly.tracker.data.database.entity.LoanEntity
import com.spendly.tracker.data.database.entity.LoanStatus
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.ui.components.CustomTitleTopAppBar
import com.spendly.tracker.ui.components.cards.SpendlyCardV2
import com.spendly.tracker.ui.effects.overScrollVertical
import com.spendly.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.spendly.tracker.ui.theme.*
import com.spendly.tracker.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToLoanDetail: (Long) -> Unit = {},
    viewModel: LoansViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }

    if (uiState.showImportSheet) {
        ImportSheetHost(
            candidates = uiState.importCandidates,
            onDismiss = { viewModel.setImportSheetVisible(false) },
            onImport = { name, txs, dir -> viewModel.importSelected(name, txs, dir) }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = "Lent & Borrowed",
                hasBackButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                hazeState = hazeState
            )
        }
    ) { paddingValues ->
        val lazyListState = rememberLazyListState()

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (uiState.activeLoans.isEmpty() && uiState.settledLoans.isEmpty() && uiState.importCandidates.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.extraLarge),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))
                    Text(
                        "No lent/borrowed entries yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        "Mark a transaction as \"Lent\" or \"Borrowed\" to start tracking money between people",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            return@Scaffold
        }

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
                bottom = paddingValues.calculateBottomPadding() + Spacing.md
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            flingBehavior = rememberOverscrollFlingBehavior { lazyListState }
        ) {
            // Summary card
            item {
                LoanSummaryCard(
                    totalLent = uiState.totalLentRemaining,
                    totalBorrowed = uiState.totalBorrowedRemaining,
                    currency = uiState.summaryCurrency
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
            }

            if (uiState.importCandidates.isNotEmpty()) {
                item {
                    ImportPastLoansCard(
                        count = uiState.importCandidates.size,
                        onImportAll = { viewModel.importAllPastLoans() },
                        onChoose = { viewModel.setImportSheetVisible(true) }
                    )
                }
            }

            // Active loans
            if (uiState.activeLoans.isNotEmpty()) {
                item {
                    Text(
                        "Active",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.xs)
                    )
                }
                items(uiState.activeLoans, key = { it.id }) { loan ->
                    LoanListItem(loan = loan, onClick = { onNavigateToLoanDetail(loan.id) })
                }
            }

            // Settled loans toggle
            if (uiState.settledLoans.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    TextButton(onClick = { viewModel.toggleShowSettled() }) {
                        Icon(
                            if (uiState.showSettledLoans) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.Icon.small)
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("Settled (${uiState.settledLoans.size})")
                    }
                }
                if (uiState.showSettledLoans) {
                    items(uiState.settledLoans, key = { it.id }) { loan ->
                        LoanListItem(loan = loan, onClick = { onNavigateToLoanDetail(loan.id) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportSheetHost(
    candidates: List<TransactionEntity>,
    onDismiss: () -> Unit,
    onImport: (String, List<TransactionEntity>, LoanDirection?) -> Unit
) {
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var personName by remember { mutableStateOf("") }
    var nameEdited by remember { mutableStateOf(false) }
    var direction by remember { mutableStateOf<LoanDirection?>(null) }
    val dateFormat = remember { java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = Dimensions.Padding.content)) {
            Text("Select past loan transactions", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                "Pick transactions with one person. The first one decides if it was lent or borrowed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            OutlinedTextField(
                value = personName,
                onValueChange = { personName = it; nameEdited = true },
                label = { Text("Person") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(
                    selected = direction == null,
                    onClick = { direction = null },
                    label = { Text("Auto") }
                )
                FilterChip(
                    selected = direction == LoanDirection.LENT,
                    onClick = { direction = LoanDirection.LENT },
                    label = { Text("Lent") }
                )
                FilterChip(
                    selected = direction == LoanDirection.BORROWED,
                    onClick = { direction = LoanDirection.BORROWED },
                    label = { Text("Borrowed") }
                )
            }
            LazyColumn(modifier = Modifier.weight(1f, fill = false).heightIn(max = 360.dp)) {
                items(candidates, key = { it.id }) { tx ->
                    val checked = tx.id in selectedIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedIds = if (checked) selectedIds - tx.id else selectedIds + tx.id
                                if (!nameEdited) {
                                    personName = candidates.firstOrNull { it.id in selectedIds }?.merchantName.orEmpty()
                                }
                            }
                            .padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tx.merchantName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                tx.dateTime.format(dateFormat),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            (if (tx.transactionType == TransactionType.INCOME) "+" else "-") +
                                CurrencyFormatter.formatCurrency(tx.amount, tx.currency),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
            Button(
                onClick = { onImport(personName, candidates.filter { it.id in selectedIds }, direction) },
                enabled = selectedIds.isNotEmpty() && personName.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Add as loan") }
            Spacer(modifier = Modifier.height(Spacing.md))
        }
    }
}

@Composable
private fun ImportPastLoansCard(
    count: Int,
    onImportAll: () -> Unit,
    onChoose: () -> Unit
) {
    SpendlyCardV2(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                "$count past transaction${if (count == 1) "" else "s"} in the Loan category",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                "Add them here, grouped by person. Fully repaid ones go to Settled.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(onClick = onImportAll) { Text("Add all") }
                TextButton(onClick = onChoose) { Text("Choose") }
            }
        }
    }
}

@Composable
private fun LoanSummaryCard(
    totalLent: BigDecimal,
    totalBorrowed: BigDecimal,
    currency: String
) {
    val isDark = isSystemInDarkTheme()
    val lentColor = if (isDark) loan_dark else loan_light
    val borrowedColor = if (isDark) income_dark else income_light

    SpendlyCardV2(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Owed to you", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    CurrencyFormatter.formatCurrency(totalLent, currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = lentColor
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("You owe", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    CurrencyFormatter.formatCurrency(totalBorrowed, currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = borrowedColor
                )
            }
        }
    }
}

@Composable
fun LoanListItem(
    loan: LoanEntity,
    onClick: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val directionColor = if (loan.direction == LoanDirection.LENT) {
        if (isDark) loan_dark else loan_light
    } else {
        if (isDark) income_dark else income_light
    }
    val progressColor = if (isDark) income_dark else income_light
    val progress = if (loan.originalAmount > BigDecimal.ZERO) {
        (BigDecimal.ONE - loan.remainingAmount.divide(loan.originalAmount, 2, java.math.RoundingMode.HALF_UP))
            .toFloat().coerceIn(0f, 1f)
    } else 0f

    SpendlyCardV2(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Person initial avatar — colored by direction
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(directionColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = loan.personName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = directionColor
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        loan.personName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        CurrencyFormatter.formatCurrency(
                            if (loan.status == LoanStatus.SETTLED) loan.originalAmount else loan.remainingAmount,
                            loan.currency
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (loan.status == LoanStatus.SETTLED)
                            MaterialTheme.colorScheme.onSurfaceVariant else directionColor
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.xs))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (loan.direction == LoanDirection.LENT) "Lent" else "Borrowed",
                        style = MaterialTheme.typography.labelSmall,
                        color = directionColor
                    )
                    if (loan.status == LoanStatus.SETTLED) {
                        Text(
                            "Settled",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "of ${CurrencyFormatter.formatCurrency(loan.originalAmount, loan.currency)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (loan.status == LoanStatus.ACTIVE) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = progressColor,
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
