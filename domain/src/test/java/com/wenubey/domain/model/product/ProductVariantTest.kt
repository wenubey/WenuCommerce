package com.wenubey.domain.model.product

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProductVariantTest {

    @Test
    fun `default has empty fields with inStock true and isDefault false`() {
        val variant = ProductVariant()
        assertThat(variant.id).isEmpty()
        assertThat(variant.label).isEmpty()
        assertThat(variant.attributes).isEmpty()
        assertThat(variant.sku).isEmpty()
        assertThat(variant.priceOverride).isNull()
        assertThat(variant.stockQuantity).isEqualTo(0)
        assertThat(variant.inStock).isTrue()
        assertThat(variant.isDefault).isFalse()
        assertThat(variant.stripePriceId).isEmpty()
    }

    @Test
    fun `toMap preserves null priceOverride rather than dropping the key`() {
        val map = ProductVariant().toMap()
        assertThat(map).containsKey("priceOverride")
        assertThat(map["priceOverride"]).isNull()
    }

    @Test
    fun `toMap contains every persisted field`() {
        val variant = ProductVariant(
            id = "v-1",
            label = "Red / L",
            attributes = mapOf("color" to "red", "size" to "L"),
            sku = "SKU-RED-L",
            priceOverride = 29.99,
            stockQuantity = 7,
            inStock = true,
            isDefault = true,
            stripePriceId = "price_123",
            createdAt = "2026-01-01",
            updatedAt = "2026-01-02",
        )
        val map = variant.toMap()
        assertThat(map.keys).containsExactly(
            "id", "label", "attributes", "sku", "priceOverride",
            "stockQuantity", "inStock", "isDefault", "stripePriceId",
            "createdAt", "updatedAt",
        )
        assertThat(map["id"]).isEqualTo("v-1")
        assertThat(map["label"]).isEqualTo("Red / L")
        assertThat(map["attributes"]).isEqualTo(mapOf("color" to "red", "size" to "L"))
        assertThat(map["sku"]).isEqualTo("SKU-RED-L")
        assertThat(map["priceOverride"]).isEqualTo(29.99)
        assertThat(map["stockQuantity"]).isEqualTo(7)
        assertThat(map["inStock"]).isEqualTo(true)
        assertThat(map["isDefault"]).isEqualTo(true)
        assertThat(map["stripePriceId"]).isEqualTo("price_123")
    }
}
