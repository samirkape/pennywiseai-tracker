package com.spendly.tracker.presentation.subscriptions
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.spendly.tracker.R
import com.spendly.tracker.data.database.entity.SubscriptionEntity
import com.spendly.tracker.data.repository.SubscriptionRepository
import com.spendly.tracker.ui.components.*
import com.spendly.tracker.ui.components.cards.SpendlyCardV2
import com.spendly.tracker.ui.components.cards.SectionHeaderV2
import com.spendly.tracker.ui.effects.overScrollVertical
import com.spendly.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.spendly.tracker.ui.theme.*
import com.spendly.tracker.utils.CurrencyFormatter
import com.spendly.tracker.utils.formatAmount
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
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
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }
    val lazyListState = rememberLazyListState()
    var hasAnimated by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    val slideOffsetPx = with(density) { 30.dp.roundToPx() }
    LaunchedEffect(Unit) {
        if (!hasAnimated) {
            delay(600)
            hasAnimated = true
        }
    }
    val resources = LocalResources.current
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
    val today = LocalDate.now()
    fun nextPaymentDateFor(sub: SubscriptionEntity): LocalDate? {
        val date = sub.nextPaymentDate ?: return null
        var nextDate = date
        while (nextDate.isBefore(today) || nextDate.isEqual(today)) {
            nextDate = nextDate.plusMonths(1)
        }
        return nextDate
    }
    val upcomingSubscriptions = remember(uiState.activeSubscriptions) {
        uiState.activeSubscriptions
            .filter { sub ->
                val next = nextPaymentDateFor(sub) ?: return@filter false
                ChronoUnit.DAYS.between(today, next) <= 7
            }
            .sortedBy { nextPaymentDateFor(it) }
    }
    val upcomingIds = remember(upcomingSubscriptions) { upcomingSubscriptions.map { it.id }.toSet() }
    val regularSubscriptions = remember(uiState.activeSubscriptions, upcomingIds) {
        uiState.activeSubscriptions.filter { it.id !in upcomingIds }
    }
    // Prepaid plans expiring within 30 days — needs more lead time to decide on renewal
    val expiringPrepaidPlans = remember(uiState.activePrepaidPlans) {
        uiState.activePrepaidPlans
            .filter { plan ->
                val daysLeft = ChronoUnit.DAYS.between(today, plan.endDate)
                daysLeft in 0..30
            }
            .sortedBy { it.endDate }
    }
    val expiringPrepaidIds = remember(expiringPrepaidPlans) { expiringPrepaidPlans.map { it.id }.toSet() }
    val regularPrepaidPlans = remember(uiState.activePrepaidPlans, expiringPrepaidIds) {
        uiState.activePrepaidPlans.filter { it.id !in expiringPrepaidIds }
    }
    val soonestUpcoming = upcomingSubscriptions.firstOrNull()
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
                    bottom = paddingValues.calculateBottomPadding() + Dimensions.Padding.content
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                flingBehavior = rememberOverscrollFlingBehavior { lazyListState }
            ) {
                // Hero summary card
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
                        SubscriptionHeroCard(
                            totalAmount = uiState.totalMonthlyAmount,
                            activeCount = uiState.activeSubscriptions.size,
                            currency = uiState.displayCurrency,
                            soonestUpcoming = soonestUpcoming,
                            nextPaymentDateProvider = ::nextPaymentDateFor,
                            prepaidMonthlyAmount = uiState.prepaidMonthlyAmount,
                            combinedMonthlyAmount = uiState.combinedMonthlyAmount,
                            combinedYearlyAmount = uiState.combinedYearlyAmount,
                            activePrepaidPlanCount = uiState.activePrepaidPlanCount,
                        )
                    }
                }
                // Due Soon section
                if (upcomingSubscriptions.isNotEmpty() || expiringPrepaidPlans.isNotEmpty()) {
                    item {
                        SectionHeaderV2(
                            title = stringResource(R.string.subs_section_due_soon),
                            leading = {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(Dimensions.Icon.medium),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        )
                    }
                    itemsIndexed(
                        items = upcomingSubscriptions,
                        key = { _, item -> "upcoming_${item.id}" }
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
                            SubscriptionListItem(
                                subscription = subscription,
                                convertedAmount = uiState.convertedAmounts[subscription.id],
                                displayCurrency = uiState.displayCurrency,
                                onEdit = { editingSubscription = subscription },
                                onHide = { viewModel.hideSubscription(subscription.id) },
                                onDelete = { deleteTarget = subscription },
                            )
                        }
                    }
                    itemsIndexed(
                        items = expiringPrepaidPlans,
                        key = { _, item -> "expiring_prepaid_${item.id}" }
                    ) { index, plan ->
                        val staggerIndex = upcomingSubscriptions.size + index
                        val visible = remember { mutableStateOf(hasAnimated) }
                        LaunchedEffect(Unit) {
                            if (!hasAnimated) { delay((staggerIndex + 1) * 50L); visible.value = true }
                        }
                        AnimatedVisibility(
                            visible = visible.value,
                            enter = fadeIn(tween(300)) + slideInVertically(
                                initialOffsetY = { slideOffsetPx },
                                animationSpec = tween(300)
                            )
                        ) {
                            PrepaidExpiringItem(plan = plan, today = today)
                        }
                    }
                }
                // Active subscriptions section
                if (regularSubscriptions.isNotEmpty()) {
                    item {
                        SectionHeaderV2(title = stringResource(R.string.subs_section_active))
                    }
                    itemsIndexed(
                        items = regularSubscriptions,
                        key = { _, item -> item.id }
                    ) { index, subscription ->
                        val staggerIndex = upcomingSubscriptions.size + index
                        val visible = remember { mutableStateOf(hasAnimated) }
                        LaunchedEffect(Unit) {
                            if (!hasAnimated) { delay((staggerIndex + 1) * 50L); visible.value = true }
                        }
                        AnimatedVisibility(
                            visible = visible.value,
                            enter = fadeIn(tween(300)) + slideInVertically(
                                initialOffsetY = { slideOffsetPx },
                                animationSpec = tween(300)
                            )
                        ) {
                            SubscriptionListItem(
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
                // Active prepaid plans section (non-expiring)
                if (regularPrepaidPlans.isNotEmpty()) {
                    item {
                        SectionHeaderV2(title = stringResource(R.string.subs_section_active_prepaid))
                    }
                    itemsIndexed(
                        items = regularPrepaidPlans,
                        key = { _, item -> "prepaid_${item.id}" }
                    ) { index, plan ->
                        val staggerIndex = upcomingSubscriptions.size + regularSubscriptions.size + index
                        val visible = remember { mutableStateOf(hasAnimated) }
                        LaunchedEffect(Unit) {
                            if (!hasAnimated) { delay((staggerIndex + 1) * 50L); visible.value = true }
                        }
                        AnimatedVisibility(
                            visible = visible.value,
                            enter = fadeIn(tween(300)) + slideInVertically(
                                initialOffsetY = { slideOffsetPx },
                                animationSpec = tween(300)
                            )
                        ) {
                            PrepaidExpiringItem(plan = plan, today = today)
                        }
                    }
                }
                // Empty state
                if (uiState.activeSubscriptions.isEmpty() && uiState.activePrepaidPlans.isEmpty() && !uiState.isLoading) {
                    item {
                        SpendlyEmptyState(
                            icon = Icons.Default.Subscriptions,
                            headline = stringResource(R.string.subs_empty_headline),
                            description = stringResource(R.string.subs_empty_description),
                        )
                    }
                }
                // Loading skeleton
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
                    } else ""
                    Text(base + hint)
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteSubscriptionPermanently(sub.id)
                            deleteTarget = null
                        }
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
// ─── Hero Summary Card ────────────────────────────────────────────────────────
@Composable
private fun SubscriptionHeroCard(
    totalAmount: BigDecimal,
    activeCount: Int,
    currency: String?,
    soonestUpcoming: SubscriptionEntity?,
    nextPaymentDateProvider: (SubscriptionEntity) -> LocalDate?,
    prepaidMonthlyAmount: BigDecimal = BigDecimal.ZERO,
    combinedMonthlyAmount: BigDecimal = totalAmount,
    combinedYearlyAmount: BigDecimal = totalAmount.multiply(BigDecimal(12)),
    activePrepaidPlanCount: Int = 0,
) {
    val hasPrepaid = activePrepaidPlanCount > 0
    fun format(amount: BigDecimal) =
        if (currency != null) CurrencyFormatter.formatCurrency(amount, currency) else amount.toPlainString()
    val amountColor = if (!isSystemInDarkTheme()) expense_light else expense_dark
    val formattedMonthly = if (hasPrepaid) format(combinedMonthlyAmount) else format(totalAmount)
    val formattedYearly = if (hasPrepaid) format(combinedYearlyAmount)
    else format(totalAmount.multiply(BigDecimal(12)))
    val today = LocalDate.now()
    val upcomingNextDate = soonestUpcoming?.let { nextPaymentDateProvider(it) }
    val daysUntilNext = upcomingNextDate?.let { ChronoUnit.DAYS.between(today, it) }
    SpendlyCardV2(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        contentPadding = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.small),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = Dimensions.Alpha.subtitle),
                    )
                    Text(
                        text = if (hasPrepaid) stringResource(R.string.subs_summary_recurring_title)
                        else stringResource(R.string.subs_summary_monthly_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = Dimensions.Alpha.subtitle),
                    )
                }
                if (activeCount > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.20f),
                        shape = MaterialTheme.shapes.extraSmall,
                    ) {
                        Text(
                            text = pluralStringResource(R.plurals.subs_active_subscription_count, activeCount, activeCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = formattedMonthly,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = amountColor,
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.Icon.small),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = Dimensions.Alpha.subtitle),
                )
                Text(
                    text = stringResource(R.string.subs_summary_yearly, formattedYearly),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = Dimensions.Alpha.subtitle),
                )
            }
            if (hasPrepaid) {
                Spacer(modifier = Modifier.height(Spacing.md))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = Dimensions.Alpha.divider)
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                val breakdownColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = Dimensions.Alpha.subtitle)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = stringResource(R.string.subs_summary_breakdown_subscriptions),
                        style = MaterialTheme.typography.bodySmall,
                        color = breakdownColor,
                    )
                    Text(
                        text = stringResource(R.string.subs_item_per_month).let { "${format(totalAmount)}$it" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.xs))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = stringResource(R.string.subs_summary_breakdown_prepaid, activePrepaidPlanCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = breakdownColor,
                    )
                    Text(
                        text = stringResource(R.string.subs_item_per_month).let { "${format(prepaidMonthlyAmount)}$it" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            if (soonestUpcoming != null && daysUntilNext != null) {
                Spacer(modifier = Modifier.height(Spacing.md))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = Dimensions.Alpha.divider)
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                val urgentColor = if (daysUntilNext <= 1L) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.80f)
                val dueLabelText = when {
                    daysUntilNext == 0L -> stringResource(R.string.subs_item_due_today)
                    daysUntilNext == 1L -> stringResource(R.string.subs_item_due_tomorrow)
                    else -> stringResource(R.string.subs_item_due_in_days, daysUntilNext.toInt())
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.small),
                        tint = urgentColor,
                    )
                    Text(
                        text = stringResource(
                            R.string.subs_hero_next_billing,
                            soonestUpcoming.merchantName,
                            dueLabelText,
                            soonestUpcoming.formatAmount(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = urgentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ─── Subscription List Item ───────────────────────────────────────────────────
@Composable
private fun SubscriptionListItem(
    subscription: SubscriptionEntity,
    convertedAmount: BigDecimal? = null,
    displayCurrency: String? = null,
    onEdit: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
) {
    var showSmsBody by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val isManual = subscription.bankName == SubscriptionRepository.MANUAL_SUBSCRIPTION_BANK
    val hasBankInfo = !subscription.bankName.isNullOrBlank() && !isManual
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        SpendlyCardV2(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = Dimensions.Padding.content,
                            end = Spacing.xs,
                            top = 18.dp,
                            bottom = 18.dp,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BrandIcon(merchantName = subscription.merchantName, size = 40.dp, showBackground = true)

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = subscription.merchantName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // Metadata row: due date chip only — category shown below amount
                        DueDateChip(nextPaymentDate = subscription.nextPaymentDate)
                    }

                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val primaryAmountText = if (convertedAmount != null && displayCurrency != null) {
                            CurrencyFormatter.formatCurrency(convertedAmount, displayCurrency)
                        } else {
                            subscription.formatAmount()
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(
                                text = primaryAmountText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (!isSystemInDarkTheme()) expense_light else expense_dark,
                            )
                            Text(
                                text = stringResource(R.string.subs_item_per_month),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 2.dp),
                            )
                        }
                        if (convertedAmount != null && displayCurrency != null) {
                            Text(
                                text = subscription.formatAmount(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // Category shown compactly below amount
                        if (!subscription.category.isNullOrBlank()) {
                            Text(
                                text = subscription.category!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.subs_item_menu_cd),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                text = { Text(stringResource(R.string.subs_item_menu_edit)) },
                                onClick = { menuExpanded = false; onEdit() },
                            )
                            if (!subscription.smsBody.isNullOrBlank()) {
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) },
                                    text = {
                                        Text(
                                            if (showSmsBody) stringResource(R.string.subs_item_menu_hide_sms)
                                            else stringResource(R.string.subs_item_menu_show_sms)
                                        )
                                    },
                                    onClick = { menuExpanded = false; showSmsBody = !showSmsBody },
                                )
                            }
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null) },
                                text = { Text(stringResource(R.string.subs_item_menu_hide)) },
                                onClick = { menuExpanded = false; onHide() },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                text = { Text(stringResource(R.string.subs_item_menu_delete)) },
                                onClick = { menuExpanded = false; onDelete() },
                                colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error),
                            )
                        }
                    }
                }
                // Bank badge strip — only for SMS-detected subscriptions
                if (hasBankInfo) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimensions.Padding.content, vertical = Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.Icon.small),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = subscription.bankName!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        // Expandable SMS / Notes panel
        AnimatedVisibility(visible = showSmsBody && !subscription.smsBody.isNullOrBlank()) {
            SpendlyCardV2(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                contentPadding = 0.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(Dimensions.Padding.content)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Dimensions.Icon.medium),
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(
                            text = if (isManual) stringResource(R.string.subs_item_notes_title)
                            else stringResource(R.string.subs_item_sms_title),
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
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(Spacing.md),
                        )
                    }
                }
            }
        }
    }
}
// ─── Prepaid Expiring Item ────────────────────────────────────────────────────
@Composable
private fun PrepaidExpiringItem(
    plan: com.spendly.tracker.data.database.entity.PrepaidExpenseEntity,
    today: LocalDate,
) {
    val daysLeft = ChronoUnit.DAYS.between(today, plan.endDate)
    val isExpiringSoon = daysLeft in 0..30
    SpendlyCardV2(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = Dimensions.Padding.content,
                    end = Dimensions.Padding.content,
                    top = 18.dp,
                    bottom = 18.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandIcon(merchantName = plan.merchantName, size = 40.dp, showBackground = true)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = plan.merchantName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Expiry chip
                val expiryLabel = when {
                    daysLeft == 0L -> stringResource(R.string.subs_prepaid_expires_today)
                    daysLeft == 1L -> stringResource(R.string.subs_prepaid_expires_tomorrow)
                    daysLeft > 1L -> stringResource(R.string.subs_prepaid_expires_in_days, daysLeft.toInt())
                    else -> plan.endDate.format(DateTimeFormatter.ofPattern("MMM d"))
                }
                val chipContainer = when {
                    daysLeft <= 1L -> MaterialTheme.colorScheme.errorContainer
                    isExpiringSoon -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                }
                val chipContent = when {
                    daysLeft <= 1L -> MaterialTheme.colorScheme.onErrorContainer
                    isExpiringSoon -> MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Surface(color = chipContainer, shape = MaterialTheme.shapes.extraSmall) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = chipContent,
                        )
                        Text(text = expiryLabel, style = MaterialTheme.typography.labelSmall, color = chipContent)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val amountColor = if (!isSystemInDarkTheme()) expense_light else expense_dark
                Text(
                    text = CurrencyFormatter.formatCurrency(plan.totalAmount, plan.currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = amountColor,
                )
                // Prepaid badge
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.extraSmall,
                ) {
                    Text(
                        text = stringResource(R.string.subs_prepaid_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
                    )
                }
                if (!plan.category.isBlank()) {
                    Text(
                        text = plan.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
// ─── Due Date Chip ────────────────────────────────────────────────────────────
@Composable
private fun DueDateChip(nextPaymentDate: LocalDate?) {
    if (nextPaymentDate == null) {
        Text(
            text = stringResource(R.string.subs_item_no_date),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val today = LocalDate.now()
    var nextDate: LocalDate = nextPaymentDate
    while (nextDate.isBefore(today) || nextDate.isEqual(today)) {
        nextDate = nextDate.plusMonths(1)
    }
    val daysUntil = ChronoUnit.DAYS.between(today, nextDate)
    val (label, containerColor, contentColor) = when {
        daysUntil == 0L -> Triple(
            stringResource(R.string.subs_item_due_today),
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
        daysUntil == 1L -> Triple(
            stringResource(R.string.subs_item_due_tomorrow),
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
        daysUntil in 2..7 -> Triple(
            stringResource(R.string.subs_item_due_in_days, daysUntil.toInt()),
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        else -> Triple(
            nextDate.format(DateTimeFormatter.ofPattern("MMM d")),
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Surface(color = containerColor, shape = MaterialTheme.shapes.extraSmall) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.CalendarToday,
                contentDescription = null,
                modifier = Modifier.size(10.dp),
                tint = contentColor,
            )
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = contentColor)
        }
    }
}
// ─── Edit Dialog ──────────────────────────────────────────────────────────────
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
                    onValueChange = { amountText = it; amountError = false },
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
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(
                            R.string.subs_edit_next_payment,
                            nextDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                        )
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
                        )
                    )
                }
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
                            nextDate = java.time.Instant.ofEpochMilli(ms)
                                .atZone(ZoneId.systemDefault()).toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) { Text(stringResource(R.string.subs_action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.subs_action_cancel))
                }
            },
        ) { DatePicker(state = datePickerState) }
    }
}
// ─── Loading Skeleton ─────────────────────────────────────────────────────────
@Composable
private fun SubscriptionItemSkeleton(modifier: Modifier = Modifier) {
    val placeholderColor = MaterialTheme.colorScheme.surfaceContainerHighest
    SpendlyCardV2(modifier = modifier.fillMaxWidth(), contentPadding = 0.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Dimensions.Padding.content),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(placeholderColor).shimmer())
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Box(modifier = Modifier.width(120.dp).height(14.dp).clip(RoundedCornerShape(Dimensions.CornerRadius.small)).background(placeholderColor).shimmer())
                Box(modifier = Modifier.width(72.dp).height(20.dp).clip(RoundedCornerShape(Dimensions.CornerRadius.small)).background(placeholderColor).shimmer())
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Box(modifier = Modifier.width(64.dp).height(14.dp).clip(RoundedCornerShape(Dimensions.CornerRadius.small)).background(placeholderColor).shimmer())
                Box(modifier = Modifier.width(28.dp).height(10.dp).clip(RoundedCornerShape(Dimensions.CornerRadius.small)).background(placeholderColor).shimmer())
            }
        }
    }
}
