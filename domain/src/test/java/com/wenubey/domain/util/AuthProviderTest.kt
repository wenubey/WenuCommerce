package com.wenubey.domain.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pins the names and ordering of AuthProvider. These names are persisted with
 * user profiles — renaming silently breaks login-with-Google / login-with-email
 * routing for existing users.
 */
class AuthProviderTest {

    @Test
    fun `enum names are stable`() {
        assertThat(AuthProvider.entries.map { it.name }).containsExactly(
            "EMAIL",
            "GOOGLE",
        ).inOrder()
    }

    @Test
    fun `valueOf returns the matching constant for every name`() {
        AuthProvider.entries.forEach { provider ->
            assertThat(AuthProvider.valueOf(provider.name)).isEqualTo(provider)
        }
    }
}
