package com.wenubey.domain.model.product

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProductTest {

    @Test
    fun `default uses USD, DRAFT status, NEW condition, no variants`() {
        val product = Product()
        assertThat(product.currency).isEqualTo("USD")
        assertThat(product.status).isEqualTo(ProductStatus.DRAFT)
        assertThat(product.condition).isEqualTo(ProductCondition.NEW)
        assertThat(product.hasVariants).isFalse()
        assertThat(product.variants).isEmpty()
        assertThat(product.images).isEmpty()
        assertThat(product.tags).isEmpty()
        assertThat(product.searchKeywords).isEmpty()
        assertThat(product.basePrice).isEqualTo(0.0)
        assertThat(product.compareAtPrice).isNull()
        assertThat(product.totalStockQuantity).isEqualTo(0)
        assertThat(product.averageRating).isEqualTo(0.0)
        assertThat(product.reviewCount).isEqualTo(0)
    }

    @Test
    fun `default shipping is PAID with zero cost`() {
        assertThat(Product().shipping.shippingType).isEqualTo(ShippingType.PAID_SHIPPING)
        assertThat(Product().shipping.shippingCost).isEqualTo(0.0)
    }

    @Test
    fun `toMap serializes status and condition by name not ordinal`() {
        val map = Product(
            status = ProductStatus.ACTIVE,
            condition = ProductCondition.LIKE_NEW,
        ).toMap()
        assertThat(map["status"]).isEqualTo("ACTIVE")
        assertThat(map["condition"]).isEqualTo("LIKE_NEW")
    }

    @Test
    fun `toMap preserves null compareAtPrice rather than dropping it`() {
        val map = Product().toMap()
        assertThat(map).containsKey("compareAtPrice")
        assertThat(map["compareAtPrice"]).isNull()
    }

    @Test
    fun `toMap key set covers every persisted property`() {
        val map = Product().toMap()
        assertThat(map.keys).containsExactly(
            "id", "title", "description", "slug",
            "sellerId", "sellerName", "sellerLogoUrl",
            "categoryId", "categoryName", "subcategoryId", "subcategoryName",
            "tags", "tagNames", "searchKeywords",
            "condition",
            "basePrice", "compareAtPrice", "currency",
            "images", "variants", "totalStockQuantity", "hasVariants",
            "shipping",
            "status", "moderationNotes", "suspendedBy", "suspendedAt",
            "averageRating", "reviewCount",
            "stripeProductId",
            "viewCount", "purchaseCount",
            "createdAt", "updatedAt", "publishedAt", "archivedAt",
        )
    }

    @Test
    fun `toMap recursively maps images variants and shipping`() {
        val product = Product(
            images = listOf(
                ProductImage(id = "i-1", downloadUrl = "url-1", sortOrder = 1),
            ),
            variants = listOf(
                ProductVariant(id = "v-1", label = "Red", sku = "R-1"),
            ),
            shipping = ProductShipping(shippingType = ShippingType.FREE_SHIPPING),
        )
        val map = product.toMap()

        @Suppress("UNCHECKED_CAST")
        val images = map["images"] as List<Map<String, Any?>>
        assertThat(images).hasSize(1)
        assertThat(images[0]["id"]).isEqualTo("i-1")
        assertThat(images[0]["downloadUrl"]).isEqualTo("url-1")

        @Suppress("UNCHECKED_CAST")
        val variants = map["variants"] as List<Map<String, Any?>>
        assertThat(variants).hasSize(1)
        assertThat(variants[0]["id"]).isEqualTo("v-1")
        assertThat(variants[0]["label"]).isEqualTo("Red")

        @Suppress("UNCHECKED_CAST")
        val shipping = map["shipping"] as Map<String, Any?>
        assertThat(shipping["shippingType"]).isEqualTo("FREE_SHIPPING")
    }
}
