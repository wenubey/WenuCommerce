package com.wenubey.wenucommerce.seller.seller_products.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wenubey.domain.model.product.ProductVariant

@Composable
internal fun VariantRow(
    variant: ProductVariant,
    enabled: Boolean = true,
    onUpdate: (ProductVariant) -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Variant",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (enabled) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove variant",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = variant.label,
                onValueChange = { onUpdate(variant.copy(label = it)) },
                label = { Text("Label (e.g. Red / XL)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = true,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = variant.sku,
                    onValueChange = { onUpdate(variant.copy(sku = it)) },
                    label = { Text("SKU") },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    singleLine = true,
                )
                OutlinedTextField(
                    value = variant.stockQuantity.toString(),
                    onValueChange = { qty ->
                        onUpdate(variant.copy(stockQuantity = qty.toIntOrNull() ?: 0))
                    },
                    label = { Text("Stock") },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    singleLine = true,
                )
            }

            OutlinedTextField(
                value = variant.priceOverride?.toString() ?: "",
                onValueChange = { price ->
                    onUpdate(variant.copy(priceOverride = price.toDoubleOrNull()))
                },
                label = { Text("Price Override (\$) - leave empty to use base price") },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = true,
            )
        }
    }
}
