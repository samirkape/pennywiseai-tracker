package com.spendly.tracker.ui.screens.connector

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendly.tracker.connector.domain.AnalysisWindow
import com.spendly.tracker.connector.domain.WindowUnit
import com.spendly.tracker.ui.components.CustomTitleTopAppBar
import com.spendly.tracker.ui.components.MarkdownText
import com.spendly.tracker.ui.components.cards.SectionHeaderV2
import com.spendly.tracker.ui.components.cards.SpendlyCardV2
import com.spendly.tracker.ui.theme.Dimensions
import com.spendly.tracker.ui.theme.Spacing
import com.spendly.tracker.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

private val QUICK_PROMPTS = listOf(
    "Where is my money going?",
    "Find recurring charges",
    "Compare with the previous period",
    "How can I save money?"
)

/**
 * Lets the user analyse the last X days / months / years of spending with the
 * configured provider (ChatGPT or Claude).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectorAnalysisScreen(
    modifier: Modifier = Modifier,
    viewModel: ConnectorAnalysisViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToConnectorSettings: () -> Unit = {}
) {
    val window by viewModel.window.collectAsStateWithLifecycle()
    val question by viewModel.question.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }

    Scaffold(
        modifier = modifier,
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehavior,
                scrollBehaviorLarge = scrollBehavior,
                title = "Spending Analysis",
                hasBackButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate up",
                        )
                    }
                },
                hazeState = hazeState
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            SectionHeaderV2(title = "Period")
            SpendlyCardV2 {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    AnalysisWindow.PRESETS.forEach { preset ->
                        FilterChip(
                            selected = window == preset,
                            onClick = { viewModel.setWindow(preset) },
                            label = { Text(preset.label()) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    text = "Custom window",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    WindowUnit.entries.forEachIndexed { index, unit ->
                        SegmentedButton(
                            selected = window.unit == unit,
                            onClick = {
                                viewModel.setWindow(AnalysisWindow.coerce(unit, window.count))
                            },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = WindowUnit.entries.size
                            ),
                            label = { Text(unit.label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    TextButton(
                        onClick = {
                            viewModel.setWindow(
                                AnalysisWindow.coerce(window.unit, window.count - 1)
                            )
                        },
                        enabled = window.count > 1
                    ) {
                        Text("−")
                    }
                    Text(
                        text = "${window.count} ${window.unit.label}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    TextButton(
                        onClick = {
                            viewModel.setWindow(
                                AnalysisWindow.coerce(window.unit, window.count + 1)
                            )
                        }
                    ) {
                        Text("+")
                    }
                }
            }

            uiState.preview?.let { preview ->
                SectionHeaderV2(title = "On-device summary")
                SpendlyCardV2 {
                    SummaryRow(
                        label = "Total spend",
                        value = CurrencyFormatter.formatCurrency(preview.totalExpense, preview.currency)
                    )
                    SummaryRow(
                        label = "Transactions",
                        value = preview.expenseCount.toString()
                    )
                    SummaryRow(
                        label = "Average per day",
                        value = CurrencyFormatter.formatCurrency(preview.dailyAverage, preview.currency)
                    )
                    preview.previousExpenseChangePercent?.let { change ->
                        SummaryRow(
                            label = "vs previous period",
                            value = "${if (change >= 0) "+" else ""}${"%.1f".format(change)}%"
                        )
                    }
                    if (preview.categories.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            text = "Top categories",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        preview.categories.take(5).forEach { category ->
                            SummaryRow(
                                label = category.category,
                                value = CurrencyFormatter.formatCurrency(category.amount, preview.currency)
                            )
                        }
                    }
                }
            }

            SectionHeaderV2(title = "Question")
            SpendlyCardV2 {
                OutlinedTextField(
                    value = question,
                    onValueChange = viewModel::setQuestion,
                    label = { Text("What do you want to know? (optional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    QUICK_PROMPTS.forEach { prompt ->
                        FilterChip(
                            selected = question == prompt,
                            onClick = {
                                viewModel.setQuestion(if (question == prompt) "" else prompt)
                            },
                            label = { Text(prompt) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.md))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    if (uiState.isAnalysing) {
                        Button(
                            onClick = viewModel::cancel,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = null,
                                modifier = Modifier.size(Dimensions.Icon.small)
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Stop")
                        }
                    } else {
                        Button(onClick = viewModel::analyse) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(Dimensions.Icon.small)
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Analyse with ${uiState.provider.displayName.substringBefore(" (")}")
                        }
                    }
                    Text(
                        text = uiState.model,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            uiState.errorMessage?.let { error ->
                SectionHeaderV2(title = "Problem")
                SpendlyCardV2 {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    TextButton(onClick = onNavigateToConnectorSettings) {
                        Text("Open connector settings")
                    }
                }
            }

            if (uiState.analysis.isNotBlank()) {
                SectionHeaderV2(title = "Analysis")
                SpendlyCardV2 {
                    MarkdownText(text = uiState.analysis)
                }
            } else if (uiState.isAnalysing) {
                SectionHeaderV2(title = "Analysis")
                SpendlyCardV2 {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(Dimensions.Icon.medium),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(Spacing.md))
                        Text(
                            text = "Asking ${uiState.provider.displayName.substringBefore(" (")}…",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
