package com.wenubey.domain.model.onboard

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BusinessTypeTest {

    @Test
    fun `enum names are stable`() {
        assertThat(BusinessType.entries.map { it.name }).containsExactly(
            "INDIVIDUAL",
            "LLC",
            "CORPORATION",
            "PARTNERSHIP",
            "NON_PROFIT",
        ).inOrder()
    }

    @Test
    fun `valueOf returns the matching constant for every name`() {
        BusinessType.entries.forEach { type ->
            assertThat(BusinessType.valueOf(type.name)).isEqualTo(type)
        }
    }
}
