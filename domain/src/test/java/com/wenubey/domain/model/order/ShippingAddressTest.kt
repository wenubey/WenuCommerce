package com.wenubey.domain.model.order

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShippingAddressTest {

    @Test
    fun `default address has all empty fields`() {
        val address = ShippingAddress.default()
        assertThat(address.id).isEmpty()
        assertThat(address.fullName).isEmpty()
        assertThat(address.line1).isEmpty()
        assertThat(address.line2).isEmpty()
        assertThat(address.city).isEmpty()
        assertThat(address.state).isEmpty()
        assertThat(address.postalCode).isEmpty()
        assertThat(address.country).isEmpty()
    }

    @Test
    fun `label combines line1 and city`() {
        val address = ShippingAddress(
            line1 = "123 Main St",
            city = "Istanbul",
        )
        assertThat(address.label).isEqualTo("123 Main St, Istanbul")
    }

    @Test
    fun `label still renders when line1 or city is empty`() {
        assertThat(ShippingAddress(line1 = "", city = "Ankara").label).isEqualTo(", Ankara")
        assertThat(ShippingAddress(line1 = "12 Oak", city = "").label).isEqualTo("12 Oak, ")
    }

    @Test
    fun `toMap contains every persisted field`() {
        val address = ShippingAddress(
            id = "addr-1",
            fullName = "Alice",
            line1 = "1 St",
            line2 = "Apt 2",
            city = "Izmir",
            state = "IZ",
            postalCode = "35000",
            country = "TR",
        )
        val map = address.toMap()
        assertThat(map.keys).containsExactly(
            "id", "fullName", "line1", "line2", "city", "state", "postalCode", "country",
        )
        assertThat(map["id"]).isEqualTo("addr-1")
        assertThat(map["fullName"]).isEqualTo("Alice")
        assertThat(map["line1"]).isEqualTo("1 St")
        assertThat(map["line2"]).isEqualTo("Apt 2")
        assertThat(map["city"]).isEqualTo("Izmir")
        assertThat(map["state"]).isEqualTo("IZ")
        assertThat(map["postalCode"]).isEqualTo("35000")
        assertThat(map["country"]).isEqualTo("TR")
    }

    @Test
    fun `toMap preserves empty string values rather than dropping keys`() {
        val map = ShippingAddress.default().toMap()
        assertThat(map.keys).hasSize(8)
        map.values.forEach { assertThat(it).isEqualTo("") }
    }
}
