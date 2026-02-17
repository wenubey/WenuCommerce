package com.wenubey.wenucommerce.seller.seller_verification

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wenubey.domain.model.onboard.BusinessInfo
import com.wenubey.domain.model.onboard.VerificationStatus
import com.wenubey.domain.model.user.User
import com.wenubey.wenucommerce.core.formatDate
import com.wenubey.wenucommerce.seller.seller_verification.components.CancelApplicationDialog
import com.wenubey.wenucommerce.seller.seller_verification.components.EditBusinessInfoDialog
import org.koin.androidx.compose.koinViewModel


// TODO change these clicks into viewmodel and action in the MVI pattern
@Composable
fun SellerVerificationStatusScreen(
    modifier: Modifier = Modifier,
    onViewDashboardClick: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: SellerVerificationViewModel = koinViewModel()
) {

    val state by viewModel.sellerVerificationState.collectAsStateWithLifecycle()

    val user = state.user

    val status = user?.businessInfo?.verificationStatus
    val notes = user?.businessInfo?.verificationNotes

    if (state.showEditDialog) {
        EditBusinessInfoDialog(
            state = state,
            onAction = viewModel::onAction,
            onDismiss = { viewModel.onAction(SellerVerificationAction.DismissDialog) }
        )
    }

    if (state.showCancelDialog) {
        CancelApplicationDialog(
            isSubmitting = state.isSubmitting,
            onConfirm = { viewModel.onAction(SellerVerificationAction.ConfirmCancelApplication)},
            onDismiss = { viewModel.onAction(SellerVerificationAction.DismissDialog)}
        )
    }

    Scaffold(modifier = modifier) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate Back"
                        )
                    }
                    Text(
                        text = "Verification Status",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            status?.let {
                item {
                    StatusCard(status = status)
                }
            }

            user?.let {
                item {
                    ApplicationDetailsCard(user = user)
                }

                item {
                    user.businessInfo?.let { businessInfo ->
                        DocumentsStatusCard(businessInfo = businessInfo)
                    }
                }

                user.businessInfo?.let { businessInfo ->
                    notes?.let { notes ->
                        if (notes.isNotEmpty()) {
                            item {
                                AdminNotesCard(
                                    notes = notes,
                                    status = businessInfo.verificationStatus
                                )
                            }
                        }
                    }
                }
            }


            status?.let {
                item {
                    ActionButtons(
                        status = status,
                        onUpdateInfoClick = { viewModel.onAction(SellerVerificationAction.ShowEditDialog) },
                        onResubmitClick = { viewModel.onAction(SellerVerificationAction.ShowEditDialog) },
                        onViewDashboardClick = onViewDashboardClick,
                        onCancelClick = { viewModel.onAction(SellerVerificationAction.ShowCancelDialog) }
                    )
                }
            }
        }

    }
}

@Composable
private fun ActionButtons(
    status: VerificationStatus,
    onUpdateInfoClick: () -> Unit,
    onResubmitClick: () -> Unit,
    onViewDashboardClick: () -> Unit,
    onCancelClick: () -> Unit,
) {
    when (status) {
        VerificationStatus.REQUEST_MORE_INFO -> {
            Button(
                onClick = onUpdateInfoClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2196F3)
                )
            ) {
                Text("Update Information")
            }
        }

        VerificationStatus.REJECTED -> {
            Button(
                onClick = onResubmitClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF9800)
                )
            ) {
                Text("Resubmit Application")
            }
        }

        VerificationStatus.APPROVED -> {
            Button(
                onClick = onViewDashboardClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Text("Go to Dashboard")
            }
        }

        VerificationStatus.PENDING -> {
            OutlinedButton(
                onClick = onCancelClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel Application")
            }
        }

        VerificationStatus.RESUBMITTED -> {
            OutlinedButton(
                onClick = onCancelClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel Application")
            }
        }

        VerificationStatus.CANCELLED -> {
            Button(
                onClick = onResubmitClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF757575)
                )
            ) {
                Text("Reapply")
            }
        }
    }
}


@Composable
private fun AdminNotesCard(notes: String, status: VerificationStatus) {
    val noteColor = when (status) {
        VerificationStatus.APPROVED -> Color(0xFF4CAF50)
        VerificationStatus.REJECTED -> Color(0xFFE53E3E)
        VerificationStatus.REQUEST_MORE_INFO -> Color(0xFF2196F3)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = noteColor.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Admin Notes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = noteColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = notes,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@Composable
private fun DocumentsStatusCard(businessInfo: BusinessInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Submitted Documents",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            DocumentStatusItem(
                label = "Tax Document",
                isUploaded = businessInfo.taxDocumentUri.isNotEmpty()
            )
            DocumentStatusItem(
                label = "Business License",
                isUploaded = businessInfo.businessLicenseDocumentUri.isNotEmpty()
            )
            DocumentStatusItem(
                label = "Identity Document",
                isUploaded = businessInfo.identityDocumentUri.isNotEmpty()
            )
        }
    }
}

@Composable
private fun DocumentStatusItem(label: String, isUploaded: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isUploaded) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = if (isUploaded) "Uploaded" else "Not uploaded",
                modifier = Modifier.size(16.dp),
                tint = if (isUploaded) Color(0xFF4CAF50) else Color(0xFFE53E3E)
            )
            Text(
                text = if (isUploaded) "Uploaded" else "Not uploaded",
                style = MaterialTheme.typography.bodySmall,
                color = if (isUploaded) Color(0xFF4CAF50) else Color(0xFFE53E3E)
            )
        }
    }
}


@Composable
private fun ApplicationDetailsCard(user: User) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Application Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            DetailRow(
                label = "Business Name",
                value = user.businessInfo?.businessName ?: "N/A"
            )
            DetailRow(
                label = "Business Type",
                value = user.businessInfo?.businessType?.name ?: "N/A"
            )
            DetailRow(
                label = "Submitted On",
                value = formatDate(user.businessInfo?.createdAt)
            )
            DetailRow(
                label = "Last Updated",
                value = formatDate(user.businessInfo?.updatedAt)
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        HorizontalDivider()
    }
}


@Composable
private fun StatusCard(status: VerificationStatus) {
    val (icon, iconColor, bgColor, statusTitle, statusMessage) = when (status) {
        VerificationStatus.PENDING -> StatusInfo(
            icon = Icons.Default.Pending,
            iconColor = Color(0xFFFF9800),
            bgColor = Color(0xFFFF9800).copy(alpha = 0.1f),
            statusTitle = "Application Pending",
            statusMessage = "Your application is being reviewed by our team. We'll notify you once a decision has been made."
        )

        VerificationStatus.APPROVED -> StatusInfo(
            icon = Icons.Default.CheckCircle,
            iconColor = Color(0xFF4CAF50),
            bgColor = Color(0xFF4CAF50).copy(alpha = 0.1f),
            statusTitle = "Application Approved!",
            statusMessage = "Congratulations! Your seller account has been approved. You can now start listing products and making sales."
        )

        VerificationStatus.REJECTED -> StatusInfo(
            icon = Icons.Default.Cancel,
            iconColor = Color(0xFFE53E3E),
            bgColor = Color(0xFFE53E3E).copy(alpha = 0.1f),
            statusTitle = "Application Rejected",
            statusMessage = "Unfortunately, your application has been rejected. Please review the notes below and consider resubmitting with the required changes."
        )

        VerificationStatus.REQUEST_MORE_INFO -> StatusInfo(
            icon = Icons.Default.Info,
            iconColor = Color(0xFF2196F3),
            bgColor = Color(0xFF2196F3).copy(alpha = 0.1f),
            statusTitle = "Additional Information Required",
            statusMessage = "We need some additional information to process your application. Please review the details below and update your information."
        )

        VerificationStatus.RESUBMITTED -> StatusInfo(
            icon = Icons.Default.Autorenew,
            iconColor = Color(0xFF9C27B0),
            bgColor = Color(0xFF9C27B0).copy(alpha = 0.1f),
            statusTitle = "Application Resubmitted",
            statusMessage = "Your updated application is under review. We'll notify you once a decision has been made."
        )

        VerificationStatus.CANCELLED -> StatusInfo(
            icon = Icons.Default.Cancel,
            iconColor = Color(0xFF757575),
            bgColor = Color(0xFF757575).copy(alpha = 0.1f),
            statusTitle = "Application Cancelled",
            statusMessage = "You cancelled your seller application. You can reapply when ready."
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = bgColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(iconColor.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = statusTitle,
                    modifier = Modifier.size(48.dp),
                    tint = iconColor
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = statusTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = iconColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class StatusInfo(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val iconColor: Color,
    val bgColor: Color,
    val statusTitle: String,
    val statusMessage: String
)

@Preview
@Composable
private fun SellerVerificationStatusScreenPreview() {
    SellerVerificationStatusScreen(
        onViewDashboardClick = {},
        onNavigateBack = {},
    )
}


