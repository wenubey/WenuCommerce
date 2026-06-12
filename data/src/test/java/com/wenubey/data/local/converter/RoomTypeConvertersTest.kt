package com.wenubey.data.local.converter

import com.google.common.truth.Truth.assertThat
import com.wenubey.domain.model.Device
import com.wenubey.domain.model.Purchase
import com.wenubey.domain.model.onboard.BusinessInfo
import com.wenubey.domain.model.onboard.BusinessType
import com.wenubey.domain.model.onboard.VerificationStatus
import com.wenubey.domain.model.product.ProductImage
import com.wenubey.domain.model.product.ProductShipping
import com.wenubey.domain.model.product.ProductVariant
import com.wenubey.domain.model.product.ShippingType
import com.wenubey.domain.model.product.Subcategory
import org.junit.Test

/**
 * Pure-JVM tests for the JSON-backed Room type converters. Each pair must
 * round-trip without loss, and malformed JSON must fall back to the safe
 * default (empty list / default value) — never throw at the SQLite boundary.
 */
class RoomTypeConvertersTest {

    private val converters = RoomTypeConverters()

    @Test
    fun `string list round-trips`() {
        val input = listOf("a", "b", "c")
        val encoded = converters.fromStringList(input)
        assertThat(converters.toStringList(encoded)).isEqualTo(input)
    }

    @Test
    fun `string list malformed json decodes as empty list`() {
        assertThat(converters.toStringList("not json")).isEmpty()
        assertThat(converters.toStringList("")).isEmpty()
    }

    @Test
    fun `empty string list round-trips`() {
        val encoded = converters.fromStringList(emptyList())
        assertThat(converters.toStringList(encoded)).isEmpty()
    }

    @Test
    fun `product image list round-trips with all fields preserved`() {
        val input = listOf(
            ProductImage(id = "i-1", downloadUrl = "url", storagePath = "/p", sortOrder = 1),
            ProductImage(id = "i-2", downloadUrl = "url-2", sortOrder = 2),
        )
        val encoded = converters.fromProductImageList(input)
        assertThat(converters.toProductImageList(encoded)).isEqualTo(input)
    }

    @Test
    fun `product image list malformed json decodes as empty list`() {
        assertThat(converters.toProductImageList("garbage")).isEmpty()
    }

    @Test
    fun `product variant list round-trips with nullable priceOverride`() {
        val input = listOf(
            ProductVariant(id = "v-1", label = "Red", priceOverride = 9.99, attributes = mapOf("color" to "red")),
            ProductVariant(id = "v-2", label = "Blue", priceOverride = null),
        )
        val encoded = converters.fromProductVariantList(input)
        val decoded = converters.toProductVariantList(encoded)
        assertThat(decoded).isEqualTo(input)
        assertThat(decoded[0].priceOverride).isEqualTo(9.99)
        assertThat(decoded[1].priceOverride).isNull()
    }

    @Test
    fun `product shipping round-trips and falls back to default on garbage`() {
        val input = ProductShipping(
            shippingType = ShippingType.FREE_SHIPPING,
            shippingCost = 0.0,
            estimatedDaysMin = 1,
            estimatedDaysMax = 3,
            shipsFrom = "TR",
        )
        val encoded = converters.fromProductShipping(input)
        assertThat(converters.toProductShipping(encoded)).isEqualTo(input)
        assertThat(converters.toProductShipping("garbage")).isEqualTo(ProductShipping())
    }

    @Test
    fun `subcategory list round-trips`() {
        val input = listOf(Subcategory("s-1", "Tops"), Subcategory("s-2", "Bottoms"))
        val encoded = converters.fromSubcategoryList(input)
        assertThat(converters.toSubcategoryList(encoded)).isEqualTo(input)
    }

    @Test
    fun `purchase list round-trips`() {
        val input = listOf(
            Purchase(purchaseId = "p-1", productId = "prod-1", quantity = 2, price = 19.99),
        )
        val encoded = converters.fromPurchaseList(input)
        assertThat(converters.toPurchaseList(encoded)).isEqualTo(input)
    }

    @Test
    fun `device list round-trips`() {
        val input = listOf(
            Device(deviceId = "d-1", deviceName = "Pixel", osVersion = "Android 15"),
        )
        val encoded = converters.fromDeviceList(input)
        assertThat(converters.toDeviceList(encoded)).isEqualTo(input)
    }

    @Test
    fun `business info round-trips including nullable verification fields`() {
        val input = BusinessInfo(
            businessName = "Foo Inc",
            businessType = BusinessType.LLC,
            verificationStatus = VerificationStatus.APPROVED,
            previousStatus = VerificationStatus.PENDING,
            verificationDate = "2026-01-01",
            verificationNotes = "ok",
            isVerified = true,
        )
        val encoded = converters.fromBusinessInfo(input)
        assertThat(encoded).isNotNull()
        assertThat(converters.toBusinessInfo(encoded)).isEqualTo(input)
    }

    @Test
    fun `business info null round-trips to null`() {
        assertThat(converters.fromBusinessInfo(null)).isNull()
        assertThat(converters.toBusinessInfo(null)).isNull()
    }

    @Test
    fun `business info malformed json decodes to null not crash`() {
        assertThat(converters.toBusinessInfo("garbage")).isNull()
    }

    @Test
    fun `string map round-trips`() {
        val input = mapOf("color" to "red", "size" to "L")
        val encoded = converters.fromStringMap(input)
        assertThat(converters.toStringMap(encoded)).isEqualTo(input)
    }

    @Test
    fun `string map malformed json decodes as empty map`() {
        assertThat(converters.toStringMap("nope")).isEmpty()
    }
}
