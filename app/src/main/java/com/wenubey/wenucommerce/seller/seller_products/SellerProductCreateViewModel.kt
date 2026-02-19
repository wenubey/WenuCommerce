package com.wenubey.wenucommerce.seller.seller_products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.domain.model.onboard.VerificationStatus
import com.wenubey.domain.model.product.Product
import com.wenubey.domain.model.product.ProductImage
import com.wenubey.domain.model.product.ProductVariant
import com.wenubey.domain.repository.AuthRepository
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.ProductRepository
import com.wenubey.domain.repository.TagRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID

class SellerProductCreateViewModel(
    private val productRepository: ProductRepository,
    private val authRepository: AuthRepository,
    private val tagRepository: TagRepository,
    dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val mainDispatcher = dispatcherProvider.main()
    private val ioDispatcher = dispatcherProvider.io()

    private val _state = MutableStateFlow(SellerProductCreateState())
    val state: StateFlow<SellerProductCreateState> = _state.asStateFlow()

    private var tagSearchJob: Job? = null

    init {
        checkSellerVerification()
    }

    private fun checkSellerVerification() {
        viewModelScope.launch(mainDispatcher) {
            val user = authRepository.currentUser.value
            val isVerified = user?.businessInfo?.verificationStatus == VerificationStatus.APPROVED
            _state.update { it.copy(isSellerVerified = isVerified) }
            if (!isVerified) {
                _state.update {
                    it.copy(errorMessage = "Your seller account must be verified before listing products.")
                }
            }
        }
    }

    fun onAction(action: SellerProductCreateAction) {
        when (action) {
            is SellerProductCreateAction.OnTitleChanged ->
                _state.update { it.copy(title = action.value) }
            is SellerProductCreateAction.OnDescriptionChanged ->
                _state.update { it.copy(description = action.value) }
            is SellerProductCreateAction.OnPriceChanged ->
                _state.update { it.copy(basePrice = action.value) }
            is SellerProductCreateAction.OnComparePriceChanged ->
                _state.update { it.copy(compareAtPrice = action.value) }
            is SellerProductCreateAction.OnConditionSelected ->
                _state.update { it.copy(condition = action.condition) }
            is SellerProductCreateAction.OnCategorySelected ->
                _state.update { it.copy(selectedCategory = action.category, selectedSubcategory = null) }
            is SellerProductCreateAction.OnSubcategorySelected ->
                _state.update { it.copy(selectedSubcategory = action.subcategory) }
            is SellerProductCreateAction.OnTagAdded -> addTag(action.tag)
            is SellerProductCreateAction.OnTagRemoved -> removeTag(action.tag)
            is SellerProductCreateAction.OnTagInputChanged -> searchTags(action.input)
            is SellerProductCreateAction.OnImagesSelected -> addImages(action.localUris)
            is SellerProductCreateAction.OnImageRemoved -> removeImage(action.index)
            is SellerProductCreateAction.OnVariantAdded -> addVariant(action.variant)
            is SellerProductCreateAction.OnVariantUpdated -> updateVariant(action.variant)
            is SellerProductCreateAction.OnVariantRemoved -> removeVariant(action.variantId)
            is SellerProductCreateAction.OnShippingUpdated ->
                _state.update { it.copy(shipping = action.shipping) }
            is SellerProductCreateAction.OnHasVariantsToggled -> toggleHasVariants()
            is SellerProductCreateAction.OnSaveDraft -> saveDraft()
            is SellerProductCreateAction.OnSubmitForReview -> submitForReview()
            is SellerProductCreateAction.OnDismissError ->
                _state.update { it.copy(errorMessage = null) }
        }
    }

    private fun addTag(tag: String) {
        val current = _state.value.tags
        if (current.size >= 10 || tag.isBlank() || current.any { it.equals(tag.trim(), ignoreCase = true) }) return
        _state.update { it.copy(tags = current + tag.trim(), tagSuggestions = listOf()) }
    }

    private fun removeTag(tag: String) {
        _state.update { it.copy(tags = it.tags - tag) }
    }

    private fun searchTags(input: String) {
        tagSearchJob?.cancel()
        if (input.isBlank()) {
            _state.update { it.copy(tagSuggestions = listOf(), isLoadingTagSuggestions = false) }
            return
        }
        tagSearchJob = viewModelScope.launch(mainDispatcher) {
            delay(300L)
            _state.update { it.copy(isLoadingTagSuggestions = true) }
            withContext(ioDispatcher) {
                tagRepository.searchTagsByPrefix(input).fold(
                    onSuccess = { tags ->
                        val current = _state.value.tags
                        val filtered = tags
                            .map { it.displayName }
                            .filter { displayName ->
                                !current.any { it.equals(displayName, ignoreCase = true) }
                            }
                        _state.update {
                            it.copy(
                                tagSuggestions = filtered,
                                isLoadingTagSuggestions = false,
                            )
                        }
                    },
                    onFailure = {
                        _state.update {
                            it.copy(isLoadingTagSuggestions = false, tagSuggestions = listOf())
                        }
                    }
                )
            }
        }
    }

    private fun addImages(localUris: List<String>) {
        val current = _state.value.localImageUris
        val remaining = 8 - current.size
        if (remaining <= 0) return
        val toAdd = localUris.take(remaining)
        _state.update { it.copy(localImageUris = current + toAdd) }
    }

    private fun removeImage(index: Int) {
        val currentLocal = _state.value.localImageUris.toMutableList()
        val currentImages = _state.value.images.toMutableList()
        if (index < currentLocal.size) {
            currentLocal.removeAt(index)
        }
        if (index < currentImages.size) {
            currentImages.removeAt(index)
        }
        _state.update { it.copy(localImageUris = currentLocal, images = currentImages) }
    }

    private fun addVariant(variant: ProductVariant) {
        val current = _state.value.variants
        if (current.size >= 20) return
        val newVariant = variant.copy(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis().toString(),
            updatedAt = System.currentTimeMillis().toString(),
        )
        _state.update { it.copy(variants = current + newVariant) }
    }

    private fun updateVariant(variant: ProductVariant) {
        val updated = _state.value.variants.map {
            if (it.id == variant.id) variant.copy(updatedAt = System.currentTimeMillis().toString())
            else it
        }
        _state.update { it.copy(variants = updated) }
    }

    private fun removeVariant(variantId: String) {
        val filtered = _state.value.variants.filter { it.id != variantId }
        _state.update { it.copy(variants = filtered) }
    }

    private fun toggleHasVariants() {
        val hasVariants = !_state.value.hasVariants
        if (!hasVariants) {
            _state.update {
                it.copy(
                    hasVariants = false,
                    variants = listOf(ProductVariant(isDefault = true, label = "Default"))
                )
            }
        } else {
            _state.update { it.copy(hasVariants = true) }
        }
    }

    private fun saveDraft() {
        if (!_state.value.isSellerVerified) return
        val current = _state.value

        viewModelScope.launch(mainDispatcher) {
            _state.update { it.copy(isSaving = true, errorMessage = null) }

            val user = authRepository.currentUser.value
            val sellerName = user?.businessInfo?.businessName ?: ""

            // Ensure at least one variant
            val variants = current.variants.ifEmpty {
                listOf(ProductVariant(isDefault = true, label = "Default"))
            }

            withContext(ioDispatcher) {
                // Resolve tags — convert raw tag names to Tag documents
                val resolvedTags = current.tags.mapNotNull { rawName ->
                    tagRepository.resolveOrCreateTag(rawName).getOrNull()
                }
                val tagIds = resolvedTags.map { it.id }
                val tagDisplayNames = resolvedTags.map { it.displayName }

                val product = Product(
                    title = current.title,
                    description = current.description,
                    basePrice = current.basePrice.toDoubleOrNull() ?: 0.0,
                    compareAtPrice = current.compareAtPrice.toDoubleOrNull(),
                    condition = current.condition,
                    categoryId = current.selectedCategory?.id ?: "",
                    categoryName = current.selectedCategory?.name ?: "",
                    subcategoryId = current.selectedSubcategory?.id ?: "",
                    subcategoryName = current.selectedSubcategory?.name ?: "",
                    tags = tagIds,
                    tagNames = tagDisplayNames,
                    variants = variants,
                    hasVariants = current.hasVariants,
                    totalStockQuantity = variants.sumOf { it.stockQuantity },
                    shipping = current.shipping,
                    sellerName = sellerName,
                )

                productRepository.createProduct(product).fold(
                    onSuccess = { createdProduct ->
                        // Upload images
                        uploadImages(createdProduct.id, current.localImageUris)

                        // Track product on seller document
                        val sellerId = authRepository.currentUser.value?.uuid
                        if (sellerId != null) {
                            productRepository.addProductToSellerDocument(
                                sellerId = sellerId,
                                productId = createdProduct.id,
                            ).onFailure { error ->
                                // Non-fatal: log but do not block the user
                                Timber.e(error, "Failed to add product to seller document")
                            }
                        }

                        _state.update {
                            it.copy(
                                isSaving = false,
                                savedProductId = createdProduct.id,
                            )
                        }
                        Timber.d("Product saved as draft: ${createdProduct.id}")
                    },
                    onFailure = { error ->
                        _state.update {
                            it.copy(
                                isSaving = false,
                                errorMessage = error.message ?: "Failed to save product"
                            )
                        }
                    }
                )
            }
        }
    }

    private fun submitForReview() {
        val current = _state.value
        if (current.title.isBlank()) {
            _state.update { it.copy(errorMessage = "Product title is required") }
            return
        }
        if ((current.basePrice.toDoubleOrNull() ?: 0.0) <= 0.0) {
            _state.update { it.copy(errorMessage = "Product price must be greater than 0") }
            return
        }
        if (current.selectedCategory == null) {
            _state.update { it.copy(errorMessage = "Please select a category") }
            return
        }

        // Save draft first, then submit
        saveDraft()
        viewModelScope.launch(mainDispatcher) {
            val state = _state.first { it.savedProductId != null }
            val productId = state.savedProductId!!
            withContext(ioDispatcher) {
                productRepository.submitForReview(productId).fold(
                    onSuccess = { Timber.d("Product submitted for review: $productId") },
                    onFailure = { error ->
                        _state.update {
                            it.copy(errorMessage = error.message ?: "Failed to submit for review")
                        }
                    }
                )
            }
        }
    }

    private suspend fun uploadImages(productId: String, localUris: List<String>) {
        val uploadedImages = mutableListOf<ProductImage>()
        localUris.forEachIndexed { index, uri ->
            val imageId = UUID.randomUUID().toString()
            productRepository.uploadProductImage(uri, productId, imageId).fold(
                onSuccess = { downloadUrl ->
                    uploadedImages.add(
                        ProductImage(
                            id = imageId,
                            downloadUrl = downloadUrl,
                            storagePath = "product_images/$productId/$imageId.jpg",
                            sortOrder = index,
                            uploadedAt = System.currentTimeMillis().toString(),
                        )
                    )
                },
                onFailure = { error ->
                    Timber.e(error, "Failed to upload image $index for product $productId")
                }
            )
        }

        if (uploadedImages.isNotEmpty()) {
            val currentProduct = productRepository.getProductById(productId).getOrNull()
            if (currentProduct != null) {
                productRepository.updateProduct(
                    currentProduct.copy(images = uploadedImages)
                ).onFailure { error ->
                    Timber.e(error, "Failed to save image URLs to product $productId")
                    _state.update {
                        it.copy(errorMessage = "Images uploaded but failed to save URLs: ${error.message}")
                    }
                }
            } else {
                Timber.e("Failed to fetch product $productId after image upload")
            }
        }
    }
}
