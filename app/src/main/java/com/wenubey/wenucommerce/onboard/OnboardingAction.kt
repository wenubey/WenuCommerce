package com.wenubey.wenucommerce.onboard

import com.wenubey.domain.model.onboard.BusinessType
import com.wenubey.wenucommerce.onboard.util.GenderUiModel
import com.wenubey.wenucommerce.onboard.util.UserRoleUiModel


sealed interface OnboardingAction {
    data class OnPhoneNumberChange(val phoneNumber: String): OnboardingAction
    data class OnNameChange(val name: String): OnboardingAction
    data class OnSurnameChange(val surname: String): OnboardingAction
    data class OnGenderSelected(val gender: GenderUiModel): OnboardingAction
    data class OnDateOfBirthSelected(val dateOfBirthMillis: Long?): OnboardingAction
    data class OnRoleSelected(val role: UserRoleUiModel): OnboardingAction
    data class OnAddressChange(val address: String): OnboardingAction
    data class OnPhotoUrlChange(val photoUrl: String): OnboardingAction
    data object OnOnboardingComplete: OnboardingAction

    // Seller-specific actions
    data class OnBusinessNameChange(val businessName: String): OnboardingAction
    data class OnTaxIdChange(val taxId: String): OnboardingAction
    data class OnBusinessLicenseChange(val businessLicense: String): OnboardingAction
    data class OnBusinessAddressChange(val businessAddress: String): OnboardingAction
    data class OnBusinessPhoneChange(val businessPhone: String): OnboardingAction
    data class OnBusinessEmailChange(val businessEmail: String): OnboardingAction
    data class OnUseRegistrationEmailToggle(val checked: Boolean): OnboardingAction
    data class OnBankAccountNumberChange(val bankAccountNumber: String): OnboardingAction
    data class OnRoutingNumberChange(val routingNumber: String): OnboardingAction
    data class OnBusinessTypeChange(val businessType: BusinessType): OnboardingAction
    data class OnBusinessDescriptionChange(val businessDescription: String): OnboardingAction

    // Document upload actions
    data class OnTaxDocumentUpload(val uri: String): OnboardingAction
    data class OnBusinessLicenseDocumentUpload(val uri: String): OnboardingAction
    data class OnIdentityDocumentUpload(val uri: String): OnboardingAction
}