package com.wenubey.wenucommerce.admin.admin_categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wenubey.wenucommerce.admin.admin_categories.components.CategoryManagementCard
import com.wenubey.wenucommerce.admin.admin_categories.components.CreateCategoryDialog
import com.wenubey.wenucommerce.admin.admin_categories.components.EditCategoryDialog
import org.koin.androidx.compose.koinViewModel

@Composable
fun AdminCategoryScreen(
    modifier: Modifier = Modifier,
    viewModel: AdminCategoryViewModel = koinViewModel(),
) {
    val state by viewModel.categoryState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.onAction(AdminCategoryAction.OnShowCreateDialog) }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Category")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = "Category Management",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Error message
            state.errorMessage?.let { error ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE53E3E).copy(alpha = 0.1f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Error",
                                tint = Color(0xFFE53E3E),
                            )
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFE53E3E),
                            )
                        }
                    }
                }
            }

            if (state.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            if (!state.isLoading && state.categories.isNotEmpty()) {
                items(state.categories, key = { it.id }) { category ->
                    CategoryManagementCard(
                        category = category,
                        onEdit = {
                            viewModel.onAction(AdminCategoryAction.OnCategorySelected(category))
                        },
                        onDelete = {
                            viewModel.onAction(AdminCategoryAction.OnDeleteCategory(category.id))
                        },
                    )
                }
            }

            if (!state.isLoading && state.categories.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "No categories",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "No categories yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "Tap + to create your first category",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        // Create Dialog
        if (state.showCreateDialog) {
            CreateCategoryDialog(
                onDismiss = { viewModel.onAction(AdminCategoryAction.OnDismissDialog) },
                onCreate = { name, description, imageUrl ->
                    viewModel.onAction(AdminCategoryAction.OnCreateCategory(name, description, imageUrl))
                },
            )
        }

        // Edit Dialog
        if (state.showEditDialog && state.selectedCategory != null) {
            EditCategoryDialog(
                category = state.selectedCategory!!,
                onDismiss = { viewModel.onAction(AdminCategoryAction.OnDismissDialog) },
                onSave = { category, newImageUri ->
                    viewModel.onAction(AdminCategoryAction.OnEditCategory(category, newImageUri))
                },
            )
        }
    }
}
