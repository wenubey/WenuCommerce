package com.wenubey.domain.auth

import com.google.common.truth.Truth.assertThat
import com.wenubey.domain.model.user.User
import org.junit.Test

class SignInResultTest {

    @Test
    fun `Success carries the authenticated User`() {
        val user = User(uuid = "u-1", email = "a@b.c")
        val result: SignInResult = SignInResult.Success(user)
        assertThat(result).isInstanceOf(SignInResult.Success::class.java)
        assertThat((result as SignInResult.Success).user).isEqualTo(user)
    }

    @Test
    fun `Failure carries an optional error message`() {
        val withMessage: SignInResult = SignInResult.Failure("network down")
        val withoutMessage: SignInResult = SignInResult.Failure(null)
        assertThat((withMessage as SignInResult.Failure).errorMessage).isEqualTo("network down")
        assertThat((withoutMessage as SignInResult.Failure).errorMessage).isNull()
    }

    @Test
    fun `Cancelled and NoCredentials are singletons`() {
        assertThat(SignInResult.Cancelled).isSameInstanceAs(SignInResult.Cancelled)
        assertThat(SignInResult.NoCredentials).isSameInstanceAs(SignInResult.NoCredentials)
    }

    @Test
    fun `exhaustive when over sealed hierarchy covers every variant`() {
        // Compile-time guarantee: if a new variant is added, `when` becomes
        // non-exhaustive and this test fails to compile. That is the test.
        val cases: List<SignInResult> = listOf(
            SignInResult.Success(User()),
            SignInResult.Cancelled,
            SignInResult.Failure("e"),
            SignInResult.NoCredentials,
        )
        val labels = cases.map {
            when (it) {
                is SignInResult.Success -> "success"
                is SignInResult.Cancelled -> "cancelled"
                is SignInResult.Failure -> "failure"
                is SignInResult.NoCredentials -> "no-creds"
            }
        }
        assertThat(labels).containsExactly("success", "cancelled", "failure", "no-creds").inOrder()
    }
}
