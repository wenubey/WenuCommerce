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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wenubey.domain.model.product.Product
import com.wenubey.wenucommerce.admin.admin_products.components.ProductDetailDialog
import org.koin.androidx.compose.koinViewModel

@Composable
fun AdminProductModerationScreen(
    modifier: Modifier = Modifier,
    viewModel: AdminProductModerationViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            state.pendingProducts.isEmpty() -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color(0xFF4CAF50),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No products pending review",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "Product Moderation (${state.pendingProducts.size})",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    state.errorMessage?.let { error ->
                        item {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    items(state.pendingProducts, key = { it.id }) { product ->
                        ProductModerationCard(
                            product = product,
                            isSelected = state.selectedProduct?.id == product.id,
                            onClick = {
                                viewModel.onAction(AdminProductModerationAction.OnProductSelected(product))
                                viewModel.onAction(AdminProductModerationAction.OnShowDetailDialog)
                            },
                            onApprove = {
                                viewModel.onAction(AdminProductModerationAction.OnProductSelected(product))
                                viewModel.onAction(AdminProductModerationAction.OnShowApproveDialog)
                            },
                            onSuspend = {
                                viewModel.onAction(AdminProductModerationAction.OnProductSelected(product))
                                viewModel.onAction(AdminProductModerationAction.OnShowSuspendDialog)
                            },
                        )
                    }
                }
            }
        }

        if (state.isActing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }

    // Approve dialog
    if (state.showApproveDialog && state.selectedProduct != null) {
        AlertDialog(
            onDismissRequest = { viewModel.onAction(AdminProductModerationAction.OnDismissDialog) },
            title = { Text("Approve Product") },
            text = {
                Text("Are you sure you want to approve '${state.selectedProduct?.title}'? It will become visible to customers.")
            },
            confirmButton = {
                Button(onClick = { viewModel.onAction(AdminProductModerationAction.OnConfirmApprove) }) {
                    Text("Approve")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onAction(AdminProductModerationAction.OnDismissDialog) }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Suspend dialog
    if (state.showSuspendDialog && state.selectedProduct != null) {
        AlertDialog(
            onDismissRequest = { viewModel.onAction(AdminProductModerationAction.OnDismissDialog) },
            title = { Text("Suspend Product") },
            text = {
                Column {
                    Text("Suspending '${state.selectedProduct?.title}'. Please provide a reason:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.suspendReason,
                        onValueChange = {
                            viewModel.onAction(AdminProductModerationAction.OnSuspendReasonChanged(it))
                        },
                        label = { Text("Reason") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onAction(AdminProductModerationAction.OnConfirmSuspend) },
                    enabled = state.suspendReason.isNotBlank(),
                ) {
                    Text("Suspend", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onAction(AdminProductModerationAction.OnDismissDialog) }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Product Detail Dialog
    if (state.showDetailDialog && state.selectedProduct != null) {
        ProductDetailDialog(
            product = state.selectedProduct!!,
            onDismiss = { viewModel.onAction(AdminProductModerationAction.OnDismissDialog) },
            onApprove = {
                viewModel.onAction(AdminProductModerationAction.OnDismissDialog)
                viewModel.onAction(AdminProductModerationAction.OnShowApproveDialog)
            },
            onSuspend = {
                viewModel.onAction(AdminProductModerationAction.OnDismissDialog)
                viewModel.onAction(AdminProductModerationAction.OnShowSuspendDialog)
            },
        )
    }
}

@Composable
private fun ProductModerationCard(
    product: Product,
    isSelected: Boolean,
    onClick: () -> Unit,
    onApprove: () -> Unit,
    onSuspend: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 6.dp else 2.dp),
        colors = if (isSelected) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ) else CardDefaults.cardColors(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(40.dp))
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "Seller: ${product.sellerName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "$${product.basePrice}",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Category: ${product.categoryName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Stock: ${product.totalStockQuantity} | Variants: ${product.variants.size}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (product.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = product.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onSuspend) {
                    Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Suspend")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onApprove) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Approve")
                }
            }
        }
    }
}
