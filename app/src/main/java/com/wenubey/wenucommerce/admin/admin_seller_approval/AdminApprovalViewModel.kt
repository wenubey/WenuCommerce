package com.wenubey.wenucommerce.admin.admin_seller_approval

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.domain.model.onboard.VerificationStatus
import com.wenubey.domain.model.user.User
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.FirestoreRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class AdminApprovalViewModel(
    private val firestoreRepository: FirestoreRepository,
    dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val mainDispatcher = dispatcherProvider.main()
    private val ioDispatcher = dispatcherProvider.io()

    private val _approvalState = MutableStateFlow(AdminSellerApprovalState())
    val approvalState: StateFlow<AdminSellerApprovalState> = _approvalState.asStateFlow()

    private var sellerListenerJob: Job? = null

    init {
        observeSellers(VerificationStatus.PENDING)
    }

    private fun observeSellers(status: VerificationStatus) {
        sellerListenerJob?.cancel()

        sellerListenerJob = viewModelScope.launch(mainDispatcher) {
            _approvalState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null
                )
            }

            withContext(ioDispatcher) {
                firestoreRepository
                    .observeSellersByStatus(status)
                    .catch { error ->
                        _approvalState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "Failed to load sellers"
                            )
                        }
                    }
                    .collect { sellers ->
                        _approvalState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = null,
                                sellers = sellers
                            )
                        }
                    }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sellerListenerJob?.cancel()
    }


    fun onAction(action: AdminSellerApprovalAction) {
        when (action) {
            is AdminSellerApprovalAction.OnApprove -> onApprove(action.sellerId, action.notes)
            is AdminSellerApprovalAction.OnReject -> onReject(action.sellerId, action.notes)
            is AdminSellerApprovalAction.OnRequestMoreInfo -> onRequestMoreInfo(
                action.sellerId,
                action.notes
            )
            is AdminSellerApprovalAction.OnSellerSelected -> onSellerSelected(action.seller)
            is AdminSellerApprovalAction.OnDismissDialog -> dismissDialog()
            is AdminSellerApprovalAction.OnFilterChange -> onFilterChange(action.status)
        }
    }

    private fun onFilterChange(status: VerificationStatus) {
        viewModelScope.launch(mainDispatcher) {
            _approvalState.update {
                Timber.d("First Seller ID: ${it.sellers.firstOrNull()?.uuid} businessInfo verificationStatus: ${it.sellers.firstOrNull()?.businessInfo?.verificationStatus} previousStatus: ${it.sellers.firstOrNull()?.businessInfo?.previousStatus}")
                it.copy(selectedFilter = status)
            }
        }
        observeSellers(status)
    }

    private fun dismissDialog() {
        viewModelScope.launch(mainDispatcher) {
            _approvalState.update {
                it.copy(
                    showApprovalDialog = false,
                    dialogType = null,
                    selectedSeller = null
                )
            }
        }
    }

    private fun onSellerSelected(seller: User) {
        viewModelScope.launch(mainDispatcher) {
            _approvalState.update {
                it.copy(
                    selectedSeller = seller,
                    showApprovalDialog = true,
                    dialogType = DialogType.APPROVE
                )
            }
        }
    }

    private fun onRequestMoreInfo(sellerId: String, notes: String) {
        viewModelScope.launch(mainDispatcher) {

            _approvalState.update { it.copy(isLoading = true, errorMessage = null) }

            withContext(ioDispatcher) {
                val result = firestoreRepository.updateSellerApprovalStatus(
                    sellerId = sellerId,
                    status = VerificationStatus.REQUEST_MORE_INFO,
                    notes = notes
                )

                result.fold(
                    onSuccess = {
                        _approvalState.update {
                            it.copy(
                                isLoading = false,
                                showApprovalDialog = false,
                                dialogType = null,
                                errorMessage = null,
                                selectedSeller = null
                            )
                        }
                    },
                    onFailure = { error ->
                        _approvalState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message
                                    ?: "Failed to request more info for seller"
                            )
                        }
                    }
                )
            }
        }
    }

    private fun onReject(sellerId: String, notes: String) {
        viewModelScope.launch(mainDispatcher) {

            _approvalState.update { it.copy(isLoading = true, errorMessage = null) }

            withContext(ioDispatcher) {
                val result = firestoreRepository.updateSellerApprovalStatus(
                    sellerId = sellerId,
                    status = VerificationStatus.REJECTED,
                    notes = notes
                )

                result.fold(
                    onSuccess = {
                        _approvalState.update {
                            it.copy(
                                isLoading = false,
                                showApprovalDialog = false,
                                dialogType = null,
                                errorMessage = null,
                                selectedSeller = null
                            )
                        }
                    },
                    onFailure = { error ->
                        _approvalState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "Failed to rejected seller"
                            )
                        }
                    }
                )
            }
        }
    }

    private fun onApprove(sellerId: String, notes: String) {
        viewModelScope.launch(mainDispatcher) {

            _approvalState.update { it.copy(isLoading = true, errorMessage = null) }

            withContext(ioDispatcher) {
                val result = firestoreRepository.updateSellerApprovalStatus(
                    sellerId = sellerId,
                    status = VerificationStatus.APPROVED,
                    notes = notes
                )

                result.fold(
                    onSuccess = {
                        _approvalState.update {
                            it.copy(
                                isLoading = false,
                                showApprovalDialog = false,
                                dialogType = null,
                                errorMessage = null,
                                selectedSeller = null
                            )
                        }
                    },
                    onFailure = { error ->
                        _approvalState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "Failed to approve seller"
                            )
                        }
                    }
                )
            }
        }
    }
}