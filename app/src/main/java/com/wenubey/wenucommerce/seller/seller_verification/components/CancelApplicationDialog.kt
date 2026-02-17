package com.wenubey.wenucommerce.seller.seller_verification.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun CancelApplicationDialog(
    isSubmitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {

    AlertDialog(
        modifier = modifier,
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Text(
                text = "Cancel Application",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text("Are you sure you want to cancel your seller application? This action cannot be undone.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53E3E))
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Yes, Cancel")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSubmitting
            ) {
                Text("No, Keep It")
            }
        }
    )
}

@Preview
@Composable
private fun CancelApplicationDialogPreview() {
    CancelApplicationDialog(
        isSubmitting = false,
        onConfirm = {},
        onDismiss = {}
    )
}