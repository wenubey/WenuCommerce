package com.wenubey.wenucommerce.customer.order_confirmation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wenubey.domain.model.order.Order
import com.wenubey.domain.model.order.OrderItem
import com.wenubey.domain.model.order.OrderStatus
import com.wenubey.domain.model.order.ShippingAddress
import com.wenubey.domain.repository.PaymentRepository
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinimalOrderScreen(
    orderId: String,
    onNavigateBack: () -> Unit = {},
    paymentRepository: PaymentRepository = koinInject(),
) {
    val order by paymentRepository.observeOrderById(orderId)
        .collectAsStateWithLifecycle(initialValue = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Order Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        if (order == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            OrderContent(
                order = order!!,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            )
        }
    }
}

@Composable
private fun OrderContent(
    order: Order,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        // Status badge
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Status:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                StatusChip(status = order.status)
            }
        }

        // Order ID
        item {
            Column {
                Text(
                    text = "Order ID",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = order.id,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // Date
        item {
            if (order.createdAt.isNotEmpty()) {
                Column {
                    Text(
                        text = "Date",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatOrderDate(order.createdAt),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // Items section header
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Items",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
        }

        // Items
        items(order.items) { item ->
            OrderItemRow(item = item)
        }

        // Totals section
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Order Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            HorizontalDivider(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            TotalsSection(
                subtotal = order.subtotal,
                shippingTotal = order.shippingTotal,
                totalAmount = order.totalAmount,
            )
        }

        // Shipping address
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Shipping Address",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            ShippingAddressCard(address = order.shippingAddress)
        }

        // Bottom padding
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun StatusChip(
    status: OrderStatus,
    modifier: Modifier = Modifier,
) {
    val (backgroundColor, contentColor) = when (status) {
        OrderStatus.PENDING -> Color(0xFFFFA000) to Color.White
        OrderStatus.CONFIRMED -> Color(0xFF1976D2) to Color.White
        OrderStatus.SHIPPED -> Color(0xFF7B1FA2) to Color.White
        OrderStatus.DELIVERED -> Color(0xFF388E3C) to Color.White
        OrderStatus.CANCELLED -> Color(0xFFD32F2F) to Color.White
    }

    SuggestionChip(
        onClick = {},
        label = {
            Text(
                text = status.displayName,
                color = contentColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        modifier = modifier,
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = backgroundColor,
        ),
    )
}

@Composable
private fun OrderItemRow(
    item: OrderItem,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.productTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
            )
            Text(
                text = "x${item.quantity} @ ${"$%.2f".format(item.snapshotPrice)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "$%.2f".format(item.lineTotal),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
    HorizontalDivider(thickness = 0.5.dp)
}

@Composable
private fun TotalsSection(
    subtotal: Double,
    shippingTotal: Double,
    totalAmount: Double,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TotalsRow(label = "Subtotal", amount = subtotal)
        TotalsRow(label = "Shipping", amount = shippingTotal)
        HorizontalDivider()
        TotalsRow(
            label = "Total",
            amount = totalAmount,
            isBold = true,
        )
    }
}

@Composable
private fun TotalsRow(
    label: String,
    amount: Double,
    isBold: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
        )
        Text(
            text = "$%.2f".format(amount),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ShippingAddressCard(
    address: ShippingAddress,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (address.fullName.isNotEmpty()) {
                Text(
                    text = address.fullName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = address.line1,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (address.line2.isNotEmpty()) {
                Text(
                    text = address.line2,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = "${address.city}, ${address.state} ${address.postalCode}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (address.country.isNotEmpty()) {
                Text(
                    text = address.country,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatOrderDate(isoDate: String): String {
    return try {
        // Parse ISO-8601 date string and format for display
        val parts = isoDate.split("T")
        if (parts.size >= 1) {
            val dateParts = parts[0].split("-")
            if (dateParts.size == 3) {
                val year = dateParts[0]
                val month = dateParts[1].toIntOrNull() ?: 0
                val day = dateParts[2].toIntOrNull() ?: 0
                val monthName = listOf(
                    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
                ).getOrElse(month - 1) { "?" }
                "$monthName $day, $year"
            } else isoDate
        } else isoDate
    } catch (_: Exception) {
        isoDate
    }
}
