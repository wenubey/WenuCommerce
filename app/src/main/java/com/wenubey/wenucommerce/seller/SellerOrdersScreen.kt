package com.wenubey.wenucommerce.seller

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// TODO Refactor Later
@Composable
fun SellerOrdersScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Orders",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        // Order status filters
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = true,
                        onClick = { },
                        label = { Text("All (23)") }
                    )
                }
                item {
                    FilterChip(
                        selected = false,
                        onClick = { },
                        label = { Text("Pending (5)") }
                    )
                }
                item {
                    FilterChip(
                        selected = false,
                        onClick = { },
                        label = { Text("Processing (8)") }
                    )
                }
                item {
                    FilterChip(
                        selected = false,
                        onClick = { },
                        label = { Text("Shipped (7)") }
                    )
                }
                item {
                    FilterChip(
                        selected = false,
                        onClick = { },
                        label = { Text("Delivered (3)") }
                    )
                }
            }
        }

        // Orders list
        items(20) { index ->
            SellerOrderCard(
                orderId = "#${1000 + index}",
                customerName = "Customer ${index + 1}",
                customerEmail = "customer${index + 1}@example.com",
                orderDate = "Jan ${15 + (index % 15)}, 2025",
                amount = "$${(index + 1) * 25}.99",
                status = when (index % 4) {
                    0 -> "Pending"
                    1 -> "Processing"
                    2 -> "Shipped"
                    else -> "Delivered"
                },
                itemCount = index % 5 + 1,
                onUpdateStatus = { },
                onViewDetails = { }
            )
        }
    }
}

@Composable
fun SellerOrderCard(
    orderId: String,
    customerName: String,
    customerEmail: String,
    orderDate: String,
    amount: String,
    status: String,
    itemCount: Int,
    onUpdateStatus: () -> Unit,
    onViewDetails: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Order header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = orderId,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Badge(
                    containerColor = when (status) {
                        "Pending" -> Color(0xFFFF9800)
                        "Processing" -> Color(0xFF2196F3)
                        "Shipped" -> Color(0xFF9C27B0)
                        "Delivered" -> Color(0xFF4CAF50)
                        else -> Color.Gray
                    }
                ) {
                    Text(text = status, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Customer info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = customerName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = customerEmail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = amount,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                    Text(
                        text = "$itemCount items",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = orderDate,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onViewDetails,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("View Details")
                }

                if (status in listOf("Pending", "Processing")) {
                    Button(
                        onClick = onUpdateStatus,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            when (status) {
                                "Pending" -> "Accept"
                                "Processing" -> "Ship"
                                else -> "Update"
                            }
                        )
                    }
                }
            }
        }
    }
}