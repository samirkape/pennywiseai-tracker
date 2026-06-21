package com.pennywiseai.tracker.presentation.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.ui.components.CategoryChip
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onNavigateBack: () -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val showAddEditDialog by viewModel.showAddEditDialog.collectAsStateWithLifecycle()
    val editingCategory by viewModel.editingCategory.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val pendingDeleteCategory by viewModel.pendingDeleteCategory.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // Show snackbar messages
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            scope.launch {
                snackbarHostState.showSnackbar(it)
                viewModel.clearSnackbarMessage()
            }
        }
    }
    
    // Group categories by type
    val expenseCategories = categories.filter { !it.isIncome }
    val incomeCategories = categories.filter { it.isIncome }

    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = "Categories",
                hasBackButton = true,
                hasActionButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                hazeState = hazeState
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showAddDialog() },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Category")
            }
        }
    ) { paddingValues ->
        val lazyListState = rememberLazyListState()
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .background(MaterialTheme.colorScheme.background)
                .overScrollVertical(),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                bottom = 100.dp // Space for FAB
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            flingBehavior = rememberOverscrollFlingBehavior { lazyListState }
        ) {
            // Expense Categories Section
            if (expenseCategories.isNotEmpty()) {
                item {
                    SectionHeaderV2(title = "Expense Categories")
                }

                items(
                    items = expenseCategories,
                    key = { it.id }
                ) { category ->
                    SwipeableCategoryItem(
                        category = category,
                        onEdit = { viewModel.showEditDialog(category) },
                        onDelete = if (!category.isSystem) {
                            { viewModel.requestDeleteCategory(category) }
                        } else null
                    )
                }
            }

            // Income Categories Section
            if (incomeCategories.isNotEmpty()) {
                item {
                    SectionHeaderV2(title = "Income Categories")
                }

                items(
                    items = incomeCategories,
                    key = { it.id }
                ) { category ->
                    SwipeableCategoryItem(
                        category = category,
                        onEdit = { viewModel.showEditDialog(category) },
                        onDelete = if (!category.isSystem) {
                            { viewModel.requestDeleteCategory(category) }
                        } else null
                    )
                }
            }
        }
    }
    
    // Add/Edit Dialog
    if (showAddEditDialog) {
        CategoryEditDialog(
            category = editingCategory,
            onDismiss = { viewModel.hideDialog() },
            onSave = { name, color, isIncome, icon ->
                viewModel.saveCategory(name, color, isIncome, icon)
            }
        )
    }

    // Delete with replacement dialog
    pendingDeleteCategory?.let { categoryToDelete ->
        DeleteCategoryDialog(
            categoryToDelete = categoryToDelete,
            allCategories = categories,
            onDismiss = { viewModel.cancelDeleteRequest() },
            onConfirm = { replacement ->
                viewModel.deleteCategory(categoryToDelete, replacement)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteCategoryDialog(
    categoryToDelete: CategoryEntity,
    allCategories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onConfirm: (replacement: CategoryEntity?) -> Unit,
) {
    val replacementOptions = allCategories.filter { it.id != categoryToDelete.id }
    var selectedReplacement by remember {
        mutableStateOf<CategoryEntity?>(
            replacementOptions.firstOrNull { it.name == "Others" } ?: replacementOptions.firstOrNull()
        )
    }
    var dropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    "Delete '${categoryToDelete.name}'? Choose what to do with its transactions:",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (replacementOptions.isNotEmpty()) {
                    Text(
                        "Reassign transactions to:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box {
                        OutlinedCard(
                            onClick = { dropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (selectedReplacement != null) {
                                    CategoryChip(
                                        category = selectedReplacement!!,
                                        showText = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    Text(
                                        "No reassignment",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "Select category",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "No reassignment (keep old name)",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                onClick = {
                                    selectedReplacement = null
                                    dropdownExpanded = false
                                }
                            )
                            HorizontalDivider()
                            replacementOptions.forEach { cat ->
                                DropdownMenuItem(
                                    text = { CategoryChip(category = cat, showText = true) },
                                    onClick = {
                                        selectedReplacement = cat
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedReplacement) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SwipeableCategoryItem(
    category: CategoryEntity,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?
) {
    CategoryItem(
        category = category,
        onClick = onEdit,
        onDelete = onDelete
    )
}

@Composable
private fun CategoryItem(
    category: CategoryEntity,
    onClick: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    PennyWiseCardV2(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Padding.content),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryChip(
                category = category,
                showText = true,
                modifier = Modifier.weight(1f)
            )

            if (category.isSystem) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(start = Spacing.sm)
                ) {
                    Text(
                        text = "System",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(
                            horizontal = Spacing.sm,
                            vertical = Spacing.xs
                        )
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(Dimensions.Icon.medium)
                    .padding(start = Spacing.sm)
            )

            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}