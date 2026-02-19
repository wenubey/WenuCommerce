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
fun SellerProductCreateScreen(
    modifier: Modifier = Modifier,
    viewModel: SellerProductCreateViewModel = koinViewModel(),
    categoryViewModel: SellerCategoryViewModel = koinViewModel(),
    onProductSaved: (String) -> Unit = {},
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
            SellerProductCreateAction.OnImagesSelected(uris.map { it.toString() })
        )
    }

    // Navigate on save
    LaunchedEffect(state.savedProductId) {
        state.savedProductId?.let { onProductSaved(it) }
    }

    if (!state.isSellerVerified) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = state.errorMessage ?: "Seller verification required.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Create Product",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
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
                    Text("Basic Info", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)

                    OutlinedTextField(
                        value = state.title,
                        onValueChange = { viewModel.onAction(SellerProductCreateAction.OnTitleChanged(it)) },
                        label = { Text("Product Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    OutlinedTextField(
                        value = state.description,
                        onValueChange = { viewModel.onAction(SellerProductCreateAction.OnDescriptionChanged(it)) },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = state.basePrice,
                            onValueChange = { viewModel.onAction(SellerProductCreateAction.OnPriceChanged(it)) },
                            label = { Text("Price ($)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = state.compareAtPrice,
                            onValueChange = { viewModel.onAction(SellerProductCreateAction.OnComparePriceChanged(it)) },
                            label = { Text("Compare At ($)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }

                    // Condition dropdown
                    var conditionExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = conditionExpanded,
                        onExpandedChange = { conditionExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = state.condition.name.replace("_", " "),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Condition") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = conditionExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(
                            expanded = conditionExpanded,
                            onDismissRequest = { conditionExpanded = false }
                        ) {
                            ProductCondition.entries.forEach { condition ->
                                DropdownMenuItem(
                                    text = { Text(condition.name.replace("_", " ")) },
                                    onClick = {
                                        viewModel.onAction(SellerProductCreateAction.OnConditionSelected(condition))
                                        conditionExpanded = false
                                    }
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
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)

                    if (state.selectedCategory != null) {
                        Text("Category: ${state.selectedCategory?.name}")
                        state.selectedSubcategory?.let {
                            Text("Subcategory: ${it.name}", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    OutlinedButton(onClick = { showCategoryPicker = true }) {
                        Text(if (state.selectedCategory != null) "Change Category" else "Select Category")
                    }
                }
            }
        }

        // Images Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Images (${state.localImageUris.size}/8)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(state.localImageUris) { index, uri ->
                            Box {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Product image $index",
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                                IconButton(
                                    onClick = { viewModel.onAction(SellerProductCreateAction.OnImageRemoved(index)) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }

                        if (state.localImageUris.size < 8) {
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

        // Tags Section with autocomplete
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
                        "Tags (${state.tags.size}/10)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.tags.forEach { tag ->
                            AssistChip(
                                onClick = { viewModel.onAction(SellerProductCreateAction.OnTagRemoved(tag)) },
                                label = { Text(tag) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove tag",
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                        }
                    }

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
                                    viewModel.onAction(SellerProductCreateAction.OnTagInputChanged(newValue))
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
                                    viewModel.onAction(SellerProductCreateAction.OnTagAdded(tagInput))
                                    tagInput = ""
                                },
                                enabled = tagInput.isNotBlank() && state.tags.size < 10,
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
                                        viewModel.onAction(SellerProductCreateAction.OnTagAdded(suggestion))
                                        tagInput = ""
                                        viewModel.onAction(SellerProductCreateAction.OnTagInputChanged(""))
                                    },
                                )
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
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Product Variants", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Has variants", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = state.hasVariants,
                                onCheckedChange = { viewModel.onAction(SellerProductCreateAction.OnHasVariantsToggled) }
                            )
                        }
                    }

                    if (state.hasVariants) {
                        state.variants.forEach { variant ->
                            VariantRow(
                                variant = variant,
                                onUpdate = { viewModel.onAction(SellerProductCreateAction.OnVariantUpdated(it)) },
                                onRemove = { viewModel.onAction(SellerProductCreateAction.OnVariantRemoved(variant.id)) },
                            )
                        }

                        if (state.variants.size < 20) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.onAction(
                                        SellerProductCreateAction.OnVariantAdded(
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
                            value = state.variants.firstOrNull()?.stockQuantity?.toString() ?: "0",
                            onValueChange = { qty ->
                                val defaultVariant = state.variants.firstOrNull()
                                    ?: ProductVariant(isDefault = true, label = "Default")
                                viewModel.onAction(
                                    SellerProductCreateAction.OnVariantUpdated(
                                        defaultVariant.copy(
                                            stockQuantity = qty.toIntOrNull() ?: 0
                                        )
                                    )
                                )
                            },
                            label = { Text("Stock Quantity") },
                            modifier = Modifier.fillMaxWidth(),
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
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Shipping", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)

                    var shippingTypeExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = shippingTypeExpanded,
                        onExpandedChange = { shippingTypeExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = state.shipping.shippingType.name.replace("_", " "),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Shipping Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = shippingTypeExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(
                            expanded = shippingTypeExpanded,
                            onDismissRequest = { shippingTypeExpanded = false }
                        ) {
                            ShippingType.entries.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.name.replace("_", " ")) },
                                    onClick = {
                                        viewModel.onAction(
                                            SellerProductCreateAction.OnShippingUpdated(
                                                state.shipping.copy(shippingType = type)
                                            )
                                        )
                                        shippingTypeExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (state.shipping.shippingType == ShippingType.PAID_SHIPPING) {
                        OutlinedTextField(
                            value = if (state.shipping.shippingCost > 0) state.shipping.shippingCost.toString() else "",
                            onValueChange = { cost ->
                                viewModel.onAction(
                                    SellerProductCreateAction.OnShippingUpdated(
                                        state.shipping.copy(shippingCost = cost.toDoubleOrNull() ?: 0.0)
                                    )
                                )
                            },
                            label = { Text("Shipping Cost ($)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }

                    OutlinedTextField(
                        value = state.shipping.shipsFrom,
                        onValueChange = { from ->
                            viewModel.onAction(
                                SellerProductCreateAction.OnShippingUpdated(
                                    state.shipping.copy(shipsFrom = from)
                                )
                            )
                        },
                        label = { Text("Ships From") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    // Estimated Delivery Days
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = if (state.shipping.estimatedDaysMin > 0)
                                state.shipping.estimatedDaysMin.toString() else "",
                            onValueChange = { value ->
                                viewModel.onAction(
                                    SellerProductCreateAction.OnShippingUpdated(
                                        state.shipping.copy(
                                            estimatedDaysMin = value.toIntOrNull() ?: 0
                                        )
                                    )
                                )
                            },
                            label = { Text("Min Days") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = if (state.shipping.estimatedDaysMax > 0)
                                state.shipping.estimatedDaysMax.toString() else "",
                            onValueChange = { value ->
                                viewModel.onAction(
                                    SellerProductCreateAction.OnShippingUpdated(
                                        state.shipping.copy(
                                            estimatedDaysMax = value.toIntOrNull() ?: 0
                                        )
                                    )
                                )
                            },
                            label = { Text("Max Days") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }

                    // International Shipping Toggle
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
                            checked = state.shipping.isInternationalShipping,
                            onCheckedChange = { enabled ->
                                viewModel.onAction(
                                    SellerProductCreateAction.OnShippingUpdated(
                                        state.shipping.copy(isInternationalShipping = enabled)
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }

        // Action Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.onAction(SellerProductCreateAction.OnSaveDraft) },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isSaving,
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text("Save Draft")
                    }
                }
                Button(
                    onClick = { viewModel.onAction(SellerProductCreateAction.OnSubmitForReview) },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isSaving,
                ) {
                    Text("Submit for Review")
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Category Picker
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
                viewModel.onAction(SellerProductCreateAction.OnCategorySelected(category))
                subcategory?.let { viewModel.onAction(SellerProductCreateAction.OnSubcategorySelected(it)) }
                showCategoryPicker = false
            },
            onDismiss = {
                showCategoryPicker = false
                categoryViewModel.onAction(SellerCategoryAction.OnDismissDialog)
            },
        )
    }

    // Create Category Dialog (triggered from CategoryPickerBottomSheet)
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

    // Create Subcategory Dialog (triggered from CategoryPickerBottomSheet)
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
