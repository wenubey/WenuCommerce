package com.wenubey.wenucommerce.core.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.wenubey.domain.model.product.Category

@Composable
fun WenuSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search products",
    isLoading: Boolean = false,
    onClearClick: () -> Unit = { onQueryChange("") },
    // Filter panel parameters
    isFilterExpanded: Boolean = false,
    onFilterToggle: (() -> Unit)? = null,
    activeFilterCount: Int = 0,
    categories: List<Category> = emptyList(),
    isLoadingCategories: Boolean = false,
    selectedCategoryId: String? = null,
    selectedSubcategoryId: String? = null,
    onCategorySelected: (String?) -> Unit = {},
    onSubcategorySelected: (String?) -> Unit = {},
    onClearFilters: () -> Unit = {},
) {
    val shape = RoundedCornerShape(12.dp)
    val borderColor = if (isFilterExpanded || activeFilterCount > 0) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    Column(
        modifier = modifier
            .border(
                width = if (isFilterExpanded || activeFilterCount > 0) 2.dp else 1.dp,
                color = borderColor,
                shape = shape,
            ),
    ) {
        // Search input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                },
            )

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                if (query.isNotBlank()) {
                    IconButton(onClick = onClearClick, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (onFilterToggle != null) {
                    IconButton(onClick = onFilterToggle, modifier = Modifier.size(36.dp)) {
                        if (activeFilterCount > 0) {
                            BadgedBox(
                                badge = {
                                    Badge { Text(activeFilterCount.toString()) }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = "Filter",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter",
                                tint = if (isFilterExpanded) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }

        // Expandable filter panel inside the same border
        AnimatedVisibility(
            visible = isFilterExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            ) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (selectedCategoryId != null || selectedSubcategoryId != null) {
                        TextButton(onClick = onClearFilters) {
                            Text("Clear", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (isLoadingCategories) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else if (categories.isEmpty()) {
                    Text(
                        text = "No categories available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                } else {
                    val activeCategories = categories.filter { it.isActive }

                    // Category chips
                    LazyRow(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(activeCategories) { category ->
                            FilterChip(
                                selected = selectedCategoryId == category.id,
                                onClick = {
                                    if (selectedCategoryId == category.id) {
                                        onCategorySelected(null)
                                    } else {
                                        onCategorySelected(category.id)
                                    }
                                },
                                label = { Text(category.name) },
                            )
                        }
                    }

                    // Subcategory chips
                    val selectedCategory = activeCategories.find { it.id == selectedCategoryId }
                    if (selectedCategory != null && selectedCategory.subcategories.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Subcategory",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
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
            }
        }
    }
}
