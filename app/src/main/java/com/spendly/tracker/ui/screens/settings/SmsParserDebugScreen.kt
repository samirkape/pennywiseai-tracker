package com.spendly.tracker.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spendly.parser.core.ParsedTransaction
import com.spendly.parser.core.bank.BankParserFactory
import com.spendly.tracker.ui.components.CustomTitleTopAppBar
import com.spendly.tracker.ui.theme.Spacing
import dev.chrisbanes.haze.HazeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsParserDebugScreen(
    onNavigateBack: () -> Unit,
) {
    var smsBody by remember { mutableStateOf("") }
    var sender by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<ParseResult?>(null) }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehavior,
                scrollBehaviorLarge = scrollBehavior,
                title = "SMS Parser Debug",
                hasBackButton = true,
                hasActionButton = false,
                navigationContent = {
                    Box(modifier = Modifier.padding(start = Spacing.md)) {
                        IconButton(
                            onClick = onNavigateBack,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = MaterialTheme.colorScheme.onBackground
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                hazeState = hazeState
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            OutlinedTextField(
                value = sender,
                onValueChange = { sender = it },
                label = { Text("Sender (e.g. HDFCBK, AD-ICICIB)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = smsBody,
                onValueChange = { smsBody = it },
                label = { Text("SMS Body") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp),
                maxLines = 10,
                shape = RoundedCornerShape(12.dp)
            )

            Button(
                onClick = {
                    val trimmedBody = smsBody.trim()
                    val trimmedSender = sender.trim()
                    if (trimmedBody.isBlank()) {
                        result = ParseResult.Error("SMS body is empty.")
                        return@Button
                    }
                    val parser = BankParserFactory.getParser(trimmedSender, trimmedBody)
                    if (parser == null) {
                        result = ParseResult.NoMatch(trimmedSender)
                        return@Button
                    }
                    val parsed = parser.parse(trimmedBody, trimmedSender, System.currentTimeMillis())
                    result = if (parsed != null) ParseResult.Success(parsed) else ParseResult.NoMatch(parser.getBankName())
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.BugReport, contentDescription = null)
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text("Parse SMS")
            }

            result?.let { ParseResultCard(it) }
        }
    }
}

private sealed interface ParseResult {
    data class Success(val transaction: ParsedTransaction) : ParseResult
    data class NoMatch(val detail: String) : ParseResult
    data class Error(val message: String) : ParseResult
}

@Composable
private fun ParseResultCard(result: ParseResult) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = when (result) {
            is ParseResult.Success -> MaterialTheme.colorScheme.secondaryContainer
            is ParseResult.NoMatch -> MaterialTheme.colorScheme.errorContainer
            is ParseResult.Error -> MaterialTheme.colorScheme.errorContainer
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            when (result) {
                is ParseResult.Success -> {
                    val t = result.transaction
                    Text(
                        "Matched: ${t.bankName}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f))
                    DebugRow("Type", t.type.name)
                    DebugRow("Amount", "${t.currency} ${t.amount}")
                    DebugRow("Merchant", t.merchant ?: "—")
                    DebugRow("Account last 4", t.accountLast4 ?: "—")
                    DebugRow("Balance", t.balance?.toString() ?: "—")
                    DebugRow("Reference", t.reference ?: "—")
                    DebugRow("From account", t.fromAccount ?: "—")
                    DebugRow("To account", t.toAccount ?: "—")
                    DebugRow("Transfer kind", t.transferKind ?: "—")
                    DebugRow("Is card", if (t.isFromCard) "Yes" else "No")
                    DebugRow("Credit limit", t.creditLimit?.toString() ?: "—")
                }
                is ParseResult.NoMatch -> {
                    Text(
                        "Not recognized",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        "No parser matched sender \"${result.detail}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                is ParseResult.Error -> {
                    Text(
                        result.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(0.6f)
        )
    }
}
