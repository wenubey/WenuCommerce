package com.wenubey.domain.model

import android.net.Uri

data class User(
    val uuid: String?,
    val role: String,
    val name: String,
    val surname: String,
    val phoneNumber: String,
    val dateOfBirth: String,
    val gender: Gender,
    val email: String,
    val address: String,
    val isEmailVerified: Boolean,
    val isPhoneNumberVerified: Boolean,
    val profilePhotoUri: Uri,
    val purchaseHistory: List<Purchase>,
    val createdAt: String,
    val updatedAt: String,
    val signedAt: String,
    val signedDevices: List<Device>,
)
