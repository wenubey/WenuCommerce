package com.wenubey.domain.model.product

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CategoryTest {

    @Test
    fun `default is active with empty fields and no subcategories`() {
        val category = Category()
        assertThat(category.id).isEmpty()
        assertThat(category.name).isEmpty()
        assertThat(category.description).isEmpty()
        assertThat(category.imageUrl).isEmpty()
        assertThat(category.subcategories).isEmpty()
        assertThat(category.isActive).isTrue()
    }

    @Test
    fun `toMap contains every persisted field`() {
        val category = Category(
            id = "c-1",
            name = "Clothing",
            description = "All clothing",
            imageUrl = "https://cdn/c.jpg",
            isActive = false,
            createdBy = "admin-1",
            createdAt = "2026-01-01",
            updatedAt = "2026-01-02",
        )
        val map = category.toMap()
        assertThat(map.keys).containsExactly(
            "id", "name", "description", "imageUrl",
            "subcategories", "isActive",
            "createdBy", "createdAt", "updatedAt",
        )
        assertThat(map["id"]).isEqualTo("c-1")
        assertThat(map["name"]).isEqualTo("Clothing")
        assertThat(map["isActive"]).isEqualTo(false)
        assertThat(map["createdBy"]).isEqualTo("admin-1")
    }

    @Test
    fun `toMap recursively maps subcategories`() {
        val category = Category(
            subcategories = listOf(
                Subcategory(id = "s-1", name = "Tops"),
                Subcategory(id = "s-2", name = "Bottoms"),
            ),
        )

        @Suppress("UNCHECKED_CAST")
        val subs = category.toMap()["subcategories"] as List<Map<String, Any>>
        assertThat(subs).hasSize(2)
        assertThat(subs[0]["id"]).isEqualTo("s-1")
        assertThat(subs[0]["name"]).isEqualTo("Tops")
        assertThat(subs[1]["id"]).isEqualTo("s-2")
        assertThat(subs[1]["name"]).isEqualTo("Bottoms")
    }
}
