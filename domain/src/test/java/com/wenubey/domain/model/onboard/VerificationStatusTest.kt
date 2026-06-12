package com.wenubey.domain.model.onboard

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VerificationStatusTest {

    @Test
    fun `enum names are stable`() {
        assertThat(VerificationStatus.entries.map { it.name }).containsExactly(
            "PENDING",
            "APPROVED",
            "REJECTED",
            "REQUEST_MORE_INFO",
            "RESUBMITTED",
            "CANCELLED",
        ).inOrder()
    }

    @Test
    fun `valueOf returns the matching constant for every name`() {
        VerificationStatus.entries.forEach { status ->
            assertThat(VerificationStatus.valueOf(status.name)).isEqualTo(status)
        }
    }
}
