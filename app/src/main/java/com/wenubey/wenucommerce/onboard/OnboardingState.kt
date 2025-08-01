package com.wenubey.wenucommerce.onboard

import android.net.Uri
import com.wenubey.domain.model.onboard.BusinessType
import com.wenubey.wenucommerce.onboard.util.GenderUiModel
import com.wenubey.wenucommerce.onboard.util.UserRoleUiModel

data class OnboardingState(
    val phoneNumber: String = "",
    val verificationCode: String = "",
    val name: String = "",
    val surname: String = "",
    val gender: GenderUiModel = GenderUiModel.default(),
    val address: String = "",
    val dateOfBirth: String? = null,
    val errorMessage: String? = null,
    val isNextButtonEnabled: Boolean = false,
    val phoneNumberError: Boolean = true,
    val nameError: Boolean = true,
    val surnameError: Boolean = true,
    val photoUrl: String = "",
    val role: UserRoleUiModel = UserRoleUiModel.default(),

    // Seller-specific fields
    val businessName: String = "",
    val businessNameError: Boolean = false,
    val taxId: String = "",
    val taxIdError: Boolean = false,
    val businessLicense: String = "",
    val businessLicenseError: Boolean = false,
    val businessAddress: String = "",
    val businessAddressError: Boolean = false,
    val businessPhone: String = "",
    val businessPhoneError: Boolean = false,
    val businessEmail: String = "",
    val businessEmailError: Boolean = false,
    val bankAccountNumber: String = "",
    val bankAccountNumberError: Boolean = false,
    val routingNumber: String = "",
    val routingNumberError: Boolean = false,
    val businessType: BusinessType = BusinessType.INDIVIDUAL,
    val businessDescription: String = "",
    val businessDescriptionError: Boolean = false,

    // Document uploads for sellers
    val taxDocumentUri: Uri = Uri.EMPTY,
    val businessLicenseDocumentUri: Uri = Uri.EMPTY,
    val identityDocumentUri: Uri = Uri.EMPTY,
    )
