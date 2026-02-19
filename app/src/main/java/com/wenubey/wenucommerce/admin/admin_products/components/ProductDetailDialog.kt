package com.wenubey.wenucommerce.admin.admin_products.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wenubey.domain.model.product.Product

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductDetailDialog(
    product: Product,
    onDismiss: () -> Unit,
    onApprove: () -> Unit,
    onSuspend: () -> Unit,
) {
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Product Review",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Images
                if (product.images.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(product.images) { index, image ->
                            if (image.downloadUrl.isNotBlank()) {
                                AsyncImage(
                                    model = image.downloadUrl,
                                    contentDescription = "Image ${index + 1}",
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
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

                HorizontalDivider()

                // Basic Info
                DetailSection(title = "Basic Info") {
                    DetailRow("Title", product.title)
                    DetailRow("Seller", product.sellerName)
                    DetailRow("Seller ID", product.sellerId)
                    DetailRow("Price", "$${product.basePrice}")
                    product.compareAtPrice?.let { DetailRow("Compare At", "$$it") }
                    DetailRow("Condition", product.condition.name.replace("_", " "))
                    DetailRow("Status", product.status.name.replace("_", " "))
                }

                HorizontalDivider()

                // Description
                DetailSection(title = "Description") {
                    Text(
                        text = product.description.ifBlank { "No description provided." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                HorizontalDivider()

                // Category
                DetailSection(title = "Category") {
                    DetailRow("Category", product.categoryName.ifBlank { "Not set" })
                    DetailRow("Subcategory", product.subcategoryName.ifBlank { "Not set" })
                }

                HorizontalDivider()

                // Tags
                if (product.tagNames.isNotEmpty()) {
                    DetailSection(title = "Tags") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            product.tagNames.forEach { tag ->
                                AssistChip(onClick = {}, label = { Text(tag) })
                            }
                        }
                    }
                    HorizontalDivider()
                }

                // Variants / Stock
                DetailSection(title = "Inventory") {
                    DetailRow("Has Variants", if (product.hasVariants) "Yes" else "No")
                    DetailRow("Total Stock", product.totalStockQuantity.toString())
                    if (product.hasVariants) {
                        Spacer(modifier = Modifier.height(4.dp))
                        product.variants.forEach { variant ->
                            Text(
                                text = "- ${variant.label}: ${variant.stockQuantity} units" +
                                        (variant.priceOverride?.let { " | \$$it" } ?: "") +
                                        (variant.sku.takeIf { it.isNotBlank() }?.let { " | SKU: $it" }
                                            ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Shipping
                DetailSection(title = "Shipping") {
                    DetailRow("Type", product.shipping.shippingType.name.replace("_", " "))
                    if (product.shipping.shippingCost > 0) {
                        DetailRow("Cost", "$${product.shipping.shippingCost}")
                    }
                    if (product.shipping.shipsFrom.isNotBlank()) {
                        DetailRow("Ships From", product.shipping.shipsFrom)
                    }
                    if (product.shipping.estimatedDaysMin > 0 || product.shipping.estimatedDaysMax > 0) {
                        DetailRow(
                            "Estimated Delivery",
                            "${product.shipping.estimatedDaysMin}-${product.shipping.estimatedDaysMax} days",
                        )
                    }
                    DetailRow(
                        "International",
                        if (product.shipping.isInternationalShipping) "Yes" else "No",
                    )
                }

                HorizontalDivider()

                // Meta
                DetailSection(title = "Metadata") {
                    DetailRow("Product ID", product.id)
                    DetailRow("Created", product.createdAt)
                    DetailRow("Last Updated", product.updatedAt)
                    if (product.moderationNotes.isNotBlank()) {
                        DetailRow("Moderation Notes", product.moderationNotes)
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onApprove,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Approve")
                }
                OutlinedButton(
                    onClick = onSuspend,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Default.Block,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Suspend", color = MaterialTheme.colorScheme.error)
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Close")
                }
            }
        },
    )
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.6f),
        )
    }
}
