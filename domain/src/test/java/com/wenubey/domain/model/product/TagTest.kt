package com.wenubey.domain.model.product

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TagTest {

    @Test
    fun `default has empty fields`() {
        val tag = Tag()
        assertThat(tag.id).isEmpty()
        assertThat(tag.name).isEmpty()
        assertThat(tag.displayName).isEmpty()
        assertThat(tag.createdBy).isEmpty()
        assertThat(tag.createdAt).isEmpty()
    }

    @Test
    fun `toMap contains every persisted field`() {
        val tag = Tag(
            id = "t-1",
            name = "summer",
            displayName = "Summer",
            createdBy = "admin-1",
            createdAt = "2026-01-01",
        )
        val map = tag.toMap()
        assertThat(map.keys).containsExactly(
            "id", "name", "displayName", "createdBy", "createdAt",
        )
        assertThat(map["id"]).isEqualTo("t-1")
        assertThat(map["name"]).isEqualTo("summer")
        assertThat(map["displayName"]).isEqualTo("Summer")
    }
}
