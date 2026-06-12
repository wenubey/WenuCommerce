package com.wenubey.domain.model.product

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProductStatusTest {

    @Test
    fun `enum names are stable`() {
        assertThat(ProductStatus.entries.map { it.name }).containsExactly(
            "DRAFT",
            "PENDING_REVIEW",
            "ACTIVE",
            "SUSPENDED",
            "ARCHIVED",
        ).inOrder()
    }

    @Test
    fun `valueOf returns the matching constant for every name`() {
        ProductStatus.entries.forEach { status ->
            assertThat(ProductStatus.valueOf(status.name)).isEqualTo(status)
        }
    }
}
