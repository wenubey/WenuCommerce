package com.wenubey.wenucommerce.testing.fakes

import com.wenubey.domain.model.order.ShippingAddress
import com.wenubey.domain.repository.AddressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeAddressRepository : AddressRepository {

    private val addressesByUser = MutableStateFlow<Map<String, List<ShippingAddress>>>(emptyMap())

    val saveAddressCalls = mutableListOf<Pair<String, ShippingAddress>>()
    val deleteAddressCalls = mutableListOf<Pair<String, String>>()
    var observeFlow: Flow<List<ShippingAddress>>? = null

    fun emit(userId: String, list: List<ShippingAddress>) {
        addressesByUser.value = addressesByUser.value.toMutableMap().apply { put(userId, list) }
    }

    override fun observeSavedAddresses(userId: String): Flow<List<ShippingAddress>> =
        observeFlow ?: addressesByUser.map { it[userId].orEmpty() }

    override suspend fun saveAddress(userId: String, address: ShippingAddress) {
        saveAddressCalls.add(userId to address)
    }

    override suspend fun deleteAddress(userId: String, addressId: String) {
        deleteAddressCalls.add(userId to addressId)
    }
}
