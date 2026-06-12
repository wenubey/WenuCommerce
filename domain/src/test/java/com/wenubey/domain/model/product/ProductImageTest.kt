package com.wenubey.domain.model.product

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProductImageTest {

    @Test
    fun `default has all empty or zero fields`() {
        val image = ProductImage()
        assertThat(image.id).isEmpty()
        assertThat(image.downloadUrl).isEmpty()
        assertThat(image.storagePath).isEmpty()
        assertThat(image.sortOrder).isEqualTo(0)
        assertThat(image.uploadedAt).isEmpty()
    }

    @Test
    fun `toMap contains every persisted field with original values`() {
        val image = ProductImage(
            id = "img-1",
            downloadUrl = "https://cdn.example/x.jpg",
            storagePath = "/products/x.jpg",
            sortOrder = 3,
            uploadedAt = "2026-01-01T00:00:00Z",
        )
        val map = image.toMap()
        assertThat(map.keys).containsExactly(
            "id", "downloadUrl", "storagePath", "sortOrder", "uploadedAt",
        )
        assertThat(map["id"]).isEqualTo("img-1")
        assertThat(map["downloadUrl"]).isEqualTo("https://cdn.example/x.jpg")
        assertThat(map["storagePath"]).isEqualTo("/products/x.jpg")
        assertThat(map["sortOrder"]).isEqualTo(3)
        assertThat(map["uploadedAt"]).isEqualTo("2026-01-01T00:00:00Z")
    }
}
