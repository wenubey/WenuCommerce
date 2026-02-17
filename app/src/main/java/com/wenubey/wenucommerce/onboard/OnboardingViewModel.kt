package com.wenubey.wenucommerce.onboard

import android.net.Uri
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.ProfileRepository
import com.wenubey.domain.model.onboard.BusinessType
import com.wenubey.wenucommerce.core.validators.isValidBankAccount
import com.wenubey.wenucommerce.core.validators.isValidEmail
import com.wenubey.wenucommerce.core.validators.isValidPhoneNumber
import com.wenubey.wenucommerce.core.validators.isValidRoutingNumber
import com.wenubey.wenucommerce.core.validators.isValidTaxId
import com.wenubey.wenucommerce.onboard.util.GenderUiModel
import com.wenubey.wenucommerce.onboard.util.UserRoleUiModel
import com.wenubey.wenucommerce.onboard.util.convertMillisToDate
import com.wenubey.wenucommerce.onboard.util.toDomainModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnboardingViewModel(
    private val profileRepository: ProfileRepository,
    dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val ioDispatcher = dispatcherProvider.io()
    private val mainDispatcher = dispatcherProvider.main()

    private val _state = MutableStateFlow(OnboardingState())
    val state = _state.asStateFlow()

    fun onAction(action: OnboardingAction) {
        when (action) {
            is OnboardingAction.OnDateOfBirthSelected -> dateOfBirthSelect(action.dateOfBirthMillis)
            is OnboardingAction.OnGenderSelected -> genderSelect(action.gender)
            is OnboardingAction.OnRoleSelected -> roleSelect(action.role)
            is OnboardingAction.OnNameChange -> nameChange(action.name)
            is OnboardingAction.OnPhoneNumberChange -> phoneNumberChange(action.phoneNumber)
            is OnboardingAction.OnSurnameChange -> surnameChanged(action.surname)
            is OnboardingAction.OnAddressChange -> addressChange(action.address)
            is OnboardingAction.OnOnboardingComplete -> onBoardingComplete()
            is OnboardingAction.OnPhotoUrlChange -> photoUrlChange(action.photoUrl)

            // Seller-specific actions
            is OnboardingAction.OnBusinessNameChange -> businessNameChange(action.businessName)
            is OnboardingAction.OnTaxIdChange -> taxIdChange(action.taxId)
            is OnboardingAction.OnBusinessLicenseChange -> businessLicenseChange(action.businessLicense)
            is OnboardingAction.OnBusinessAddressChange -> businessAddressChange(action.businessAddress)
            is OnboardingAction.OnBusinessPhoneChange -> businessPhoneChange(action.businessPhone)
            is OnboardingAction.OnBusinessEmailChange -> businessEmailChange(action.businessEmail)
            is OnboardingAction.OnBankAccountNumberChange -> bankAccountNumberChange(action.bankAccountNumber)
            is OnboardingAction.OnRoutingNumberChange -> routingNumberChange(action.routingNumber)
            is OnboardingAction.OnBusinessTypeChange -> businessTypeChange(action.businessType)
            is OnboardingAction.OnBusinessDescriptionChange -> businessDescriptionChange(action.businessDescription)
            is OnboardingAction.OnTaxDocumentUpload -> taxDocumentUpload(action.uri)
            is OnboardingAction.OnBusinessLicenseDocumentUpload -> businessLicenseDocumentUpload(action.uri)
            is OnboardingAction.OnIdentityDocumentUpload -> identityDocumentUpload(action.uri)
        }
        validateForm()
    }

    private fun photoUrlChange(photoUrl: String) = viewModelScope.launch(mainDispatcher) {
        _state.update { it.copy(photoUrl = photoUrl) }
    }

    private fun addressChange(address: String) = viewModelScope.launch(mainDispatcher) {
        _state.update { it.copy(address = address) }
    }

    private fun dateOfBirthSelect(millis: Long?) = viewModelScope.launch(mainDispatcher) {
        _state.update { it.copy(dateOfBirth = millis?.convertMillisToDate()) }
    }

    private fun genderSelect(gender: GenderUiModel) = viewModelScope.launch(mainDispatcher) {
        _state.update { it.copy(gender = gender) }
    }

    private fun roleSelect(role: UserRoleUiModel) = viewModelScope.launch(mainDispatcher) {
        _state.update { it.copy(role = role) }
    }

    private fun nameChange(name: String) = viewModelScope.launch(mainDispatcher) {
        _state.update {
            it.copy(
                name = name,
                nameError = name.isBlank()
            )
        }
    }

    private fun phoneNumberChange(phoneNumber: String) = viewModelScope.launch(mainDispatcher) {
        _state.update {
            it.copy(
                phoneNumber = phoneNumber,
                phoneNumberError = phoneNumber.isBlank()
            )
        }
    }

    private fun surnameChanged(surname: String) = viewModelScope.launch(mainDispatcher) {
        _state.update {
            it.copy(
                surname = surname,
                surnameError = surname.isBlank(),
            )
        }
    }

    // Seller-specific field changes
    private fun businessNameChange(businessName: String) = viewModelScope.launch(mainDispatcher) {
        _state.update {
            it.copy(
                businessName = businessName,
                businessNameError = businessName.isBlank()
            )
        }
    }

    private fun taxIdChange(taxId: String) = viewModelScope.launch(mainDispatcher) {
        _state.update {
            it.copy(
                taxId = taxId,
                taxIdError = taxId.isBlank() || !isValidTaxId(taxId)
            )
        }
    }

    private fun businessLicenseChange(businessLicense: String) = viewModelScope.launch(mainDispatcher) {
        _state.update {
            it.copy(
                businessLicense = businessLicense,
                businessLicenseError = false // Optional field
            )
        }
    }

    private fun businessAddressChange(businessAddress: String) = viewModelScope.launch(mainDispatcher) {
        _state.update {
            it.copy(
                businessAddress = businessAddress,
                businessAddressError = businessAddress.isBlank()
            )
        }
    }

    private fun businessPhoneChange(businessPhone: String) = viewModelScope.launch(mainDispatcher) {
        _state.update {
            it.copy(
                businessPhone = businessPhone,
                businessPhoneError = businessPhone.isBlank() || !isValidPhoneNumber(businessPhone)
            )
        }
    }

    private fun businessEmailChange(businessEmail: String) = viewModelScope.launch(mainDispatcher) {
        _state.update {
            it.copy(
                businessEmail = businessEmail,
                businessEmailError = businessEmail.isBlank() || !isValidEmail(businessEmail)
            )
        }
    }

    private fun bankAccountNumberChange(bankAccountNumber: String) = viewModelScope.launch(mainDispatcher) {
        _state.update {
            it.copy(
                bankAccountNumber = bankAccountNumber,
                bankAccountNumberError = bankAccountNumber.isBlank() || !isValidBankAccount(bankAccountNumber)
            )
        }
    }

    private fun routingNumberChange(routingNumber: String) = viewModelScope.launch(mainDispatcher) {
        _state.update {
            it.copy(
                routingNumber = routingNumber,
                routingNumberError = routingNumber.isBlank() || !isValidRoutingNumber(routingNumber)
            )
        }
    }

    private fun businessTypeChange(businessType: BusinessType) = viewModelScope.launch(mainDispatcher) {
        _state.update { it.copy(businessType = businessType) }
    }

    private fun businessDescriptionChange(businessDescription: String) = viewModelScope.launch(mainDispatcher) {
        _state.update {
            it.copy(
                businessDescription = businessDescription,
                businessDescriptionError = false // Optional field
            )
        }
    }

    // Document upload actions
    private fun taxDocumentUpload(uri: String) = viewModelScope.launch(mainDispatcher) {
        _state.update { it.copy(taxDocumentUri = Uri.parse(uri)) }
    }

    private fun businessLicenseDocumentUpload(uri: String) = viewModelScope.launch(mainDispatcher) {
        _state.update { it.copy(businessLicenseDocumentUri = Uri.parse(uri)) }
    }

    private fun identityDocumentUpload(uri: String) = viewModelScope.launch(mainDispatcher) {
        _state.update { it.copy(identityDocumentUri = Uri.parse(uri)) }
    }

    private fun onBoardingComplete() = viewModelScope.launch(ioDispatcher) {
        val result = profileRepository.onboarding(
            name = _state.value.name,
            surname = _state.value.surname,
            phoneNumber = _state.value.phoneNumber,
            dateOfBirth = _state.value.dateOfBirth ?: "",
            gender = _state.value.gender.toDomainModel(),
            address = _state.value.address,
            photoUrl = Uri.parse(_state.value.photoUrl),
            role = _state.value.role.toDomainModel(),
            // Seller-specific data
            businessName = _state.value.businessName,
            taxId = _state.value.taxId,
            businessLicense = _state.value.businessLicense,
            businessAddress = _state.value.businessAddress,
            businessPhone = _state.value.businessPhone,
            businessEmail = _state.value.businessEmail,
            bankAccountNumber = _state.value.bankAccountNumber,
            routingNumber = _state.value.routingNumber,
            businessType = _state.value.businessType,
            businessDescription = _state.value.businessDescription,
            taxDocumentUri = _state.value.taxDocumentUri,
            businessLicenseDocumentUri = _state.value.businessLicenseDocumentUri,
            identityDocumentUri = _state.value.identityDocumentUri
        )
        withContext(mainDispatcher) {
            result.fold(
                onSuccess = { user ->
                    _state.update {
                        it.copy(completedUser = user)
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(errorMessage = error.message)
                    }
                }
            )

        }
    }

    private fun validateForm() {
        val currentState = _state.value
        val isSeller = currentState.role.name == "Seller"

        // Basic validation for all users
        val basicFormValid = currentState.name.isNotBlank() &&
                currentState.surname.isNotBlank() &&
                currentState.phoneNumber.isNotBlank()

        // Additional validation for sellers
        val sellerFormValid = if (!isSeller) {
            true // If not a seller, seller validation passes
        } else {
            currentState.businessName.isNotBlank() &&
                    currentState.taxId.isNotBlank() &&
                    currentState.businessAddress.isNotBlank() &&
                    currentState.businessPhone.isNotBlank() &&
                    currentState.businessEmail.isNotBlank() &&
                    currentState.bankAccountNumber.isNotBlank() &&
                    currentState.routingNumber.isNotBlank() &&
                    currentState.taxDocumentUri != Uri.EMPTY &&
                    currentState.identityDocumentUri != Uri.EMPTY &&
                    !currentState.businessNameError &&
                    !currentState.taxIdError &&
                    !currentState.businessAddressError &&
                    !currentState.businessPhoneError &&
                    !currentState.businessEmailError &&
                    !currentState.bankAccountNumberError &&
                    !currentState.routingNumberError
        }

        _state.update {
            it.copy(isNextButtonEnabled = basicFormValid && sellerFormValid)
        }
    }

}