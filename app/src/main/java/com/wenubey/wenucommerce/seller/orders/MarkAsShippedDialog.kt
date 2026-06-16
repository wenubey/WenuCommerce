package com.wenubey.wenucommerce.seller.orders

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Optional-tracking-number dialog shown when seller advances a sub-order to SHIPPED.
 *
 * Per CONTEXT D5: tracking is free-text v1, no carrier picker, no format validation.
 * "Skip" maps to `onApply(null)` upstream so the caller sees a single confirmation
 * pathway. "Apply" emits the trimmed text (or null when blank).
 */
@Composable
fun MarkAsShippedDialog(
    onApply: (trackingNumber: String?) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    var trackingNumber by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mark as shipped") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Add a tracking number to share with the customer, or skip to mark as shipped without one.")
                OutlinedTextField(
                    value = trackingNumber,
                    onValueChange = { trackingNumber = it },
                    label = { Text("Tracking number (optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = trackingNumber.trim()
                    onApply(trimmed.ifBlank { null })
                },
            ) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text("Skip") }
        },
    )
}
