package com.wenubey.wenucommerce.customer.orders

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wenubey.domain.model.order.Order
import com.wenubey.domain.model.order.OrderStatus
import com.wenubey.domain.model.order.SellerOrder
import com.wenubey.wenucommerce.core.components.OrderStatusBadge
import com.wenubey.wenucommerce.core.components.OrderStatusStepper
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerOrderDetailScreen(
    orderId: String,
    onBack: () -> Unit,
    viewModel: CustomerOrderDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.onAction(CustomerOrderDetailAction.OnDismissError)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.order?.let { "Order #${it.id.take(8)}" } ?: "Order") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            state.isLoading && state.order == null -> {
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Loading…")
                }
            }
            state.order == null -> {
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Order not found")
                }
            }
            else -> {
                val order = state.order!!
                LazyColumn(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { OrderHeaderCard(order = order) }
                    if (state.sellerOrders.isEmpty()) {
                        // Webhook-latency window: the paid order exists but its
                        // per-seller sub-orders have not synced yet.
                        item {
                            Text(
                                text = "We're finalizing your order — item details will appear shortly.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                    items(state.sellerOrders, key = { it.id }) { seller ->
                        SellerSection(
                            sellerOrder = seller,
                            isExpanded = state.expandedSellerIds.contains(seller.id),
                            isCollapsible = state.sellerOrders.size > 1,
                            onToggle = {
                                viewModel.onAction(CustomerOrderDetailAction.OnToggleSection(seller.id))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderHeaderCard(order: Order) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Shipping to", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(order.shippingAddress.fullName, style = MaterialTheme.typography.bodyMedium)
            Text(order.shippingAddress.line1, style = MaterialTheme.typography.bodySmall)
            if (order.shippingAddress.line2.isNotBlank()) {
                Text(order.shippingAddress.line2, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "${order.shippingAddress.city}, ${order.shippingAddress.state} ${order.shippingAddress.postalCode}",
                style = MaterialTheme.typography.bodySmall,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Subtotal", style = MaterialTheme.typography.bodyMedium)
                Text("$%.2f".format(order.subtotal), style = MaterialTheme.typography.bodyMedium)
            }
            if (order.shippingTotal > 0.0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Shipping", style = MaterialTheme.typography.bodyMedium)
                    Text("$%.2f".format(order.shippingTotal), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (order.discountAmount > 0.0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "You saved",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "$%.2f".format(order.discountAmount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Total", style = MaterialTheme.typography.titleMedium)
                Text("$%.2f".format(order.totalAmount), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun SellerSection(
    sellerOrder: SellerOrder,
    isExpanded: Boolean,
    isCollapsible: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { if (isCollapsible) it.clickable { onToggle() } else it }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = sellerOrder.sellerName.ifBlank { "Seller" },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                OrderStatusBadge(status = sellerOrder.status)
                if (isCollapsible) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                    )
                }
            }

            // Body (single-seller always visible; multi-seller toggles)
            val showBody = !isCollapsible || isExpanded
            AnimatedVisibility(visible = showBody) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (sellerOrder.items.isNotEmpty()) {
                        HorizontalDivider()
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            sellerOrder.items.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "${item.quantity}× ${item.productTitle}",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = "$%.2f".format(item.lineTotal),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                    OrderStatusStepper(
                        history = sellerOrder.statusHistory,
                        currentStatus = sellerOrder.status,
                        trackingNumber = sellerOrder.trackingNumber,
                    )
                    if (sellerOrder.status == OrderStatus.CANCELLED && sellerOrder.refundedAmount != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Refund of $%.2f issued — funds typically arrive within 5–10 business days"
                                .format(sellerOrder.refundedAmount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
