package com.wenubey.wenucommerce.seller.seller_verification

import android.net.Uri
import com.wenubey.domain.model.onboard.BusinessType
import com.wenubey.domain.model.user.User

data class SellerVerificationState(
    val user: User? = null,
    val errorMessage: String? = null,
    val isLoading: Boolean = true,

    // Dialog states
    val showEditDialog: Boolean = false,
    val showCancelDialog: Boolean = false,

    // Editable fields (initialized from user's businessInfo)
    val businessName: String = "",
    val businessNameError: Boolean = false,
    val businessType: BusinessType = BusinessType.INDIVIDUAL,
    val businessDescription: String = "",
    val businessAddress: String = "",
    val businessAddressError: Boolean = false,
    val businessPhone: String = "",
    val businessPhoneError: Boolean = false,
    val businessEmail: String = "",
    val businessEmailError: Boolean = false,
    val taxId: String = "",
    val taxIdError: Boolean = false,
    val businessLicense: String = "",
    val bankAccountNumber: String = "",
    val bankAccountNumberError: Boolean = false,
    val routingNumber: String = "",
    val routingNumberError: Boolean = false,

    val existingTaxDocumentUrl: String = "",
    val existingBusinessLicenseUrl: String = "",
    val existingIdentityDocumentUrl: String = "",

    val newTaxDocumentUri: Uri? = null,
    val newBusinessLicenseUri: Uri? = null,
    val newIdentityDocumentUri: Uri? = null,

    // Submission state
    val isSubmitting: Boolean = false,
    val submissionSuccess: Boolean = false,
)
