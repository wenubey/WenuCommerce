package com.wenubey.wenucommerce.seller.orders

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import java.util.Locale

/**
 * Seller cancel confirmation. Renders a refund preview computed from
 * `subtotal + shippingShare - discountShare` (per CONTEXT D6 + 06-01 callable
 * refund math). Tap Confirm cancel -> calls into `cancelSellerOrder` callable
 * (atomic Stripe partial refund + status flip).
 */
@Composable
fun CancelOrderDialog(
    refundPreviewAmount: Double,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val refundLabel = String.format(Locale.US, "$%.2f", refundPreviewAmount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cancel order?") },
        text = {
            Text(
                "Cancelling will issue a refund of $refundLabel to the customer. " +
                    "This cannot be undone.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    "Confirm cancel",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep order") }
        },
    )
}
