package com.wenubey.domain.model.product

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SubcategoryTest {

    @Test
    fun `default has empty id and name`() {
        val sub = Subcategory()
        assertThat(sub.id).isEmpty()
        assertThat(sub.name).isEmpty()
    }

    @Test
    fun `toMap contains id and name only`() {
        val map = Subcategory(id = "s-1", name = "Tops").toMap()
        assertThat(map.keys).containsExactly("id", "name")
        assertThat(map["id"]).isEqualTo("s-1")
        assertThat(map["name"]).isEqualTo("Tops")
    }
}
