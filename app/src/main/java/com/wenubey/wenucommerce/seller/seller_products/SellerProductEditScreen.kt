package com.wenubey.wenucommerce.seller.seller_products

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wenubey.domain.model.product.ProductCondition
import com.wenubey.domain.model.product.ProductVariant
import com.wenubey.domain.model.product.ShippingType
import com.wenubey.domain.model.product.Subcategory
import com.wenubey.wenucommerce.seller.seller_categories.SellerCategoryAction
import com.wenubey.wenucommerce.seller.seller_categories.SellerCategoryViewModel
import com.wenubey.wenucommerce.seller.seller_categories.components.CategoryPickerBottomSheet
import com.wenubey.wenucommerce.seller.seller_categories.components.CreateCategoryDialog
import com.wenubey.wenucommerce.seller.seller_categories.components.CreateSubcategoryDialog
import com.wenubey.wenucommerce.seller.seller_products.components.VariantRow
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SellerProductEditScreen(
    modifier: Modifier = Modifier,
    viewModel: SellerProductEditViewModel = koinViewModel(),
    categoryViewModel: SellerCategoryViewModel = koinViewModel(),
    onNavigateBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categoryState by categoryViewModel.categoryState.collectAsStateWithLifecycle()

    var showCategoryPicker by remember { mutableStateOf(false) }
    var tagInput by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        viewModel.onAction(
            SellerProductEditAction.OnImagesSelected(uris.map { it.toString() })
        )
    }

    LaunchedEffect(state.savedSuccessfully) {
        if (state.savedSuccessfully) onNavigateBack()
    }

    when {
        state.isLoading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        state.product == null -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = state.errorMessage ?: "Product not found",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        else -> {
            val product = state.product!!

            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                item {
                    Text(
                        text = "Edit Product",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (!state.isEditable) {
                        Text(
                            text = "This product is in ${product.status.name} status and cannot be edited.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                // Error message
                state.errorMessage?.let { error ->
                    item {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                // Basic Info Section
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Basic Info",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )

                            OutlinedTextField(
                                value = product.title,
                                onValueChange = {
                                    viewModel.onAction(
                                        SellerProductEditAction.OnProductUpdated(product.copy(title = it))
                                    )
                                },
                                label = { Text("Product Title") },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = state.isEditable,
                                singleLine = true,
                            )

                            OutlinedTextField(
                                value = product.description,
                                onValueChange = {
                                    viewModel.onAction(
                                        SellerProductEditAction.OnProductUpdated(product.copy(description = it))
                                    )
                                },
                                label = { Text("Description") },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = state.isEditable,
                                minLines = 3,
                                maxLines = 6,
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = product.basePrice.toString(),
                                    onValueChange = { price ->
                                        viewModel.onAction(
                                            SellerProductEditAction.OnProductUpdated(
                                                product.copy(basePrice = price.toDoubleOrNull() ?: 0.0)
                                            )
                                        )
                                    },
                                    label = { Text("Price ($)") },
                                    modifier = Modifier.weight(1f),
                                    enabled = state.isEditable,
                                    singleLine = true,
                                )
                                OutlinedTextField(
                                    value = product.compareAtPrice?.toString() ?: "",
                                    onValueChange = { price ->
                                        viewModel.onAction(
                                            SellerProductEditAction.OnProductUpdated(
                                                product.copy(compareAtPrice = price.toDoubleOrNull())
                                            )
                                        )
                                    },
                                    label = { Text("Compare At ($)") },
                                    modifier = Modifier.weight(1f),
                                    enabled = state.isEditable,
                                    singleLine = true,
                                )
                            }

                            // Condition dropdown
                            var conditionExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = conditionExpanded,
                                onExpandedChange = { if (state.isEditable) conditionExpanded = it },
                            ) {
                                OutlinedTextField(
                                    value = product.condition.name.replace("_", " "),
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Condition") },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = conditionExpanded)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                    enabled = state.isEditable,
                                )
                                ExposedDropdownMenu(
                                    expanded = conditionExpanded,
                                    onDismissRequest = { conditionExpanded = false },
                                ) {
                                    ProductCondition.entries.forEach { condition ->
                                        DropdownMenuItem(
                                            text = { Text(condition.name.replace("_", " ")) },
                                            onClick = {
                                                viewModel.onAction(
                                                    SellerProductEditAction.OnProductUpdated(
                                                        product.copy(condition = condition)
                                                    )
                                                )
                                                conditionExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Category Section
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Category",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            if (state.selectedCategory != null) {
                                Text("Category: ${state.selectedCategory?.name}")
                                state.selectedSubcategory?.let {
                                    Text(
                                        "Subcategory: ${it.name}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            } else {
                                Text("Category: ${product.categoryName}")
                                if (product.subcategoryName.isNotBlank()) {
                                    Text(
                                        "Subcategory: ${product.subcategoryName}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            if (state.isEditable) {
                                OutlinedButton(onClick = { showCategoryPicker = true }) {
                                    Text(
                                        if (state.selectedCategory != null) "Change Category"
                                        else "Select Category"
                                    )
                                }
                            }
                        }
                    }
                }

                // Images Section
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val totalImages = product.images.size + state.localImageUris.size
                            Text(
                                "Images ($totalImages/8)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )

                            // Combine existing remote images with newly selected local URIs
                            val allDisplayUris: List<String> =
                                product.images.map { it.downloadUrl } + state.localImageUris

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(allDisplayUris) { index, uri ->
                                    Box {
                                        AsyncImage(
                                            model = uri,
                                            contentDescription = "Product image $index",
                                            modifier = Modifier
                                                .size(100.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop,
                                        )
                                        if (state.isEditable) {
                                            IconButton(
                                                onClick = {
                                                    viewModel.onAction(
                                                        SellerProductEditAction.OnImageRemoved(index)
                                                    )
                                                },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(24.dp),
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Remove",
                                                    tint = MaterialTheme.colorScheme.error,
                                                )
                                            }
                                        }
                                    }
                                }

                                if (state.isEditable && totalImages < 8) {
                                    item {
                                        OutlinedButton(
                                            onClick = { imagePickerLauncher.launch("image/*") },
                                            modifier = Modifier.size(100.dp),
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Add Image")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Tags Section
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Tags (${product.tagNames.size}/10)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )

                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                product.tagNames.forEach { tag ->
                                    AssistChip(
                                        onClick = {
                                            if (state.isEditable) {
                                                viewModel.onAction(SellerProductEditAction.OnTagRemoved(tag))
                                            }
                                        },
                                        label = { Text(tag) },
                                        trailingIcon = {
                                            if (state.isEditable) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Remove tag",
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                        },
                                    )
                                }
                            }

                            if (state.isEditable) {
                                val suggestionDropdownExpanded = state.tagSuggestions.isNotEmpty()
                                ExposedDropdownMenuBox(
                                    expanded = suggestionDropdownExpanded,
                                    onExpandedChange = { /* controlled by state */ },
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        OutlinedTextField(
                                            value = tagInput,
                                            onValueChange = { newValue ->
                                                tagInput = newValue
                                                viewModel.onAction(
                                                    SellerProductEditAction.OnTagInputChanged(newValue)
                                                )
                                            },
                                            label = { Text("Add tag") },
                                            modifier = Modifier
                                                .weight(1f)
                                                .menuAnchor(MenuAnchorType.PrimaryEditable),
                                            singleLine = true,
                                            trailingIcon = {
                                                if (state.isLoadingTagSuggestions) {
                                                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                                }
                                            },
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                viewModel.onAction(SellerProductEditAction.OnTagAdded(tagInput))
                                                tagInput = ""
                                            },
                                            enabled = tagInput.isNotBlank() && product.tagNames.size < 10,
                                        ) {
                                            Text("Add")
                                        }
                                    }
                                    ExposedDropdownMenu(
                                        expanded = suggestionDropdownExpanded,
                                        onDismissRequest = { /* cleared by selecting or clearing input */ },
                                    ) {
                                        state.tagSuggestions.forEach { suggestion ->
                                            DropdownMenuItem(
                                                text = { Text(suggestion) },
                                                onClick = {
                                                    viewModel.onAction(SellerProductEditAction.OnTagAdded(suggestion))
                                                    tagInput = ""
                                                    viewModel.onAction(SellerProductEditAction.OnTagInputChanged(""))
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Variants Section
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Product Variants",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                if (state.isEditable) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Has variants", style = MaterialTheme.typography.bodySmall)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Switch(
                                            checked = product.hasVariants,
                                            onCheckedChange = {
                                                viewModel.onAction(SellerProductEditAction.OnHasVariantsToggled)
                                            },
                                        )
                                    }
                                }
                            }

                            if (product.hasVariants) {
                                product.variants.forEach { variant ->
                                    VariantRow(
                                        variant = variant,
                                        enabled = state.isEditable,
                                        onUpdate = {
                                            viewModel.onAction(SellerProductEditAction.OnVariantUpdated(it))
                                        },
                                        onRemove = {
                                            viewModel.onAction(
                                                SellerProductEditAction.OnVariantRemoved(variant.id)
                                            )
                                        },
                                    )
                                }
                                if (state.isEditable && product.variants.size < 20) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.onAction(
                                                SellerProductEditAction.OnVariantAdded(
                                                    ProductVariant(label = "", attributes = mapOf())
                                                )
                                            )
                                        }
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Add Variant")
                                    }
                                }
                            } else {
                                Text(
                                    "Single product with no variants. Stock managed below.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedTextField(
                                    value = product.variants.firstOrNull()?.stockQuantity?.toString() ?: "0",
                                    onValueChange = { qty ->
                                        val defaultVariant = product.variants.firstOrNull()
                                            ?: ProductVariant(isDefault = true, label = "Default")
                                        viewModel.onAction(
                                            SellerProductEditAction.OnVariantUpdated(
                                                defaultVariant.copy(stockQuantity = qty.toIntOrNull() ?: 0)
                                            )
                                        )
                                    },
                                    label = { Text("Stock Quantity") },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = state.isEditable,
                                    singleLine = true,
                                )
                            }
                        }
                    }
                }

                // Shipping Section
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Shipping",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )

                            var shippingTypeExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = shippingTypeExpanded,
                                onExpandedChange = { if (state.isEditable) shippingTypeExpanded = it },
                            ) {
                                OutlinedTextField(
                                    value = product.shipping.shippingType.name.replace("_", " "),
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Shipping Type") },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = shippingTypeExpanded)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                    enabled = state.isEditable,
                                )
                                ExposedDropdownMenu(
                                    expanded = shippingTypeExpanded,
                                    onDismissRequest = { shippingTypeExpanded = false },
                                ) {
                                    ShippingType.entries.forEach { type ->
                                        DropdownMenuItem(
                                            text = { Text(type.name.replace("_", " ")) },
                                            onClick = {
                                                viewModel.onAction(
                                                    SellerProductEditAction.OnShippingUpdated(
                                                        product.shipping.copy(shippingType = type)
                                                    )
                                                )
                                                shippingTypeExpanded = false
                                            },
                                        )
                                    }
                                }
                            }

                            if (product.shipping.shippingType == ShippingType.PAID_SHIPPING) {
                                OutlinedTextField(
                                    value = if (product.shipping.shippingCost > 0)
                                        product.shipping.shippingCost.toString() else "",
                                    onValueChange = { cost ->
                                        viewModel.onAction(
                                            SellerProductEditAction.OnShippingUpdated(
                                                product.shipping.copy(shippingCost = cost.toDoubleOrNull() ?: 0.0)
                                            )
                                        )
                                    },
                                    label = { Text("Shipping Cost ($)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = state.isEditable,
                                    singleLine = true,
                                )
                            }

                            OutlinedTextField(
                                value = product.shipping.shipsFrom,
                                onValueChange = { from ->
                                    viewModel.onAction(
                                        SellerProductEditAction.OnShippingUpdated(
                                            product.shipping.copy(shipsFrom = from)
                                        )
                                    )
                                },
                                label = { Text("Ships From") },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = state.isEditable,
                                singleLine = true,
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = if (product.shipping.estimatedDaysMin > 0)
                                        product.shipping.estimatedDaysMin.toString() else "",
                                    onValueChange = { value ->
                                        viewModel.onAction(
                                            SellerProductEditAction.OnShippingUpdated(
                                                product.shipping.copy(
                                                    estimatedDaysMin = value.toIntOrNull() ?: 0
                                                )
                                            )
                                        )
                                    },
                                    label = { Text("Min Days") },
                                    modifier = Modifier.weight(1f),
                                    enabled = state.isEditable,
                                    singleLine = true,
                                )
                                OutlinedTextField(
                                    value = if (product.shipping.estimatedDaysMax > 0)
                                        product.shipping.estimatedDaysMax.toString() else "",
                                    onValueChange = { value ->
                                        viewModel.onAction(
                                            SellerProductEditAction.OnShippingUpdated(
                                                product.shipping.copy(
                                                    estimatedDaysMax = value.toIntOrNull() ?: 0
                                                )
                                            )
                                        )
                                    },
                                    label = { Text("Max Days") },
                                    modifier = Modifier.weight(1f),
                                    enabled = state.isEditable,
                                    singleLine = true,
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(
                                        text = "International Shipping",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = "Ships to international destinations",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = product.shipping.isInternationalShipping,
                                    onCheckedChange = { enabled ->
                                        viewModel.onAction(
                                            SellerProductEditAction.OnShippingUpdated(
                                                product.shipping.copy(isInternationalShipping = enabled)
                                            )
                                        )
                                    },
                                    enabled = state.isEditable,
                                )
                            }
                        }
                    }
                }

                // Moderation Notes
                if (product.moderationNotes.isNotBlank()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Moderation Notes",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = product.moderationNotes,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }

                // Actions
                if (state.isEditable) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.onAction(SellerProductEditAction.OnSave) },
                                modifier = Modifier.weight(1f),
                                enabled = !state.isSaving,
                            ) {
                                if (state.isSaving) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                } else {
                                    Text("Save Changes")
                                }
                            }
                            Button(
                                onClick = { viewModel.onAction(SellerProductEditAction.OnSubmitForReview) },
                                modifier = Modifier.weight(1f),
                                enabled = !state.isSaving,
                            ) {
                                Text("Submit for Review")
                            }
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }

            // Category picker bottom sheet
            if (showCategoryPicker) {
                CategoryPickerBottomSheet(
                    state = categoryState,
                    onCategorySelected = { category ->
                        categoryViewModel.onAction(SellerCategoryAction.OnCategorySelected(category))
                    },
                    onSubcategorySelected = { subcategory ->
                        categoryViewModel.onAction(SellerCategoryAction.OnSubcategorySelected(subcategory))
                    },
                    onCreateNewCategory = {
                        categoryViewModel.onAction(SellerCategoryAction.OnShowCreateCategoryDialog)
                    },
                    onCreateNewSubcategory = {
                        categoryViewModel.onAction(SellerCategoryAction.OnShowCreateSubcategoryDialog)
                    },
                    onConfirm = { category, subcategory ->
                        viewModel.onAction(SellerProductEditAction.OnCategorySelected(category))
                        subcategory?.let {
                            viewModel.onAction(SellerProductEditAction.OnSubcategorySelected(it))
                        }
                        showCategoryPicker = false
                    },
                    onDismiss = {
                        showCategoryPicker = false
                        categoryViewModel.onAction(SellerCategoryAction.OnDismissDialog)
                    },
                )
            }

            // Create Category Dialog
            if (categoryState.showCreateCategoryDialog) {
                CreateCategoryDialog(
                    onConfirm = { name, description ->
                        categoryViewModel.onAction(
                            SellerCategoryAction.OnCreateNewCategory(name, description)
                        )
                    },
                    onDismiss = {
                        categoryViewModel.onAction(SellerCategoryAction.OnDismissDialog)
                    },
                )
            }

            // Create Subcategory Dialog
            if (categoryState.showCreateSubcategoryDialog) {
                val selectedCategoryId = categoryState.selectedCategory?.id
                if (selectedCategoryId != null) {
                    CreateSubcategoryDialog(
                        categoryId = selectedCategoryId,
                        onConfirm = { catId, name ->
                            categoryViewModel.onAction(
                                SellerCategoryAction.OnCreateNewSubcategory(
                                    categoryId = catId,
                                    subcategory = Subcategory(name = name),
                                )
                            )
                        },
                        onDismiss = {
                            categoryViewModel.onAction(SellerCategoryAction.OnDismissDialog)
                        },
                    )
                }
            }
        }
    }
}
