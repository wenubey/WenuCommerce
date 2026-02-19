package com.wenubey.wenucommerce.customer

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wenubey.domain.model.product.Product
import com.wenubey.wenucommerce.core.components.EmptyNetworkState
import com.wenubey.wenucommerce.core.components.ShimmerCategoryRow
import com.wenubey.wenucommerce.core.components.ShimmerProductGrid
import com.wenubey.wenucommerce.core.components.WenuSearchBar
import com.wenubey.wenucommerce.customer.customer_home.CustomerHomeAction
import com.wenubey.wenucommerce.customer.customer_home.CustomerHomeViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerHomeScreen(
    modifier: Modifier = Modifier,
    viewModel: CustomerHomeViewModel = koinViewModel(),
    onProductClick: (String) -> Unit = {},
) {
    val state by viewModel.homeState.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()

    val isSearchActive = state.searchQuery.isNotBlank()

    var isFilterExpanded by rememberSaveable { mutableStateOf(false) }

    val activeFilterCount = listOfNotNull(
        state.filterSheetCategoryId,
        state.filterSheetSubcategoryId,
    ).size

    // Determine if this is a first-launch-no-data scenario
    val isEmpty = state.categories.isEmpty() && state.products.isEmpty() && !state.isLoading

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        // Search bar (always visible, outside pull-to-refresh)
        WenuSearchBar(
            query = state.searchQuery,
            onQueryChange = {
                viewModel.onAction(CustomerHomeAction.OnSearchQueryChanged(it))
            },
            isLoading = state.isSearching,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            isFilterExpanded = isFilterExpanded,
            onFilterToggle = { isFilterExpanded = !isFilterExpanded },
            activeFilterCount = activeFilterCount,
            categories = state.categories,
            isLoadingCategories = false,
            selectedCategoryId = state.filterSheetCategoryId,
            selectedSubcategoryId = state.filterSheetSubcategoryId,
            onCategorySelected = { categoryId ->
                viewModel.onAction(CustomerHomeAction.OnSearchFilterCategorySelected(categoryId))
            },
            onSubcategorySelected = { subcategoryId ->
                viewModel.onAction(CustomerHomeAction.OnSearchFilterSubcategorySelected(subcategoryId))
            },
            onClearFilters = {
                viewModel.onAction(CustomerHomeAction.OnClearSearchFilters)
            },
        )

        // Main content wrapped in PullToRefreshBox
        // Per locked decision: "Pull-to-refresh available on content screens + automatic background sync"
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.onAction(CustomerHomeAction.OnPullToRefresh) },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                // First launch, no network, no cached data
                // Per locked decision: "First launch with no network: empty state with
                // friendly illustration + 'Connect to the internet to get started' + retry button"
                isEmpty && !isOnline && !isSearchActive -> {
                    EmptyNetworkState(
                        onRetry = { viewModel.onAction(CustomerHomeAction.OnPullToRefresh) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Loading state (first launch with empty Room, or online with no data yet)
                // Per locked decision: "Shimmer appears both on first launch (empty Room)"
                (state.isLoading || (isEmpty && isOnline)) && !isSearchActive -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        ShimmerCategoryRow(modifier = Modifier.fillMaxWidth())
                        ShimmerProductGrid(modifier = Modifier.fillMaxWidth())
                    }
                }

                // Normal content (browse or search)
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Welcome card (only when search is inactive)
                        if (!isSearchActive) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ShoppingBag,
                                            contentDescription = "Shopping",
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Column {
                                            Text(
                                                text = "Welcome to WenuCommerce",
                                                style = MaterialTheme.typography.titleLarge,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = "Discover amazing products",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (!isSearchActive) {
                            // Categories row
                            item {
                                Text(
                                    text = "Categories",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                                // Shimmer over category row during refresh
                                if (state.isRefreshing) {
                                    ShimmerCategoryRow(modifier = Modifier.fillMaxWidth())
                                } else {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(state.categories) { category ->
                                            CategoryCard(
                                                title = category.name,
                                                isSelected = state.selectedCategoryId == category.id,
                                                onClick = {
                                                    viewModel.onAction(
                                                        CustomerHomeAction.OnCategorySelected(category.id)
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                            }

                            // Subcategory chip row
                            val selectedCategory =
                                state.categories.find { it.id == state.selectedCategoryId }
                            if (selectedCategory != null && selectedCategory.subcategories.isNotEmpty()) {
                                item {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        item {
                                            FilterChip(
                                                selected = state.selectedSubcategoryId == null,
                                                onClick = {
                                                    viewModel.onAction(
                                                        CustomerHomeAction.OnSubcategorySelected(null)
                                                    )
                                                },
                                                label = { Text("All") },
                                            )
                                        }
                                        items(selectedCategory.subcategories) { subcategory ->
                                            FilterChip(
                                                selected = state.selectedSubcategoryId == subcategory.id,
                                                onClick = {
                                                    viewModel.onAction(
                                                        CustomerHomeAction.OnSubcategorySelected(
                                                            subcategory.id
                                                        )
                                                    )
                                                },
                                                label = { Text(subcategory.name) },
                                            )
                                        }
                                    }
                                }
                            }

                            // Products section
                            item {
                                Text(
                                    text = "Products",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }

                            if (state.isRefreshing) {
                                // Per locked decision: "Shimmer appears ... during pull-to-refresh"
                                item {
                                    ShimmerProductGrid(modifier = Modifier.fillMaxWidth())
                                }
                            } else if (state.isLoadingProducts) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            } else if (state.products.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (state.selectedCategoryId != null)
                                                "No products in this category yet"
                                            else
                                                "Select a category to browse products",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            } else {
                                items(state.products, key = { it.id }) { product ->
                                    CustomerProductCard(
                                        product = product,
                                        onClick = { onProductClick(product.id) },
                                    )
                                }
                            }
                        } else {
                            // Search results mode
                            state.searchError?.let { error ->
                                item {
                                    Text(
                                        text = error,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }

                            item {
                                Text(
                                    text = "Search Results (${state.searchResults.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }

                            if (state.isSearching) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            } else if (state.searchResults.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No products found for '${state.searchQuery}'. Try a different search term.",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            } else {
                                items(state.searchResults, key = { it.id }) { product ->
                                    CustomerProductCard(
                                        product = product,
                                        onClick = { onProductClick(product.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryCard(
    title: String,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
) {
    Card(
        modifier = Modifier.size(100.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Category,
                contentDescription = title,
                modifier = Modifier.size(32.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
fun CustomerProductCard(
    product: Product,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val coverImage = product.images.firstOrNull()
            if (coverImage != null && coverImage.downloadUrl.isNotBlank()) {
                AsyncImage(
                    model = coverImage.downloadUrl,
                    contentDescription = product.title,
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        ),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = product.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$${product.basePrice}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    product.compareAtPrice?.let { compareAt ->
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$$compareAt",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = TextDecoration.LineThrough,
                        )
                    }
                }

                if (product.reviewCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFFFFC107),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "%.1f (%d)".format(product.averageRating, product.reviewCount),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                Text(
                    text = product.sellerName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

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
