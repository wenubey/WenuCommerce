package com.wenubey.wenucommerce.core.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Skeleton placeholder mimicking a category chip.
 * Per locked decision: "shimmer/skeleton placeholders mimicking content layout (category lists)".
 */
@Composable
fun ShimmerCategoryChip(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(80.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .shimmerEffect(),
    )
}

/**
 * A horizontal row of shimmer category chip placeholders.
 * Per locked decision: "shimmer/skeleton placeholders mimicking content layout (category lists)".
 */
@Composable
fun ShimmerCategoryRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(5) {
            ShimmerCategoryChip()
        }
    }
}
