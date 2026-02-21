package com.wenubey.wenucommerce.customer.checkout.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun CheckoutProgressDots(
    currentStep: Int,
    totalSteps: Int = 3,
    onStepClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(totalSteps) { index ->
            val isCurrentStep = index == currentStep
            val isPastStep = index < currentStep

            val dotSize by animateDpAsState(
                targetValue = when {
                    isCurrentStep -> 16.dp
                    isPastStep -> 12.dp
                    else -> 10.dp
                },
                animationSpec = tween(durationMillis = 200),
                label = "dot_size_$index",
            )

            val isFilled = isCurrentStep || isPastStep
            val isClickable = index < currentStep // Only allow clicking on past steps

            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .then(
                        if (isFilled) {
                            Modifier.background(MaterialTheme.colorScheme.primary)
                        } else {
                            Modifier
                                .background(MaterialTheme.colorScheme.surface)
                                .border(
                                    width = 1.5.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                    shape = CircleShape,
                                )
                        }
                    )
                    .then(
                        if (isClickable) {
                            Modifier.clickable { onStepClick(index) }
                        } else {
                            Modifier
                        }
                    ),
            )
        }
    }
}
