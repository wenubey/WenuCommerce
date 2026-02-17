package com.wenubey.domain.repository

import android.net.Uri
import com.wenubey.domain.model.Gender
import com.wenubey.domain.model.onboard.BusinessInfo
import com.wenubey.domain.model.onboard.BusinessType
import com.wenubey.domain.model.user.User
import com.wenubey.domain.model.user.UserRole
import com.wenubey.domain.util.DocumentType

interface ProfileRepository {

    suspend fun onboarding(
        name: String,
        surname: String,
        phoneNumber: String,
        dateOfBirth: String,
        address: String,
        gender: Gender,
        photoUrl: Uri,
        role: UserRole,
        // Seller-specific parameters
        businessName: String = "",
        taxId: String = "",
        businessLicense: String = "",
        businessAddress: String = "",
        businessPhone: String = "",
        businessEmail: String = "",
        bankAccountNumber: String = "",
        routingNumber: String = "",
        businessType: BusinessType = BusinessType.INDIVIDUAL,
        businessDescription: String = "",
        taxDocumentUri: Uri = Uri.EMPTY,
        businessLicenseDocumentUri: Uri = Uri.EMPTY,
        identityDocumentUri: Uri = Uri.EMPTY
    ): Result<User>

    suspend fun deleteSellerData(userUid: String): Result<Unit>

    suspend fun updateSellerDocument(userUid: String, documentType: DocumentType, newDocumentUri: Uri): Result<String>

    suspend fun updateSellerBusinessInfo(userUid: String, businessInfo: BusinessInfo): Result<Unit>

    suspend fun cancelSellerApplication(userUid: String): Result<Unit>
}