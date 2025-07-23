package com.wenubey.wenucommerce.onboard

import android.net.Uri
import com.wenubey.wenucommerce.onboard.util.GenderUiModel


sealed interface OnboardingAction {
    data class OnPhoneNumberChange(val phoneNumber: String): OnboardingAction
    data class OnNameChange(val name: String): OnboardingAction
    data class OnSurnameChange(val surname: String): OnboardingAction
    data class OnGenderSelected(val gender: GenderUiModel): OnboardingAction
    data class OnDateOfBirthSelected(val dateOfBirthMillis: Long?): OnboardingAction
    data class OnAddressChange(val address: String): OnboardingAction
    data class OnPhotoUrlChange(val photoUrl: Uri): OnboardingAction
    data object OnOnboardingComplete: OnboardingAction
}