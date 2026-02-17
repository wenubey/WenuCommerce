package com.wenubey.wenucommerce.admin.admin_seller_approval.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wenubey.domain.model.onboard.VerificationStatus
import com.wenubey.domain.model.user.User
import com.wenubey.wenucommerce.admin.admin_seller_approval.DialogType
import com.wenubey.wenucommerce.core.generateDummyUser


@Composable
fun ApprovalDialog(
    seller: User,
    dialogType: DialogType,
    onDismiss: () -> Unit,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onRequestMoreInfo: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var notes by remember { mutableStateOf("") }

    // Determine if seller can be modified based on current status
    val currentStatus = seller.businessInfo?.verificationStatus ?: VerificationStatus.PENDING
    val isReadOnly = currentStatus == VerificationStatus.APPROVED || currentStatus == VerificationStatus.REJECTED || currentStatus == VerificationStatus.CANCELLED

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isReadOnly) "Seller Details" else "Seller Application Approval",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                SellerInfoSection(seller = seller)

                Spacer(modifier = Modifier.height(16.dp))

                // Show current status and notes if exists
                if (isReadOnly) {
                    Text(
                        text = "Current Status",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    VerificationStatusBadge(status = currentStatus)

                    seller.businessInfo?.verificationNotes?.let { existingNotes ->
                        if (existingNotes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Admin Notes",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = existingNotes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Show notes input for pending/under review
                    Text(
                        text = "Notes (Optional)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        placeholder = {
                            Text(text = "Add any notes for the seller...")
                        },
                        maxLines = 5
                    )
                }
            }
        },
        confirmButton = {
            // Show different buttons based on status
            if (isReadOnly) {
                // Read-only: Just show Close button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close")
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (currentStatus) {
                        VerificationStatus.PENDING -> {
                            // Show all 3 actions for pending sellers
                            Button(
                                onClick = { onApprove(notes.takeIf { it.isNotBlank() } ?: "Approved") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50)
                                )
                            ) {
                                Text("Approve")
                            }

                            Button(
                                onClick = { onRequestMoreInfo(notes.takeIf { it.isNotBlank() } ?: "More information needed") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2196F3)
                                )
                            ) {
                                Text("Request More Info")
                            }

                            Button(
                                onClick = { onReject(notes.takeIf { it.isNotBlank() } ?: "Rejected") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFE53E3E)
                                )
                            ) {
                                Text("Reject")
                            }
                        }

                        VerificationStatus.REQUEST_MORE_INFO -> {
                            // Show only Approve/Reject for sellers under review
                            Button(
                                onClick = { onApprove(notes.takeIf { it.isNotBlank() } ?: "Approved") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50)
                                )
                            ) {
                                Text("Approve")
                            }

                            Button(
                                onClick = { onReject(notes.takeIf { it.isNotBlank() } ?: "Rejected") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFE53E3E)
                                )
                            ) {
                                Text("Reject")
                            }
                        }

                        VerificationStatus.RESUBMITTED -> {
                            // Show only Approve/Reject for sellers under review
                            Button(
                                onClick = { onApprove(notes.takeIf { it.isNotBlank() } ?: "Approved") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50)
                                )
                            ) {
                                Text("Approve")
                            }

                            Button(
                                onClick = { onReject(notes.takeIf { it.isNotBlank() } ?: "Rejected") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFE53E3E)
                                )
                            ) {
                                Text("Reject")
                            }
                        }

                        else -> {} // No actions for approved/rejected
                    }
                }
            }
        },
        modifier = modifier
    )
}


@Composable
private fun SellerInfoSection(seller: User) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InfoRow(
            label = "Business Name",
            value = seller.businessInfo?.businessName ?: "N/A"
        )
        InfoRow(
            label = "Owner",
            value = "${seller.name} ${seller.surname}"
        )
        InfoRow(
            label = "Email",
            value = seller.businessInfo?.businessEmail ?: seller.email
        )
        InfoRow(
            label = "Phone",
            value = seller.businessInfo?.businessPhone ?: seller.phoneNumber
        )
        InfoRow(
            label = "Business Type",
            value = seller.businessInfo?.businessType?.name ?: "N/A"
        )
        InfoRow(
            label = "Tax ID",
            value = seller.businessInfo?.taxId?.takeIf { it.isNotEmpty() } ?: "Not Provided"

        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.6f)
        )
    }
}


@Preview
@Composable
private fun ApprovalDialogPreview() {
    ApprovalDialog(
        seller = generateDummyUser(),
        dialogType = DialogType.APPROVE,
        onDismiss = {},
        onApprove = {},
        onReject = {},
        onRequestMoreInfo = {},
    )
}
