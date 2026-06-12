package com.wenubey.domain.auth

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SignUpResultTest {

    @Test
    fun `Failure carries an optional error message`() {
        val withMessage: SignUpResult = SignUpResult.Failure("email taken")
        val withoutMessage: SignUpResult = SignUpResult.Failure(null)
        assertThat((withMessage as SignUpResult.Failure).errorMessage).isEqualTo("email taken")
        assertThat((withoutMessage as SignUpResult.Failure).errorMessage).isNull()
    }

    @Test
    fun `Success and Cancelled are singletons`() {
        assertThat(SignUpResult.Success).isSameInstanceAs(SignUpResult.Success)
        assertThat(SignUpResult.Cancelled).isSameInstanceAs(SignUpResult.Cancelled)
    }

    @Test
    fun `exhaustive when over sealed hierarchy covers every variant`() {
        val cases: List<SignUpResult> = listOf(
            SignUpResult.Success,
            SignUpResult.Cancelled,
            SignUpResult.Failure("e"),
        )
        val labels = cases.map {
            when (it) {
                SignUpResult.Success -> "success"
                SignUpResult.Cancelled -> "cancelled"
                is SignUpResult.Failure -> "failure"
            }
        }
        assertThat(labels).containsExactly("success", "cancelled", "failure").inOrder()
    }
}
