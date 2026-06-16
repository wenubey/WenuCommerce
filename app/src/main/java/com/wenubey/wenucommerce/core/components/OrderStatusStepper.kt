package com.wenubey.wenucommerce.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wenubey.domain.model.order.OrderStatus
import com.wenubey.domain.model.order.StatusEntry

private enum class StepState { Completed, Current, Upcoming }

/**
 * Vertical 4-step stepper rendering PENDING → CONFIRMED → SHIPPED → DELIVERED.
 *
 * When [currentStatus] is CANCELLED, the stepper renders steps up to (and
 * including) the last entry in [history] with a Completed visual, then appends a
 * single "Cancelled on …" marker row in error colour. Upcoming-after-cancel
 * steps are NOT rendered.
 *
 * When [currentStatus] is SHIPPED and [trackingNumber] is non-null, a
 * [TrackingNumberChip] is rendered under the SHIPPED label.
 */
@Composable
fun OrderStatusStepper(
    history: List<StatusEntry>,
    currentStatus: OrderStatus,
    trackingNumber: String?,
    modifier: Modifier = Modifier,
) {
    val steps = listOf(
        OrderStatus.PENDING,
        OrderStatus.CONFIRMED,
        OrderStatus.SHIPPED,
        OrderStatus.DELIVERED,
    )
    val historyByStatus: Map<OrderStatus, StatusEntry> = history.associateBy { it.status }
    val isCancelled = currentStatus == OrderStatus.CANCELLED

    Column(modifier = modifier.fillMaxWidth()) {
        if (isCancelled) {
            // Show only steps that exist in history as Completed, then a cancellation marker.
            val completedSteps = steps.filter { historyByStatus.containsKey(it) }
            completedSteps.forEachIndexed { index, step ->
                StepperStep(
                    label = step.displayName,
                    timestamp = historyByStatus[step]?.timestamp,
                    state = StepState.Completed,
                    showConnector = true, // connector down to cancellation marker
                    trackingNumber = if (step == OrderStatus.SHIPPED) trackingNumber else null,
                )
            }
            val cancelEntry = historyByStatus[OrderStatus.CANCELLED]
            CancelledMarker(timestamp = cancelEntry?.timestamp)
        } else {
            val currentIndex = steps.indexOf(currentStatus).coerceAtLeast(0)
            steps.forEachIndexed { index, step ->
                val state = when {
                    index < currentIndex -> StepState.Completed
                    index == currentIndex -> StepState.Current
                    else -> StepState.Upcoming
                }
                StepperStep(
                    label = step.displayName,
                    timestamp = historyByStatus[step]?.timestamp,
                    state = state,
                    showConnector = index < steps.lastIndex,
                    trackingNumber = if (step == OrderStatus.SHIPPED && state != StepState.Upcoming) trackingNumber else null,
                )
            }
        }
    }
}

@Composable
private fun StepperStep(
    label: String,
    timestamp: String?,
    state: StepState,
    showConnector: Boolean,
    trackingNumber: String?,
) {
    val cs = MaterialTheme.colorScheme
    val (icon, tint) = when (state) {
        StepState.Completed -> Icons.Filled.CheckCircle to cs.primary
        StepState.Current -> Icons.Filled.RadioButtonChecked to cs.primary
        StepState.Upcoming -> Icons.Outlined.RadioButtonUnchecked to cs.onSurfaceVariant
    }
    val labelColor = if (state == StepState.Upcoming) cs.onSurfaceVariant else cs.onSurface

    Row(modifier = Modifier.fillMaxWidth()) {
        // Left rail: icon + connector
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(28.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "$label step $state",
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
            if (showConnector) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(36.dp)
                        .background(
                            if (state == StepState.Completed) cs.primary
                            else cs.outlineVariant
                        ),
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = labelColor,
            )
            if (!timestamp.isNullOrBlank()) {
                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
            if (trackingNumber != null) {
                TrackingNumberChip(trackingNumber = trackingNumber)
            }
        }
    }
}

@Composable
private fun CancelledMarker(timestamp: String?) {
    val cs = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(28.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Cancel,
                contentDescription = "Cancelled step",
                tint = cs.error,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = if (timestamp.isNullOrBlank()) "Cancelled" else "Cancelled on $timestamp",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.error,
            )
        }
    }
}
