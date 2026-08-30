package com.spendly.tracker.ui.screens.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LightbulbCircle
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.domain.model.InsightConfidence
import com.spendly.tracker.ui.components.cards.SpendlyCardV2
import com.spendly.tracker.ui.theme.Spacing
import com.spendly.tracker.ui.theme.income_light
import com.spendly.tracker.ui.theme.income_dark
import com.spendly.tracker.ui.theme.expense_light
import com.spendly.tracker.ui.theme.expense_dark
import com.spendly.tracker.ui.theme.transfer_light
import com.spendly.tracker.ui.theme.transfer_dark
import com.spendly.tracker.utils.CurrencyFormatter
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightDetailScreen(
    viewModel: InsightDetailViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onTransactionClick: (Long) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Insight Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        val insight = uiState.insight

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (insight == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "This insight is no longer available.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        val accentColor = insightColor(insight)

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // ── Hero header: icon, badge, headline metric ──────────────────
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = insightIcon(insight.type),
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(Spacing.sm))
                        Column {
                            Text(
                                text = insightLabel(insight.type),
                                style = MaterialTheme.typography.labelLarge,
                                color = accentColor,
                                fontWeight = FontWeight.SemiBold
                            )
                            ConfidenceLabel(insight.confidence)
                        }
                    }
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        text = insight.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = insight.primaryValue,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                        if (insight.secondaryText.isNotBlank()) {
                            Spacer(Modifier.width(Spacing.sm))
                            Text(
                                text = insight.secondaryText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                }
            }

            // ── Narrative paragraph ─────────────────────────────────────────
            item {
                Text(
                    text = uiState.narrative,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // ── Breakdown bars (reuses the same visual as the insight card) ─
            if (uiState.breakdownItems.isNotEmpty()) {
                item {
                    val maxMetric = remember(uiState.breakdownItems) {
                        uiState.breakdownItems.maxOf { it.second.stripToFloat() }.coerceAtLeast(1f)
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f))
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Text(
                            text = uiState.breakdownTitle.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        uiState.breakdownItems.forEachIndexed { index, (label, value) ->
                            BreakdownRow(
                                item = BreakdownItem(label, value, value, value.stripToFloat()),
                                index = index,
                                maxMetric = maxMetric,
                                accentColor = accentColor,
                                showAmount = true
                            )
                        }
                    }
                }
            }

            // ── How we know ──────────────────────────────────────────────
            item {
                SpendlyCardV2(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LightbulbCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(Spacing.xs))
                            Text(
                                text = "How we know",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            text = uiState.howWeKnow,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Feedback ─────────────────────────────────────────────────
            item {
                FeedbackRow(
                    feedback = uiState.feedback,
                    onHelpful = { viewModel.submitFeedback(true) },
                    onNotHelpful = { viewModel.submitFeedback(false) },
                )
            }

            // ── Contributing transactions ───────────────────────────────
            if (uiState.contributingTransactions.isNotEmpty()) {
                item {
                    Text(
                        text = "Transactions (${uiState.contributingTransactions.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(uiState.contributingTransactions, key = { it.id }) { txn ->
                    ContributingTransactionRow(
                        transaction = txn,
                        accentColor = accentColor,
                        onClick = { onTransactionClick(txn.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfidenceLabel(confidence: InsightConfidence) {
    val text = when (confidence) {
        InsightConfidence.HIGH -> "High confidence"
        InsightConfidence.MEDIUM -> "Medium confidence"
        InsightConfidence.LOW -> "Low confidence"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun FeedbackRow(
    feedback: String?,
    onHelpful: () -> Unit,
    onNotHelpful: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        OutlinedButton(
            onClick = onHelpful,
            modifier = Modifier.weight(1f),
            colors = if (feedback == "up") {
                ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                )
            } else ButtonDefaults.outlinedButtonColors()
        ) {
            Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text("Helpful")
        }
        OutlinedButton(
            onClick = onNotHelpful,
            modifier = Modifier.weight(1f),
            colors = if (feedback == "down") {
                ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                )
            } else ButtonDefaults.outlinedButtonColors()
        ) {
            Icon(Icons.Default.ThumbDown, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text("Not helpful")
        }
    }
}

private val txnDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

@Composable
private fun ContributingTransactionRow(
    transaction: TransactionEntity,
    accentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    SpendlyCardV2(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = Spacing.md
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = transaction.merchantName.take(1).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = accentColor
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.merchantName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${transaction.category} • ${transaction.dateTime.toLocalDate().format(txnDateFormatter)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val isDark = isSystemInDarkTheme()
            val amountPrefix = when (transaction.transactionType) {
                TransactionType.INCOME -> "+"
                TransactionType.TRANSFER -> ""
                else -> "-"
            }
            val amountColor = when (transaction.transactionType) {
                TransactionType.INCOME -> if (!isDark) income_light else income_dark
                TransactionType.TRANSFER -> if (!isDark) transfer_light else transfer_dark
                else -> if (!isDark) expense_light else expense_dark
            }
            Text(
                text = "$amountPrefix${CurrencyFormatter.formatCurrency(transaction.amount)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = amountColor
            )
        }
    }
}
