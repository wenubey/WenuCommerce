package com.wenubey.domain.repository

import com.wenubey.domain.model.order.ShippingAddress
import kotlinx.coroutines.flow.Flow

interface AddressRepository {
    fun observeSavedAddresses(userId: String): Flow<List<ShippingAddress>>
    suspend fun saveAddress(userId: String, address: ShippingAddress)
    suspend fun deleteAddress(userId: String, addressId: String)
}
