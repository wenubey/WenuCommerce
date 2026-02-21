package com.wenubey.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "addresses",
    primaryKeys = ["userId", "addressId"]
)
data class AddressEntity(
    val userId: String,
    val addressId: String,
    val fullName: String = "",
    val line1: String = "",
    val line2: String = "",
    val city: String = "",
    val state: String = "",
    val postalCode: String = "",
    val country: String = ""
)
