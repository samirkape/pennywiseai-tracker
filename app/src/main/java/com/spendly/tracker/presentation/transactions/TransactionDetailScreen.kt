package com.spendly.tracker.presentation.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.spendly.tracker.R
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.spendly.tracker.presentation.add.ReceiptPickerSection
import com.spendly.tracker.ui.effects.overScrollVertical
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spendly.tracker.data.database.entity.BudgetImpactType
import com.spendly.tracker.data.database.entity.CategoryEntity
import com.spendly.tracker.data.database.entity.GoalContributionEntity
import com.spendly.tracker.data.database.entity.GoalEntity
import com.spendly.tracker.data.database.entity.LoanDirection
import com.spendly.tracker.data.database.entity.LoanEntity
import com.spendly.tracker.data.database.entity.ProfileEntity
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionGroupEntity
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.data.database.entity.TransferKind
import com.spendly.tracker.presentation.add.PaymentChannel
import com.spendly.tracker.ui.LocalNavAnimatedVisibilityScope
import com.spendly.tracker.ui.LocalSharedTransitionScope
import com.spendly.tracker.ui.components.BrandIcon
import com.spendly.tracker.ui.components.CategoryChip
import com.spendly.tracker.ui.components.CategoryDot
import com.spendly.tracker.ui.components.CustomTitleTopAppBar
import com.spendly.tracker.ui.components.GroupedListItem
import com.spendly.tracker.ui.components.ListItemPosition
import com.spendly.tracker.ui.components.PennyWiseCard
import com.spendly.tracker.ui.components.cards.SectionHeaderV2
import com.spendly.tracker.ui.components.listItemPadding
import com.spendly.tracker.ui.components.toShape
import com.spendly.tracker.ui.components.SplitBreakdownCard
import com.spendly.tracker.ui.components.SplitEditor
import com.spendly.tracker.ui.components.SplitItem
import com.spendly.tracker.presentation.categories.CategoryEditDialog
import com.spendly.tracker.ui.theme.*
import com.spendly.tracker.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.spendly.tracker.utils.formatAmount
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// Reusable filled field colors for edit mode
@Composable
private fun editFilledColors() = TextFieldDefaults.colors(
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

private val editTopShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
private val editBottomShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
private val editFullShape = RoundedCornerShape(16.dp)

@Composable
private fun EditSectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = Spacing.xs),
    )
}

@Composable
private fun EditScreenDivider() {
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth(),
        thickness = Dimensions.Component.dividerThickness,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
    )
}

@Composable
private fun MoreOptionsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    leadingIcon: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                leadingIcon()
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditOptionsSection(
    transaction: TransactionEntity,
    accountProfileId: Long?,
    currentGroup: TransactionGroupEntity?,
    viewModel: TransactionDetailViewModel,
) {
    val accountDefault = accountProfileId ?: ProfileEntity.PERSONAL_ID
    val effectiveProfileId = transaction.profileId ?: accountDefault
    val isEffectivelyBusiness = effectiveProfileId == ProfileEntity.BUSINESS_ID

    val existingReceiptsEdit by viewModel.existingReceipts.collectAsStateWithLifecycle()
    val pendingReceiptUrisEdit by viewModel.pendingReceiptUris.collectAsStateWithLifecycle()
    val removedReceiptIdsEdit by viewModel.removedReceiptIds.collectAsStateWithLifecycle()
    val displayExisting = existingReceiptsEdit.filter { it.id !in removedReceiptIdsEdit }
    val displayReceiptUris = displayExisting.map { it.uri } + pendingReceiptUrisEdit

    val groupedCardShape = ListItemPosition.Single.toShape()
    val groupedCardColor = MaterialTheme.colorScheme.surfaceContainerLow

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        EditSectionLabel(label = "More options")

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(listItemPadding),
            shape = groupedCardShape,
            color = groupedCardColor,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.WorkOutline,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "Classification",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (isEffectivelyBusiness) {
                                    "Counted as a business transaction"
                                } else {
                                    "Counted as a personal transaction"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.md),
                    ) {
                        SegmentedButton(
                            selected = !isEffectivelyBusiness,
                            onClick = {
                                val newId = if (accountDefault == ProfileEntity.PERSONAL_ID) null else ProfileEntity.PERSONAL_ID
                                viewModel.updateProfileId(newId)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        ) {
                            Text("Personal")
                        }
                        SegmentedButton(
                            selected = isEffectivelyBusiness,
                            onClick = {
                                val newId = if (accountDefault == ProfileEntity.BUSINESS_ID) null else ProfileEntity.BUSINESS_ID
                                viewModel.updateProfileId(newId)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        ) {
                            Text("Business")
                        }
                    }
                }

                EditScreenDivider()

                MoreOptionsSwitchRow(
                    title = "Recurring",
                    subtitle = "Marks this as a repeating payment or income",
                    checked = transaction.isRecurring,
                    onCheckedChange = { viewModel.updateRecurringStatus(it) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                )

                EditScreenDivider()

                MoreOptionsSwitchRow(
                    title = "Exclude from tracking",
                    subtitle = "Hidden from budgets, totals, and analytics",
                    checked = transaction.isExcludedFromTracking,
                    onCheckedChange = { viewModel.updateExcludedFromTracking(it) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                )

                EditScreenDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.showGroupSheet() }
                        .padding(horizontal = Spacing.md, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "Transaction group",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = currentGroup?.name ?: "Organize related transactions together",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (currentGroup != null) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                EditScreenDivider()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Receipts",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Attach photos for your records",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    ReceiptPickerSection(
                        receiptUris = displayReceiptUris,
                        onReceiptAdded = { uri -> viewModel.addPendingReceiptUri(uri) },
                        onReceiptRemoved = { index ->
                            if (index < displayExisting.size) {
                                viewModel.removeExistingReceipt(displayExisting[index].id)
                            } else {
                                viewModel.removePendingReceiptUri(index - displayExisting.size)
                            }
                        },
                        onCreateCameraUri = { viewModel.createCameraUri() },
                        showOptionalCaption = false,
                    )
                }
            }
        }

        transaction.bankName?.let { bankName ->
            GroupedListItem(
                headline = { Text("Source bank") },
                supporting = { Text(bankName) },
                leading = {
                    Icon(
                        Icons.Default.AccountBalance,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.medium),
                    )
                },
                shape = ListItemPosition.Single.toShape(),
                padding = listItemPadding,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    transactionId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToLoanDetail: (Long) -> Unit = {},
    onFindSimilar: (String) -> Unit = {},
    onNavigateToTransactionDetail: (Long) -> Unit = {},
    viewModel: TransactionDetailViewModel = hiltViewModel()
) {
    val transaction by viewModel.transaction.collectAsStateWithLifecycle()
    val isEditMode by viewModel.isEditMode.collectAsStateWithLifecycle()
    val editableTransaction by viewModel.editableTransaction.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val saveSuccess by viewModel.saveSuccess.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val merchantRenameGrouped by viewModel.merchantRenameGrouped.collectAsStateWithLifecycle()
    val futureParsingPrompt by viewModel.futureParsingPrompt.collectAsStateWithLifecycle()
    val showDeleteDialog by viewModel.showDeleteDialog.collectAsStateWithLifecycle()
    val isDeleting by viewModel.isDeleting.collectAsStateWithLifecycle()
    val deleteSuccess by viewModel.deleteSuccess.collectAsStateWithLifecycle()
    val accountPrimaryCurrency by viewModel.primaryCurrency.collectAsStateWithLifecycle()
    val convertedAmount by viewModel.convertedAmount.collectAsStateWithLifecycle()

    val bulkCategorySaveConfirmParams by viewModel.bulkCategorySaveConfirm.collectAsStateWithLifecycle()
    val bulkCategoryPreviewRows by viewModel.bulkCategoryPreviewRows.collectAsStateWithLifecycle()
    val bulkCategoryUndoSnackCount by viewModel.bulkCategoryUndoSnackCount.collectAsStateWithLifecycle()
    val merchantRenameUndoCount by viewModel.merchantRenameUndoCount.collectAsStateWithLifecycle()

    // Split state
    val splits by viewModel.splits.collectAsStateWithLifecycle()
    val showSplitEditor by viewModel.showSplitEditor.collectAsStateWithLifecycle()
    val hasSplits by viewModel.hasSplits.collectAsStateWithLifecycle()

    // Loan state
    val loan by viewModel.loan.collectAsStateWithLifecycle()
    val showMarkAsLoanSheet by viewModel.showMarkAsLoanSheet.collectAsStateWithLifecycle()
    val recentPersonNames by viewModel.recentPersonNames.collectAsStateWithLifecycle()

    // Goal state
    val showLinkGoalSheet by viewModel.showLinkGoalSheet.collectAsStateWithLifecycle()
    val availableGoals by viewModel.availableGoals.collectAsStateWithLifecycle()
    val linkedGoalContributions by viewModel.linkedGoalContributions.collectAsStateWithLifecycle()
// Account profile state
    val accountProfileId by viewModel.accountProfileId.collectAsStateWithLifecycle()

    // Receipt state
    val fullScreenReceiptUri by viewModel.fullScreenReceiptUri.collectAsStateWithLifecycle()

    // Group state
    val currentGroup by viewModel.currentGroup.collectAsStateWithLifecycle()
    val availableGroups by viewModel.availableGroups.collectAsStateWithLifecycle()
    val showGroupSheet by viewModel.showGroupSheet.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current

    // Show success snackbar
    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            scope.launch {
                snackbarHostState.showSnackbar("Transaction updated successfully")
                viewModel.clearSaveSuccess()
            }
        }
    }
    
    // Show error snackbar
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            scope.launch {
                snackbarHostState.showSnackbar(it)
            }
        }
    }

    LaunchedEffect(bulkCategoryUndoSnackCount) {
        val n = bulkCategoryUndoSnackCount ?: return@LaunchedEffect
        val message = resources.getString(R.string.txn_bulk_undo_message, n)
        val undoAction = resources.getString(R.string.txn_bulk_undo_action)
        val undoDone = resources.getString(R.string.txn_bulk_undo_done)
        when (
            snackbarHostState.showSnackbar(
                message = message,
                actionLabel = undoAction,
                duration = SnackbarDuration.Long,
            )
        ) {
            SnackbarResult.ActionPerformed -> {
                viewModel.undoBulkCategoryFromSnackSuspend()
                snackbarHostState.showSnackbar(undoDone)
            }
            else -> { /* dismissed without undo */ }
        }
        viewModel.clearBulkCategoryUndoSnack()
    }

    LaunchedEffect(merchantRenameUndoCount) {
        val n = merchantRenameUndoCount ?: return@LaunchedEffect
        val message = resources.getString(R.string.merchant_rename_undo_message, n)
        val undoAction = resources.getString(R.string.merchant_rename_undo_action)
        val undoDone = resources.getString(R.string.merchant_rename_undo_done)
        when (
            snackbarHostState.showSnackbar(
                message = message,
                actionLabel = undoAction,
                duration = SnackbarDuration.Long,
            )
        ) {
            SnackbarResult.ActionPerformed -> {
                viewModel.undoMerchantRenameSuspend()
                snackbarHostState.showSnackbar(undoDone)
            }
            else -> viewModel.clearMerchantRenameUndoSnack()
        }
    }

    LaunchedEffect(transactionId) {
        viewModel.loadTransaction(transactionId)
    }
    
    // Handle delete success
    LaunchedEffect(deleteSuccess) {
        if (deleteSuccess) {
            onNavigateBack()
        }
    }

    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            if (!isEditMode && transaction != null) {
                FloatingActionButton(
                    onClick = { viewModel.enterEditMode() },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Transaction"
                    )
                }
            }
        },
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = if (isEditMode) "Edit Transaction" else "Transaction Details",
                hasBackButton = true,
                hasActionButton = !isEditMode && transaction != null,
                navigationContent = {
                    IconButton(onClick = {
                        if (isEditMode) {
                            viewModel.cancelEdit()
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            if (isEditMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isEditMode) "Cancel" else "Back"
                        )
                    }
                },
                actionContent = {
                    if (!isEditMode && transaction != null) {
                        IconButton(onClick = { viewModel.showDeleteDialog() }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Transaction",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                hazeState = hazeState
            )
        }
    ) { paddingValues ->
        val displayTransaction = if (isEditMode) editableTransaction else transaction
        displayTransaction?.let { txn ->
            TransactionDetailContent(
                transaction = txn,
                isEditMode = isEditMode,
                isSaving = isSaving,
                viewModel = viewModel,
                accountPrimaryCurrency = accountPrimaryCurrency,
                convertedAmount = convertedAmount,
                splits = splits,
                showSplitEditor = showSplitEditor,
                hasSplits = hasSplits,
                loan = loan,
                onNavigateToLoanDetail = onNavigateToLoanDetail,
                onFindSimilar = onFindSimilar,
                onNavigateToTransactionDetail = onNavigateToTransactionDetail,
                accountProfileId = accountProfileId,
                hazeState = hazeState,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
    
    merchantRenameGrouped?.let { groupedState ->
        MerchantRenameGroupedSheet(
            state = groupedState,
            onApprove = { viewModel.approveCurrentCandidate() },
            onSkip = { viewModel.skipCurrentCandidate() },
            onOpenTransaction = { txId -> onNavigateToTransactionDetail(txId) },
            onDismiss = { viewModel.dismissMerchantRenameGrouped() },
        )
    }

    futureParsingPrompt?.let { prompt ->
        FutureParsingPromptDialog(
            prompt = prompt,
            onConfirm = { extras -> viewModel.confirmFutureParsing(extras) },
            onDismiss = { viewModel.dismissFutureParsing() },
            onNever = { viewModel.neverFutureParsing() },
        )
    }

    bulkCategorySaveConfirmParams?.let { confirm ->
        val scopeLabel = when (confirm.scope) {
            BulkCategoryDateScope.ALL_TIME -> stringResource(R.string.txn_bulk_category_scope_all)
            BulkCategoryDateScope.LAST_90_DAYS -> stringResource(R.string.txn_bulk_category_scope_90d)
            BulkCategoryDateScope.LAST_365_DAYS -> stringResource(R.string.txn_bulk_category_scope_365d)
        }
        AlertDialog(
            onDismissRequest = { viewModel.dismissBulkCategorySave() },
            title = { Text(stringResource(R.string.txn_bulk_category_confirm_title)) },
            text = {
                val previewDateFmt = remember {
                    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
                }
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        stringResource(
                            R.string.txn_bulk_category_confirm_message,
                            confirm.otherCount,
                            confirm.merchantName,
                            scopeLabel,
                        )
                    )
                    val selectedPastParts = buildList {
                        if (confirm.pastCategory) add(stringResource(R.string.txn_bulk_past_category))
                        if (confirm.pastMerchant) add(stringResource(R.string.txn_bulk_past_merchant))
                        if (confirm.pastType) add(stringResource(R.string.txn_bulk_past_type))
                    }
                    if (selectedPastParts.isNotEmpty()) {
                        Text(
                            stringResource(
                                R.string.txn_bulk_confirm_selected_line,
                                selectedPastParts.joinToString(", "),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        stringResource(R.string.txn_bulk_confirm_undo_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (bulkCategoryPreviewRows.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.txn_bulk_category_preview_header),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            items(bulkCategoryPreviewRows, key = { it.id }) { row ->
                                val amountLabel = CurrencyFormatter.formatCurrency(row.amount, row.currency)
                                Text(
                                    text = stringResource(
                                        R.string.txn_bulk_category_preview_row,
                                        row.dateTime.format(previewDateFmt),
                                        amountLabel,
                                        row.category,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmBulkCategorySave() }) {
                    Text(stringResource(R.string.txn_bulk_category_confirm_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissBulkCategorySave() }) {
                    Text(stringResource(R.string.txn_bulk_category_confirm_cancel))
                }
            },
        )
    }


    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteDialog() },
            title = { Text("Delete Transaction") },
            text = { 
                Text("Are you sure you want to delete this transaction? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteTransaction() },
                    enabled = !isDeleting
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(Dimensions.Icon.small),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            "Delete",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Group Bottom Sheet
    if (showGroupSheet) {
        GroupBottomSheet(
            currentGroup = currentGroup,
            availableGroups = availableGroups,
            onDismiss = { viewModel.hideGroupSheet() },
            onAddToGroup = { groupId -> viewModel.addToGroup(groupId) },
            onRemoveFromGroup = { viewModel.removeFromGroup() },
            onCreateGroup = { name, note -> viewModel.createGroupAndAdd(name, note) }
        )
    }

    // Mark as Loan Bottom Sheet
    if (showMarkAsLoanSheet) {
        val txType = transaction?.transactionType
        val inferredDirection = if (txType == TransactionType.INCOME) LoanDirection.BORROWED else LoanDirection.LENT
        MarkAsLoanBottomSheet(
            transactionAmount = transaction?.amount ?: BigDecimal.ZERO,
            transactionCurrency = transaction?.currency ?: "INR",
            direction = inferredDirection,
            recentPersonNames = recentPersonNames,
            onDismiss = { viewModel.hideMarkAsLoanSheet() },
            onConfirm = { personName, note ->
                viewModel.createLoanFromTransaction(personName, inferredDirection, note)
            }
        )
    }

    // Split Among Goals Bottom Sheet
    if (showLinkGoalSheet) {
        SplitGoalsBottomSheet(
            goals = availableGoals,
            transactionAmount = transaction?.amount ?: BigDecimal.ZERO,
            existingContributions = linkedGoalContributions,
            onDismiss = { viewModel.hideLinkGoalSheet() },
            onConfirm = { splits -> viewModel.linkToGoals(splits) }
        )
    }

    // Full-screen Receipt Dialog
    fullScreenReceiptUri?.let { uri ->
        Dialog(
            onDismissRequest = { viewModel.hideFullScreenReceipt() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = uri,
                    contentDescription = "Receipt full screen",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.md),
                    contentScale = ContentScale.Fit
                )
                FilledIconButton(
                    onClick = { viewModel.hideFullScreenReceipt() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.md)
                        .statusBarsPadding(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.5f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        }
    }
}

@Composable
private fun TransactionDetailContent(
    transaction: TransactionEntity,
    isEditMode: Boolean,
    isSaving: Boolean,
    viewModel: TransactionDetailViewModel,
    accountPrimaryCurrency: String,
    convertedAmount: BigDecimal?,
    splits: List<SplitItem>,
    showSplitEditor: Boolean,
    hasSplits: Boolean,
    loan: LoanEntity?,
    onNavigateToLoanDetail: (Long) -> Unit,
    onFindSimilar: (String) -> Unit,
    onNavigateToTransactionDetail: (Long) -> Unit,
    accountProfileId: Long?,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .background(MaterialTheme.colorScheme.background)
                .imePadding()
                .overScrollVertical()
                .verticalScroll(scrollState)
                .padding(horizontal = Dimensions.Padding.content)
                .padding(top = Spacing.sm, bottom = Dimensions.Padding.content)
        ) {
            if (isEditMode) {
                EditSectionLabel(label = "Basics")
                Spacer(modifier = Modifier.height(Spacing.sm))
                EditableTransactionHeader(
                    transaction = transaction,
                    viewModel = viewModel
                )

                Spacer(modifier = Modifier.height(Spacing.md))
                EditScreenDivider()
                Spacer(modifier = Modifier.height(Spacing.sm))

                EditSectionLabel(label = "Details")
                Spacer(modifier = Modifier.height(Spacing.sm))
                EditableExtractedInfoCard(
                    transaction = transaction,
                    accountProfileId = accountProfileId,
                    viewModel = viewModel,
                    splits = splits,
                    showSplitEditor = showSplitEditor
                )

                // Bottom spacer for sticky Save button (button height + nav bar inset)
                val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                Spacer(modifier = Modifier.height(56.dp + navBarBottom))

            } else {
                TransactionReceipt(
                    transaction = transaction,
                    primaryCurrency = accountPrimaryCurrency,
                    convertedAmount = convertedAmount,
                    viewModel = viewModel,
                    splits = splits,
                    hasSplits = hasSplits,
                    loan = loan,
                    onNavigateToLoanDetail = onNavigateToLoanDetail,
                    onFindSimilar = onFindSimilar,
                    onNavigateToTransactionDetail = onNavigateToTransactionDetail,
                    accountProfileId = accountProfileId
                )
            }
        }

        // Sticky Save button in edit mode
        if (isEditMode) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.background,
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
            ) {
                Column {
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        thickness = Dimensions.Component.dividerThickness,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
                    )
                    Button(
                        onClick = { viewModel.saveChanges() },
                        enabled = !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimensions.Padding.content, vertical = Spacing.md)
                            .navigationBarsPadding()
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(Dimensions.Icon.small),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Text("Saving…")
                        } else {
                            Text("Save", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

// ==================== Clean detail read-only view ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransactionReceipt(
    transaction: TransactionEntity,
    primaryCurrency: String,
    convertedAmount: BigDecimal?,
    viewModel: TransactionDetailViewModel,
    splits: List<SplitItem>,
    hasSplits: Boolean,
    loan: LoanEntity?,
    onNavigateToLoanDetail: (Long) -> Unit,
    onFindSimilar: (String) -> Unit = {},
    onNavigateToTransactionDetail: (Long) -> Unit = {},
    accountProfileId: Long? = null
) {
    val currentGroup by viewModel.currentGroup.collectAsStateWithLifecycle()
    val linkedGoalContribution by viewModel.linkedGoalContribution.collectAsStateWithLifecycle()
    val linkedGoalContributions by viewModel.linkedGoalContributions.collectAsStateWithLifecycle()
    val isDark = isSystemInDarkTheme()
    val typeColor = when (transaction.transactionType) {
        TransactionType.INCOME -> if (isDark) income_dark else income_light
        TransactionType.EXPENSE, TransactionType.CREDIT -> if (isDark) expense_dark else expense_light
        TransactionType.TRANSFER -> if (isDark) transfer_dark else transfer_light
        TransactionType.INVESTMENT -> if (isDark) investment_dark else investment_light
    }
    val sign = when (transaction.transactionType) {
        TransactionType.INCOME -> "+"
        TransactionType.EXPENSE, TransactionType.CREDIT -> "-"
        TransactionType.TRANSFER -> ""
        TransactionType.INVESTMENT -> ""
    }

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // ── Hero Header ──
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val heroIconModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        Modifier.sharedElement(
                            sharedTransitionScope.rememberSharedContentState(
                                key = "brand_icon_${transaction.id}"
                            ),
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                } else {
                    Modifier
                }
                BrandIcon(
                    merchantName = transaction.merchantName,
                    modifier = heroIconModifier,
                    size = 56.dp,
                    showBackground = true
                )

                Spacer(modifier = Modifier.height(Spacing.sm))

                Text(
                    text = transaction.merchantName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(Spacing.sm))

                // Transaction type chip
                val typeLabel = transaction.transactionType.name.lowercase()
                    .replaceFirstChar { it.uppercase() }
                val typeIcon = when (transaction.transactionType) {
                    TransactionType.INCOME -> Icons.AutoMirrored.Filled.TrendingUp
                    TransactionType.EXPENSE -> Icons.AutoMirrored.Filled.TrendingDown
                    TransactionType.CREDIT -> Icons.Default.CreditCard
                    TransactionType.TRANSFER -> Icons.Default.SwapHoriz
                    TransactionType.INVESTMENT -> Icons.AutoMirrored.Filled.ShowChart
                }
                // Amount - displayed prominently
                Text(
                    text = "$sign${transaction.formatAmount()}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = typeColor,
                    textAlign = TextAlign.Center
                )

                if (transaction.currency.isNotEmpty() &&
                    !transaction.currency.equals(primaryCurrency, ignoreCase = true) &&
                    convertedAmount != null
                ) {
                    Text(
                        text = "\u2248 ${CurrencyFormatter.formatCurrency(convertedAmount, primaryCurrency)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                // Transaction type chip
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            text = typeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    icon = {
                        Icon(
                            typeIcon,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.Icon.small),
                            tint = typeColor
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = typeColor.copy(alpha = 0.12f),
                        labelColor = typeColor,
                        iconContentColor = typeColor
                    ),
                    border = null
                )

                // Loan status chip (if linked to a loan)
                val isDarkTheme = isSystemInDarkTheme()
                val loanColor = if (isDarkTheme) loan_dark else loan_light
                if (loan != null) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    SuggestionChip(
                        onClick = { onNavigateToLoanDetail(loan.id) },
                        label = {
                            Text(
                                text = if (loan.direction == LoanDirection.LENT)
                                    "Lent to ${loan.personName}" else "Borrowed from ${loan.personName}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        icon = {
                            Icon(
                                Icons.Default.SwapHoriz,
                                contentDescription = null,
                                modifier = Modifier.size(Dimensions.Icon.small),
                                tint = loanColor
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = loanColor.copy(alpha = 0.12f),
                            labelColor = loanColor,
                            iconContentColor = loanColor
                        ),
                        border = null
                    )
                }

                // Group chip (if assigned to a group)
                val groupColor = MaterialTheme.colorScheme.tertiary
                currentGroup?.let { group ->
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    SuggestionChip(
                        onClick = { viewModel.showGroupSheet() },
                        label = {
                            Text(
                                text = group.name,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        icon = {
                            Icon(
                                Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(Dimensions.Icon.small)
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = groupColor.copy(alpha = 0.12f),
                            labelColor = groupColor,
                            iconContentColor = groupColor
                        ),
                        border = null
                    )
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val similarTransactions by viewModel.similarTransactions.collectAsStateWithLifecycle()
            val quickActions = buildList {
                if (loan == null) {
                    add(
                        TransactionQuickAction(
                            icon = Icons.Default.SwapHoriz,
                            label = if (transaction.transactionType == TransactionType.INCOME) {
                                "Track as borrowed"
                            } else {
                                "Track as lent"
                            },
                            compactLabel = if (transaction.transactionType == TransactionType.INCOME) {
                                "Borrow"
                            } else {
                                "Lend"
                            },
                            onClick = { viewModel.showMarkAsLoanSheet() }
                        )
                    )
                }
                if (currentGroup == null) {
                    add(
                        TransactionQuickAction(
                            icon = Icons.Outlined.FolderOpen,
                            label = "Add to group",
                            compactLabel = "Group",
                            onClick = { viewModel.showGroupSheet() }
                        )
                    )
                }
                if (linkedGoalContributions.isEmpty()) {
                    add(
                        TransactionQuickAction(
                            icon = Icons.Default.EmojiEvents,
                            label = "Link to goal",
                            compactLabel = "Goal",
                            onClick = { viewModel.showLinkGoalSheet() }
                        )
                    )
                } else {
                    val n = linkedGoalContributions.size
                    add(
                        TransactionQuickAction(
                            icon = Icons.Default.EmojiEvents,
                            label = if (n == 1) "Edit goal" else "Edit $n goals",
                            compactLabel = if (n == 1) "1 goal" else "$n goals",
                            onClick = { viewModel.showLinkGoalSheet() }
                        )
                    )
                }
            }

            // Use compact layout only when there are many actions — fewer items get larger icons/text.
            val compactScreen = quickActions.size >= 5

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // Quick Actions Row
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        quickActions.forEach { action ->
                            QuickActionItem(
                                icon = action.icon,
                                label = if (compactScreen) action.compactLabel else action.label,
                                onClick = action.onClick,
                                compact = compactScreen,
                                modifier = Modifier.weight(1f),
                                enabled = action.enabled
                            )
                        }
                    }
                }

                // ── Similar Transactions Section ──
                if (similarTransactions.isNotEmpty()) {
                    val similarCardWidth = if (compactScreen) 136.dp else 160.dp
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 1.dp
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.md)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onFindSimilar(transaction.merchantName) },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(Dimensions.Icon.medium),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Similar Items",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "See all",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = "See all similar transactions",
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                contentPadding = PaddingValues(end = Spacing.xs),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(similarTransactions) { similarTxn ->
                                    Surface(
                                        modifier = Modifier
                                            .width(similarCardWidth)
                                            .clickable { onNavigateToTransactionDetail(similarTxn.id) },
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        tonalElevation = 2.dp
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(Spacing.sm),
                                            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                                        ) {
                                            Text(
                                                text = similarTxn.merchantName,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = CurrencyFormatter.formatCurrency(similarTxn.amount.abs(), similarTxn.currency),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = similarTxn.dateTime.format(
                                                    DateTimeFormatter.ofPattern("MMM d")
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Details Section ──
        Column(modifier = Modifier.fillMaxWidth()) {
            // Date & Time
            DetailInfoRow(
                icon = Icons.Default.CalendarToday,
                label = "Date & Time",
                value = transaction.dateTime.format(
                    DateTimeFormatter.ofPattern("EEE, MMM d, yyyy \u00b7 h:mm a")
                )
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Category (budget) and optional tags
            val tagCategories = transaction.tags.split(",").filter { it.isNotBlank() }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.sm),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.Category,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = Spacing.sm)
                        .size(Dimensions.Icon.medium),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (hasSplits && splits.isNotEmpty()) "Categories (split)"
                            else "Budget category",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        if (hasSplits && splits.isNotEmpty()) {
                            SuggestionChip(
                                onClick = {},
                                label = { Text("Split (${splits.size} categories)", style = MaterialTheme.typography.bodyMedium) },
                                border = null,
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            )
                        } else {
                            SuggestionChip(
                                onClick = {},
                                label = { Text(transaction.category, style = MaterialTheme.typography.bodyMedium) },
                                border = null,
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            )
                        }
                    }
                    if (tagCategories.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            text = "Tags",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            tagCategories.forEach { catName ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(catName, style = MaterialTheme.typography.bodyMedium) },
                                    border = null,
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                )
                            }
                        }
                        Text(
                            text = "Tags are for organization only and do not affect budgets.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Bank
            transaction.bankName?.let {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                DetailInfoRow(
                    icon = Icons.Default.AccountBalance,
                    label = "Bank",
                    value = it
                )
            }

            // Description
            transaction.description?.let {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                DetailInfoRow(
                    icon = Icons.Default.Description,
                    label = "Description",
                    value = it
                )
            }

            // Recurring
            if (transaction.isRecurring) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                DetailInfoRow(
                    icon = Icons.Default.Repeat,
                    label = "Status",
                    value = "Recurring"
                )
            }

            // Excluded from tracking
            if (transaction.isExcludedFromTracking) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                DetailInfoRow(
                    icon = Icons.Default.VisibilityOff,
                    label = "Tracking",
                    value = "Excluded from budgets & reports"
                )
            }

            // Credit Card bill payment hint
            if (transaction.transferKind == com.spendly.tracker.data.database.entity.TransferKind.CC_BILL_PAYMENT) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                DetailInfoRow(
                    icon = Icons.Default.CreditCard,
                    label = "Credit Card Payment",
                    value = "Not counted in spending (paired with card purchase)"
                )
                transaction.linkedTransactionId?.let { linkedId ->
                    DetailInfoRow(
                        icon = Icons.Default.Link,
                        label = "Linked transaction",
                        value = "#$linkedId"
                    )
                }
            }

            // Classification
            val effectiveProfileId = transaction.profileId ?: accountProfileId
            val isEffectivelyBusiness = effectiveProfileId == ProfileEntity.BUSINESS_ID
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            DetailInfoRow(
                icon = if (isEffectivelyBusiness) Icons.Default.Business else Icons.Default.Person,
                label = "Classification",
                value = if (isEffectivelyBusiness) "Business" else "Personal"
            )

            // Account info
            if (transaction.fromAccount != null && transaction.toAccount != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                val maskedFrom = transaction.fromAccount.let { from ->
                    if (from.length > 4) "*".repeat(from.length - 4) + from.takeLast(4) else from
                }
                val maskedTo = transaction.toAccount.let { to ->
                    if (to.length > 4) "*".repeat(to.length - 4) + to.takeLast(4) else to
                }
                TransferFlowRow(fromValue = maskedFrom, toValue = maskedTo)
            } else {
                transaction.accountNumber?.let {
                    if (transaction.fromAccount == null && transaction.toAccount == null) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        val masked = if (it.length > 4) {
                            "*".repeat(it.length - 4) + it.takeLast(4)
                        } else it
                        DetailInfoRow(
                            icon = Icons.Default.AccountBalanceWallet,
                            label = "Account",
                            value = masked
                        )
                    }
                }
                transaction.fromAccount?.let { from ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    val masked = if (from.length > 4) {
                        "*".repeat(from.length - 4) + from.takeLast(4)
                    } else from
                    DetailInfoRow(
                        icon = Icons.Default.Output,
                        label = "From",
                        value = masked
                    )
                }
                transaction.toAccount?.let { to ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    val masked = if (to.length > 4) {
                        "*".repeat(to.length - 4) + to.takeLast(4)
                    } else to
                    DetailInfoRow(
                        icon = Icons.Default.Input,
                        label = "To",
                        value = masked
                    )
                }
            }

            // Balance
            transaction.balanceAfter?.let {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                DetailInfoRow(
                    icon = Icons.Default.AccountBalanceWallet,
                    label = "Balance",
                    value = CurrencyFormatter.formatCurrency(it, viewModel.primaryCurrency.value)
                )
            }

            // Reference number (prefer extracted reference, fallback to SMS sender)
            val referenceValue = transaction.reference ?: transaction.smsSender
            referenceValue?.let {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                DetailInfoRow(
                    icon = Icons.Default.Tag,
                    label = "Reference",
                    value = it
                )
            }
        }

        // ── Receipt Section ──
        val receipts by viewModel.existingReceipts.collectAsStateWithLifecycle()
        if (receipts.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Icon(
                            Icons.Default.Receipt,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.Icon.medium),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (receipts.size == 1) "Receipt" else "Receipts (${receipts.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "Tap to view",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        items(receipts) { receipt ->
                            AsyncImage(
                                model = receipt.uri,
                                contentDescription = "Receipt",
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.showFullScreenReceipt(receipt.uri) },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }

        // ── SMS Section ──
        if (!transaction.smsBody.isNullOrBlank()) {
            ExpandableSmsSection(smsBody = transaction.smsBody)
        }

        // ── Split Breakdown ──
        if (hasSplits && splits.isNotEmpty()) {
            SplitBreakdownCard(
                splits = splits,
                currency = transaction.currency
            )
        }
    }
}

@Composable
private fun QuickActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val contentColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                       else MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 52.dp else 58.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = if (compact) 3.dp else Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(if (compact) 14.dp else 18.dp),
            tint = contentColor
        )
        Spacer(modifier = Modifier.height(if (compact) 2.dp else 4.dp))
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

private data class TransactionQuickAction(
    val icon: ImageVector,
    val label: String,
    val compactLabel: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true
)

@Composable
private fun DetailInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(Dimensions.Icon.medium),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TransferFlowRow(
    fromValue: String,
    toValue: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Icon(
            Icons.Default.SwapHoriz,
            contentDescription = null,
            modifier = Modifier.size(Dimensions.Icon.medium),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp
        ) {
            Text(
                text = fromValue,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = Spacing.sm)
            )
        }

        Icon(
            Icons.Default.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(Dimensions.Icon.small),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp
        ) {
            Text(
                text = toValue,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = Spacing.sm)
            )
        }
    }
}

@Composable
private fun ExpandableSmsSection(smsBody: String) {
    var expanded by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Dimensions.Icon.small)
                    )
                    Text(
                        text = if (expanded) "Hide SMS" else "Show original SMS",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimensions.Icon.medium)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Text(
                    text = smsBody,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditableTransactionHeader(
    transaction: TransactionEntity,
    viewModel: TransactionDetailViewModel
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // Amount and Currency
        val primaryCurrency by viewModel.primaryCurrency.collectAsStateWithLifecycle()
        val showAmountError by viewModel.showAmountError.collectAsStateWithLifecycle()
        val showMerchantError by viewModel.showMerchantError.collectAsStateWithLifecycle()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.Top
        ) {
            CurrencyDropdown(
                selectedCurrency = transaction.currency.ifEmpty { primaryCurrency },
                onCurrencySelected = { viewModel.updateCurrency(it) },
                modifier = Modifier.width(130.dp)
            )

            TextField(
                value = transaction.amount.stripTrailingZeros().toPlainString(),
                onValueChange = { viewModel.updateAmount(it) },
                label = { Text("Amount *", fontWeight = FontWeight.SemiBold) },
                textStyle = MaterialTheme.typography.headlineSmall,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = showAmountError,
                supportingText = if (showAmountError) { { Text("Amount is required") } } else null,
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = editFullShape,
                colors = editFilledColors()
            )
        }

        // Merchant + Description (connected group)
        val suggestedMerchantRenames by viewModel.suggestedMerchantRenames.collectAsStateWithLifecycle()
        val merchantAutocompleteSuggestions by viewModel.merchantAutocompleteSuggestions.collectAsStateWithLifecycle()
        val visibleMerchantRenameSuggestions = suggestedMerchantRenames.filter {
            !it.equals(transaction.merchantName, ignoreCase = true) &&
                merchantAutocompleteSuggestions.none { suggestion ->
                    suggestion.equals(it, ignoreCase = true)
                }
        }
        var merchantDropdownExpanded by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(1.5.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = merchantDropdownExpanded && merchantAutocompleteSuggestions.isNotEmpty(),
                onExpandedChange = { merchantDropdownExpanded = it },
            ) {
                TextField(
                    value = transaction.merchantName,
                    onValueChange = {
                        viewModel.updateMerchantName(it)
                        merchantDropdownExpanded = it.isNotBlank()
                    },
                    label = { Text("Merchant", fontWeight = FontWeight.SemiBold) },
                    leadingIcon = {
                        BrandIcon(
                            merchantName = transaction.merchantName,
                            size = 24.dp,
                            showBackground = false
                        )
                    },
                    trailingIcon = {
                        if (merchantAutocompleteSuggestions.isNotEmpty()) {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = merchantDropdownExpanded)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryEditable),
                    shape = editTopShape,
                    isError = showMerchantError,
                    supportingText = if (showMerchantError) { { Text("Merchant is required") } } else null,
                    colors = editFilledColors()
                )

                ExposedDropdownMenu(
                    expanded = merchantDropdownExpanded && merchantAutocompleteSuggestions.isNotEmpty(),
                    onDismissRequest = { merchantDropdownExpanded = false },
                ) {
                    merchantAutocompleteSuggestions.forEach { suggestion ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                ) {
                                    BrandIcon(
                                        merchantName = suggestion,
                                        size = 20.dp,
                                        showBackground = false,
                                    )
                                    Text(
                                        text = suggestion,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            },
                            onClick = {
                                viewModel.updateMerchantName(suggestion)
                                merchantDropdownExpanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
            }

            if (visibleMerchantRenameSuggestions.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(color = MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        text = stringResource(R.string.txn_edit_suggested_merchant_rename),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        visibleMerchantRenameSuggestions.forEach { suggestedName ->
                            FilterChip(
                                selected = false,
                                onClick = { viewModel.applyMerchantRenameSuggestion(suggestedName) },
                                label = {
                                    Text(
                                        stringResource(
                                            R.string.txn_edit_suggested_merchant_rename_chip,
                                            suggestedName
                                        )
                                    )
                                },
                                leadingIcon = {
                                    BrandIcon(
                                        merchantName = suggestedName,
                                        size = 18.dp,
                                        showBackground = false
                                    )
                                }
                            )
                        }
                    }
                }
            }

            TextField(
                value = transaction.description ?: "",
                onValueChange = { viewModel.updateDescription(it) },
                label = { Text("Description (Optional)", fontWeight = FontWeight.SemiBold) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.medium)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = editBottomShape,
                colors = editFilledColors()
            )
        }

        // Transaction Type
        val topLevelTypes = listOf(
            TransactionType.INCOME,
            TransactionType.EXPENSE,
            TransactionType.TRANSFER,
            TransactionType.INVESTMENT
        )
        val isExpenseSelected = transaction.transactionType == TransactionType.EXPENSE ||
                transaction.transactionType == TransactionType.CREDIT
        var paymentChannel by remember(transaction.transactionType) {
            mutableStateOf(
                when (transaction.transactionType) {
                    TransactionType.CREDIT -> PaymentChannel.CREDIT_CARD
                    else -> PaymentChannel.ACCOUNT
                }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            for (type in topLevelTypes) {
                val selected = if (type == TransactionType.EXPENSE) isExpenseSelected
                else transaction.transactionType == type
                FilterChip(
                    modifier = Modifier.weight(1f),
                    selected = selected,
                    onClick = { viewModel.updateTransactionType(type) },
                    label = {
                        Text(
                            type.name.lowercase(Locale.getDefault())
                                .replaceFirstChar { it.titlecase(Locale.getDefault()) },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        containerColor = MaterialTheme.colorScheme.surface,
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

        // Payment Channel sub-chips (Expense only)
        if (isExpenseSelected) {
            val channels = listOf(
                PaymentChannel.ACCOUNT to "Account",
                PaymentChannel.CASH to "Cash",
                PaymentChannel.CREDIT_CARD to "Credit Card"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                for ((channel, label) in channels) {
                    val channelSelected = paymentChannel == channel
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = channelSelected,
                        onClick = {
                            paymentChannel = channel
                            viewModel.updateTransactionType(
                                if (channel == PaymentChannel.CREDIT_CARD) TransactionType.CREDIT
                                else TransactionType.EXPENSE
                            )
                        },
                        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = if (channelSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            containerColor = MaterialTheme.colorScheme.surface,
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

        // Transfer sub-chips (Transfer only)
        val isTransferSelected = transaction.transactionType == TransactionType.TRANSFER
        if (isTransferSelected) {
            val transferSubOptions = listOf(
                TransferKind.SELF_TRANSFER to stringResource(R.string.txn_type_transfer_self),
                TransferKind.OTHERS_TRANSFER to stringResource(R.string.txn_type_transfer_others),
                TransferKind.CC_BILL_PAYMENT to stringResource(R.string.txn_transfer_cc_bill_short),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                for ((kind, label) in transferSubOptions) {
                    val subSelected = transaction.transferKind == kind
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = subSelected,
                        onClick = { viewModel.updateTransferKind(kind) },
                        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = if (subSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderWidth = 0.dp,
                            selected = subSelected,
                            enabled = true
                        )
                    )
                }
            }
        }

        // Date and Time
        DateTimeField(
            dateTime = transaction.dateTime,
            onDateTimeChange = { viewModel.updateDateTime(it) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditableExtractedInfoCard(
    transaction: TransactionEntity,
    accountProfileId: Long?,
    viewModel: TransactionDetailViewModel,
    splits: List<SplitItem>,
    showSplitEditor: Boolean
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle(initialValue = emptyList())
    val currentGroup by viewModel.currentGroup.collectAsStateWithLifecycle()
    val merchantMappingCategoryHint by viewModel.merchantMappingCategoryHint.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(1.5.dp),
        ) {
            if (transaction.transactionType == TransactionType.TRANSFER) {
                AccountNumberField(
                    accountNumber = transaction.fromAccount,
                    onAccountNumberChange = { viewModel.updateFromAccount(it) },
                    viewModel = viewModel,
                    label = "From Account",
                    placeholder = "Select or enter source account",
                    excludeAccount = transaction.toAccount
                )
                AccountNumberField(
                    accountNumber = transaction.toAccount,
                    onAccountNumberChange = { viewModel.updateToAccount(it) },
                    viewModel = viewModel,
                    label = "To Account",
                    placeholder = "Select or enter destination account",
                    excludeAccount = transaction.fromAccount
                )
            } else {
                AccountNumberField(
                    accountNumber = transaction.accountNumber,
                    onAccountNumberChange = { viewModel.updateAccountNumber(it) },
                    viewModel = viewModel
                )
            }

            if (!showSplitEditor) {
                // Hide category for self-transfers: they are internal movements excluded from spend
                val isSelfTransfer = transaction.transactionType == TransactionType.TRANSFER &&
                        transaction.transferKind == TransferKind.SELF_TRANSFER
                if (!isSelfTransfer) {
                    CategoryMultiSelect(
                        primaryCategory = transaction.category,
                        viewModel = viewModel
                    )
                    merchantMappingCategoryHint?.let { mappedCategory ->
                        Text(
                            text = stringResource(R.string.txn_mapping_conflict_hint, mappedCategory),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }
            }
        }

        // Split button
        if (!showSplitEditor && transaction.transactionType != TransactionType.TRANSFER) {
            OutlinedButton(
                onClick = { viewModel.enableSplitMode() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.CallSplit,
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.Icon.small)
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text("Split into categories")
            }
        }

        if (!showSplitEditor) {
            if (transaction.transactionType == TransactionType.INCOME) {
                BudgetImpactSection(viewModel = viewModel)
            }
        }

        EditOptionsSection(
            transaction = transaction,
            accountProfileId = accountProfileId,
            currentGroup = currentGroup,
            viewModel = viewModel,
        )
    }

    // Show SplitEditor when in split mode
    if (showSplitEditor) {
        Spacer(modifier = Modifier.height(Spacing.md))
        SplitEditor(
            totalAmount = transaction.amount,
            currency = transaction.currency,
            splits = splits,
            availableCategories = categories.map { it.name },
            onSplitsChanged = { viewModel.updateSplits(it) },
            onRemoveSplits = { viewModel.removeSplits() },
            onCreateCategory = { name, color, isIncome, icon ->
                viewModel.createAndSelectSplitCategory(name, color, isIncome, icon)
            },
            modifier = Modifier.padding(horizontal = 0.dp)
        )
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetImpactSection(viewModel: TransactionDetailViewModel) {
    val budgetImpactType by viewModel.budgetImpactType.collectAsStateWithLifecycle()
    val budgetCategory by viewModel.budgetCategory.collectAsStateWithLifecycle()
    val activeBudgetCategories by viewModel.activeBudgetCategories.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Text(
            text = "Budget impact",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = budgetImpactType == null,
                onClick = { viewModel.updateBudgetImpactType(null) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                label = { Text("None", style = MaterialTheme.typography.labelSmall) }
            )
            SegmentedButton(
                selected = budgetImpactType == BudgetImpactType.DEDUCT_SPENT,
                onClick = { viewModel.updateBudgetImpactType(BudgetImpactType.DEDUCT_SPENT) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                label = { Text("Refund", style = MaterialTheme.typography.labelSmall) }
            )
            SegmentedButton(
                selected = budgetImpactType == BudgetImpactType.ADD_TO_LIMIT,
                onClick = { viewModel.updateBudgetImpactType(BudgetImpactType.ADD_TO_LIMIT) },
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
                                    viewModel.updateBudgetCategory(category)
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CategoryMultiSelect(
    primaryCategory: String,
    viewModel: TransactionDetailViewModel
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle(initialValue = emptyList())
    val categorySuggestions by viewModel.categorySuggestions.collectAsStateWithLifecycle()
    val pendingTags by viewModel.pendingTags.collectAsStateWithLifecycle()
    val tagSuggestions by viewModel.tagSuggestions.collectAsStateWithLifecycle()
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var showCreateCategoryDialog by remember { mutableStateOf(false) }
    var tagInput by remember { mutableStateOf("") }

    val catEntity = categories.find { it.name == primaryCategory }
    val suggestedCategories = categorySuggestions.categories

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // ── Category ──
        Text(
            text = "Category",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (suggestedCategories.isNotEmpty() && categorySuggestions.source != null) {
            val suggestionHeader = when (categorySuggestions.source) {
                CategorySuggestionSource.MERCHANT -> stringResource(
                    R.string.txn_edit_suggested_merchant,
                    categorySuggestions.merchantName
                )
                CategorySuggestionSource.USED_TODAY -> stringResource(R.string.txn_edit_suggested_today)
                null -> ""
            }
            Text(
                text = suggestionHeader,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                suggestedCategories.forEach { catName ->
                    val suggestedEntity = categories.find { it.name == catName }
                    FilterChip(
                        selected = false,
                        onClick = { viewModel.updateCategory(catName) },
                        label = {
                            Text(stringResource(R.string.txn_edit_suggested_chip, catName))
                        },
                        leadingIcon = if (suggestedEntity != null) {
                            { CategoryDot(color = suggestedEntity.color, modifier = Modifier.padding(start = 4.dp)) }
                        } else null,
                        trailingIcon = null
                    )
                }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            FilterChip(
                selected = true,
                onClick = {},
                label = { Text(primaryCategory) },
                leadingIcon = if (catEntity != null) {
                    { CategoryDot(color = catEntity.color, modifier = Modifier.padding(start = 4.dp)) }
                } else null,
                trailingIcon = null
            )
            Box {
                FilterChip(
                    selected = false,
                    onClick = { categoryDropdownExpanded = true },
                    label = { Text("Change") },
                    leadingIcon = null,
                    trailingIcon = null
                )
                DropdownMenu(
                    expanded = categoryDropdownExpanded,
                    onDismissRequest = { categoryDropdownExpanded = false }
                ) {
                    categories.filter { it.name != primaryCategory }.forEach { category ->
                        DropdownMenuItem(
                            text = { CategoryChip(category = category) },
                            onClick = {
                                viewModel.updateCategory(category.name)
                                categoryDropdownExpanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Create new category",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        onClick = {
                            categoryDropdownExpanded = false
                            showCreateCategoryDialog = true
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }

        if (showCreateCategoryDialog) {
            CategoryEditDialog(
                category = null,
                onDismiss = { showCreateCategoryDialog = false },
                onSave = { name, color, isIncome, icon ->
                    viewModel.createCategoryAndSelect(name, color, isIncome, icon)
                    showCreateCategoryDialog = false
                }
            )
        }

        // ── Tags ──
        HorizontalDivider(
            modifier = Modifier.padding(vertical = Spacing.sm),
            thickness = Dimensions.Component.dividerThickness,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
        )
        Text(
            text = "Tags",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Free-form labels for search and organization. Don't affect budgets.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (pendingTags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                pendingTags.forEach { tag ->
                    FilterChip(
                        selected = true,
                        onClick = { viewModel.removePendingTag(tag) },
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
        // Suggestions (filtered by current tagInput, excluding already-added tags)
        if (tagSuggestions.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                tagSuggestions.take(10).forEach { suggestion ->
                    SuggestionChip(
                        onClick = {
                            viewModel.addPendingTag(suggestion)
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
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = {
                        if (tagInput.isNotBlank()) {
                            viewModel.addPendingTag(tagInput)
                            tagInput = ""
                            viewModel.updateTagQuery("")
                        }
                    }
                )
            )
            TextButton(
                onClick = {
                    if (tagInput.isNotBlank()) {
                        viewModel.addPendingTag(tagInput)
                        tagInput = ""
                        viewModel.updateTagQuery("")
                    }
                }
            ) {
                Text("Add")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeField(
    dateTime: LocalDateTime,
    onDateTimeChange: (LocalDateTime) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val dateLinePattern = remember { DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault()) }
    val timePattern = remember { DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FilledTonalButton(
            onClick = { showDatePicker = true },
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = 14.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Date",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = dateTime.format(dateLinePattern),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        FilledTonalButton(
            onClick = { showTimePicker = true },
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = 14.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                )
                Text(
                    text = dateTime.format(timePattern),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateTime.toLocalDate().toEpochDay() * 24 * 60 * 60 * 1000
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val newDate = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                        onDateTimeChange(dateTime.withYear(newDate.year)
                            .withMonth(newDate.monthValue)
                            .withDayOfMonth(newDate.dayOfMonth))
                    }
                    showDatePicker = false
                }) { Text("OK") }
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
            initialHour = dateTime.hour,
            initialMinute = dateTime.minute
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    onDateTimeChange(dateTime.withHour(timePickerState.hour).withMinute(timePickerState.minute))
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyDropdown(
    selectedCurrency: String,
    onCurrencySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    // Common currencies
    val currencies = listOf(
        "INR", "USD", "EUR", "GBP", "AED", "SGD",
        "CAD", "AUD", "JPY", "CNY", "NPR", "ETB",
        "THB", "MYR", "KWD", "KRW"
    )

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        TextField(
            value = "${CurrencyFormatter.getCurrencySymbol(selectedCurrency)} $selectedCurrency",
            onValueChange = { },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            singleLine = true,
            shape = editFullShape,
            colors = editFilledColors()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            currencies.forEach { currency ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                CurrencyFormatter.getCurrencySymbol(currency),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.width(32.dp)
                            )
                            Text(currency)
                        }
                    },
                    onClick = {
                        onCurrencySelected(currency)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountNumberField(
    accountNumber: String?,
    onAccountNumberChange: (String?) -> Unit,
    viewModel: TransactionDetailViewModel,
    label: String = "Account (Optional)",
    placeholder: String = "Select or enter account number",
    excludeAccount: String? = null
) {
    val availableAccounts by viewModel.availableAccounts.collectAsStateWithLifecycle()
    val filteredAccounts = availableAccounts.filter { it.accountLast4 != excludeAccount }
    var expanded by remember { mutableStateOf(false) }
    var selectedAccount by remember(accountNumber) { 
        mutableStateOf(
            availableAccounts.find { 
                accountNumber?.endsWith(it.accountLast4) == true 
            }?.displayName ?: accountNumber ?: ""
        )
    }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        TextField(
            value = selectedAccount,
            onValueChange = { newValue ->
                selectedAccount = newValue
                // If manually typing, update the account number directly
                if (!availableAccounts.any { it.displayName == newValue }) {
                    onAccountNumberChange(newValue.ifEmpty { null })
                }
            },
            label = { Text(label, fontWeight = FontWeight.SemiBold) },
            leadingIcon = {
                Icon(
                    if (availableAccounts.any { it.displayName == selectedAccount && it.isCreditCard }) {
                        Icons.Default.CreditCard
                    } else {
                        Icons.Default.AccountBalance
                    },
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.Icon.medium)
                )
            },
            shape = editFullShape,
            colors = editFilledColors(),
            trailingIcon = {
                Row {
                    // Clear button if there's text
                    if (selectedAccount.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                selectedAccount = ""
                                onAccountNumberChange(null)
                            }
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear",
                                modifier = Modifier.size(Dimensions.Icon.medium)
                            )
                        }
                    }
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable),
            singleLine = true,
            placeholder = { Text(placeholder) }
        )
        
        if (filteredAccounts.isNotEmpty()) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                filteredAccounts.forEach { account ->
                    DropdownMenuItem(
                        text = { 
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    if (account.isCreditCard) Icons.Default.CreditCard 
                                    else Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    modifier = Modifier.size(Dimensions.Icon.medium),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(account.displayName)
                            }
                        },
                        onClick = {
                            selectedAccount = account.displayName
                            onAccountNumberChange(account.accountLast4)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}

// ==================== Mark as Loan Bottom Sheet ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkAsLoanBottomSheet(
    transactionAmount: BigDecimal,
    transactionCurrency: String,
    direction: LoanDirection,
    recentPersonNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (personName: String, note: String?) -> Unit
) {
    var personName by remember { mutableStateOf("") }
    var isAddingNew by remember { mutableStateOf(recentPersonNames.isEmpty()) }
    var note by remember { mutableStateOf("") }

    val isDark = isSystemInDarkTheme()
    val loanColor = if (isDark) loan_dark else loan_light
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val sheetTitle = if (direction == LoanDirection.LENT) "Track money lent" else "Track money borrowed"
 
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
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = "$sheetTitle: ${CurrencyFormatter.formatCurrency(transactionAmount, transactionCurrency)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (direction == LoanDirection.LENT) "Who did you pay for?" else "Who paid for you?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isAddingNew && recentPersonNames.isNotEmpty()) {
                // Pick from existing people
                recentPersonNames.forEach { name ->
                    Surface(
                        onClick = { personName = name },
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                        color = if (personName == name) loanColor.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = if (personName == name) BorderStroke(1.dp, loanColor)
                        else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(loanColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    name.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = loanColor
                                )
                            }
                            Text(
                                name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            if (personName == name) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = loanColor,
                                    modifier = Modifier.size(Dimensions.Icon.small)
                                )
                            }
                        }
                    }
                }

                // Add new person option
                TextButton(onClick = { isAddingNew = true; personName = "" }) {
                    Icon(
                        Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.small)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("New person")
                }
            } else {
                // Text field for new person name
                TextField(
                    value = personName,
                    onValueChange = { personName = it },
                    label = { Text("Person's name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = editFullShape,
                    colors = editFilledColors()
                )
                if (recentPersonNames.isNotEmpty()) {
                    TextButton(onClick = { isAddingNew = false; personName = "" }) {
                        Icon(
                            Icons.Default.People,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.Icon.small)
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("Pick existing")
                    }
                }
            }

            // Optional note
            TextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = editFullShape,
                colors = editFilledColors()
            )

            // Confirm button
            Button(
                onClick = {
                    onConfirm(
                        personName.trim(),
                        note.trim().ifEmpty { null }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = personName.isNotBlank(),
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                colors = ButtonDefaults.buttonColors(
                    containerColor = loanColor
                )
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.Icon.small)
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text("Confirm")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupBottomSheet(
    currentGroup: TransactionGroupEntity?,
    availableGroups: List<TransactionGroupEntity>,
    onDismiss: () -> Unit,
    onAddToGroup: (Long) -> Unit,
    onRemoveFromGroup: () -> Unit,
    onCreateGroup: (String, String?) -> Unit
) {
    var showCreateField by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.Padding.content)
                .padding(bottom = Dimensions.Padding.content)
        ) {
            Text(
                text = "Transaction Group",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = Spacing.md)
            )

            if (currentGroup != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Current: ${currentGroup.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = {
                        onRemoveFromGroup()
                        onDismiss()
                    }) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(bottom = Spacing.md))
            }

            val otherGroups = availableGroups.filter { it.id != currentGroup?.id }
            if (otherGroups.isNotEmpty()) {
                Text(
                    text = if (currentGroup != null) "Move to group" else "Add to group",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.sm)
                )
                otherGroups.forEach { group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAddToGroup(group.id) }
                            .padding(vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(Dimensions.Icon.medium)
                        )
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
            }

            if (showCreateField) {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    label = { Text("Group name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (newGroupName.isNotBlank()) {
                                    onCreateGroup(newGroupName.trim(), null)
                                }
                            },
                            enabled = newGroupName.isNotBlank()
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Create")
                        }
                    }
                )
            } else {
                TextButton(
                    onClick = { showCreateField = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.small)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("Create new group")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitGoalsBottomSheet(
    goals: List<GoalEntity>,
    transactionAmount: BigDecimal,
    existingContributions: List<GoalContributionEntity>,
    onDismiss: () -> Unit,
    onConfirm: (List<Pair<Long, BigDecimal>>) -> Unit
) {
    val selectedAmounts = remember(goals, existingContributions) {
        mutableStateMapOf<Long, String>().apply {
            existingContributions.forEach { c -> put(c.goalId, c.amount.toPlainString()) }
        }
    }
    // Boxes the user has explicitly typed — their values are preserved during auto-recalc.
    // Existing contributions start locked so reopening the sheet keeps intentional splits intact.
    val lockedIds = remember(existingContributions) {
        mutableStateSetOf<Long>().apply { existingContributions.forEach { add(it.goalId) } }
    }

    fun formatShare(v: BigDecimal): String =
        v.setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    fun distributeAmong(ids: List<Long>, amount: BigDecimal) {
        if (ids.isEmpty()) return
        val share = amount.divide(BigDecimal(ids.size), 2, java.math.RoundingMode.FLOOR)
        val lastShare = amount - share.multiply(BigDecimal(ids.size - 1))
        ids.forEachIndexed { i, id ->
            selectedAmounts[id] = formatShare(if (i == ids.lastIndex) lastShare else share)
        }
    }

    // When a box is checked/unchecked — only free (unlocked) boxes absorb the remainder.
    fun redistributeFree() {
        val free = selectedAmounts.keys.filter { it !in lockedIds }
        if (free.isEmpty()) return
        val locked = selectedAmounts.entries
            .filter { it.key in lockedIds }
            .mapNotNull { it.value.toBigDecimalOrNull() }
            .fold(BigDecimal.ZERO) { acc, v -> acc + v }
        val remainder = transactionAmount - locked
        if (remainder < BigDecimal.ZERO) return
        distributeAmong(free, remainder)
    }

    // When user types in anchorId — anchor and other locked boxes stay fixed,
    // all remaining free boxes split the leftover.
    fun redistributeRemainderExcluding(anchorId: Long, anchorAmount: BigDecimal) {
        val free = selectedAmounts.keys.filter { it != anchorId && it !in lockedIds }
        if (free.isEmpty()) return
        val locked = selectedAmounts.entries
            .filter { it.key != anchorId && it.key in lockedIds }
            .mapNotNull { it.value.toBigDecimalOrNull() }
            .fold(BigDecimal.ZERO) { acc, v -> acc + v }
        val remainder = transactionAmount - anchorAmount - locked
        if (remainder < BigDecimal.ZERO) return
        distributeAmong(free, remainder)
    }

    val totalSelected = selectedAmounts.values
        .mapNotNull { it.toBigDecimalOrNull() }
        .fold(BigDecimal.ZERO) { acc, v -> acc + v }
    val remaining = transactionAmount - totalSelected
    val isValid = selectedAmounts.isNotEmpty() && remaining.compareTo(BigDecimal.ZERO) == 0

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = com.spendly.tracker.ui.theme.Dimensions.Padding.content)
                .padding(bottom = com.spendly.tracker.ui.theme.Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(com.spendly.tracker.ui.theme.Spacing.sm)
        ) {
            Text(
                text = "Split Among Goals",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
            Text(
                text = "Transaction: ${com.spendly.tracker.utils.CurrencyFormatter.formatCurrency(transactionAmount)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (goals.isEmpty()) {
                Text(
                    text = "No active goals. Create a goal first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                goals.forEach { goal ->
                    val isSelected = selectedAmounts.containsKey(goal.id)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(com.spendly.tracker.ui.theme.Spacing.sm)
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    lockedIds.remove(goal.id)
                                    selectedAmounts[goal.id] = "0"
                                    redistributeFree()
                                } else {
                                    lockedIds.remove(goal.id)
                                    selectedAmounts.remove(goal.id)
                                    redistributeFree()
                                }
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = goal.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                            )
                            val progress = if (goal.targetAmount > BigDecimal.ZERO)
                                (goal.currentAmount.toFloat() / goal.targetAmount.toFloat()).coerceIn(0f, 1f)
                            else 0f
                            Text(
                                text = "${(progress * 100).toInt()}% complete",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isSelected) {
                            val isLocked = goal.id in lockedIds
                            val pct = selectedAmounts[goal.id]?.toBigDecimalOrNull()
                                ?.takeIf { transactionAmount > BigDecimal.ZERO }
                                ?.let { amt ->
                                    (amt.toDouble() / transactionAmount.toDouble() * 100)
                                        .toInt().coerceIn(0, 100)
                                }
                            Column(horizontalAlignment = Alignment.End) {
                                OutlinedTextField(
                                    value = selectedAmounts[goal.id] ?: "",
                                    onValueChange = { newText ->
                                        lockedIds.add(goal.id)
                                        selectedAmounts[goal.id] = newText
                                        val parsed = newText.toBigDecimalOrNull()
                                        if (parsed != null && parsed >= BigDecimal.ZERO) {
                                            redistributeRemainderExcluding(goal.id, parsed)
                                        }
                                    },
                                    label = { Text(if (isLocked) "Fixed" else "Auto") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.width(120.dp)
                                )
                                if (pct != null) {
                                    Text(
                                        text = "$pct%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }

            val remainingColor = when {
                remaining.compareTo(BigDecimal.ZERO) == 0 -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.error
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Unallocated",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = com.spendly.tracker.utils.CurrencyFormatter.formatCurrency(remaining),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = remainingColor
                )
            }

            Button(
                onClick = {
                    val splits = selectedAmounts.mapNotNull { (goalId, amtText) ->
                        amtText.toBigDecimalOrNull()?.let { goalId to it }
                    }
                    onConfirm(splits)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isValid
            ) {
                Text(if (existingContributions.isEmpty()) "Link to Goals" else "Update Splits")
            }
        }
    }
}


