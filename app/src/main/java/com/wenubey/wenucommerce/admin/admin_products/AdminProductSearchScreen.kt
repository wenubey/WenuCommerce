package com.wenubey.wenucommerce.admin.admin_products

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wenubey.domain.model.product.Product
import com.wenubey.domain.model.product.ProductStatus
import com.wenubey.wenucommerce.admin.admin_products.components.ProductDetailDialog
import com.wenubey.wenucommerce.core.components.WenuSearchBar
import org.koin.androidx.compose.koinViewModel

@Composable
fun AdminProductSearchScreen(
    modifier: Modifier = Modifier,
    viewModel: AdminProductSearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val isSearchActive = state.searchQuery.isNotBlank()

    var isFilterExpanded by rememberSaveable { mutableStateOf(false) }

    val activeFilterCount = listOfNotNull(
        state.filterCategoryId,
        state.filterSubcategoryId,
    ).size

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Product Search",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        WenuSearchBar(
            query = state.searchQuery,
            onQueryChange = {
                viewModel.onAction(AdminProductSearchAction.OnSearchQueryChanged(it))
            },
            isLoading = state.isSearching,
            placeholder = "Search all products",
            modifier = Modifier.fillMaxWidth(),
            isFilterExpanded = isFilterExpanded,
            onFilterToggle = {
                viewModel.onAction(AdminProductSearchAction.OnRequestCategoryLoad)
                isFilterExpanded = !isFilterExpanded
            },
            activeFilterCount = activeFilterCount,
            categories = state.categories,
            isLoadingCategories = state.isLoadingCategories,
            selectedCategoryId = state.filterCategoryId,
            selectedSubcategoryId = state.filterSubcategoryId,
            onCategorySelected = { categoryId ->
                viewModel.onAction(AdminProductSearchAction.OnFilterCategorySelected(categoryId))
            },
            onSubcategorySelected = { subcategoryId ->
                viewModel.onAction(AdminProductSearchAction.OnFilterSubcategorySelected(subcategoryId))
            },
            onClearFilters = {
                viewModel.onAction(AdminProductSearchAction.OnClearCategoryFilters)
            },
        )

        // Status filter chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = state.statusFilter == null,
                    onClick = {
                        viewModel.onAction(AdminProductSearchAction.OnStatusFilterChanged(null))
                    },
                    label = { Text("All") },
                )
            }
            items(ProductStatus.entries.toList()) { status ->
                FilterChip(
                    selected = state.statusFilter == status,
                    onClick = {
                        viewModel.onAction(AdminProductSearchAction.OnStatusFilterChanged(status))
                    },
                    label = { Text(status.name.replace("_", " ")) },
                )
            }
        }

        // Error message
        state.errorMessage?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Results area
        when {
            !isSearchActive -> {
                // Prompt state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Enter a search term to find products",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            state.isSearching -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.filteredResults.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No products found for '${state.searchQuery}'",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            else -> {
                Text(
                    text = "${state.filteredResults.size} results for '${state.searchQuery}'",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.filteredResults, key = { it.id }) { product ->
                        AdminProductSearchCard(
                            product = product,
                            onClick = {
                                viewModel.onAction(AdminProductSearchAction.OnProductSelected(product))
                            },
                        )
                    }
                }
            }
        }
    }

    // Product Detail Dialog
    if (state.showDetailDialog && state.selectedProduct != null) {
        ProductDetailDialog(
            product = state.selectedProduct!!,
            onDismiss = { viewModel.onAction(AdminProductSearchAction.OnDismissDetailDialog) },
            onApprove = { viewModel.onAction(AdminProductSearchAction.OnDismissDetailDialog) },
            onSuspend = { viewModel.onAction(AdminProductSearchAction.OnDismissDetailDialog) },
        )
    }

}

@Composable
private fun AdminProductSearchCard(
    product: Product,
    onClick: () -> Unit,
) {
    val statusColor = when (product.status) {
        ProductStatus.ACTIVE -> Color(0xFF4CAF50)
        ProductStatus.DRAFT -> Color(0xFFFF9800)
        ProductStatus.PENDING_REVIEW -> Color(0xFF2196F3)
        ProductStatus.SUSPENDED -> Color(0xFFE53E3E)
        ProductStatus.ARCHIVED -> Color(0xFF9E9E9E)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Cover image
            val coverImage = product.images.firstOrNull()
            if (coverImage != null && coverImage.downloadUrl.isNotBlank()) {
                AsyncImage(
                    model = coverImage.downloadUrl,
                    contentDescription = product.title,
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp),
                        ),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Product info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Seller: ${product.sellerName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${product.categoryName} / ${product.subcategoryName}".trimEnd(' ', '/'),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "$${product.basePrice}",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold,
                    )
                    Badge(containerColor = statusColor) {
                        Text(
                            text = product.status.name.replace("_", " "),
                            color = Color.White,
                        )
                    }
                }

                // Tag chips (display only, max 5)
                if (product.tagNames.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        items(product.tagNames.take(5)) { tag ->
                            SuggestionChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
