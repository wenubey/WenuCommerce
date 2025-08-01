package com.wenubey.wenucommerce.onboard.components

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Seller Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Business Information Section
            Text(
                text = "Business Details",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = state.businessName,
                onValueChange = { onAction(OnboardingAction.OnBusinessNameChange(it)) },
                label = { Text("Business Name *") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Business,
                        contentDescription = null
                    )
                },
                isError = state.businessNameError,
                modifier = Modifier.fillMaxWidth()
            )

            BusinessTypeDropdownMenu(
                selectedBusinessType = state.businessType,
                onBusinessTypeSelected = {
                    onAction(OnboardingAction.OnBusinessTypeChange(it))
                }
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

            // Contact Information
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Business Contact",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = state.businessPhone,
                onValueChange = { onAction(OnboardingAction.OnBusinessPhoneChange(it)) },
                label = { Text("Business Phone *") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null
                    )
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
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                isError = state.businessEmailError,
                modifier = Modifier.fillMaxWidth()
            )

            // Tax and Legal Information
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tax & Legal Information",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

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

            // Banking Information
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Banking Information",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

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

            // Document Upload Section
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Required Documents",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

            DocumentUploadRow(
                label = "Tax Document *",
                hasDocument = state.taxDocumentUri != Uri.EMPTY,
                onUpload = { onDocumentPicker(DocumentType.TAX_DOCUMENT) }
            )

            DocumentUploadRow(
                label = "Identity Document *",
                hasDocument = state.identityDocumentUri != Uri.EMPTY,
                onUpload = { onDocumentPicker(DocumentType.IDENTITY_DOCUMENT) }
            )

            if (state.businessLicense.isNotBlank()) {
                DocumentUploadRow(
                    label = "Business License",
                    hasDocument = state.businessLicenseDocumentUri != Uri.EMPTY,
                    onUpload = { onDocumentPicker(DocumentType.BUSINESS_LICENSE_DOCUMENT) }
                )
            }
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
                    text = "✓ Uploaded",
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