package com.wenubey.domain.model.order

import kotlinx.serialization.Serializable

@Serializable
data class ShippingAddress(
    val id: String = "",
    val fullName: String = "",
    val line1: String = "",
    val line2: String = "",
    val city: String = "",
    val state: String = "",
    val postalCode: String = "",
    val country: String = ""
) {
    val label: String get() = "$line1, $city"

    fun toMap(): Map<String, Any> = buildMap {
        put("id", id)
        put("fullName", fullName)
        put("line1", line1)
        put("line2", line2)
        put("city", city)
        put("state", state)
        put("postalCode", postalCode)
        put("country", country)
    }

    companion object {
        fun default() = ShippingAddress()
    }
}
