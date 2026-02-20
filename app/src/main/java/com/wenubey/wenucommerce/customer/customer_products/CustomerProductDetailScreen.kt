package com.wenubey.wenucommerce.customer.customer_products

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wenubey.domain.model.product.ProductReview
import com.wenubey.domain.model.product.ShippingType
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomerProductDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: CustomerProductDetailViewModel = koinViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToCart: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show snackbar when a cart message is set
    val cartMessage = state.cartMessage
    LaunchedEffect(cartMessage) {
        if (cartMessage != null) {
            val result = snackbarHostState.showSnackbar(
                message = cartMessage,
                actionLabel = if (cartMessage == "Added to cart") "View Cart" else null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                onNavigateToCart()
            }
            viewModel.onAction(CustomerProductDetailAction.DismissCartMessage)
        }
    }

    // Login prompt dialog
    if (state.showLoginPrompt) {
        AlertDialog(
            onDismissRequest = {
                viewModel.onAction(CustomerProductDetailAction.DismissLoginPrompt)
            },
            title = { Text("Sign in required") },
            text = { Text("Please sign in to add items to your cart.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onAction(CustomerProductDetailAction.DismissLoginPrompt)
                    // Navigation to sign-in handled by the host — for now dismiss
                }) {
                    Text("Sign In")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.onAction(CustomerProductDetailAction.DismissLoginPrompt)
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.product == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = state.errorMessage ?: "Product not found",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            else -> {
                val product = state.product!!
                val selectedVariant = state.selectedVariant
                val displayPrice = selectedVariant?.priceOverride ?: product.basePrice
                val isOutOfStock = product.totalStockQuantity <= 0

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Images
                    item {
                        if (product.images.isNotEmpty()) {
                            val sortedImages = product.images.sortedBy { it.sortOrder }
                            val pagerState = rememberPagerState(pageCount = { sortedImages.size })
                            val coroutineScope = rememberCoroutineScope()

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Main pager
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp)),
                                ) { page ->
                                    AsyncImage(
                                        model = sortedImages[page].downloadUrl,
                                        contentDescription = "${product.title} image ${page + 1}",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop,
                                    )
                                }

                                // Page indicator dots
                                if (sortedImages.size > 1) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        sortedImages.forEachIndexed { index, _ ->
                                            Box(
                                                modifier = Modifier
                                                    .padding(horizontal = 4.dp)
                                                    .size(if (pagerState.currentPage == index) 10.dp else 8.dp)
                                                    .background(
                                                        color = if (pagerState.currentPage == index)
                                                            MaterialTheme.colorScheme.primary
                                                        else
                                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                                        shape = CircleShape,
                                                    )
                                            )
                                        }
                                    }
                                }

                                // Thumbnail row
                                if (sortedImages.size > 1) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(sortedImages.size) { index ->
                                            val isSelected = pagerState.currentPage == index
                                            AsyncImage(
                                                model = sortedImages[index].downloadUrl,
                                                contentDescription = "${product.title} thumbnail ${index + 1}",
                                                modifier = Modifier
                                                    .size(60.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .border(
                                                        width = if (isSelected) 2.dp else 1.dp,
                                                        color = if (isSelected)
                                                            MaterialTheme.colorScheme.primary
                                                        else
                                                            MaterialTheme.colorScheme.outlineVariant,
                                                        shape = RoundedCornerShape(8.dp),
                                                    )
                                                    .clickable {
                                                        coroutineScope.launch {
                                                            pagerState.animateScrollToPage(index)
                                                        }
                                                    },
                                                contentScale = ContentScale.Crop,
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(250.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(64.dp))
                            }
                        }
                    }

                    // Title & Price
                    item {
                        Text(
                            text = product.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$${displayPrice}",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                            product.compareAtPrice?.let { compareAt ->
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "$$compareAt",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textDecoration = TextDecoration.LineThrough,
                                )
                            }
                        }

                        // Rating
                        if (product.reviewCount > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                repeat(5) { index ->
                                    Icon(
                                        imageVector = if (index < product.averageRating.toInt()) Icons.Filled.Star else Icons.Outlined.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = Color(0xFFFFC107),
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "%.1f (%d reviews)".format(product.averageRating, product.reviewCount),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Sold by ${product.sellerName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Condition: ${product.condition.name.replace("_", " ")}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    // Variants
                    if (product.hasVariants && product.variants.size > 1) {
                        item {
                            Text(
                                text = "Options",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                product.variants.forEach { variant ->
                                    FilterChip(
                                        selected = selectedVariant?.id == variant.id,
                                        onClick = {
                                            viewModel.onAction(CustomerProductDetailAction.OnVariantSelected(variant))
                                        },
                                        label = {
                                            Text(variant.label)
                                        },
                                        enabled = variant.inStock,
                                    )
                                }
                            }

                            selectedVariant?.let { variant ->
                                if (!variant.inStock) {
                                    Text(
                                        text = "Out of stock",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                } else {
                                    Text(
                                        text = "${variant.stockQuantity} in stock",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    // Description
                    item {
                        Text(
                            text = "Description",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = product.description,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    // Shipping
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.LocalShipping,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    val shippingText = when (product.shipping.shippingType) {
                                        ShippingType.FREE_SHIPPING -> "Free Shipping"
                                        ShippingType.PAID_SHIPPING -> "Shipping: $${product.shipping.shippingCost}"
                                        ShippingType.LOCAL_PICKUP_ONLY -> "Local Pickup Only"
                                        ShippingType.DIGITAL_DELIVERY -> "Digital Delivery"
                                    }
                                    Text(text = shippingText, style = MaterialTheme.typography.bodyMedium)
                                    if (product.shipping.shipsFrom.isNotBlank()) {
                                        Text(
                                            text = "Ships from ${product.shipping.shipsFrom}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (product.shipping.estimatedDaysMin > 0) {
                                        Text(
                                            text = "Delivery: ${product.shipping.estimatedDaysMin}–${product.shipping.estimatedDaysMax} days",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Add to Cart section
                    item {
                        CartActionSection(
                            isOutOfStock = isOutOfStock,
                            isInCart = state.isInCart,
                            cartQuantity = state.cartQuantity,
                            selectedQuantity = state.selectedQuantity,
                            maxStock = product.totalStockQuantity,
                            isAddingToCart = state.isAddingToCart,
                            onSetQuantity = { qty ->
                                viewModel.onAction(CustomerProductDetailAction.SetQuantity(qty))
                            },
                            onAddToCart = {
                                viewModel.onAction(CustomerProductDetailAction.AddToCart)
                            },
                            onUpdateCartQuantity = { newQty ->
                                viewModel.onAction(CustomerProductDetailAction.UpdateCartQuantity(newQty))
                            },
                        )
                    }

                    // Tags — display tagNames (denormalised) if available, fallback to tags
                    val displayTags = product.tagNames.ifEmpty { product.tags }
                    if (displayTags.isNotEmpty()) {
                        item {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                displayTags.forEach { tag ->
                                    Text(
                                        text = "#$tag",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }

                    // Reviews section
                    item {
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Reviews (${state.reviews.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    if (state.reviews.isEmpty()) {
                        item {
                            Text(
                                text = "No reviews yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    items(state.reviews, key = { it.id }) { review ->
                        ReviewCard(
                            review = review,
                            onHelpful = {
                                viewModel.onAction(
                                    CustomerProductDetailAction.OnMarkReviewHelpful(review.id)
                                )
                            }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }
        }

        // Snackbar at the bottom
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun CartActionSection(
    isOutOfStock: Boolean,
    isInCart: Boolean,
    cartQuantity: Int,
    selectedQuantity: Int,
    maxStock: Int,
    isAddingToCart: Boolean,
    onSetQuantity: (Int) -> Unit,
    onAddToCart: () -> Unit,
    onUpdateCartQuantity: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Low stock warning (when stock <= 3 and in stock)
        if (!isOutOfStock && maxStock <= 3) {
            Text(
                text = "Only $maxStock left",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Medium,
            )
        }

        when {
            isOutOfStock -> {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Out of Stock")
                }
            }
            isInCart -> {
                // Already in cart: show "In Cart (x{qty})" with inline stepper
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Stepper for adjusting cart quantity
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        IconButton(
                            onClick = {
                                val newQty = cartQuantity - 1
                                if (newQty >= 1) onUpdateCartQuantity(newQty)
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Text(
                                text = "-",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            text = "$cartQuantity",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        IconButton(
                            onClick = {
                                val newQty = cartQuantity + 1
                                if (newQty <= maxStock) onUpdateCartQuantity(newQty)
                            },
                            modifier = Modifier.size(36.dp),
                            enabled = cartQuantity < maxStock,
                        ) {
                            Text(
                                text = "+",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        enabled = false,
                    ) {
                        Icon(
                            Icons.Default.ShoppingCart,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("In Cart (x$cartQuantity)")
                    }
                }
            }
            else -> {
                // Not in cart: show stepper + "Add to Cart" button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Quantity stepper
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        IconButton(
                            onClick = { onSetQuantity(selectedQuantity - 1) },
                            modifier = Modifier.size(36.dp),
                            enabled = selectedQuantity > 1,
                        ) {
                            Text(
                                text = "-",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            text = "$selectedQuantity",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        IconButton(
                            onClick = { onSetQuantity(selectedQuantity + 1) },
                            modifier = Modifier.size(36.dp),
                            enabled = selectedQuantity < maxStock,
                        ) {
                            Text(
                                text = "+",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Button(
                        onClick = onAddToCart,
                        modifier = Modifier.weight(1f),
                        enabled = !isAddingToCart,
                    ) {
                        if (isAddingToCart) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Add to Cart")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(
    review: ProductReview,
    onHelpful: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (review.reviewerPhotoUrl.isNotBlank()) {
                    AsyncImage(
                        model = review.reviewerPhotoUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = review.reviewerName.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = review.reviewerName, style = MaterialTheme.typography.labelMedium)
                    Row {
                        repeat(5) { index ->
                            Icon(
                                imageVector = if (index < review.rating) Icons.Filled.Star else Icons.Outlined.Star,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFFFFC107),
                            )
                        }
                    }
                }
            }

            if (review.title.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = review.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = review.body,
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onHelpful, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.Star,
                        contentDescription = "Helpful",
                        modifier = Modifier.size(16.dp),
                    )
                }
                if (review.helpfulCount > 0) {
                    Text(
                        text = "${review.helpfulCount} found helpful",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
