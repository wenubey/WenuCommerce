package com.wenubey.wenucommerce.customer.checkout.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wenubey.domain.model.order.ShippingAddress
import com.wenubey.domain.repository.AddressRepository
import com.wenubey.domain.repository.AuthRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressFormScreen(
    onAddressSaved: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    addressRepository: AddressRepository = koinInject(),
    authRepository: AuthRepository = koinInject(),
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var fullName by remember { mutableStateOf("") }
    var line1 by remember { mutableStateOf("") }
    var line2 by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var postalCode by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }

    // Validation error messages
    var fullNameError by remember { mutableStateOf<String?>(null) }
    var line1Error by remember { mutableStateOf<String?>(null) }
    var cityError by remember { mutableStateOf<String?>(null) }
    var stateError by remember { mutableStateOf<String?>(null) }
    var postalCodeError by remember { mutableStateOf<String?>(null) }
    var countryError by remember { mutableStateOf<String?>(null) }

    var isSaving by remember { mutableStateOf(false) }

    fun validate(): Boolean {
        var isValid = true
        fullNameError = if (fullName.isBlank()) { isValid = false; "Full name is required" } else null
        line1Error = if (line1.isBlank()) { isValid = false; "Address line 1 is required" } else null
        cityError = if (city.isBlank()) { isValid = false; "City is required" } else null
        stateError = if (state.isBlank()) { isValid = false; "State / Province is required" } else null
        postalCodeError = if (postalCode.isBlank()) { isValid = false; "Postal code is required" } else null
        countryError = if (country.isBlank()) { isValid = false; "Country is required" } else null
        return isValid
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Address") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            AddressTextField(
                value = fullName,
                onValueChange = { fullName = it; fullNameError = null },
                label = "Full Name",
                errorMessage = fullNameError,
            )

            AddressTextField(
                value = line1,
                onValueChange = { line1 = it; line1Error = null },
                label = "Address Line 1",
                errorMessage = line1Error,
            )

            AddressTextField(
                value = line2,
                onValueChange = { line2 = it },
                label = "Address Line 2 (optional)",
                errorMessage = null,
            )

            AddressTextField(
                value = city,
                onValueChange = { city = it; cityError = null },
                label = "City",
                errorMessage = cityError,
            )

            AddressTextField(
                value = state,
                onValueChange = { state = it; stateError = null },
                label = "State / Province",
                errorMessage = stateError,
            )

            AddressTextField(
                value = postalCode,
                onValueChange = { postalCode = it; postalCodeError = null },
                label = "Postal Code",
                errorMessage = postalCodeError,
            )

            AddressTextField(
                value = country,
                onValueChange = { country = it; countryError = null },
                label = "Country",
                errorMessage = countryError,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (validate()) {
                        val userId = authRepository.currentUser.value?.uuid ?: return@Button
                        isSaving = true
                        scope.launch {
                            val address = ShippingAddress(
                                id = UUID.randomUUID().toString(),
                                fullName = fullName.trim(),
                                line1 = line1.trim(),
                                line2 = line2.trim(),
                                city = city.trim(),
                                state = state.trim(),
                                postalCode = postalCode.trim(),
                                country = country.trim(),
                            )
                            runCatching {
                                addressRepository.saveAddress(userId, address)
                            }
                            isSaving = false
                            onAddressSaved()
                        }
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isSaving) "Saving..." else "Save Address")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AddressTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = errorMessage != null,
        supportingText = if (errorMessage != null) {
            { Text(text = errorMessage) }
        } else null,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
    )
}
