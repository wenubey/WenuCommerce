package com.wenubey.wenucommerce.onboard.components

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.ContactMail
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wenubey.wenucommerce.onboard.OnboardingAction
import com.wenubey.wenucommerce.onboard.OnboardingState

@Composable
fun SellerFieldsSection(
    state: OnboardingState,
    onAction: (OnboardingAction) -> Unit,
    onDocumentPicker: (DocumentType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Business Details
        SectionCard(
            icon = Icons.Default.Business,
            title = "Business Details"
        ) {
            OutlinedTextField(
                value = state.businessName,
                onValueChange = { onAction(OnboardingAction.OnBusinessNameChange(it)) },
                label = { Text("Business Name *") },
                isError = state.businessNameError,
                modifier = Modifier.fillMaxWidth()
            )

            BusinessTypeDropdownMenu(
                selectedBusinessType = state.businessType,
                onBusinessTypeSelected = {
                    onAction(OnboardingAction.OnBusinessTypeChange(it))
                },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.businessDescription,
                onValueChange = { onAction(OnboardingAction.OnBusinessDescriptionChange(it)) },
                label = { Text("Business Description") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.businessAddress,
                onValueChange = { onAction(OnboardingAction.OnBusinessAddressChange(it)) },
                label = { Text("Business Address *") },
                isError = state.businessAddressError,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Contact Information
        SectionCard(
            icon = Icons.Default.ContactMail,
            title = "Contact Information"
        ) {
            OutlinedTextField(
                value = state.businessPhone,
                onValueChange = { onAction(OnboardingAction.OnBusinessPhoneChange(it)) },
                label = { Text("Business Phone *") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Phone, contentDescription = null)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError = state.businessPhoneError,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.businessEmail,
                onValueChange = { onAction(OnboardingAction.OnBusinessEmailChange(it)) },
                label = { Text("Business Email *") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Email, contentDescription = null)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                isError = state.businessEmailError,
                enabled = !state.useRegistrationEmail,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = state.useRegistrationEmail,
                    onCheckedChange = {
                        onAction(OnboardingAction.OnUseRegistrationEmailToggle(it))
                    }
                )
                Text(
                    text = "Same as registration email",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Tax & Legal
        SectionCard(
            icon = Icons.Default.Gavel,
            title = "Tax & Legal"
        ) {
            OutlinedTextField(
                value = state.taxId,
                onValueChange = { onAction(OnboardingAction.OnTaxIdChange(it)) },
                label = { Text("Tax ID / EIN *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = state.taxIdError,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.businessLicense,
                onValueChange = { onAction(OnboardingAction.OnBusinessLicenseChange(it)) },
                label = { Text("Business License Number") },
                isError = state.businessLicenseError,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Banking
        SectionCard(
            icon = Icons.Default.AccountBalance,
            title = "Banking Information"
        ) {
            OutlinedTextField(
                value = state.bankAccountNumber,
                onValueChange = { onAction(OnboardingAction.OnBankAccountNumberChange(it)) },
                label = { Text("Bank Account Number *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = state.bankAccountNumberError,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.routingNumber,
                onValueChange = { onAction(OnboardingAction.OnRoutingNumberChange(it)) },
                label = { Text("Routing Number *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = state.routingNumberError,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Documents
        SectionCard(
            icon = Icons.Default.Description,
            title = "Required Documents"
        ) {
            DocumentUploadRow(
                label = "Tax Document *",
                hasDocument = state.taxDocumentUri != Uri.EMPTY,
                onUpload = { onDocumentPicker(DocumentType.TAX_DOCUMENT) }
            )

            HorizontalDivider()

            DocumentUploadRow(
                label = "Identity Document *",
                hasDocument = state.identityDocumentUri != Uri.EMPTY,
                onUpload = { onDocumentPicker(DocumentType.IDENTITY_DOCUMENT) }
            )

            HorizontalDivider()

            DocumentUploadRow(
                label = "Business License",
                hasDocument = state.businessLicenseDocumentUri != Uri.EMPTY,
                onUpload = { onDocumentPicker(DocumentType.BUSINESS_LICENSE_DOCUMENT) }
            )
        }
    }
}

@Composable
private fun SectionCard(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))

            content()
        }
    }
}

@Composable
private fun DocumentUploadRow(
    label: String,
    hasDocument: Boolean,
    onUpload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasDocument) {
                Text(
                    text = "Uploaded",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            IconButton(onClick = onUpload) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "Upload $label",
                    tint = if (hasDocument) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// Document types for seller verification
enum class DocumentType {
    TAX_DOCUMENT,
    BUSINESS_LICENSE_DOCUMENT,
    IDENTITY_DOCUMENT
}
