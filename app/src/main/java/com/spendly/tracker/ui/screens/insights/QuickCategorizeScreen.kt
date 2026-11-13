package com.spendly.tracker.ui.screens.insights

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendly.tracker.data.database.entity.CategoryEntity
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.ui.components.CustomTitleTopAppBar
import com.spendly.tracker.ui.components.parseColor
import com.spendly.tracker.ui.icons.CategoryIcons
import com.spendly.tracker.ui.theme.Dimensions
import com.spendly.tracker.ui.theme.Spacing
import com.spendly.tracker.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCategorizeScreen(
    viewModel: QuickCategorizeViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onReviewOlder: () -> Unit = {}
) {
    val transactions by viewModel.uncategorizedTransactions.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val allTags by viewModel.allUsedTags.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val hazeState = remember { HazeState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val title = when (viewModel.scope) {
        QuickCategorizeScope.TODAY -> "Review today"
        QuickCategorizeScope.HISTORICAL -> "Uncategorized history"
        QuickCategorizeScope.ALL -> "Quick Categorize"
    }

    Scaffold(
        modifier = Modifier,
        containerColor = Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                title = title,
                scrollBehaviorSmall = scrollBehavior,
                scrollBehaviorLarge = scrollBehavior,
                hazeState = hazeState,
                hasBackButton = true,
                navigationContent = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                transactions.isEmpty() -> {
                    EmptyCategorizeState(
                        onBack = onBack,
                        scope = viewModel.scope,
                        onReviewOlder = onReviewOlder,
                    )
                }
                else -> {
                    CategorizeContent(
                        transactions = transactions,
                        categories = categories,
                        tagSuggestions = allTags,
                        onCategorize = { id, category, tags, bulk ->
                            viewModel.categorizeTransaction(id, category, tags, bulk)
                        },
                        onSkip = { id -> viewModel.skipTransaction(id) },
                        onEdit = { id -> }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorizeContent(
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    tagSuggestions: List<String>,
    onCategorize: (Long, String, List<String>, Boolean) -> Unit,
    onSkip: (Long) -> Unit,
    onEdit: (Long) -> Unit = {}
) {
    val currentTransaction = transactions.firstOrNull() ?: return
    var selectedCategory by remember(currentTransaction.id) { mutableStateOf<String?>(null) }
    var selectedTags by remember(currentTransaction.id) {
        mutableStateOf(
            currentTransaction.tags
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
        )
    }
    var showTagSheet by remember(currentTransaction.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Card representation of the transaction
        TransactionCard(
            transaction = currentTransaction,
            onSkip = { onSkip(currentTransaction.id) },
            onEdit = { onEdit(currentTransaction.id) }
        )

        Spacer(modifier = Modifier.height(Spacing.sm))

        TagSummaryRow(
            tags = selectedTags,
            onClick = { showTagSheet = true }
        )

        Spacer(modifier = Modifier.height(Spacing.sm))

        Text(
            text = "Select Category",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(Spacing.sm))

        // Category Grid
        LazyVerticalGrid(
            columns = GridCells.Adaptive(100.dp),
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            items(categories, key = { it.id }) { category ->
                CategorySelectChip(
                    name = category.name,
                    icon = CategoryIcons.getIcon(CategoryIcons.resolveKey(category.name, category.icon)),
                    color = parseColor(category.color, MaterialTheme.colorScheme.primary),
                    isSelected = selectedCategory == category.name,
                    onClick = { selectedCategory = category.name }
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))


        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            OutlinedButton(
                onClick = { onSkip(currentTransaction.id) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text("Skip")
            }

            Button(
                onClick = {
                    selectedCategory?.let {
                        onCategorize(currentTransaction.id, it, selectedTags, false)
                        selectedCategory = null
                    }
                },
                enabled = selectedCategory != null,
                modifier = Modifier.weight(1.5f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text("Confirm")
            }
        }
    }

    if (showTagSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTagSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            TagEditorSheet(
                selectedTags = selectedTags,
                suggestions = tagSuggestions,
                onAddTag = { tag ->
                    if (tag !in selectedTags) selectedTags = selectedTags + tag
                },
                onRemoveTag = { tag -> selectedTags = selectedTags - tag },
                onDone = { showTagSheet = false },
            )
        }
    }
}

@Composable
private fun TagSummaryRow(
    tags: List<String>,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Label,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(Spacing.xs))
        Text(
            text = if (tags.isEmpty()) "Add tags (optional)" else tags.joinToString(" · "),
            style = MaterialTheme.typography.labelLarge,
            color = if (tags.isEmpty()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Edit",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TagEditorSheet(
    selectedTags: List<String>,
    suggestions: List<String>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onDone: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val filteredSuggestions = remember(suggestions, input, selectedTags) {
        val query = input.trim()
        suggestions
            .filter { tag ->
                tag !in selectedTags &&
                    (query.isBlank() || tag.contains(query, ignoreCase = true))
            }
            .take(12)
    }

    fun commitInput() {
        val trimmed = input.trim()
        if (trimmed.isNotEmpty()) {
            onAddTag(trimmed)
            input = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = "Tags",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Optional labels to group transactions across categories.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (selectedTags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                selectedTags.forEach { tag ->
                    FilterChip(
                        selected = true,
                        onClick = { onRemoveTag(tag) },
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove tag",
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }
        }

        if (filteredSuggestions.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                filteredSuggestions.forEach { tag ->
                    SuggestionChip(
                        onClick = {
                            onAddTag(tag)
                            input = ""
                        },
                        label = { Text(tag) },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Add tag…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { commitInput() }),
            )
            TextButton(onClick = { commitInput() }) {
                Text("Add")
            }
        }

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Done")
        }
    }
}

@Composable
private fun TransactionCard(
    transaction: TransactionEntity,
    onSkip: () -> Unit,
    onEdit: () -> Unit = {}
) {
    var showSms by remember { mutableStateOf(false) }
    val hasSms = !transaction.smsBody.isNullOrBlank()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = transaction.merchantName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    Text(
                        text = CurrencyFormatter.formatCurrency(transaction.amount, transaction.currency),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    Text(
                        text = transaction.dateTime.toLocalDate().toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.xs)
                ) {
                    IconButton(
                        onClick = onEdit
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    IconButton(
                        onClick = onSkip
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Skip",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (hasSms) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSms = !showSms }
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Sms,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        text = "View SMS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (showSms) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                AnimatedVisibility(visible = showSms) {
                    Text(
                        text = transaction.smsBody ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md)
                    )
                }
            }
        }
    }
}

@Composable
private fun CategorySelectChip(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // Compute on-color for selected state based on luminance of the category color
    val onCategoryColor = remember(color) {
        val luminance = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
        if (luminance > 0.35f) Color(0xFF1C1B1F) else Color.White
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) color else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (isSelected) onCategoryColor else MaterialTheme.colorScheme.onSurface,
        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .padding(Spacing.sm)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) onCategoryColor.copy(alpha = 0.2f) else color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isSelected) onCategoryColor else color
                )
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EmptyCategorizeState(
    onBack: () -> Unit,
    scope: QuickCategorizeScope,
    onReviewOlder: () -> Unit,
) {
    val isTodayScope = scope == QuickCategorizeScope.TODAY
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
        Text(
            text = "All caught up!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = when (scope) {
                QuickCategorizeScope.TODAY ->
                    "Every transaction from today has a category. Add tags from the review screen whenever you want extra context."
                QuickCategorizeScope.HISTORICAL ->
                    "There are no older uncategorized transactions left. Every past transaction has a category."
                QuickCategorizeScope.ALL ->
                    "You've categorized all your recent transactions. Your insights will be more accurate now."
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Spacing.xl))
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (scope == QuickCategorizeScope.ALL) "Back to Analytics" else "Back")
        }
        if (isTodayScope) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            OutlinedButton(
                onClick = onReviewOlder,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Review older uncategorized")
            }
        }
    }
}

