package com.wenubey.wenucommerce.seller.seller_discounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wenubey.domain.model.discount.DiscountType
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellerDiscountCreateEditScreen(
    code: String?,
    isSeller: Boolean,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscountCreateEditViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) {
            onNavigateBack()
        }
    }

    LaunchedEffect(state.saveError) {
        state.saveError?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.onAction(DiscountCreateEditAction.DismissError)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(if (state.isEditMode) "Edit Discount" else "Create Discount")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        if (state.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Coupon Code
                CouponCodeField(
                    code = state.code,
                    isEditMode = state.isEditMode,
                    onCodeChange = { viewModel.onAction(DiscountCreateEditAction.UpdateCode(it)) },
                    onGenerate = { viewModel.onAction(DiscountCreateEditAction.GenerateCode) },
                )

                // Discount Type
                DiscountTypeSelector(
                    selectedType = state.type,
                    onTypeSelected = { viewModel.onAction(DiscountCreateEditAction.UpdateType(it)) },
                )

                // Value (hidden for FREE_SHIPPING)
                if (state.type != DiscountType.FREE_SHIPPING) {
                    OutlinedTextField(
                        value = state.value,
                        onValueChange = { viewModel.onAction(DiscountCreateEditAction.UpdateValue(it)) },
                        label = {
                            Text(
                                when (state.type) {
                                    DiscountType.PERCENTAGE -> "Percentage (0-100)"
                                    DiscountType.FIXED_AMOUNT -> "Amount ($)"
                                    else -> ""
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                }

                // Max Discount Cap (only for PERCENTAGE)
                if (state.type == DiscountType.PERCENTAGE) {
                    OutlinedTextField(
                        value = state.maxDiscountCap,
                        onValueChange = { viewModel.onAction(DiscountCreateEditAction.UpdateMaxCap(it)) },
                        label = { Text("Maximum discount amount ($)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                }

                // Minimum Order Amount
                OutlinedTextField(
                    value = state.minimumOrderAmount,
                    onValueChange = { viewModel.onAction(DiscountCreateEditAction.UpdateMinOrder(it)) },
                    label = { Text("Minimum order ($), optional") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )

                // Usage Limit
                OutlinedTextField(
                    value = state.usageLimit,
                    onValueChange = { viewModel.onAction(DiscountCreateEditAction.UpdateUsageLimit(it)) },
                    label = { Text("Usage limit (empty = unlimited)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )

                // Expiry Date
                ExpiryDateSection(
                    expiresAt = state.expiresAt,
                    onDateSelected = { viewModel.onAction(DiscountCreateEditAction.UpdateExpiryDate(it)) },
                )

                // Product Picker
                ProductPickerSection(
                    products = state.availableProducts,
                    searchQuery = state.productSearchQuery,
                    onSearchChange = { viewModel.onAction(DiscountCreateEditAction.UpdateProductSearch(it)) },
                    onToggleProduct = { viewModel.onAction(DiscountCreateEditAction.ToggleProduct(it)) },
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Save Button
                Button(
                    onClick = { viewModel.onAction(DiscountCreateEditAction.Save) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isSaving,
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(if (state.isEditMode) "Update Discount" else "Create Discount")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun CouponCodeField(
    code: String,
    isEditMode: Boolean,
    onCodeChange: (String) -> Unit,
    onGenerate: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            label = { Text("Coupon Code") },
            modifier = Modifier.weight(1f),
            enabled = !isEditMode,
            singleLine = true,
        )
        TextButton(
            onClick = onGenerate,
            enabled = !isEditMode,
        ) {
            Text("Generate")
        }
    }
}

@Composable
private fun DiscountTypeSelector(
    selectedType: DiscountType,
    onTypeSelected: (DiscountType) -> Unit,
) {
    Column {
        Text(
            text = "Discount Type",
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DiscountType.entries.forEach { type ->
                FilterChip(
                    selected = type == selectedType,
                    onClick = { onTypeSelected(type) },
                    label = {
                        Text(
                            when (type) {
                                DiscountType.PERCENTAGE -> "Percentage"
                                DiscountType.FIXED_AMOUNT -> "Fixed Amount"
                                DiscountType.FREE_SHIPPING -> "Free Shipping"
                            }
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpiryDateSection(
    expiresAt: Long?,
    onDateSelected: (Long?) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    Column {
        Text(
            text = "Expiry Date",
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (expiresAt != null) {
                    Instant.ofEpochMilli(expiresAt)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                } else {
                    "No expiry"
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showDatePicker = true }) {
                Text("Set Expiry")
            }
            if (expiresAt != null) {
                TextButton(onClick = { onDateSelected(null) }) {
                    Text("Clear")
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = expiresAt)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDateSelected(datePickerState.selectedDateMillis)
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun ProductPickerSection(
    products: List<ProductPickerItem>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onToggleProduct: (String) -> Unit,
) {
    Column {
        Text(
            text = "Apply to specific products (optional)",
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            label = { Text("Search products") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(8.dp))

        val filteredProducts = if (searchQuery.isBlank()) {
            products
        } else {
            products.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }

        if (filteredProducts.isEmpty()) {
            Text(
                text = if (products.isEmpty()) "No products available" else "No matching products",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(filteredProducts, key = { it.productId }) { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = item.isSelected,
                            onCheckedChange = { onToggleProduct(item.productId) },
                        )
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
