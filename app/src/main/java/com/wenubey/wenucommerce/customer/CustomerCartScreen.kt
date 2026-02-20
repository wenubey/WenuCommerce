package com.wenubey.wenucommerce.customer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wenubey.domain.model.CartItem
import com.wenubey.wenucommerce.customer.customer_cart.CartAction
import com.wenubey.wenucommerce.customer.customer_cart.CartViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerCartScreen(
    modifier: Modifier = Modifier,
    onNavigateToProduct: (String) -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    viewModel: CartViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show undo snackbar when an item is removed
    val undoItem = state.undoItem
    LaunchedEffect(undoItem) {
        if (undoItem != null) {
            val result = snackbarHostState.showSnackbar(
                message = "Item removed",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            when (result) {
                SnackbarResult.ActionPerformed -> viewModel.onAction(CartAction.UndoRemove(undoItem))
                SnackbarResult.Dismissed -> viewModel.clearUndoItem()
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AnimatedVisibility(visible = state.isSelectionMode) {
                TopAppBar(
                    title = { Text("${state.selectedItems.size} selected") },
                    actions = {
                        TextButton(onClick = { viewModel.onAction(CartAction.ClearSelection) }) {
                            Text("Cancel")
                        }
                        IconButton(onClick = { viewModel.onAction(CartAction.DeleteSelected) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (state.cartItems.isNotEmpty() && !state.isLoading) {
                CartBottomBar(
                    subtotal = state.subtotal,
                    canCheckout = state.canCheckout,
                    onCheckout = { viewModel.onAction(CartAction.Checkout) }
                )
            }
        }
    ) { paddingValues ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.error ?: "An error occurred",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            else -> {
                AnimatedContent(
                    targetState = state.cartItems.isEmpty(),
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "cart_empty_state",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                ) { isEmpty ->
                    if (isEmpty) {
                        CartEmptyState(onStartShopping = onNavigateToHome)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            items(
                                items = state.cartItems,
                                key = { it.productId },
                            ) { cartItem ->
                                CartItemRow(
                                    item = cartItem,
                                    isSelectionMode = state.isSelectionMode,
                                    isSelected = cartItem.productId in state.selectedItems,
                                    onIncrement = {
                                        viewModel.onAction(CartAction.IncrementQuantity(cartItem.productId))
                                    },
                                    onDecrement = {
                                        viewModel.onAction(CartAction.DecrementQuantity(cartItem.productId))
                                    },
                                    onRemove = {
                                        viewModel.onAction(CartAction.RemoveItem(cartItem.productId))
                                    },
                                    onToggleSelection = {
                                        viewModel.onAction(CartAction.ToggleSelection(cartItem.productId))
                                    },
                                    onNavigateToProduct = {
                                        onNavigateToProduct(cartItem.productId)
                                    },
                                )
                            }
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CartItemRow(
    item: CartItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit,
    onToggleSelection: () -> Unit,
    onNavigateToProduct: () -> Unit,
) {
    val isUnavailable = item.isProductDeleted || item.availableStock <= 0
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRemove()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.error)
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete item",
                    tint = MaterialTheme.colorScheme.onError,
                )
            }
        },
    ) {
        Surface(
            onClick = if (isSelectionMode) onToggleSelection else onNavigateToProduct,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .alpha(if (isUnavailable) 0.5f else 1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelection() },
                    )
                }

                // Product thumbnail
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.shapes.small,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.productImageUrl.isNotBlank()) {
                        AsyncImage(
                            model = item.productImageUrl,
                            contentDescription = item.productTitle,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            Icons.Default.ShoppingCart,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Unavailability overlay
                    if (isUnavailable) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                    MaterialTheme.shapes.small,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (item.isProductDeleted) "N/A" else "Out",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onError,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                // Product info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.productTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                    )
                    Text(
                        text = "$%.2f".format(item.snapshotPrice),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    when {
                        item.isProductDeleted -> Text(
                            text = "No longer available",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        item.availableStock <= 0 -> Text(
                            text = "Out of Stock",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        item.availableStock <= 3 -> Text(
                            text = "Only ${item.availableStock} left",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }

                // Stepper + trash
                if (!isUnavailable) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            IconButton(
                                onClick = onDecrement,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Text(
                                    text = "-",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                text = "${item.quantity}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.width(24.dp),
                            )
                            IconButton(
                                onClick = onIncrement,
                                modifier = Modifier.size(32.dp),
                                enabled = item.quantity < item.availableStock,
                            ) {
                                Text(
                                    text = "+",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        IconButton(
                            onClick = onRemove,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove item",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CartEmptyState(
    onStartShopping: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.ShoppingCart,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Text(
                text = "Your cart feels lonely!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Add some products to get started",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onStartShopping) {
                Text("Start Shopping")
            }
        }
    }
}

@Composable
private fun CartBottomBar(
    subtotal: Double,
    canCheckout: Boolean,
    onCheckout: () -> Unit,
) {
    Surface(
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Subtotal",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "$%.2f".format(subtotal),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Button(
                onClick = onCheckout,
                enabled = canCheckout,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text("Checkout")
            }
        }
    }
}
