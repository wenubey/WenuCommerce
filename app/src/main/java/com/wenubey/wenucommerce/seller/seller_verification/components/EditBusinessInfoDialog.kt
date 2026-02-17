package com.wenubey.wenucommerce.seller.seller_verification.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wenubey.wenucommerce.onboard.components.BusinessTypeDropdownMenu
import com.wenubey.wenucommerce.seller.seller_verification.SellerVerificationAction
import com.wenubey.wenucommerce.seller.seller_verification.SellerVerificationState

@Composable
fun EditBusinessInfoDialog(
    state: SellerVerificationState,
    onAction: (SellerVerificationAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = { if (!state.isSubmitting) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !state.isSubmitting,
            dismissOnClickOutside = !state.isSubmitting,
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Edit Business Information",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        enabled = !state.isSubmitting
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }

                HorizontalDivider()

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SectionHeader("Business Details")

                    OutlinedTextField(
                        value = state.businessName,
                        onValueChange = { onAction(SellerVerificationAction.OnBusinessNameChange(it)) },
                        label = { Text("Business Name *") },
                        leadingIcon = { Icon(imageVector = Icons.Default.Business, null) },
                        isError = state.businessNameError,
                        enabled = !state.isSubmitting,
                        modifier = Modifier.fillMaxWidth()
                    )

                    BusinessTypeDropdownMenu(
                        selectedBusinessType = state.businessType,
                        onBusinessTypeSelected = { onAction(SellerVerificationAction.OnBusinessTypeChange(it))}
                    )

                    OutlinedTextField(
                        value = state.businessDescription,
                        onValueChange = { onAction(SellerVerificationAction.OnBusinessDescriptionChange(it))},
                        label = { Text("Business Description") },
                        maxLines = 3,
                        enabled = !state.isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = state.businessAddress,
                        onValueChange = { onAction(SellerVerificationAction.OnBusinessAddressChange(it))},
                        label = { Text("Business Address *") },
                        isError = state.businessAddressError,
                        enabled = !state.isSubmitting,
                        modifier = Modifier.fillMaxWidth()
                    )

                    SectionHeader("Contact Information")

                    OutlinedTextField(
                        value = state.businessPhone,
                        onValueChange = { onAction(SellerVerificationAction.OnBusinessPhoneChange(it))},
                        label = { Text("Business Phone *") },
                        leadingIcon = { Icon(Icons.Default.Phone, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        isError = state.businessPhoneError,
                        enabled = !state.isSubmitting
                    )

                    OutlinedTextField(
                        value = state.businessEmail,
                        onValueChange = { onAction(SellerVerificationAction.OnBusinessEmailChange(it))},
                        label = { Text("Business Email *") },
                        leadingIcon = { Icon(Icons.Default.Email, null)},
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        isError = state.businessEmailError,
                        enabled = !state.isSubmitting,
                        modifier = Modifier.fillMaxWidth()
                    )

                    SectionHeader("Tax & Legal Information")

                    OutlinedTextField(
                        value = state.taxId,
                        onValueChange = { onAction(SellerVerificationAction.OnTaxIdChange(it)) },
                        label = { Text("Tax ID / EIN *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = state.taxIdError,
                        enabled = !state.isSubmitting,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = state.businessLicense,
                        onValueChange = { onAction(SellerVerificationAction.OnBusinessLicenseChange(it)) },
                        label = { Text("Business License Number") },
                        enabled = !state.isSubmitting,
                        modifier = Modifier.fillMaxWidth()
                    )

                    SectionHeader("Banking Information")

                    OutlinedTextField(
                        value = state.bankAccountNumber,
                        onValueChange = { onAction(SellerVerificationAction.OnBankAccountNumberChange(it)) },
                        label = { Text("Bank Account Number *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = state.bankAccountNumberError,
                        enabled = !state.isSubmitting,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = state.routingNumber,
                        onValueChange = { onAction(SellerVerificationAction.OnRoutingNumberChange(it)) },
                        label = { Text("Routing Number *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = state.routingNumberError,
                        enabled = !state.isSubmitting,
                        modifier = Modifier.fillMaxWidth()
                    )

                    SectionHeader("Documents")

                    DocumentUploadRow(
                        label = "Tax Document",
                        hasExistingDocument = state.existingTaxDocumentUrl.isNotEmpty(),
                        hasNewDocument = state.newTaxDocumentUri != null,
                        enabled = !state.isSubmitting,
                        onDocumentSelected = { onAction(SellerVerificationAction.OnTaxDocumentSelected(it)) }
                    )

                    DocumentUploadRow(
                        label = "Business License",
                        hasExistingDocument = state.existingBusinessLicenseUrl.isNotEmpty(),
                        hasNewDocument = state.newBusinessLicenseUri != null,
                        enabled = !state.isSubmitting,
                        onDocumentSelected = { onAction(SellerVerificationAction.OnBusinessLicenseDocumentSelected(it)) }
                    )

                    DocumentUploadRow(
                        label = "Identity Document",
                        hasExistingDocument = state.existingIdentityDocumentUrl.isNotEmpty(),
                        hasNewDocument = state.newIdentityDocumentUri != null,
                        enabled = !state.isSubmitting,
                        onDocumentSelected = { onAction(SellerVerificationAction.OnIdentityDocumentSelected(it)) }
                    )
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !state.isSubmitting
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = { onAction(SellerVerificationAction.SubmitUpdatedInfo) },
                        enabled = !state.isSubmitting
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Submit")
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun DocumentUploadRow(
    label: String,
    hasExistingDocument: Boolean,
    hasNewDocument: Boolean,
    enabled: Boolean,
    onDocumentSelected: (Uri) -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { onDocumentSelected(it) } }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                when {
                    hasNewDocument -> Text(
                        "New document selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    hasExistingDocument -> Text(
                        "Existing document",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = { launcher.launch("*/*") },
                enabled = enabled
            ) {
                Icon(
                    imageVector = when {
                        hasNewDocument -> Icons.Default.CheckCircle
                        hasExistingDocument -> Icons.Default.Edit
                        else -> Icons.Default.Upload
                    },
                    contentDescription = "Upload $label",
                    tint = if (hasNewDocument) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}



@Preview
@Composable
private fun EditBusinessInfoDialogPreview() {
    EditBusinessInfoDialog(
        state = SellerVerificationState(),
        onAction = {},
        onDismiss = {}
    )
}