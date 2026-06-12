package com.wenubey.wenucommerce.testing.fakes

import android.net.Uri
import com.wenubey.domain.model.Gender
import com.wenubey.domain.model.onboard.BusinessInfo
import com.wenubey.domain.model.onboard.BusinessType
import com.wenubey.domain.model.user.User
import com.wenubey.domain.model.user.UserRole
import com.wenubey.domain.repository.ProfileRepository
import com.wenubey.domain.util.DocumentType

class FakeProfileRepository : ProfileRepository {

    data class OnboardingCall(
        val name: String,
        val role: UserRole,
        val businessName: String,
    )

    val onboardingCalls = mutableListOf<OnboardingCall>()
    val deleteSellerDataCalls = mutableListOf<String>()
    val updateSellerDocumentCalls = mutableListOf<Triple<String, DocumentType, Uri>>()
    val updateSellerBusinessInfoCalls = mutableListOf<Pair<String, BusinessInfo>>()
    val cancelSellerApplicationCalls = mutableListOf<String>()

    var onboardingResult: Result<User> = Result.success(User())
    var deleteSellerDataResult: Result<Unit> = Result.success(Unit)
    var updateSellerDocumentResult: (DocumentType) -> Result<String> = {
        Result.success("https://fake/${it.name}.jpg")
    }
    var updateSellerBusinessInfoResult: Result<Unit> = Result.success(Unit)
    var cancelSellerApplicationResult: Result<Unit> = Result.success(Unit)

    override suspend fun onboarding(
        name: String,
        surname: String,
        phoneNumber: String,
        dateOfBirth: String,
        address: String,
        gender: Gender,
        photoUrl: Uri,
        role: UserRole,
        businessName: String,
        taxId: String,
        businessLicense: String,
        businessAddress: String,
        businessPhone: String,
        businessEmail: String,
        bankAccountNumber: String,
        routingNumber: String,
        businessType: BusinessType,
        businessDescription: String,
        taxDocumentUri: Uri,
        businessLicenseDocumentUri: Uri,
        identityDocumentUri: Uri,
    ): Result<User> {
        onboardingCalls.add(OnboardingCall(name = name, role = role, businessName = businessName))
        return onboardingResult
    }

    override suspend fun deleteSellerData(userUid: String): Result<Unit> {
        deleteSellerDataCalls.add(userUid)
        return deleteSellerDataResult
    }

    override suspend fun updateSellerDocument(
        userUid: String,
        documentType: DocumentType,
        newDocumentUri: Uri,
    ): Result<String> {
        updateSellerDocumentCalls.add(Triple(userUid, documentType, newDocumentUri))
        return updateSellerDocumentResult(documentType)
    }

    override suspend fun updateSellerBusinessInfo(
        userUid: String,
        businessInfo: BusinessInfo,
    ): Result<Unit> {
        updateSellerBusinessInfoCalls.add(userUid to businessInfo)
        return updateSellerBusinessInfoResult
    }

    override suspend fun cancelSellerApplication(userUid: String): Result<Unit> {
        cancelSellerApplicationCalls.add(userUid)
        return cancelSellerApplicationResult
    }
}
