package com.wenubey.wenucommerce.core.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Reusable heart button for toggling wishlist state.
 *
 * - Shows filled red heart when [isWishlisted], outlined heart when not.
 * - Scale bounce animation fires on every [isWishlisted] change (Research Pattern 7).
 * - Per decision: "No snackbar when toggling wishlist from browse/detail — heart animation is the only feedback"
 */
@Composable
fun WishlistHeartButton(
    isWishlisted: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = remember { Animatable(1f) }

    LaunchedEffect(isWishlisted) {
        scale.animateTo(
            targetValue = 1.3f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        )
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        )
    }

    IconButton(
        onClick = onToggle,
        modifier = modifier,
    ) {
        Icon(
            imageVector = if (isWishlisted) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (isWishlisted) "Remove from wishlist" else "Add to wishlist",
            tint = if (isWishlisted) Color.Red else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
        )
    }
}
