package com.wenubey.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,

    // Enum stored as name string
    val role: String = "CUSTOMER",
    val name: String = "",
    val surname: String = "",
    val phoneNumber: String = "",
    val dateOfBirth: String = "",
    val gender: String = "MALE",
    val email: String = "",
    val address: String = "",
    val isEmailVerified: Boolean = false,
    val isPhoneNumberVerified: Boolean = false,
    val profilePhotoUri: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val signedAt: String = "",

    // JSON-serialized nested types
    val purchaseHistoryJson: String = "[]",
    val signedDevicesJson: String = "[]",
    val businessInfoJson: String? = null,
    val productsJson: String = "[]",
)
