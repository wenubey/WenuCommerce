package com.wenubey.wenucommerce.core.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wenubey.domain.model.product.Category

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryFilterSheet(
    categories: List<Category>,
    isLoadingCategories: Boolean,
    selectedCategoryId: String?,
    selectedSubcategoryId: String?,
    onCategorySelected: (String?) -> Unit,
    onSubcategorySelected: (String?) -> Unit,
    onClearFilters: () -> Unit,
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
                .padding(horizontal = 16.dp),
        ) {
            // Title row with Clear Filters button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Filter by Category",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onClearFilters,
                    enabled = selectedCategoryId != null || selectedSubcategoryId != null,
                ) {
                    Text("Clear Filters")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Loading state
            if (isLoadingCategories) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (categories.isEmpty()) {
                // Error / empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No categories available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // Category chips
                val activeCategories = categories.filter { it.isActive }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(activeCategories) { category ->
                        FilterChip(
                            selected = selectedCategoryId == category.id,
                            onClick = {
                                if (selectedCategoryId == category.id) {
                                    // Deselect: clear both category and subcategory
                                    onCategorySelected(null)
                                } else {
                                    onCategorySelected(category.id)
                                }
                            },
                            label = { Text(category.name) },
                        )
                    }
                }

                // Subcategory chips (shown when a category is selected and has subcategories)
                val selectedCategory = activeCategories.find { it.id == selectedCategoryId }
                if (selectedCategory != null && selectedCategory.subcategories.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Subcategory",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // "All" chip to reset subcategory selection
                        item {
                            FilterChip(
                                selected = selectedSubcategoryId == null,
                                onClick = { onSubcategorySelected(null) },
                                label = { Text("All") },
                            )
                        }
                        items(selectedCategory.subcategories) { subcategory ->
                            FilterChip(
                                selected = selectedSubcategoryId == subcategory.id,
                                onClick = { onSubcategorySelected(subcategory.id) },
                                label = { Text(subcategory.name) },
                            )
                        }
                    }
                }
            }

            // Bottom spacer for navigation bar inset
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
