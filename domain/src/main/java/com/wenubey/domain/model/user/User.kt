package com.wenubey.domain.model.user

import com.wenubey.domain.model.Device
import com.wenubey.domain.model.Gender
import com.wenubey.domain.model.Purchase
import com.wenubey.domain.model.onboard.BusinessInfo
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val uuid: String? = null,
    val role: UserRole = UserRole.CUSTOMER,
    val name: String = "",
    val surname: String = "",
    val phoneNumber: String = "",
    val dateOfBirth: String = "",
    val gender: Gender = Gender.MALE,
    val email: String = "",
    val address: String = "",
    val isEmailVerified: Boolean = false,
    val isPhoneNumberVerified: Boolean = false,
    val profilePhotoUri: String = "",
    val purchaseHistory: List<Purchase> = listOf(),
    val createdAt: String = "",
    val updatedAt: String = "",
    val signedAt: String = "",
    val signedDevices: List<Device> = listOf(),
    val businessInfo: BusinessInfo? = null,
) {
    companion object {
        fun default(): User = User(
            uuid = null,
            role = UserRole.CUSTOMER,
            name = "",
            surname = "",
            phoneNumber = "",
            dateOfBirth = "",
            gender = Gender.MALE,
            email = "",
            address = "",
            isEmailVerified = false,
            isPhoneNumberVerified = false,
            profilePhotoUri = "",
            purchaseHistory = emptyList(),
            createdAt = "",
            updatedAt = "",
            signedAt = "",
            signedDevices = emptyList(),
            businessInfo = BusinessInfo()
        )
    }
}
