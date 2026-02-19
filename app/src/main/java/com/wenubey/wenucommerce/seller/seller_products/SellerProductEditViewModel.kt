package com.wenubey.wenucommerce.seller.seller_products

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.domain.model.product.Category
import com.wenubey.domain.model.product.ProductImage
import com.wenubey.domain.model.product.ProductStatus
import com.wenubey.domain.model.product.ProductVariant
import com.wenubey.domain.model.product.Subcategory
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.ProductRepository
import com.wenubey.domain.repository.TagRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID

class SellerProductEditViewModel(
    private val productRepository: ProductRepository,
    private val tagRepository: TagRepository,
    private val savedStateHandle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val mainDispatcher = dispatcherProvider.main()
    private val ioDispatcher = dispatcherProvider.io()

    private val _state = MutableStateFlow(SellerProductEditState())
    val state: StateFlow<SellerProductEditState> = _state.asStateFlow()

    private val productId: String? = savedStateHandle["productId"]

    private var tagSearchJob: Job? = null

    init {
        productId?.let { loadProduct(it) }
    }

    private fun loadProduct(id: String) {
        viewModelScope.launch(mainDispatcher) {
            _state.update { it.copy(isLoading = true) }
            withContext(ioDispatcher) {
                productRepository.getProductById(id).fold(
                    onSuccess = { product ->
                        val isEditable = product.status == ProductStatus.DRAFT ||
                                product.status == ProductStatus.SUSPENDED
                        _state.update {
                            it.copy(
                                product = product,
                                isLoading = false,
                                isEditable = isEditable,
                                // Seed category from loaded product so the category card
                                // shows a pre-populated value immediately.
                                selectedCategory = if (product.categoryId.isNotBlank())
                                    Category(id = product.categoryId, name = product.categoryName)
                                else null,
                                selectedSubcategory = if (product.subcategoryId.isNotBlank())
                                    Subcategory(id = product.subcategoryId, name = product.subcategoryName)
                                else null,
                            )
                        }
                    },
                    onFailure = { error ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "Failed to load product"
                            )
                        }
                    }
                )
            }
        }
    }

    fun onAction(action: SellerProductEditAction) {
        when (action) {
            // --- existing ---
            is SellerProductEditAction.OnProductUpdated ->
                _state.update { it.copy(product = action.product) }
            is SellerProductEditAction.OnSave -> save()
            is SellerProductEditAction.OnSubmitForReview -> submitForReview()
            is SellerProductEditAction.OnDismissError ->
                _state.update { it.copy(errorMessage = null) }

            // --- images ---
            is SellerProductEditAction.OnImagesSelected -> addImages(action.localUris)
            is SellerProductEditAction.OnImageRemoved -> removeImage(action.index)

            // --- tags ---
            is SellerProductEditAction.OnTagAdded -> addTag(action.tag)
            is SellerProductEditAction.OnTagRemoved -> removeTag(action.tag)
            is SellerProductEditAction.OnTagInputChanged -> searchTags(action.input)

            // --- category ---
            is SellerProductEditAction.OnCategorySelected -> {
                _state.update {
                    it.copy(
                        selectedCategory = action.category,
                        selectedSubcategory = null,
                        product = it.product?.copy(
                            categoryId = action.category.id,
                            categoryName = action.category.name,
                            subcategoryId = "",
                            subcategoryName = "",
                        )
                    )
                }
            }
            is SellerProductEditAction.OnSubcategorySelected -> {
                _state.update {
                    it.copy(
                        selectedSubcategory = action.subcategory,
                        product = it.product?.copy(
                            subcategoryId = action.subcategory.id,
                            subcategoryName = action.subcategory.name,
                        )
                    )
                }
            }

            // --- variants ---
            is SellerProductEditAction.OnVariantAdded -> addVariant(action.variant)
            is SellerProductEditAction.OnVariantUpdated -> updateVariant(action.variant)
            is SellerProductEditAction.OnVariantRemoved -> removeVariant(action.variantId)
            is SellerProductEditAction.OnHasVariantsToggled -> toggleHasVariants()

            // --- shipping ---
            is SellerProductEditAction.OnShippingUpdated -> {
                _state.update {
                    it.copy(product = it.product?.copy(shipping = action.shipping))
                }
            }
        }
    }

    private fun save() {
        val product = _state.value.product ?: return
        if (!_state.value.isEditable) return

        viewModelScope.launch(mainDispatcher) {
            _state.update { it.copy(isSaving = true, errorMessage = null) }

            withContext(ioDispatcher) {
                // 1. Resolve tag display names -> tag IDs
                val resolvedTags = product.tagNames.mapNotNull { rawName ->
                    tagRepository.resolveOrCreateTag(rawName).getOrNull()
                }
                val tagIds = resolvedTags.map { it.id }
                val tagDisplayNames = resolvedTags.map { it.displayName }

                val productWithTags = product.copy(tags = tagIds, tagNames = tagDisplayNames)

                // 2. Upload any new local images
                val uploadedImages = mutableListOf<ProductImage>()
                _state.value.localImageUris.forEachIndexed { index, uri ->
                    val imageId = UUID.randomUUID().toString()
                    productRepository.uploadProductImage(uri, product.id, imageId).fold(
                        onSuccess = { downloadUrl ->
                            uploadedImages.add(
                                ProductImage(
                                    id = imageId,
                                    downloadUrl = downloadUrl,
                                    storagePath = "product_images/${product.id}/$imageId.jpg",
                                    sortOrder = productWithTags.images.size + index,
                                    uploadedAt = System.currentTimeMillis().toString(),
                                )
                            )
                        },
                        onFailure = { error ->
                            Timber.e(error, "Failed to upload edit image $index for ${product.id}")
                        }
                    )
                }

                val mergedImages = productWithTags.images + uploadedImages
                val finalProduct = productWithTags.copy(
                    images = mergedImages,
                    totalStockQuantity = productWithTags.variants.sumOf { it.stockQuantity },
                )

                // 3. Persist
                productRepository.updateProduct(finalProduct).fold(
                    onSuccess = {
                        _state.update {
                            it.copy(
                                isSaving = false,
                                savedSuccessfully = true,
                                localImageUris = listOf(),
                            )
                        }
                        Timber.d("Product updated: ${finalProduct.id}")
                    },
                    onFailure = { error ->
                        _state.update {
                            it.copy(
                                isSaving = false,
                                errorMessage = error.message ?: "Failed to update product"
                            )
                        }
                    }
                )
            }
        }
    }

    private fun submitForReview() {
        val product = _state.value.product ?: return

        viewModelScope.launch(mainDispatcher) {
            _state.update { it.copy(isSaving = true, errorMessage = null) }
            withContext(ioDispatcher) {
                // Save first
                productRepository.updateProduct(product).fold(
                    onSuccess = {
                        productRepository.submitForReview(product.id).fold(
                            onSuccess = {
                                _state.update { it.copy(isSaving = false, savedSuccessfully = true) }
                                Timber.d("Product submitted for review: ${product.id}")
                            },
                            onFailure = { error ->
                                _state.update {
                                    it.copy(
                                        isSaving = false,
                                        errorMessage = error.message ?: "Failed to submit for review"
                                    )
                                }
                            }
                        )
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

    // IMAGES

    private fun addImages(localUris: List<String>) {
        val existingRemote = _state.value.product?.images?.size ?: 0
        val currentLocal = _state.value.localImageUris
        val totalUsed = existingRemote + currentLocal.size
        val remaining = 8 - totalUsed
        if (remaining <= 0) return
        val toAdd = localUris.take(remaining)
        _state.update { it.copy(localImageUris = it.localImageUris + toAdd) }
    }

    private fun removeImage(index: Int) {
        val existingImages = _state.value.product?.images?.toMutableList() ?: return
        val localUris = _state.value.localImageUris.toMutableList()
        val remoteCount = existingImages.size

        if (index < remoteCount) {
            val image = existingImages[index]
            if (image.storagePath.isNotBlank()) {
                viewModelScope.launch(ioDispatcher) {
                    productRepository.deleteProductImage(image.storagePath)
                }
            }
            existingImages.removeAt(index)
            _state.update {
                it.copy(product = it.product?.copy(images = existingImages))
            }
        } else {
            val localIndex = index - remoteCount
            if (localIndex < localUris.size) {
                localUris.removeAt(localIndex)
                _state.update { it.copy(localImageUris = localUris) }
            }
        }
    }

    // TAGS

    private fun addTag(tag: String) {
        val current = _state.value.product?.tagNames ?: return
        if (current.size >= 10 || tag.isBlank() ||
            current.any { it.equals(tag.trim(), ignoreCase = true) }
        ) return
        val updated = current + tag.trim()
        _state.update {
            it.copy(
                product = it.product?.copy(tagNames = updated),
                tagSuggestions = listOf(),
            )
        }
    }

    private fun removeTag(tag: String) {
        val updated = (_state.value.product?.tagNames ?: return) - tag
        _state.update { it.copy(product = it.product?.copy(tagNames = updated)) }
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
                        val current = _state.value.product?.tagNames ?: listOf()
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

    // VARIANTS

    private fun addVariant(variant: ProductVariant) {
        val current = _state.value.product?.variants ?: return
        if (current.size >= 20) return
        val newVariant = variant.copy(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis().toString(),
            updatedAt = System.currentTimeMillis().toString(),
        )
        _state.update { it.copy(product = it.product?.copy(variants = current + newVariant)) }
    }

    private fun updateVariant(variant: ProductVariant) {
        val current = _state.value.product?.variants ?: return
        val updated = current.map {
            if (it.id == variant.id) variant.copy(updatedAt = System.currentTimeMillis().toString())
            else it
        }
        _state.update { it.copy(product = it.product?.copy(variants = updated)) }
    }

    private fun removeVariant(variantId: String) {
        val current = _state.value.product?.variants ?: return
        val filtered = current.filter { it.id != variantId }
        _state.update { it.copy(product = it.product?.copy(variants = filtered)) }
    }

    private fun toggleHasVariants() {
        val product = _state.value.product ?: return
        val hasVariants = !product.hasVariants
        if (!hasVariants) {
            _state.update {
                it.copy(
                    product = product.copy(
                        hasVariants = false,
                        variants = listOf(ProductVariant(isDefault = true, label = "Default")),
                    )
                )
            }
        } else {
            _state.update { it.copy(product = product.copy(hasVariants = true)) }
        }
    }
}
