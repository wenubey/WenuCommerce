package com.wenubey.wenucommerce.seller.seller_storefront

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.FirestoreRepository
import com.wenubey.domain.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SellerStorefrontViewModel(
    private val productRepository: ProductRepository,
    private val firestoreRepository: FirestoreRepository,
    private val savedStateHandle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val mainDispatcher = dispatcherProvider.main()
    private val ioDispatcher = dispatcherProvider.io()

    private val _state = MutableStateFlow(SellerStorefrontState())
    val state: StateFlow<SellerStorefrontState> = _state.asStateFlow()

    private val sellerId: String? = savedStateHandle["sellerId"]

    init {
        sellerId?.let { loadStorefront(it) }
    }

    private fun loadStorefront(sellerId: String) {
        viewModelScope.launch(mainDispatcher) {
            _state.update { it.copy(isLoading = true) }

            withContext(ioDispatcher) {
                // Load seller info
                firestoreRepository.getUser(sellerId).fold(
                    onSuccess = { user ->
                        _state.update {
                            it.copy(sellerName = user.businessInfo?.businessName ?: user.name)
                        }
                    },
                    onFailure = { /* Continue without seller name */ }
                )

                // Load products
                productRepository.getStorefrontProducts(sellerId).fold(
                    onSuccess = { products ->
                        _state.update {
                            it.copy(products = products, isLoading = false)
                        }
                    },
                    onFailure = { error ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "Failed to load storefront"
                            )
                        }
                    }
                )
            }
        }
    }
}
