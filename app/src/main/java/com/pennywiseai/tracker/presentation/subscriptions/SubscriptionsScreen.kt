package com.pennywiseai.tracker.presentation.subscriptions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.theme.Dimensions
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.ui.components.*
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.components.cards.SummaryCardV2
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.theme.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.formatAmount
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    viewModel: SubscriptionsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onAddSubscriptionClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var editingSubscription by remember { mutableStateOf<SubscriptionEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<SubscriptionEntity?>(null) }

    // Scroll behaviors for collapsible TopAppBar
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }
    val lazyListState = rememberLazyListState()

    // Staggered entrance animation state — only animates on first composition
    var hasAnimated by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    val slideOffsetPx = with(density) { 30.dp.roundToPx() }

    // Mark entrance animation as complete after all stagger delays have fired
    LaunchedEffect(Unit) {
        if (!hasAnimated) {
            delay(600) // slightly after the last possible stagger
            hasAnimated = true
        }
    }

    val resources = LocalResources.current

    // Show snackbar when subscription is hidden
    LaunchedEffect(uiState.lastHiddenSubscription) {
        uiState.lastHiddenSubscription?.let { subscription ->
            val result = snackbarHostState.showSnackbar(
                message = resources.getString(R.string.subs_snackbar_hidden, subscription.merchantName),
                actionLabel = resources.getString(R.string.subs_snackbar_undo),
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoHide()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = stringResource(R.string.subs_screen_title),
                hazeState = hazeState
            )
        },
        floatingActionButton = {
            SmallFloatingActionButton(
                onClick = onAddSubscriptionClick,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.subs_fab_add_cd)
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .hazeSource(hazeState)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            flingBehavior = rememberOverscrollFlingBehavior { lazyListState }
        ) {
            // Total Monthly Subscriptions Summary (0ms delay)
            item {
                val visible = remember { mutableStateOf(hasAnimated) }
                LaunchedEffect(Unit) {
                    if (!hasAnimated) { delay(0); visible.value = true }
                }
                AnimatedVisibility(
                    visible = visible.value,
                    enter = fadeIn(tween(300)) + slideInVertically(
                        initialOffsetY = { slideOffsetPx },
                        animationSpec = tween(300)
                    )
                ) {
                    TotalSubscriptionsSummary(
                        totalAmount = uiState.totalMonthlyAmount,
                        activeCount = uiState.activeSubscriptions.size,
                        currency = uiState.displayCurrency
                    )
                }
            }

            // Active Subscriptions (staggered 50ms per item, starting at 50ms)
            if (uiState.activeSubscriptions.isNotEmpty()) {
                item {
                    SectionHeaderV2(title = stringResource(R.string.subs_section_active))
                }
                itemsIndexed(
                    items = uiState.activeSubscriptions,
                    key = { _, item -> item.id }
                ) { index, subscription ->
                    val visible = remember { mutableStateOf(hasAnimated) }
                    LaunchedEffect(Unit) {
                        if (!hasAnimated) { delay((index + 1) * 50L); visible.value = true }
                    }
                    AnimatedVisibility(
                        visible = visible.value,
                        enter = fadeIn(tween(300)) + slideInVertically(
                            initialOffsetY = { slideOffsetPx },
                            animationSpec = tween(300)
                        )
                    ) {
                        SwipeableSubscriptionItem(
                            subscription = subscription,
                            convertedAmount = uiState.convertedAmounts[subscription.id],
                            displayCurrency = uiState.displayCurrency,
                            onEdit = { editingSubscription = subscription },
                            onHide = { viewModel.hideSubscription(subscription.id) },
                            onDelete = { deleteTarget = subscription },
                        )
                    }
                }
            }

            // Empty State
            if (uiState.activeSubscriptions.isEmpty() && !uiState.isLoading) {
                item {
                    PennyWiseEmptyState(
                        icon = Icons.Default.Subscriptions,
                        headline = stringResource(R.string.subs_empty_headline),
                        description = stringResource(R.string.subs_empty_description),
                    )
                }
            }

            // Loading State
            if (uiState.isLoading) {
                items(5) {
                    SubscriptionItemSkeleton()
                }
            }
        }
        }

        editingSubscription?.let { sub ->
            SubscriptionEditDialog(
                subscription = sub,
                onDismiss = { editingSubscription = null },
                onSave = { updated ->
                    viewModel.saveSubscriptionEdits(updated)
                    editingSubscription = null
                },
            )
        }

        deleteTarget?.let { sub ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text(stringResource(R.string.subs_delete_title)) },
                text = {
                    val base = stringResource(R.string.subs_delete_message, sub.merchantName)
                    val hint = if (sub.bankName == SubscriptionRepository.MANUAL_SUBSCRIPTION_BANK) {
                        "\n\n" + stringResource(R.string.subs_delete_message_recurring_hint)
                    } else {
                        ""
                    }
                    Text(base + hint)
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteSubscriptionPermanently(sub.id)
                            deleteTarget = null
                        },
                    ) { Text(stringResource(R.string.subs_delete_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) {
                        Text(stringResource(R.string.subs_action_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun TotalSubscriptionsSummary(
    totalAmount: BigDecimal,
    activeCount: Int,
    currency: String? = null
) {
    val amountColor = if (!isSystemInDarkTheme()) expense_light else expense_dark

    SummaryCardV2(
        title = stringResource(R.string.subs_summary_monthly_title),
        amount = if (currency != null) {
            CurrencyFormatter.formatCurrency(totalAmount, currency)
        } else {
            totalAmount.toPlainString()
        },
        subtitle = pluralStringResource(
            R.plurals.subs_active_subscription_count,
            activeCount,
            activeCount,
        ),
        amountColor = amountColor,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubscriptionEditDialog(
    subscription: SubscriptionEntity,
    onDismiss: () -> Unit,
    onSave: (SubscriptionEntity) -> Unit,
) {
    var merchant by remember(subscription.id) { mutableStateOf(subscription.merchantName) }
    var amountText by remember(subscription.id) {
        mutableStateOf(subscription.amount.stripTrailingZeros().toPlainString())
    }
    var category by remember(subscription.id) { mutableStateOf(subscription.category.orEmpty()) }
    var nextDate by remember(subscription.id) {
        mutableStateOf(subscription.nextPaymentDate ?: LocalDate.now().plusMonths(1))
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.subs_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text(stringResource(R.string.subs_edit_merchant)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        amountError = false
                    },
                    isError = amountError,
                    label = { Text(stringResource(R.string.subs_edit_amount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(stringResource(R.string.subs_edit_category_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            R.string.subs_edit_next_payment,
                            nextDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                        ),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amt = runCatching { BigDecimal(amountText.trim()) }.getOrNull()
                    if (merchant.isBlank() || amt == null || amt <= BigDecimal.ZERO) {
                        amountError = true
                        return@TextButton
                    }
                    onSave(
                        subscription.copy(
                            merchantName = merchant.trim(),
                            amount = amt,
                            category = category.trim().ifBlank { null },
                            nextPaymentDate = nextDate,
                            updatedAt = LocalDateTime.now(),
                        ),
                    )
                },
            ) { Text(stringResource(R.string.subs_action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.subs_action_cancel)) }
        },
    )

    if (showDatePicker) {
        val millisInitial = nextDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = millisInitial)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { ms ->
                            val picked = java.time.Instant.ofEpochMilli(ms)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            nextDate = picked
                        }
                        showDatePicker = false
                    },
                ) { Text(stringResource(R.string.subs_action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.subs_action_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun SwipeableSubscriptionItem(
    subscription: SubscriptionEntity,
    convertedAmount: BigDecimal? = null,
    displayCurrency: String? = null,
    onEdit: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
) {
    var showSmsBody by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        PennyWiseCardV2(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimensions.Padding.content),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandIcon(
                    merchantName = subscription.merchantName,
                    size = 48.dp,
                    showBackground = true,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = Spacing.sm),
                ) {
                    Text(
                        text = subscription.merchantName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!subscription.smsBody.isNullOrBlank()) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Chat,
                                contentDescription = stringResource(R.string.subs_item_sms_available_cd),
                                modifier = Modifier.size(Dimensions.Icon.small),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        val today = LocalDate.now()
                        val subscriptionDate = subscription.nextPaymentDate
                        if (subscriptionDate == null) {
                            Text(
                                text = stringResource(R.string.subs_item_no_date),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            var nextPaymentDate: LocalDate = subscriptionDate
                            while (nextPaymentDate.isBefore(today) || nextPaymentDate.isEqual(today)) {
                                nextPaymentDate = nextPaymentDate.plusMonths(1)
                            }
                            val daysUntilNext = ChronoUnit.DAYS.between(today, nextPaymentDate)
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                modifier = Modifier.size(Dimensions.Icon.small),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = when {
                                    daysUntilNext == 0L -> stringResource(R.string.subs_item_due_today)
                                    daysUntilNext == 1L -> stringResource(R.string.subs_item_due_tomorrow)
                                    daysUntilNext in 2..7 -> stringResource(
                                        R.string.subs_item_due_in_days,
                                        daysUntilNext.toInt(),
                                    )
                                    else -> nextPaymentDate.format(DateTimeFormatter.ofPattern("MMM d"))
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    daysUntilNext <= 3 -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        subscription.category?.let { category ->
                            Text(
                                text = stringResource(R.string.subs_item_category_bullet, category),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (convertedAmount != null && displayCurrency != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = CurrencyFormatter.formatCurrency(convertedAmount, displayCurrency),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (!isSystemInDarkTheme()) expense_light else expense_dark,
                            )
                            Text(
                                text = "(${subscription.formatAmount()})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text(
                            text = subscription.formatAmount(),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (!isSystemInDarkTheme()) expense_light else expense_dark,
                        )
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.subs_item_menu_cd),
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.subs_item_menu_edit)) },
                                onClick = {
                                    menuExpanded = false
                                    onEdit()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.subs_item_menu_hide)) },
                                onClick = {
                                    menuExpanded = false
                                    onHide()
                                },
                            )
                            if (!subscription.smsBody.isNullOrBlank()) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (showSmsBody) {
                                                stringResource(R.string.subs_item_menu_hide_sms)
                                            } else {
                                                stringResource(R.string.subs_item_menu_show_sms)
                                            },
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        showSmsBody = !showSmsBody
                                    },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.subs_item_menu_delete)) },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                },
                                colors = MenuDefaults.itemColors(
                                    textColor = MaterialTheme.colorScheme.error,
                                ),
                            )
                        }
                    }
                }
            }
        }
        if (showSmsBody && !subscription.smsBody.isNullOrBlank()) {
            PennyWiseCardV2(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                contentPadding = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimensions.Padding.content),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Dimensions.Icon.medium),
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(
                            text = if (subscription.bankName == SubscriptionRepository.MANUAL_SUBSCRIPTION_BANK) {
                                stringResource(R.string.subs_item_notes_title)
                            } else {
                                stringResource(R.string.subs_item_sms_title)
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = subscription.smsBody ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            modifier = Modifier.padding(Spacing.md),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionItemSkeleton(
    modifier: Modifier = Modifier
) {
    val placeholderColor = MaterialTheme.colorScheme.surfaceContainerHighest

    PennyWiseCardV2(
        modifier = modifier.fillMaxWidth(),
        contentPadding = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Padding.content),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circle placeholder for BrandIcon (48dp)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(placeholderColor)
                    .shimmer()
            )

            // Two stacked rectangles for merchant name + metadata
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(Dimensions.CornerRadius.small))
                        .background(placeholderColor)
                        .shimmer()
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Box(
                    modifier = Modifier
                        .width(90.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(Dimensions.CornerRadius.small))
                        .background(placeholderColor)
                        .shimmer()
                )
            }

            // Right-aligned amount rectangle
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(Dimensions.CornerRadius.small))
                    .background(placeholderColor)
                    .shimmer()
            )
        }
    }
}

