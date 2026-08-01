package com.spendly.tracker.ui.screens.behavioral

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.spendly.tracker.ui.components.BrandIcon
import com.spendly.tracker.ui.components.CategoryIcon
import com.spendly.tracker.ui.components.SpendlyCard
import com.spendly.tracker.ui.components.cards.SectionHeaderV2
import com.spendly.tracker.ui.icons.CategoryMapping
import com.spendly.tracker.ui.theme.Dimensions
import com.spendly.tracker.ui.theme.Spacing
import com.spendly.tracker.utils.CurrencyFormatter
import java.time.format.DateTimeFormatter

@Composable
fun TagInsightsCard(
    topTags: List<TagData>,
    overlaps: List<CategoryOverlapData>,
    multiTaggedTransactions: List<MultiCategoryTransactionData>,
    currency: String,
    modifier: Modifier = Modifier,
    onOverlapClick: (CategoryOverlapData) -> Unit = {},
    onTransactionClick: (Long) -> Unit = {}
) {
    data class TagPage(val title: String, val subtitle: String)

    val pages = remember(topTags, overlaps, multiTaggedTransactions) {
        buildList {
            if (topTags.isNotEmpty()) add(TagPage("Top Tags", "${topTags.size} tags used"))
            if (overlaps.isNotEmpty()) add(TagPage("Co-occurrence", "${overlaps.size} tag pairs"))
            if (multiTaggedTransactions.isNotEmpty()) {
                add(TagPage("Multi-Tagged", "${multiTaggedTransactions.size} transactions"))
            }
        }
    }
    if (pages.isEmpty()) return

    val pagerState = rememberPagerState { pages.size }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeaderV2(title = "Tag Insights")

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(end = Dimensions.Padding.content),
            pageSpacing = Spacing.sm
        ) { pageIndex ->
            val page = pages[pageIndex]
            SpendlyCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = page.title,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = page.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = Spacing.xs),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    when (page.title) {
                        "Top Tags" -> TopTagsPageContent(tags = topTags, currency = currency)
                        "Co-occurrence" -> CoOccurrencePageContent(
                            overlaps = overlaps,
                            onOverlapClick = onOverlapClick
                        )
                        "Multi-Tagged" -> MultiTaggedPageContent(
                            transactions = multiTaggedTransactions,
                            currency = currency,
                            onTransactionClick = onTransactionClick
                        )
                    }
                }
            }
        }

        if (pages.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                pages.indices.forEach { index ->
                    val isActive = pagerState.currentPage == index
                    val width by animateDpAsState(
                        targetValue = if (isActive) 16.dp else 6.dp,
                        animationSpec = tween(200),
                        label = "tag_dot_$index"
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .width(width)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun TopTagsPageContent(
    tags: List<TagData>,
    currency: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        tags.take(6).forEach { tag ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = Spacing.sm, vertical = 2.dp)
                ) {
                    Text(
                        text = tag.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${tag.transactionCount} txn${if (tag.transactionCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = " · ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = CurrencyFormatter.formatAbbreviated(tag.totalAmount.toDouble(), currency),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun CoOccurrencePageContent(
    overlaps: List<CategoryOverlapData>,
    onOverlapClick: (CategoryOverlapData) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        overlaps.take(5).forEach { overlap ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOverlapClick(overlap) }
                    .padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Box(modifier = Modifier.size(40.dp)) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                (CategoryMapping.categories[overlap.categoryA]?.color ?: Color.Gray)
                                    .copy(alpha = 0.15f)
                            )
                            .align(Alignment.TopStart),
                        contentAlignment = Alignment.Center
                    ) {
                        CategoryIcon(category = overlap.categoryA, size = 16.dp)
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                (CategoryMapping.categories[overlap.categoryB]?.color ?: Color.Gray)
                                    .copy(alpha = 0.15f)
                            )
                            .align(Alignment.BottomEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        CategoryIcon(category = overlap.categoryB, size = 14.dp)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${overlap.categoryA} + ${overlap.categoryB}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${overlap.coOccurrenceCount} transactions together",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                ) {
                    Text(
                        text = "${overlap.coOccurrenceCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun MultiTaggedPageContent(
    transactions: List<MultiCategoryTransactionData>,
    currency: String,
    onTransactionClick: (Long) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        transactions.take(4).forEach { tx ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onTransactionClick(tx.transactionId) }
                    .padding(vertical = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    BrandIcon(merchantName = tx.merchantName, size = 36.dp, showBackground = true)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tx.merchantName,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = tx.dateTime.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = CurrencyFormatter.formatCurrency(tx.amount, tx.currency.ifEmpty { currency }),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    items(tx.categories) { cat ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                            icon = { CategoryIcon(category = cat, size = 12.dp) },
                            border = null,
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    }
                }
            }
        }
    }
}
