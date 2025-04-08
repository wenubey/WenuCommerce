package com.wenubey.wenucommerce.onboard

import android.net.Uri
import com.wenubey.wenucommerce.onboard.util.GenderUiModel

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
    val photoUrl: Uri = Uri.EMPTY,
)
