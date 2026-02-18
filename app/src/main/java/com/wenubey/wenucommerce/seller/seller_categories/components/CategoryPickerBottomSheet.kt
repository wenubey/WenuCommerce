package com.wenubey.wenucommerce.seller.seller_categories.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wenubey.domain.model.product.Category
import com.wenubey.domain.model.product.Subcategory
import com.wenubey.wenucommerce.seller.seller_categories.SellerCategoryState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerBottomSheet(
    state: SellerCategoryState,
    onCategorySelected: (Category) -> Unit,
    onSubcategorySelected: (Subcategory) -> Unit,
    onCreateNewCategory: () -> Unit,
    onCreateNewSubcategory: () -> Unit,
    onConfirm: (Category, Subcategory?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = if (state.selectedCategory == null) "Select Category" else "Select Subcategory",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (state.selectedCategory == null) {
                // Category list
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    items(state.categories) { category ->
                        ListItem(
                            headlineContent = { Text(category.name) },
                            supportingContent = {
                                if (category.description.isNotBlank()) {
                                    Text(category.description)
                                }
                            },
                            trailingContent = {
                                if (category.subcategories.isNotEmpty()) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                                }
                            },
                            modifier = Modifier.clickable { onCategorySelected(category) },
                        )
                        HorizontalDivider()
                    }

                    item {
                        ListItem(
                            headlineContent = { Text("Create New Category") },
                            leadingContent = {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            modifier = Modifier.clickable { onCreateNewCategory() },
                            colors = ListItemDefaults.colors(
                                headlineColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            } else {
                // Subcategory list
                Text(
                    text = "Category: ${state.selectedCategory.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    // "No subcategory" option
                    item {
                        ListItem(
                            headlineContent = { Text("No subcategory") },
                            trailingContent = {
                                if (state.selectedSubcategory == null) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected")
                                }
                            },
                            modifier = Modifier.clickable { onSubcategorySelected(Subcategory()) },
                        )
                        HorizontalDivider()
                    }

                    items(state.selectedCategory.subcategories) { subcategory ->
                        ListItem(
                            headlineContent = { Text(subcategory.name) },
                            trailingContent = {
                                if (state.selectedSubcategory?.id == subcategory.id) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected")
                                }
                            },
                            modifier = Modifier.clickable { onSubcategorySelected(subcategory) },
                        )
                        HorizontalDivider()
                    }

                    item {
                        ListItem(
                            headlineContent = { Text("Create New Subcategory") },
                            leadingContent = {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            modifier = Modifier.clickable { onCreateNewSubcategory() },
                            colors = ListItemDefaults.colors(
                                headlineColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            state.selectedCategory?.let { category ->
                                val subcategory = state.selectedSubcategory?.takeIf { it.id.isNotBlank() }
                                onConfirm(category, subcategory)
                            }
                        },
                    ) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
}
