package com.wenubey.domain.model.product

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProductConditionTest {

    @Test
    fun `enum names are stable`() {
        assertThat(ProductCondition.entries.map { it.name }).containsExactly(
            "NEW",
            "LIKE_NEW",
            "GOOD",
            "FAIR",
            "POOR",
        ).inOrder()
    }

    @Test
    fun `valueOf returns the matching constant for every name`() {
        ProductCondition.entries.forEach { condition ->
            assertThat(ProductCondition.valueOf(condition.name)).isEqualTo(condition)
        }
    }
}
