package com.wenubey.wenucommerce.admin.admin_products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.wenubey.domain.model.product.ProductStatus
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.ProductRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class AdminProductModerationViewModel(
    private val productRepository: ProductRepository,
    private val auth: FirebaseAuth,
    dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val mainDispatcher = dispatcherProvider.main()
    private val ioDispatcher = dispatcherProvider.io()

    private val _state = MutableStateFlow(AdminProductModerationState())
    val state: StateFlow<AdminProductModerationState> = _state.asStateFlow()

    private var observeJob: Job? = null

    init {
        observePendingProducts()
    }

    private fun observePendingProducts() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch(mainDispatcher) {
            _state.update { it.copy(isLoading = true) }
            productRepository.observeProductsByStatus(ProductStatus.PENDING_REVIEW).collect { products ->
                _state.update {
                    it.copy(
                        pendingProducts = products,
                        isLoading = false,
                        errorMessage = null,
                    )
                }
            }
        }
    }

    fun onAction(action: AdminProductModerationAction) {
        when (action) {
            is AdminProductModerationAction.OnProductSelected ->
                _state.update { it.copy(selectedProduct = action.product) }
            is AdminProductModerationAction.OnSuspendReasonChanged ->
                _state.update { it.copy(suspendReason = action.reason) }
            is AdminProductModerationAction.OnShowSuspendDialog ->
                _state.update { it.copy(showSuspendDialog = true) }
            is AdminProductModerationAction.OnShowApproveDialog ->
                _state.update { it.copy(showApproveDialog = true) }
            is AdminProductModerationAction.OnShowDetailDialog ->
                _state.update { it.copy(showDetailDialog = true) }
            is AdminProductModerationAction.OnDismissDialog ->
                _state.update {
                    it.copy(
                        showSuspendDialog = false,
                        showApproveDialog = false,
                        showDetailDialog = false,
                        suspendReason = "",
                    )
                }
            is AdminProductModerationAction.OnConfirmApprove -> approveProduct()
            is AdminProductModerationAction.OnConfirmSuspend -> suspendProduct()
        }
    }

    private fun approveProduct() {
        val product = _state.value.selectedProduct ?: return

        viewModelScope.launch(mainDispatcher) {
            _state.update { it.copy(isActing = true, showApproveDialog = false) }
            withContext(ioDispatcher) {
                productRepository.approveProduct(product.id).fold(
                    onSuccess = {
                        _state.update {
                            it.copy(isActing = false, selectedProduct = null, errorMessage = null)
                        }
                        Timber.d("Product approved: ${product.id}")
                    },
                    onFailure = { error ->
                        _state.update {
                            it.copy(
                                isActing = false,
                                errorMessage = error.message ?: "Failed to approve product"
                            )
                        }
                    }
                )
            }
        }
    }

    private fun suspendProduct() {
        val product = _state.value.selectedProduct ?: return
        val reason = _state.value.suspendReason
        val adminId = auth.currentUser?.uid ?: return

        if (reason.isBlank()) {
            _state.update { it.copy(errorMessage = "Please provide a suspension reason") }
            return
        }

        viewModelScope.launch(mainDispatcher) {
            _state.update { it.copy(isActing = true, showSuspendDialog = false) }
            withContext(ioDispatcher) {
                productRepository.suspendProduct(product.id, reason, adminId).fold(
                    onSuccess = {
                        _state.update {
                            it.copy(
                                isActing = false,
                                selectedProduct = null,
                                suspendReason = "",
                                errorMessage = null,
                            )
                        }
                        Timber.d("Product suspended: ${product.id}")
                    },
                    onFailure = { error ->
                        _state.update {
                            it.copy(
                                isActing = false,
                                errorMessage = error.message ?: "Failed to suspend product"
                            )
                        }
                    }
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        observeJob?.cancel()
    }
}
