package com.wenubey.wenucommerce.admin.admin_seller_approval.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wenubey.domain.model.onboard.BusinessInfo
import com.wenubey.domain.model.onboard.VerificationStatus
import com.wenubey.domain.model.user.User
import com.wenubey.wenucommerce.core.formatDate
import com.wenubey.wenucommerce.core.generateDummyUser

//TODO move to Colors Constant on this file
@Composable
fun SellerApplicationCard(
    seller: User,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Business,
                        //TODO move to constant string
                        contentDescription = "Business",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(8.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = seller.businessInfo?.businessName ?: "Unknown Business",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${seller.name} ${seller.surname}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                VerificationStatusBadge(
                    status = seller.businessInfo?.verificationStatus
                        ?: VerificationStatus.PENDING
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ContactInfoItem(
                    icon = Icons.Default.Email,
                    label = "Email",
                    value = seller.businessInfo?.businessEmail ?: seller.email,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                ContactInfoItem(
                    icon = Icons.Default.Phone,
                    label = "Phone",
                    value = seller.businessInfo?.businessPhone ?: seller.phoneNumber,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Type: ${seller.businessInfo?.businessType?.name ?: "N/A"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = "Applied: ${formatDate(seller.businessInfo?.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            seller.businessInfo?.let { businessInfo ->
                if (businessInfo.taxDocumentUri.isNotEmpty() ||
                    businessInfo.businessLicenseDocumentUri.isNotEmpty() ||
                    businessInfo.identityDocumentUri.isNotEmpty()
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    DocumentIndicators(businessInfo)
                }
            }
        }
    }
}

@Composable
private fun DocumentIndicators(businessInfo: BusinessInfo) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (businessInfo.taxDocumentUri.isNotEmpty()) {
            DocumentChip(label = "Tax Doc")
        }
        if (businessInfo.businessLicenseDocumentUri.isNotEmpty()) {
            DocumentChip(label = "License")
        }
        if (businessInfo.identityDocumentUri.isNotEmpty()) {
            DocumentChip(label = "ID")
        }
    }
}

@Composable
private fun DocumentChip(label: String) {
    Row(
        modifier = Modifier
            .background(
                color = Color(0xFF4CAF50).copy(alpha = 0.1f),
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Document Uploaded",
            modifier = Modifier.size(12.dp),
            tint = Color(0xFF4CAF50)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF4CAF50)
        )
    }
}

@Composable
fun VerificationStatusBadge(
    status: VerificationStatus,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor, statusText) = when (status) {
        VerificationStatus.PENDING -> Triple(
            Color(0xFFFF9800).copy(alpha = 0.1f),
            Color(0xFFFF9800),
            "PENDING"
        )

        VerificationStatus.APPROVED -> Triple(
            Color(0xFF4CAF50).copy(alpha = 0.1f),
            Color(0xFF4CAF50),
            "APPROVED"
        )

        VerificationStatus.REJECTED -> Triple(
            Color(0xFFE53E3E).copy(alpha = 0.1f),
            Color(0xFFE53E3E),
            "REJECTED"
        )

        VerificationStatus.REQUEST_MORE_INFO -> Triple(
            Color(0xFF2196F3).copy(alpha = 0.1f),
            Color(0xFF2196F3),
            "MORE INFO"
        )

        VerificationStatus.RESUBMITTED -> Triple(
            Color(0xFF9C27B0).copy(alpha = 0.1f),
            Color(0xFF9C27B0),
            "RESUBMITTED"
        )

        VerificationStatus.CANCELLED -> Triple(
            Color(0xFF757575).copy(alpha = 0.1f),
            Color(0xFF757575),
            "CANCELLED"
        )
    }

    Text(
        text = statusText,
        modifier = modifier
            .background(
                color = backgroundColor,
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 12.dp, 6.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = textColor
    )
}


@Composable
private fun ContactInfoItem(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}


@Preview
@Composable
private fun SellerApplicationCardPreview() {
        SellerApplicationCard(
            seller = generateDummyUser(),
            onClick = {}
        )
}


