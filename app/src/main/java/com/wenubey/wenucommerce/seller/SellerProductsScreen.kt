package com.wenubey.wenucommerce.seller

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wenubey.domain.model.product.Product
import com.wenubey.domain.model.product.ProductStatus
import com.wenubey.wenucommerce.core.components.WenuSearchBar
import com.wenubey.wenucommerce.seller.seller_products.SellerProductListAction
import com.wenubey.wenucommerce.seller.seller_products.SellerProductListViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SellerProductsScreen(
    modifier: Modifier = Modifier,
    viewModel: SellerProductListViewModel = koinViewModel(),
    onAddProduct: () -> Unit = {},
    onEditProduct: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var isFilterExpanded by rememberSaveable { mutableStateOf(false) }

    val activeFilterCount = listOfNotNull(
        state.filterCategoryId,
        state.filterSubcategoryId,
    ).size

    Box(modifier = modifier.fillMaxSize()) {
        if (state.isLoading && state.products.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                stickyHeader {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "My Products",
                            style = MaterialTheme.typography.headlineSmall,
                        )

                        WenuSearchBar(
                            query = state.searchQuery,
                            onQueryChange = {
                                viewModel.onAction(SellerProductListAction.OnSearchQueryChanged(it))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            isFilterExpanded = isFilterExpanded,
                            onFilterToggle = {
                                viewModel.onAction(SellerProductListAction.OnRequestCategoryLoad)
                                isFilterExpanded = !isFilterExpanded
                            },
                            activeFilterCount = activeFilterCount,
                            categories = state.categories,
                            isLoadingCategories = state.isLoadingCategories,
                            selectedCategoryId = state.filterCategoryId,
                            selectedSubcategoryId = state.filterSubcategoryId,
                            onCategorySelected = { categoryId ->
                                viewModel.onAction(
                                    SellerProductListAction.OnFilterCategorySelected(categoryId)
                                )
                            },
                            onSubcategorySelected = { subcategoryId ->
                                viewModel.onAction(
                                    SellerProductListAction.OnFilterSubcategorySelected(subcategoryId)
                                )
                            },
                            onClearFilters = {
                                viewModel.onAction(SellerProductListAction.OnClearCategoryFilters)
                            },
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item {
                                FilterChip(
                                    selected = state.selectedStatusFilter == null,
                                    onClick = {
                                        viewModel.onAction(SellerProductListAction.OnStatusFilterSelected(null))
                                    },
                                    label = { Text("All") }
                                )
                            }
                            items(ProductStatus.entries.toList()) { status ->
                                FilterChip(
                                    selected = state.selectedStatusFilter == status,
                                    onClick = {
                                        viewModel.onAction(
                                            SellerProductListAction.OnStatusFilterSelected(status)
                                        )
                                    },
                                    label = { Text(status.name.replace("_", " ")) }
                                )
                            }
                        }
                    }
                }

                // Error
                state.errorMessage?.let { error ->
                    item {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                if (state.filteredProducts.isEmpty() && !state.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No products found. Tap 'Add Product' to create one.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                items(state.filteredProducts, key = { it.id }) { product ->
                    SellerProductCard(
                        product = product,
                        onEdit = { onEditProduct(product.id) },
                        onSubmitForReview = {
                            viewModel.onAction(SellerProductListAction.OnSubmitForReview(product.id))
                        },
                        onArchive = {
                            viewModel.onAction(SellerProductListAction.OnShowDeleteDialog(product))
                        },
                        onUnarchive = {
                            viewModel.onAction(SellerProductListAction.OnUnarchiveProduct(product.id))
                        },
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = onAddProduct,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Product")
        }
    }

    // Archive confirmation dialog
    if (state.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onAction(SellerProductListAction.OnDismissDeleteDialog) },
            title = { Text("Archive Product") },
            text = { Text("Are you sure you want to archive '${state.productToDelete?.title}'?") },
            confirmButton = {
                TextButton(onClick = { viewModel.onAction(SellerProductListAction.OnConfirmDelete) }) {
                    Text("Archive", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onAction(SellerProductListAction.OnDismissDeleteDialog) }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SellerProductCard(
    product: Product,
    onEdit: () -> Unit,
    onSubmitForReview: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
) {
    val statusColor = when (product.status) {
        ProductStatus.ACTIVE -> Color(0xFF4CAF50)
        ProductStatus.DRAFT -> Color(0xFFFF9800)
        ProductStatus.PENDING_REVIEW -> Color(0xFF2196F3)
        ProductStatus.SUSPENDED -> Color(0xFFE53E3E)
        ProductStatus.ARCHIVED -> Color(0xFF9E9E9E)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Product Image
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

                // Product Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "$${product.basePrice}",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Stock: ${product.totalStockQuantity}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (product.moderationNotes.isNotBlank() && product.status == ProductStatus.SUSPENDED) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Note: ${product.moderationNotes}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Badge(containerColor = statusColor) {
                        Text(
                            text = product.status.name.replace("_", " "),
                            color = Color.White,
                        )
                    }
                }

                // Actions
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (product.status == ProductStatus.DRAFT || product.status == ProductStatus.SUSPENDED) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = onSubmitForReview) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Submit for Review"
                            )
                        }
                    }
                    if (product.status != ProductStatus.ARCHIVED) {
                        IconButton(onClick = onArchive) {
                            Icon(
                                Icons.Default.Archive,
                                contentDescription = "Archive",
                                tint = Color(0xFFE53E3E),
                            )
                        }
                    } else {
                        IconButton(onClick = onUnarchive) {
                            Icon(
                                Icons.Default.Unarchive,
                                contentDescription = "Unarchive",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            // Tags overlay in top-right corner
            if (product.tagNames.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    product.tagNames.take(3).forEach { tag ->
                        Badge(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
