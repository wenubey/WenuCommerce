package com.wenubey.wenucommerce.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Verified-purchase badge (UI-SPEC C-03), modelled on [OrderStatusBadge]'s
 * BadgeChip. Rendered only when a review's `isVerifiedPurchase` is true
 * (REVW-05). The visible text doubles as the accessibility label.
 */
@Composable
fun VerifiedPurchaseBadge(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Text(
        text = "Verified Purchase",
        style = MaterialTheme.typography.labelMedium,
        color = cs.onPrimaryContainer,
        modifier = modifier
            .background(color = cs.primaryContainer, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
