package com.wenubey.wenucommerce.seller.seller_discounts

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.wenubey.domain.model.discount.DiscountCode
import com.wenubey.domain.model.discount.DiscountType
import org.koin.androidx.compose.koinViewModel
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellerDiscountListScreen(
    onNavigateToCreate: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscountListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text("Discounts") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToCreate) {
                Icon(Icons.Default.Add, contentDescription = "Create discount")
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                state.discounts.isEmpty() -> {
                    Text(
                        text = "No discount codes yet",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    ) {
                        items(state.discounts, key = { it.code }) { discount ->
                            DiscountListItem(
                                discount = discount,
                                onClick = { onNavigateToEdit(discount.code) },
                                onDeactivate = {
                                    viewModel.onAction(DiscountListAction.Deactivate(discount.code))
                                },
                                onDelete = {
                                    viewModel.onAction(DiscountListAction.Delete(discount.code))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscountListItem(
    discount: DiscountCode,
    onClick: () -> Unit,
    onDeactivate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = deriveStatus(discount)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Row 1: Code + status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = discount.code,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                StatusBadge(status = status)
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Row 2: Discount type + amount
            Text(
                text = formatDiscountDescription(discount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Row 3: Usage count + actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatUsage(discount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row {
                    if (discount.isActive) {
                        IconButton(onClick = onDeactivate) {
                            Icon(
                                Icons.Default.VisibilityOff,
                                contentDescription = "Deactivate",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: DiscountStatus) {
    val (text, color) = when (status) {
        DiscountStatus.ACTIVE -> "Active" to Color(0xFF2E7D32)
        DiscountStatus.EXPIRED -> "Expired" to Color(0xFFF57F17)
        DiscountStatus.USED_UP -> "Used up" to MaterialTheme.colorScheme.error
        DiscountStatus.INACTIVE -> "Inactive" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.12f),
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
        )
    }
}

private enum class DiscountStatus {
    ACTIVE, EXPIRED, USED_UP, INACTIVE
}

private fun deriveStatus(discount: DiscountCode): DiscountStatus {
    if (!discount.isActive) return DiscountStatus.INACTIVE

    // Check expiry
    if (!discount.expiresAt.isNullOrEmpty()) {
        try {
            val expiryInstant = Instant.parse(discount.expiresAt)
            if (expiryInstant.isBefore(Instant.now())) return DiscountStatus.EXPIRED
        } catch (_: Exception) {
            // If parsing fails, treat as not expired
        }
    }

    // Check usage limit
    val limit = discount.usageLimit
    if (limit != null && discount.usageCount >= limit) {
        return DiscountStatus.USED_UP
    }

    return DiscountStatus.ACTIVE
}

private fun formatDiscountDescription(discount: DiscountCode): String {
    return when (discount.type) {
        DiscountType.PERCENTAGE -> "${discount.value.toInt()}% off"
        DiscountType.FIXED_AMOUNT -> "$${String.format("%.2f", discount.value)} off"
        DiscountType.FREE_SHIPPING -> "Free Shipping"
    }
}

private fun formatUsage(discount: DiscountCode): String {
    val limit = discount.usageLimit?.toString() ?: "unlimited"
    return "${discount.usageCount}/$limit used"
}
