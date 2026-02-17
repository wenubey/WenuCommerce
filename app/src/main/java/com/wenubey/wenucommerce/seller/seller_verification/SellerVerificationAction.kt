package com.wenubey.wenucommerce.seller.seller_verification

import android.net.Uri
import com.wenubey.domain.model.onboard.BusinessType

sealed interface SellerVerificationAction {

    data object ShowEditDialog : SellerVerificationAction
    data object ShowCancelDialog : SellerVerificationAction
    data object DismissDialog : SellerVerificationAction

    data class OnBusinessNameChange(val value: String) : SellerVerificationAction
    data class OnBusinessTypeChange(val value: BusinessType) : SellerVerificationAction
    data class OnBusinessDescriptionChange(val value: String) : SellerVerificationAction
    data class OnBusinessAddressChange(val value: String) : SellerVerificationAction
    data class OnBusinessPhoneChange(val value: String) : SellerVerificationAction
    data class OnBusinessEmailChange(val value: String) : SellerVerificationAction
    data class OnTaxIdChange(val value: String) : SellerVerificationAction
    data class OnBusinessLicenseChange(val value: String) : SellerVerificationAction
    data class OnBankAccountNumberChange(val value: String) : SellerVerificationAction
    data class OnRoutingNumberChange(val value: String) : SellerVerificationAction

    data class OnTaxDocumentSelected(val uri: Uri) : SellerVerificationAction
    data class OnBusinessLicenseDocumentSelected(val uri: Uri) : SellerVerificationAction
    data class OnIdentityDocumentSelected(val uri: Uri) : SellerVerificationAction

    data object SubmitUpdatedInfo : SellerVerificationAction
    data object ConfirmCancelApplication : SellerVerificationAction
}