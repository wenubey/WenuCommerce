package com.wenubey.wenucommerce.seller.seller_verification

import android.net.Uri
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.domain.model.onboard.BusinessInfo
import com.wenubey.domain.model.onboard.BusinessType
import com.wenubey.domain.model.onboard.VerificationStatus
import com.wenubey.domain.repository.AuthRepository
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.ProfileRepository
import com.wenubey.domain.util.DocumentType
import com.wenubey.wenucommerce.core.validators.isValidBankAccount
import com.wenubey.wenucommerce.core.validators.isValidEmail
import com.wenubey.wenucommerce.core.validators.isValidRoutingNumber
import com.wenubey.wenucommerce.core.validators.isValidTaxId
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SellerVerificationViewModel(
    dispatcherProvider: DispatcherProvider,
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val ioDispatcher = dispatcherProvider.io()
    private val mainDispatcher = dispatcherProvider.main()

    private val _sellerVerificationState = MutableStateFlow(SellerVerificationState())
    val sellerVerificationState: StateFlow<SellerVerificationState> =
        _sellerVerificationState.asStateFlow()

    private var userObserverJob: Job? = null



    init {
        observeCurrentUser()
    }

    private fun observeCurrentUser() {
        userObserverJob?.cancel()

        userObserverJob = viewModelScope.launch(mainDispatcher) {
            authRepository.currentUser.collect { user ->
                _sellerVerificationState.update {
                    it.copy(user = user)
                }
            }
        }
    }

    fun onAction(action: SellerVerificationAction) {
        when (action) {
            is SellerVerificationAction.ShowEditDialog -> showEditDialog()
            is SellerVerificationAction.ShowCancelDialog -> showCancelDialog()
            is SellerVerificationAction.DismissDialog -> dismissDialog()

            // Field Changes
            is SellerVerificationAction.OnBusinessNameChange -> businessNameChange(action.value)
            is SellerVerificationAction.OnBusinessTypeChange -> businessTypeChange(action.value)
            is SellerVerificationAction.OnBusinessDescriptionChange -> businessDescriptionChange(
                action.value
            )

            is SellerVerificationAction.OnBusinessPhoneChange -> businessPhoneChange(action.value)
            is SellerVerificationAction.OnBusinessEmailChange -> businessEmailChange(action.value)
            is SellerVerificationAction.OnTaxIdChange -> taxIdChange(action.value)
            is SellerVerificationAction.OnBusinessLicenseChange -> businessLicenseChange(action.value)
            is SellerVerificationAction.OnBankAccountNumberChange -> bankAccountNumberChange(action.value)
            is SellerVerificationAction.OnRoutingNumberChange -> routingNumberChange(action.value)
            is SellerVerificationAction.OnBusinessAddressChange -> businessAddressChange(action.value)

            // Document Uploads
            is SellerVerificationAction.OnTaxDocumentSelected -> taxDocumentSelected(action.uri)
            is SellerVerificationAction.OnBusinessLicenseDocumentSelected -> businessLicenseDocumentSelected(
                action.uri
            )

            is SellerVerificationAction.OnIdentityDocumentSelected -> identityDocumentSelected(
                action.uri
            )

            // Submissions
            is SellerVerificationAction.ConfirmCancelApplication -> cancelApplication()
            is SellerVerificationAction.SubmitUpdatedInfo -> submitUpdatedInfo()
        }
    }

    private fun cancelApplication() {
        viewModelScope.launch(ioDispatcher) {
            _sellerVerificationState.update { it.copy(isSubmitting = true) }

            val userUid = _sellerVerificationState.value.user?.uuid ?: return@launch
            profileRepository.cancelSellerApplication(userUid)
                .onSuccess {
                    withContext(mainDispatcher) {
                        _sellerVerificationState.update {
                            it.copy(
                                isSubmitting = false,
                                showCancelDialog = false,
                            )
                        }
                    }
                }
                .onFailure { error ->
                    withContext(mainDispatcher) {
                        _sellerVerificationState.update {
                            it.copy(
                                isSubmitting = false,
                                errorMessage = error.message
                            )
                        }
                    }
                }
        }
    }

    private fun submitUpdatedInfo() {
        viewModelScope.launch(ioDispatcher) {
            _sellerVerificationState.update { it.copy(isSubmitting = true) }

            val state = _sellerVerificationState.value
            val userUid = state.user?.uuid ?: return@launch
            val currentStatus = state.user.businessInfo?.verificationStatus

            var taxDocUrl = state.existingTaxDocumentUrl
            var businessLicenseUrl = state.existingBusinessLicenseUrl
            var identityDocUrl = state.existingIdentityDocumentUrl

            state.newTaxDocumentUri?.let { uri ->
                profileRepository.updateSellerDocument(userUid, DocumentType.TAX_DOCUMENTS, uri)
                    .onSuccess { taxDocUrl = it }
                    .onFailure { handleError(it) }
            }

            state.newBusinessLicenseUri?.let { uri ->
                profileRepository.updateSellerDocument(userUid, DocumentType.BUSINESS_LICENSE, uri)
                    .onSuccess { businessLicenseUrl = it }
                    .onFailure { handleError(it) }
            }

            state.newIdentityDocumentUri?.let { uri ->
                profileRepository.updateSellerDocument(userUid, DocumentType.IDENTITY_DOCUMENTS, uri)
                    .onSuccess { identityDocUrl = it }
                    .onFailure { handleError(it) }
            }

            val updatedBusinessInfo = BusinessInfo(
                businessName = state.businessName,
                businessType = state.businessType,
                businessDescription = state.businessDescription,
                businessAddress = state.businessAddress,
                businessPhone = state.businessPhone,
                businessEmail = state.businessEmail,
                taxId = state.taxId,
                businessLicense = state.businessLicense,
                bankAccountNumber = state.bankAccountNumber,
                routingNumber = state.routingNumber,
                taxDocumentUri = taxDocUrl,
                businessLicenseDocumentUri = businessLicenseUrl,
                identityDocumentUri = identityDocUrl,
                verificationStatus = VerificationStatus.RESUBMITTED,
                previousStatus = currentStatus,
                createdAt = state.user.businessInfo?.createdAt ?: "",
                updatedAt = System.currentTimeMillis().toString(),
            )

            profileRepository.updateSellerBusinessInfo(userUid, updatedBusinessInfo)
                .onSuccess { handleSuccess() }
                .onFailure { handleError(it) }
        }
    }

    private suspend fun handleSuccess() {
        withContext(mainDispatcher) {
            _sellerVerificationState.update {
                it.copy(
                    isSubmitting = false,
                    submissionSuccess = true,
                    showEditDialog = false,
                    newTaxDocumentUri = null,
                    newBusinessLicenseUri = null,
                    newIdentityDocumentUri = null,
                )
            }
        }
    }

    private suspend fun handleError(error: Throwable) {
        withContext(mainDispatcher) {
            _sellerVerificationState.update {
                it.copy(
                    isSubmitting = false,
                    errorMessage = error.message
                )
            }
        }
    }


    private fun identityDocumentSelected(uri: Uri) {
        viewModelScope.launch(mainDispatcher) {
            _sellerVerificationState.update {
                it.copy(newIdentityDocumentUri = uri)
            }
        }
    }

    private fun businessLicenseDocumentSelected(uri: Uri) {
        viewModelScope.launch(mainDispatcher) {
            _sellerVerificationState.update {
                it.copy(newBusinessLicenseUri = uri)
            }
        }
    }

    private fun taxDocumentSelected(uri: Uri) {
        viewModelScope.launch(mainDispatcher) {
            _sellerVerificationState.update {
                it.copy(newTaxDocumentUri = uri)
            }
        }
    }

    // TODO add address Validator
    private fun businessAddressChange(value: String) {
        viewModelScope.launch(mainDispatcher) {
            _sellerVerificationState.update {
                it.copy(
                    businessAddress = value
                )
            }
        }
    }

    private fun routingNumberChange(value: String) {
        viewModelScope.launch(mainDispatcher) {
            _sellerVerificationState.update {
                it.copy(
                    routingNumber = value,
                    routingNumberError = isValidRoutingNumber(value)
                )
            }
        }
    }

    private fun bankAccountNumberChange(value: String) {
        viewModelScope.launch(mainDispatcher) {
            _sellerVerificationState.update {
                it.copy(
                    bankAccountNumber = value,
                    bankAccountNumberError = isValidBankAccount(value)
                )
            }
        }
    }

    private fun businessLicenseChange(value: String) {
        viewModelScope.launch(mainDispatcher) {
            _sellerVerificationState.update {
                it.copy(
                    businessLicense = value
                )
            }
        }
    }

    private fun taxIdChange(value: String) {
        viewModelScope.launch(mainDispatcher) {
            _sellerVerificationState.update {
                it.copy(
                    taxId = value,
                    taxIdError = isValidTaxId(value)
                )
            }
        }
    }

    private fun businessEmailChange(value: String) {
        viewModelScope.launch(mainDispatcher) {

            _sellerVerificationState.update {
                it.copy(
                    businessEmail = value,
                    businessEmailError = isValidEmail(value),
                )
            }


        }
    }

    private fun businessPhoneChange(value: String) {
        viewModelScope.launch(mainDispatcher) {
            _sellerVerificationState.update {
                it.copy(
                    businessPhone = value
                )
            }
        }
    }

    private fun businessDescriptionChange(value: String) {
        viewModelScope.launch(mainDispatcher) {
            _sellerVerificationState.update {
                it.copy(
                    businessDescription = value
                )
            }
        }
    }

    private fun businessTypeChange(value: BusinessType) {
        viewModelScope.launch(mainDispatcher) {
            _sellerVerificationState.update {
                it.copy(
                    businessType = value
                )
            }
        }
    }

    private fun businessNameChange(value: String) {
        viewModelScope.launch(mainDispatcher) {
            _sellerVerificationState.update {
                it.copy(
                    businessName = value,
                    businessNameError = value.isBlank()
                )
            }
        }
    }

    private fun dismissDialog() {
        viewModelScope.launch(mainDispatcher) {
            _sellerVerificationState.update {
                it.copy(
                    showEditDialog = false,
                    showCancelDialog = false,
                    newTaxDocumentUri = null,
                    newBusinessLicenseUri = null,
                    newIdentityDocumentUri = null,
                )
            }

        }
    }

    private fun showCancelDialog() {
        viewModelScope.launch(mainDispatcher) {
            _sellerVerificationState.update {
                it.copy(
                    showCancelDialog = true
                )
            }
        }
    }

    private fun showEditDialog() {
        val businessInfo = _sellerVerificationState.value.user?.businessInfo

        viewModelScope.launch(mainDispatcher) {
            _sellerVerificationState.update {
                it.copy(
                    showEditDialog = true,
                    businessName = businessInfo?.businessName ?: "",
                    businessType = businessInfo?.businessType ?: BusinessType.INDIVIDUAL,
                    businessDescription = businessInfo?.businessDescription ?: "",
                    businessAddress = businessInfo?.businessAddress ?: "",
                    businessPhone = businessInfo?.businessPhone ?: "",
                    businessEmail = businessInfo?.businessEmail ?: "",
                    taxId = businessInfo?.taxId ?: "",
                    businessLicense = businessInfo?.businessLicense ?: "",
                    bankAccountNumber = businessInfo?.bankAccountNumber ?: "",
                    routingNumber = businessInfo?.routingNumber ?: "",
                    existingTaxDocumentUrl = businessInfo?.taxDocumentUri ?: "",
                    existingBusinessLicenseUrl = businessInfo?.businessLicenseDocumentUri ?: "",
                    existingIdentityDocumentUrl = businessInfo?.identityDocumentUri ?: "",
                    newTaxDocumentUri = null,
                    newBusinessLicenseUri = null,
                    newIdentityDocumentUri = null,
                )
            }
        }
    }
}