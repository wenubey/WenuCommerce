package com.wenubey.data.local.mapper

import com.wenubey.data.local.entity.UserEntity
import com.wenubey.domain.model.Device
import com.wenubey.domain.model.Gender
import com.wenubey.domain.model.Purchase
import com.wenubey.domain.model.onboard.BusinessInfo
import com.wenubey.domain.model.user.User
import com.wenubey.domain.model.user.UserRole
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

fun UserEntity.toDomain(): User = User(
    uuid = id,
    role = runCatching { UserRole.valueOf(role) }.getOrElse { UserRole.CUSTOMER },
    name = name,
    surname = surname,
    phoneNumber = phoneNumber,
    dateOfBirth = dateOfBirth,
    gender = runCatching { Gender.valueOf(gender) }.getOrElse { Gender.NOT_SPECIFIED },
    email = email,
    address = address,
    isEmailVerified = isEmailVerified,
    isPhoneNumberVerified = isPhoneNumberVerified,
    profilePhotoUri = profilePhotoUri,
    createdAt = createdAt,
    updatedAt = updatedAt,
    signedAt = signedAt,
    purchaseHistory = runCatching { json.decodeFromString<List<Purchase>>(purchaseHistoryJson) }.getOrElse { emptyList() },
    signedDevices = runCatching { json.decodeFromString<List<Device>>(signedDevicesJson) }.getOrElse { emptyList() },
    businessInfo = businessInfoJson?.let {
        runCatching { json.decodeFromString<BusinessInfo>(it) }.getOrNull()
    },
    products = runCatching { json.decodeFromString<List<String>>(productsJson) }.getOrElse { emptyList() },
)

fun User.toEntity(): UserEntity = UserEntity(
    id = uuid ?: "",
    role = role.name,
    name = name,
    surname = surname,
    phoneNumber = phoneNumber,
    dateOfBirth = dateOfBirth,
    gender = gender.name,
    email = email,
    address = address,
    isEmailVerified = isEmailVerified,
    isPhoneNumberVerified = isPhoneNumberVerified,
    profilePhotoUri = profilePhotoUri,
    createdAt = createdAt,
    updatedAt = updatedAt,
    signedAt = signedAt,
    purchaseHistoryJson = json.encodeToString(purchaseHistory),
    signedDevicesJson = json.encodeToString(signedDevices),
    businessInfoJson = businessInfo?.let { json.encodeToString(it) },
    productsJson = json.encodeToString(products),
)
