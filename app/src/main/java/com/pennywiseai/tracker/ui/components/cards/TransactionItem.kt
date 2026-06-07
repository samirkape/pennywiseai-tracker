package com.pennywiseai.tracker.ui.components.cards

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.view.HapticFeedbackConstants
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransferKind
import com.pennywiseai.tracker.ui.LocalNavAnimatedVisibilityScope
import com.pennywiseai.tracker.ui.LocalSharedTransitionScope
import com.pennywiseai.tracker.ui.components.BrandIcon
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.formatAmount
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionItem(
    transaction: TransactionEntity,
    convertedAmount: BigDecimal? = null,
    displayCurrency: String? = null,
    /** When filtering by category: amount that counts toward that bucket (split portion). */
    categoryDisplayAmount: BigDecimal? = null,
    showDate: Boolean = true,
    profileAccountKeys: Map<Long, Set<String>> = emptyMap(),
    flat: Boolean = false,
    onClick: () -> Unit = {},
    onExcludeToggle: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    /** When no brand logo exists, use this category's icon instead of merchant-name inference. */
    categoryForIconFallback: String? = null,
    /** Custom icon key from database for the category. */
    categoryIconKey: String? = null,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    val view = LocalView.current
    val isDark = isSystemInDarkTheme()
    val amountColor = remember(transaction.transactionType, isDark) {
        when (transaction.transactionType) {
            TransactionType.INCOME -> if (!isDark) income_light else income_dark
            TransactionType.EXPENSE -> if (!isDark) expense_light else expense_dark
            TransactionType.CREDIT -> if (!isDark) credit_light else credit_dark
            TransactionType.TRANSFER -> if (!isDark) transfer_light else transfer_dark
            TransactionType.INVESTMENT -> if (!isDark) investment_light else investment_dark
        }
    }

    val dateTimeFormatter = remember { DateTimeFormatter.ofPattern("d MMM \u00B7 h:mm a") }
    val dateTimeFormatterWithYear = remember { DateTimeFormatter.ofPattern("d MMM yyyy \u00B7 h:mm a") }
    val dateTimeText = remember(transaction.dateTime) {
        val currentYear = LocalDate.now().year
        val formatter = if (transaction.dateTime.year != currentYear) dateTimeFormatterWithYear else dateTimeFormatter
        transaction.dateTime.format(formatter)
    }

    val isEffectivelyBusiness = remember(transaction, profileAccountKeys) {
        val effectiveProfileId = transaction.profileId ?: run {
            if (transaction.bankName != null && transaction.accountNumber != null) {
                val key = "${transaction.bankName}_${transaction.accountNumber}"
                profileAccountKeys.entries.firstOrNull { (_, keys) -> keys.contains(key) }?.key
            } else null
        }
        effectiveProfileId == ProfileEntity.BUSINESS_ID
    }

    val showSplitPortion = categoryDisplayAmount != null

    val isCcBillPayment = transaction.transferKind == TransferKind.CC_BILL_PAYMENT

    val subtitle = remember(transaction, dateTimeText, isEffectivelyBusiness, showSplitPortion, isCcBillPayment) {
        buildList {
            add(dateTimeText)
            when {
                isCcBillPayment -> add("CC Payment")
                transaction.transactionType == TransactionType.CREDIT -> add("Card")
                transaction.transactionType == TransactionType.TRANSFER -> add("Transfer")
                transaction.transactionType == TransactionType.INVESTMENT -> add("Investment")
                else -> {}
            }
            if (showSplitPortion) add("Split")
            if (transaction.isRecurring) add("Recurring")
            if (isEffectivelyBusiness) add("Business")
            if (transaction.isExcludedFromTracking) add("Excluded")
        }.joinToString(" \u00B7 ")
    }

    val amountPrefix = remember(transaction.transactionType) {
        when (transaction.transactionType) {
            TransactionType.INCOME -> "+"
            TransactionType.EXPENSE, TransactionType.CREDIT, TransactionType.INVESTMENT -> "-"
            TransactionType.TRANSFER -> ""
        }
    }

    val amountCurrency = displayCurrency ?: transaction.currency
    val formattedAmount = when {
        showSplitPortion -> CurrencyFormatter.formatCurrency(categoryDisplayAmount!!, amountCurrency)
        convertedAmount != null && displayCurrency != null ->
            CurrencyFormatter.formatCurrency(convertedAmount, displayCurrency)
        else -> transaction.formatAmount()
    }

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current

    @Composable
    fun RowContent(cardModifier: Modifier) {
        ListItemCardV2(
        title = transaction.merchantName,
        subtitle = subtitle,
        amount = "$amountPrefix$formattedAmount",
        amountColor = amountColor,
        flat = flat,
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onClick()
        },
        modifier = cardModifier.alpha(if (transaction.isExcludedFromTracking) 0.45f else 1f),
        leadingContent = {
            val iconModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
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
                modifier = iconModifier,
                size = Dimensions.Icon.list + 2.dp,
                showBackground = true,
                categoryOverride = categoryForIconFallback,
                iconKey = categoryIconKey,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    showSplitPortion -> {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "$amountPrefix$formattedAmount",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = amountColor
                            )
                            Text(
                                text = "(of ${transaction.formatAmount()})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    convertedAmount != null && displayCurrency != null -> {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "$amountPrefix${CurrencyFormatter.formatCurrency(convertedAmount, displayCurrency)}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = amountColor
                            )
                            Text(
                                text = "(${transaction.formatAmount()})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = "$amountPrefix$formattedAmount",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = amountColor
                        )
                    }
                }
                if (onExcludeToggle != null || onDelete != null) {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            onExcludeToggle?.let { action ->
                                DropdownMenuItem(
                                    text = {
                                        Text(if (transaction.isExcludedFromTracking) "Include in tracking" else "Exclude from tracking")
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (transaction.isExcludedFromTracking) Icons.Default.Visibility else Icons.Default.Block,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = { action(); showMenu = false }
                                )
                            }
                            onDelete?.let { action ->
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = { action(); showMenu = false }
                                )
                            }
                        }
                    }
                }
            }
        }
    )
    }

    RowContent(modifier)
}
