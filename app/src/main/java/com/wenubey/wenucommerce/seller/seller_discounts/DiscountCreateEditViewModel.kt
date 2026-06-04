package com.wenubey.wenucommerce.seller.seller_discounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.domain.model.discount.DiscountCode
import com.wenubey.domain.model.discount.DiscountType
import com.wenubey.domain.model.user.UserRole
import com.wenubey.domain.repository.AuthRepository
import com.wenubey.domain.repository.DiscountRepository
import com.wenubey.domain.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant

class DiscountCreateEditViewModel(
    private val discountRepository: DiscountRepository,
    private val authRepository: AuthRepository,
    private val productRepository: ProductRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(DiscountCreateEditState())
    val state: StateFlow<DiscountCreateEditState> = _state.asStateFlow()

    private val editCode: String? = savedStateHandle["code"]
    private val isSeller: Boolean = savedStateHandle["isSeller"] ?: true

    init {
        if (editCode != null) {
            loadExistingDiscount(editCode)
        }
        loadAvailableProducts()
    }

    private fun loadExistingDiscount(code: String) {
        _state.update { it.copy(isLoading = true, isEditMode = true) }
        val user = authRepository.currentUser.value
        val sellerId = if (user?.role == UserRole.ADMIN) "" else user?.uuid.orEmpty()

        viewModelScope.launch {
            discountRepository.observeDiscountCodes(sellerId)
                .catch { e ->
                    Timber.e(e, "DiscountCreateEditViewModel: failed to load discount $code")
                    _state.update { it.copy(isLoading = false, saveError = e.message) }
                }
                .collect { discounts ->
                    val discount = discounts.find { it.code == code }
                    if (discount != null) {
                        _state.update { state ->
                            state.copy(
                                isLoading = false,
                                isEditMode = true,
                                code = discount.code,
                                type = discount.type,
                                value = if (discount.type == DiscountType.FREE_SHIPPING) "" else discount.value.toString(),
                                maxDiscountCap = discount.maxDiscountCap?.toString() ?: "",
                                minimumOrderAmount = discount.minimumOrderAmount?.toString() ?: "",
                                targetProductIds = discount.targetProductIds,
                                expiresAt = discount.expiresAt?.let {
                                    try {
                                        Instant.parse(it).toEpochMilli()
                                    } catch (_: Exception) {
                                        null
                                    }
                                },
                                usageLimit = discount.usageLimit?.toString() ?: "",
                            )
                        }
                    }
                }
        }
    }

    private fun loadAvailableProducts() {
        val user = authRepository.currentUser.value
        val userId = user?.uuid.orEmpty()

        viewModelScope.launch {
            val productsFlow = if (isSeller) {
                productRepository.observeSellerProducts(userId)
            } else {
                // For admin, observe all active products across sellers
                productRepository.observeSellerProducts("")
            }

            productsFlow
                .catch { e ->
                    Timber.e(e, "DiscountCreateEditViewModel: failed to load products")
                }
                .collect { products ->
                    _state.update { state ->
                        state.copy(
                            availableProducts = products.map { product ->
                                ProductPickerItem(
                                    productId = product.id,
                                    title = product.title,
                                    isSelected = state.targetProductIds.contains(product.id),
                                )
                            }
                        )
                    }
                }
        }
    }

    fun onAction(action: DiscountCreateEditAction) {
        when (action) {
            is DiscountCreateEditAction.UpdateCode -> {
                _state.update { it.copy(code = action.code.uppercase()) }
            }
            is DiscountCreateEditAction.GenerateCode -> {
                _state.update { it.copy(code = generateCode()) }
            }
            is DiscountCreateEditAction.UpdateType -> {
                _state.update { it.copy(type = action.type) }
            }
            is DiscountCreateEditAction.UpdateValue -> {
                _state.update { it.copy(value = action.value) }
            }
            is DiscountCreateEditAction.UpdateMaxCap -> {
                _state.update { it.copy(maxDiscountCap = action.cap) }
            }
            is DiscountCreateEditAction.UpdateMinOrder -> {
                _state.update { it.copy(minimumOrderAmount = action.amount) }
            }
            is DiscountCreateEditAction.UpdateUsageLimit -> {
                _state.update { it.copy(usageLimit = action.limit) }
            }
            is DiscountCreateEditAction.UpdateExpiryDate -> {
                _state.update { it.copy(expiresAt = action.millis) }
            }
            is DiscountCreateEditAction.UpdateProductSearch -> {
                _state.update { it.copy(productSearchQuery = action.query) }
            }
            is DiscountCreateEditAction.ToggleProduct -> {
                _state.update { state ->
                    val currentIds = state.targetProductIds.toMutableList()
                    if (currentIds.contains(action.productId)) {
                        currentIds.remove(action.productId)
                    } else {
                        currentIds.add(action.productId)
                    }
                    state.copy(
                        targetProductIds = currentIds,
                        availableProducts = state.availableProducts.map { item ->
                            if (item.productId == action.productId) {
                                item.copy(isSelected = !item.isSelected)
                            } else {
                                item
                            }
                        }
                    )
                }
            }
            is DiscountCreateEditAction.Save -> save()
            is DiscountCreateEditAction.DismissError -> {
                _state.update { it.copy(saveError = null) }
            }
        }
    }

    private fun save() {
        val currentState = _state.value

        // Validation
        if (currentState.code.isBlank()) {
            _state.update { it.copy(saveError = "Coupon code is required") }
            return
        }

        if (currentState.type != DiscountType.FREE_SHIPPING) {
            val valueDouble = currentState.value.toDoubleOrNull()
            if (valueDouble == null || valueDouble <= 0) {
                _state.update { it.copy(saveError = "Value must be greater than 0") }
                return
            }
            if (currentState.type == DiscountType.PERCENTAGE && valueDouble > 100) {
                _state.update { it.copy(saveError = "Percentage must be between 0 and 100") }
                return
            }
        }

        _state.update { it.copy(isSaving = true, saveError = null) }

        val user = authRepository.currentUser.value
        val sellerId = if (user?.role == UserRole.ADMIN) "" else user?.uuid.orEmpty()

        val discount = DiscountCode(
            code = currentState.code.trim().uppercase(),
            type = currentState.type,
            value = if (currentState.type == DiscountType.FREE_SHIPPING) 0.0 else currentState.value.toDoubleOrNull() ?: 0.0,
            maxDiscountCap = currentState.maxDiscountCap.toDoubleOrNull(),
            minimumOrderAmount = currentState.minimumOrderAmount.toDoubleOrNull(),
            targetProductIds = currentState.targetProductIds,
            sellerId = sellerId,
            expiresAt = currentState.expiresAt?.let { Instant.ofEpochMilli(it).toString() },
            usageLimit = currentState.usageLimit.toIntOrNull(),
            isActive = true,
        )

        viewModelScope.launch {
            val result = if (currentState.isEditMode) {
                discountRepository.updateDiscountCode(discount)
            } else {
                discountRepository.createDiscountCode(discount)
            }

            result
                .onSuccess {
                    _state.update { it.copy(isSaving = false, saveSuccess = true) }
                }
                .onFailure { e ->
                    Timber.e(e, "DiscountCreateEditViewModel: save failed")
                    _state.update { it.copy(isSaving = false, saveError = e.message) }
                }
        }
    }

    private fun generateCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..8).map { chars.random() }.joinToString("")
    }
}
