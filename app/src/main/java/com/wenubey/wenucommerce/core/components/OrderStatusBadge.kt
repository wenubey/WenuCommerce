package com.wenubey.wenucommerce.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wenubey.domain.model.order.AggregateOrderStatus
import com.wenubey.domain.model.order.OrderStatus

/**
 * Material 3 chip-style badge that renders an order status with a colour token
 * derived from the status. Used both at the order-row (aggregate) and per-seller
 * (sub-order) levels.
 */
@Composable
fun OrderStatusBadge(
    status: AggregateOrderStatus,
    modifier: Modifier = Modifier,
) {
    val (label, container, content) = aggregateStatusStyle(status)
    BadgeChip(label = label, container = container, content = content, modifier = modifier)
}

@Composable
fun OrderStatusBadge(
    status: OrderStatus,
    modifier: Modifier = Modifier,
) {
    val (label, container, content) = orderStatusStyle(status)
    BadgeChip(label = label, container = container, content = content, modifier = modifier)
}

@Composable
private fun BadgeChip(
    label: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        modifier = modifier
            .background(color = container, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun aggregateStatusStyle(status: AggregateOrderStatus): Triple<String, Color, Color> {
    val cs = MaterialTheme.colorScheme
    return when (status) {
        AggregateOrderStatus.PENDING -> Triple("Pending", cs.tertiaryContainer, cs.onTertiaryContainer)
        AggregateOrderStatus.CONFIRMED -> Triple("Confirmed", cs.tertiaryContainer, cs.onTertiaryContainer)
        AggregateOrderStatus.SHIPPED -> Triple("Shipped", cs.primaryContainer, cs.onPrimaryContainer)
        AggregateOrderStatus.DELIVERED -> Triple("Delivered", cs.primary, cs.onPrimary)
        AggregateOrderStatus.CANCELLED -> Triple("Cancelled", cs.errorContainer, cs.onErrorContainer)
        AggregateOrderStatus.PARTIALLY_CANCELLED -> Triple("Partially cancelled", cs.errorContainer, cs.onErrorContainer)
    }
}

@Composable
private fun orderStatusStyle(status: OrderStatus): Triple<String, Color, Color> {
    val cs = MaterialTheme.colorScheme
    return when (status) {
        OrderStatus.PENDING -> Triple("Pending", cs.tertiaryContainer, cs.onTertiaryContainer)
        OrderStatus.CONFIRMED -> Triple("Confirmed", cs.tertiaryContainer, cs.onTertiaryContainer)
        OrderStatus.SHIPPED -> Triple("Shipped", cs.primaryContainer, cs.onPrimaryContainer)
        OrderStatus.DELIVERED -> Triple("Delivered", cs.primary, cs.onPrimary)
        OrderStatus.CANCELLED -> Triple("Cancelled", cs.errorContainer, cs.onErrorContainer)
    }
}
