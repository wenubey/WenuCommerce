package com.wenubey.wenucommerce.customer.customer_reviews

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Interactive 1-5 star selector for the write/edit review form (UI-SPEC C-02).
 *
 * Each star is a 44dp touch target (accessibility) with a 32dp visual icon.
 * Filled stars (index < [selectedRating]) use `colorScheme.primary`; unselected
 * stars use `colorScheme.onSurfaceVariant`. Tapping star N emits
 * `onRatingSelected(N + 1)` (1-indexed). No animation.
 */
@Composable
fun StarRatingSelector(
    selectedRating: Int,
    onRatingSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    Row(modifier = modifier) {
        repeat(5) { index ->
            val starNumber = index + 1
            val isSelected = index < selectedRating
            IconButton(
                onClick = { onRatingSelected(starNumber) },
                enabled = enabled,
                modifier = Modifier
                    .size(44.dp)
                    .semantics { contentDescription = "Rate $starNumber out of 5 stars" },
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = if (isSelected) cs.primary else cs.onSurfaceVariant,
                )
            }
        }
    }
}
