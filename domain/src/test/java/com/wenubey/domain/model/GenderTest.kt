package com.wenubey.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GenderTest {

    @Test
    fun `enum names are stable`() {
        assertThat(Gender.entries.map { it.name }).containsExactly(
            "MALE",
            "FEMALE",
            "OTHER",
            "NOT_SPECIFIED",
        ).inOrder()
    }

    @Test
    fun `valueOf returns the matching constant for every name`() {
        Gender.entries.forEach { g ->
            assertThat(Gender.valueOf(g.name)).isEqualTo(g)
        }
    }
}
