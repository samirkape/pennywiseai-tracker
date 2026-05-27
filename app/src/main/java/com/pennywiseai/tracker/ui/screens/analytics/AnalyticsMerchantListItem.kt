package com.pennywiseai.tracker.ui.screens.analytics

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.components.BrandIcon
import com.pennywiseai.tracker.ui.components.cards.ListItemCardV2

@Composable
fun AnalyticsMerchantListItem(
    merchant: MerchantData,
    currency: String,
    onClick: () -> Unit = {},
) {
    val subtitle = buildString {
        append("${merchant.transactionCount} ")
        append(if (merchant.transactionCount == 1) "transaction" else "transactions")
        if (merchant.isSubscription) {
            append(" • Subscription")
        }
    }

    ListItemCardV2(
        leadingContent = {
            BrandIcon(
                merchantName = merchant.name,
                size = 48.dp,
                showBackground = true,
            )
        },
        title = merchant.name,
        subtitle = subtitle,
        amount = com.pennywiseai.tracker.utils.CurrencyFormatter.formatCurrency(merchant.amount, currency),
        onClick = onClick,
    )
}
