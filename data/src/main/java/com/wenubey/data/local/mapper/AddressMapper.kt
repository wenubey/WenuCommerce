package com.wenubey.data.local.mapper

import com.wenubey.data.local.entity.AddressEntity
import com.wenubey.domain.model.order.ShippingAddress

fun AddressEntity.toDomain(): ShippingAddress = ShippingAddress(
    id = addressId,
    fullName = fullName,
    line1 = line1,
    line2 = line2,
    city = city,
    state = state,
    postalCode = postalCode,
    country = country
)

fun ShippingAddress.toEntity(userId: String): AddressEntity = AddressEntity(
    userId = userId,
    addressId = id,
    fullName = fullName,
    line1 = line1,
    line2 = line2,
    city = city,
    state = state,
    postalCode = postalCode,
    country = country
)
