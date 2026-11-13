package com.spendly.tracker.presentation.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spendly.tracker.ui.theme.Dimensions
import com.spendly.tracker.ui.theme.Spacing
import com.spendly.tracker.utils.CompareOp
import com.spendly.tracker.utils.PendingSuggestion
import com.spendly.tracker.utils.SearchField
import com.spendly.tracker.utils.SearchNode
import com.spendly.tracker.utils.appendClauseText
import com.spendly.tracker.utils.parseFlatAndChain
import com.spendly.tracker.utils.removeFlatClause
import com.spendly.tracker.utils.sanitizeQueryValue
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Guided (wizard-style) UI for Kibana-like transaction queries.
 *
 * A single horizontal row shows the currently parsed query as removable
 * clause chips, followed by launcher chips (`+ Merchant`, `+ Amount`, …).
 * Tapping a launcher opens a bottom sheet offering tappable real values
 * (merchant names, categories, tags, accounts, date presets or an amount
 * builder); picking one appends a clause to the search query string, joined
 * with an explicit `and`.
 *
 * The query string remains the single source of truth — users can still type
 * freely (and edit manually) in the search bar at any time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionSearchAssistant(
    query: String,
    merchants: List<String>,
    categories: List<String>,
    tags: List<String>,
    accountKeys: Set<String>,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var wizardField by rememberSaveable { mutableStateOf<SearchField?>(null) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val clauses = remember(query) { parseFlatAndChain(query).orEmpty() }
        clauses.forEachIndexed { index, node ->
            AssistChip(
                onClick = onRemove@{ onQueryChange(removeFlatClause(query, index)) },
                label = {
                    Text(clauseChipText(node), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                trailingIcon = {
                    Icon(
                        Icons.Filled.Cancel,
                        contentDescription = "Remove filter",
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
        if (clauses.isNotEmpty()) {
            Spacer(Modifier.width(Spacing.xs))
        }
        SEARCH_LAUNCHERS.forEach { launcher ->
            AssistChip(
                onClick = { wizardField = launcher.field },
                label = { Text(launcher.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = {
                    Icon(
                        launcher.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
    }

    wizardField?.let { field ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { wizardField = null },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            TransactionQueryFieldPicker(
                field = field,
                merchants = merchants,
                categories = categories,
                tags = tags,
                accountKeys = accountKeys,
                onAppendClause = { clause ->
                    onQueryChange(appendClauseText(query, clause))
                    wizardField = null
                },
            )
        }
    }
}

/**
 * The per-field wizard step: pick a value (or build a comparison) and the
 * corresponding clause is appended to the query.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TransactionQueryFieldPicker(
    field: SearchField,
    merchants: List<String>,
    categories: List<String>,
    tags: List<String>,
    accountKeys: Set<String>,
    onAppendClause: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Dimensions.Padding.content)
            .padding(top = Spacing.sm, bottom = Spacing.lg)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "${field.label} filter",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = pickerHint(field),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.md))

        when (field) {
            SearchField.MERCHANT -> MerchantPicker(merchants, onAppendClause)
            SearchField.CATEGORY -> MultiValuesPicker(
                hint = if (categories.isEmpty()) "No categories yet." else null,
                values = categories,
                previewLabel = field.label,
                chipLabel = { it },
                toClause = { name -> suggestionClause(field, name).orEmpty() },
                onAppendClause = onAppendClause,
            )
            SearchField.TYPE -> ValuesFlowPicker(
                hint = null,
                values = TYPE_VALUES,
                toClause = { name -> suggestionClause(field, name).orEmpty() },
                onAppendClause = onAppendClause,
            )
            SearchField.DATE -> Column {
                ValuesFlowPicker(
                    values = DATE_PRESETS,
                    hint = null,
                    toClause = { label -> suggestionClause(field, label).orEmpty() },
                    onAppendClause = onAppendClause,
                )
                Spacer(Modifier.height(Spacing.md))
                Text(
                    "Or a custom range",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.xs))
                CustomRangePicker(onAppendClause)
            }
            SearchField.AMOUNT -> AmountPicker(onAppendClause)
            SearchField.TAG -> ValuesFlowPicker(
                hint = if (tags.isEmpty()) "No tags yet — add tags from a transaction's details."
                else null,
                values = tags,
                toClause = { tag -> suggestionClause(field, tag).orEmpty() },
                onAppendClause = onAppendClause,
            )
            SearchField.ACCOUNT -> MultiValuesPicker(
                hint = if (accountKeys.isEmpty()) "No accounts or cards found for this period." else null,
                values = accountKeys.sorted(),
                previewLabel = "${field.label}s",
                chipLabel = { key -> accountChipLabel(key) },
                toClause = { key -> accountClause(key).orEmpty() },
                onAppendClause = onAppendClause,
            )
            else -> Text(
                text = "Type this filter directly in the search bar, e.g. ${field.primaryName()}:value",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun pickerHint(field: SearchField): String = when (field) {
    SearchField.MERCHANT -> "Pick a merchant — only transactions with this exact merchant name match."
    SearchField.CATEGORY -> "Pick a category to see only transactions in it."
    SearchField.TYPE -> "Pick a transaction type."
    SearchField.DATE -> "Pick a period to restrict results to."
    SearchField.AMOUNT -> "Choose a comparison and enter an amount, or a between range."
    SearchField.TAG -> "Pick a tag to see only transactions tagged with it."
    SearchField.ACCOUNT -> "Pick an account or card (matches the last 4 digits)."
    else -> "Pick a value for ${field.label}."
}

// ---------------------------------------------------------------------------
// Launchers
// ---------------------------------------------------------------------------

private class FieldLauncher(val field: SearchField, val icon: ImageVector) {
    val label = "+ ${field.label}"
}

private val SEARCH_LAUNCHERS = listOf(
    FieldLauncher(SearchField.MERCHANT, Icons.Default.Store),
    FieldLauncher(SearchField.CATEGORY, Icons.Default.Category),
    FieldLauncher(SearchField.AMOUNT, Icons.Default.Payments),
    FieldLauncher(SearchField.DATE, Icons.Default.CalendarMonth),
    FieldLauncher(SearchField.TYPE, Icons.Default.SwapHoriz),
    FieldLauncher(SearchField.TAG, Icons.Default.Sell),
    FieldLauncher(SearchField.ACCOUNT, Icons.Default.AccountBalance),
)

/** `merchant:"Netflix"` renders as `Merchant: Netflix ✕`; close removes the clause. */
private fun clauseChipText(node: SearchNode): String = when (node) {
    is SearchNode.Field -> {
        val op = when (node.op) {
            CompareOp.EQ -> ""
            CompareOp.GT -> "> "
            CompareOp.GTE -> "≥ "
            CompareOp.LT -> "< "
            CompareOp.LTE -> "≤ "
        }
        val value = op + node.value.ifBlank { "*" }
        if (node.field == SearchField.AMOUNT && node.op != CompareOp.EQ) {
            "Amount $value"
        } else {
            "${node.field.label}: $value"
        }
    }
    is SearchNode.FreeText -> "\"${node.text}\""
    is SearchNode.Not -> {
        val innerText = clauseChipText(node.inner)
        "Not " + innerText.replaceFirstChar { it.lowercaseChar() }
    }
    is SearchNode.And -> clauseChipText(node.left)
    is SearchNode.Or -> {
        // OR group of same-shape clauses renders as one chip, e.g.
        // `Category: Groceries or Travel` (created by the multi-select builders).
        val fields = orGroupFields(node)
        if (fields != null) {
            val labels = fields.map { it.field.label }.distinct()
            val values = fields.joinToString(" or ") { it.value }
            if (labels.size == 1) "${labels[0]}: $values" else "${labels.joinToString("/")} $values"
        } else {
            "Group"
        }
    }
}

/** Leaf field clauses of an OR group; null when the group contains anything else. */
private fun orGroupFields(node: SearchNode): List<SearchNode.Field>? = when (node) {
    is SearchNode.Field -> listOf(node)
    is SearchNode.Or -> orGroupFields(node.left)?.let { l -> orGroupFields(node.right)?.let { r -> l + r } }
    else -> null
}

// ---------------------------------------------------------------------------
// Value pickers
// ---------------------------------------------------------------------------

private val TYPE_VALUES = listOf("Expense", "Income", "Credit", "Transfer", "Investment")

private val DATE_PRESETS = listOf(
    "Today", "Yesterday", "This week", "This month", "This year",
    "Last 7 days", "Last 30 days", "Last 90 days",
)

/** Canonical clause text for a suggested value; reused by pickers and type-ahead. */
internal fun suggestionClause(field: SearchField, value: String): String? = when (field) {
    SearchField.MERCHANT -> "merchant:\"${sanitizeQueryValue(value)}\""
    SearchField.CATEGORY -> "category:\"${sanitizeQueryValue(value)}\""
    SearchField.TAG -> "tag:\"${sanitizeQueryValue(value)}\""
    SearchField.TYPE -> "type:${value.lowercase().trim()}"
    SearchField.DATE -> {
        val normalized = value.lowercase().replace(" ", "_")
        if (normalized.endsWith("_days")) "date:\"${value.lowercase()}\"" else "date:$normalized"
    }
    else -> null
}

/** Tappable chips of predefined values; picking one appends its clause. */
@Composable
private fun ValuesFlowPicker(
    values: List<String>,
    hint: String?,
    toClause: (String) -> String,
    onAppendClause: (String) -> Unit,
) {
    if (hint != null) {
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.sm))
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        values.forEach { value ->
            FilterChip(
                selected = false,
                onClick = { onAppendClause(toClause(value)) },
                label = { Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

/** Searchable list of merchant names; a tap adds an exact merchant clause. */
@Composable
private fun MerchantPicker(
    merchants: List<String>,
    onAppendClause: (String) -> Unit,
) {
    var filter by remember { mutableStateOf("") }

    Column {
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            placeholder = { Text("Filter list…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.sm))

        val filtered = merchants.filter { it.contains(filter, ignoreCase = true) }
        if (filtered.isEmpty()) {
            Text(
                text = "No matching merchants yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(filtered) { merchant ->
                    ListItem(
                        headlineContent = {
                            Text(merchant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onAppendClause("merchant:\"${sanitizeQueryValue(merchant)}\"")
                            },
                    )
                }
            }
        }
    }
}

private enum class AmountMode(val symbol: String, val queryOp: String?) {
    GREATER(">", ">"),
    GREATER_OR_EQUAL("≥", ">="),
    LESS("<", "<"),
    LESS_OR_EQUAL("≤", "<="),
    EQUAL("=", null),
    BETWEEN("between", null)
}

/** Amount filter builder: comparison chips + numeric input(s) + Apply button. */
@Composable
private fun AmountPicker(onAppendClause: (String) -> Unit) {
    var mode by remember { mutableStateOf(AmountMode.GREATER) }
    var lowText by remember { mutableStateOf("") }
    var highText by remember { mutableStateOf("") }

    val cleanedLow = lowText.filter { it.isDigit() || it == '.' }
    val cleanedHigh = highText.filter { it.isDigit() || it == '.' }
    val valid = when (mode) {
        AmountMode.BETWEEN -> isAmount(cleanedLow) && isAmount(cleanedHigh)
        else -> isAmount(cleanedLow)
    }

    Column {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            AmountMode.entries.forEach { candidate ->
                FilterChip(
                    selected = mode == candidate,
                    onClick = { mode = candidate },
                    label = { Text(candidate.symbol) },
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))

        val label = if (mode == AmountMode.BETWEEN) "From amount" else "Amount"
        OutlinedTextField(
            value = cleanedLow,
            onValueChange = { lowText = it },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        if (mode == AmountMode.BETWEEN) {
            Spacer(Modifier.height(Spacing.sm))
            OutlinedTextField(
                value = cleanedHigh,
                onValueChange = { highText = it },
                label = { Text("To amount") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(Spacing.md))

        if (valid) {
            val preview = when (mode) {
                AmountMode.EQUAL -> "Amount: $cleanedLow"
                AmountMode.BETWEEN -> "Amount: $cleanedLow – $cleanedHigh"
                else -> {
                    val opSymbol = mode.symbol
                    "Amount $opSymbol $cleanedLow"
                }
            }
            Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))
        }

        Button(
            onClick = {
                val clause = when (mode) {
                    AmountMode.EQUAL -> "amount:$cleanedLow"
                    AmountMode.BETWEEN -> "amount:$cleanedLow-$cleanedHigh"
                    else -> "amount${mode.queryOp}$cleanedLow"
                }
                onAppendClause(clause)
            },
            enabled = valid,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Add filter")
        }
    }
}

private fun isAmount(raw: String): Boolean =
    raw.matches(Regex("\\d+(\\.\\d+)?")) && raw.toDoubleOrNull() != null

/**
 * Multi-select chip picker used for Category and Account filters: chips
 * toggle on tap, a special "All" chip selects/deselects the whole list, and
 * an Apply button appends the clause — one value, or several joined with
 * `or` (matching transactions in any of the chosen values).
 */
@Composable
private fun MultiValuesPicker(
    values: List<String>,
    hint: String?,
    previewLabel: String,
    chipLabel: (String) -> String,
    toClause: (String) -> String,
    onAppendClause: (String) -> Unit,
) {
    var selected by remember { mutableStateOf(setOf<String>()) }

    Column {
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            FilterChip(
                selected = selected.size == values.size && values.isNotEmpty(),
                onClick = {
                    selected = if (selected.size == values.size) emptySet() else values.toSet()
                },
                label = { Text("All") },
            )
            values.forEach { value ->
                FilterChip(
                    selected = value in selected,
                    onClick = {
                        selected = if (value in selected) selected - value else selected + value
                    },
                    label = {
                        Text(chipLabel(value), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))

        val preview = selected.sorted()
        if (preview.isNotEmpty()) {
            Text(
                text = "$previewLabel: " + preview.joinToString(" or ") { chipLabel(it) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Spacing.xs))
        }

        Button(
            onClick = {
                val clauses = preview.map(toClause)
                val clause = if (clauses.size == 1) clauses.first()
                else clauses.joinToString(" or ").let { articles -> "($articles)" }
                onAppendClause(clause)
            },
            enabled = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (selected.size <= 1) "Add filter"
                else "Add ${selected.size} filters"
            )
        }
    }
}

private fun accountChipLabel(key: String): String {
    val parts = key.split("_", limit = 2)
    val bank = parts.getOrNull(0).orEmpty().ifBlank { "Account" }
    val last4 = parts.getOrNull(1).orEmpty()
    return if (last4.isBlank()) bank else "$bank ••$last4"
}

private fun accountClause(key: String): String? {
    val last4 = key.split("_", limit = 2).getOrNull(1).orEmpty()
    return if (last4.isBlank()) null else "account:${sanitizeQueryValue(last4)}"
}

/** Pick a custom date range with Material date dialogs → `date:START-END`. */
@Composable
private fun CustomRangePicker(onAppendClause: (String) -> Unit) {
    var start by remember { mutableStateOf<LocalDate?>(null) }
    var end by remember { mutableStateOf<LocalDate?>(null) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    Column {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            FilterChip(
                selected = false,
                onClick = { pickingStart = true },
                label = {
                    Text(if (start == null) "Start date…" else "Start: $start", maxLines = 1)
                },
            )
            FilterChip(
                selected = false,
                enabled = start != null,
                onClick = { pickingEnd = true },
                label = {
                    Text(if (end == null) "End date…" else "End: $end", maxLines = 1)
                },
            )
        }
        Spacer(Modifier.height(Spacing.md))

        val s = start
        val e = end
        val bothSelected = s != null && e != null
        if (bothSelected) {
            Text(
                text = if (s!!.isAfter(e!!)) "Date: $e – $s incl." else "Date: $s – $e incl.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xs))
        }

        Button(
            onClick = {
                onAppendClause(if (s!!.isAfter(e!!)) "date:$e-$s" else "date:$s-$e")
            },
            enabled = bothSelected,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Add filter")
        }
    }

    if (pickingStart) {
        LocalDateDialog(
            initial = start,
            title = "Start date",
            onDismiss = { pickingStart = false },
        ) { picked ->
            start = picked
            if (end != null && picked.isAfter(end)) end = null
            pickingStart = false
            pickingEnd = true
        }
    }
    if (pickingEnd) {
        LocalDateDialog(
            initial = end,
            title = "End date",
            onDismiss = { pickingEnd = false },
        ) { picked ->
            end = picked
            pickingEnd = false
        }
    }
}

/** M3 DatePicker wrapper that selects whole days (DatePicker works in UTC millis). */
@Composable
private fun LocalDateDialog(
    initial: LocalDate?,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onConfirm(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        )
                    }
                },
                enabled = state.selectedDateMillis != null,
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    ) {
        // DatePicker has its own title slot; DatePickerDialog has none.
        DatePicker(state = state, title = { Text(title) })
    }
}

// ---------------------------------------------------------------------------
// Type-ahead suggestions (inline completion of the trailing clause)
// ---------------------------------------------------------------------------

/**
 * Gives suggestions for the trailing in-progress field clause when typed manually.
 * `merchant:ne` → [Netflix], `date:t` → [ Today, Yesterday, This year ], etc.
 * Tapping replaces only the in-progress token with the full clause.
 */
@Composable
fun TransactionSearchSuggestionsRow(
    inProgress: PendingSuggestion,
    merchants: List<String>,
    categories: List<String>,
    tags: List<String>,
    onApply: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val partial = inProgress.partialValue

    val values = remember(
        inProgress.field, partial, merchants, categories, tags,
    ) {
        when (inProgress.field) {
            SearchField.MERCHANT -> merchants.filter { it.contains(partial, ignoreCase = true) }
            SearchField.CATEGORY -> categories.filter { it.contains(partial, ignoreCase = true) }
            SearchField.TAG -> tags.filter { it.contains(partial, ignoreCase = true) }
            SearchField.TYPE -> TYPE_VALUES.filter { it.startsWith(partial, ignoreCase = true) }
            SearchField.DATE -> DATE_PRESETS.filter {
                it.lowercase().replace(" ", "_").startsWith(
                    partial.lowercase().replace(" ", "_"),
                )
            }
            else -> emptyList()
        }.take(8)
    }
    if (values.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        values.forEach { value ->
            AssistChip(
                onClick = { suggestionClause(inProgress.field, value)?.let(onApply) },
                label = { Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}
