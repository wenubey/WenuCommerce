package com.wenubey.domain.model.user

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UserRoleTest {

    @Test
    fun `enum names are stable`() {
        assertThat(UserRole.entries.map { it.name }).containsExactly(
            "CUSTOMER",
            "SELLER",
            "ADMIN",
        ).inOrder()
    }

    @Test
    fun `valueOf returns the matching constant for every name`() {
        UserRole.entries.forEach { role ->
            assertThat(UserRole.valueOf(role.name)).isEqualTo(role)
        }
    }
}
