package com.wenubey.wenucommerce.seller.orders

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wenubey.domain.model.order.OrderStatus
import com.wenubey.domain.model.order.SellerOrder
import com.wenubey.domain.model.order.allowedNext
import com.wenubey.wenucommerce.core.components.OrderStatusBadge
import com.wenubey.wenucommerce.core.components.OrderStatusStepper
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellerOrderDetailScreen(
    sellerOrderId: String,
    onNavigateBack: () -> Unit,
    viewModel: SellerOrderDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.onAction(SellerOrderDetailAction.OnDismissError)
        }
    }
    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.onAction(SellerOrderDetailAction.OnConsumeToast)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Order #${sellerOrderId.take(8)}") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        val order = state.sellerOrder
        if (order == null) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            return@Scaffold
        }

        SellerOrderDetailBody(
            order = order,
            isMutating = state.isMutating,
            onAction = viewModel::onAction,
            contentPadding = paddingValues,
        )

        if (state.showShippedDialog) {
            MarkAsShippedDialog(
                onApply = { tracking ->
                    viewModel.onAction(SellerOrderDetailAction.OnConfirmShipped(tracking))
                },
                onSkip = {
                    viewModel.onAction(SellerOrderDetailAction.OnConfirmShipped(null))
                },
                onDismiss = {
                    viewModel.onAction(SellerOrderDetailAction.OnDismissShippedDialog)
                },
            )
        }
        if (state.showCancelDialog) {
            val refundPreview = order.subtotal + order.shippingShare - order.discountShare
            CancelOrderDialog(
                refundPreviewAmount = refundPreview,
                onConfirm = { viewModel.onAction(SellerOrderDetailAction.OnConfirmCancel) },
                onDismiss = { viewModel.onAction(SellerOrderDetailAction.OnDismissCancelDialog) },
            )
        }
    }
}

@Composable
private fun SellerOrderDetailBody(
    order: SellerOrder,
    isMutating: Boolean,
    onAction: (SellerOrderDetailAction) -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header card: id + status + totals
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Order #${order.id.take(8)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    OrderStatusBadge(status = order.status)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Placed: ${order.createdAt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = String.format(
                        Locale.US,
                        "Subtotal: $%.2f  •  Shipping: $%.2f  •  Discount: -$%.2f",
                        order.subtotal,
                        order.shippingShare,
                        order.discountShare,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                val total = order.subtotal + order.shippingShare - order.discountShare
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = String.format(Locale.US, "Total: $%.2f", total),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Items
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Items",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (order.items.isEmpty()) {
                    Text(
                        text = "No items",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    order.items.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${item.quantity}× ${item.productTitle}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "$%.2f",
                                    item.lineTotal,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }

        // Status timeline (stepper)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Status timeline",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OrderStatusStepper(
                    history = order.statusHistory,
                    currentStatus = order.status,
                    trackingNumber = order.trackingNumber,
                )
            }
        }

        // Cancelled — refund info footer
        if (order.status == OrderStatus.CANCELLED && order.refundedAmount != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Text(
                    text = String.format(
                        Locale.US,
                        "Refund of $%.2f issued — funds typically arrive within 5–10 business days.",
                        order.refundedAmount,
                    ),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        // Action row — forward-only advance + pre-SHIPPED cancel
        SellerOrderActionsRow(
            currentStatus = order.status,
            isMutating = isMutating,
            onAction = onAction,
        )
    }
}

@Composable
private fun SellerOrderActionsRow(
    currentStatus: OrderStatus,
    isMutating: Boolean,
    onAction: (SellerOrderDetailAction) -> Unit,
) {
    val nextStatuses = currentStatus.allowedNext()
    val advanceTargets = nextStatuses.filter { it != OrderStatus.CANCELLED }
    val canCancel = currentStatus in setOf(OrderStatus.PENDING, OrderStatus.CONFIRMED)

    if (advanceTargets.isEmpty() && !canCancel) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        advanceTargets.forEach { next ->
            Button(
                onClick = { onAction(SellerOrderDetailAction.OnAdvanceClicked(next)) },
                enabled = !isMutating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Advance to ${next.displayName}")
            }
        }
        if (canCancel) {
            OutlinedButton(
                onClick = { onAction(SellerOrderDetailAction.OnCancelClicked) },
                enabled = !isMutating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cancel order")
            }
        }
    }
}
