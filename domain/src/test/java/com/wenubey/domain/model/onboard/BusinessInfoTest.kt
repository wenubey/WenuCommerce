package com.wenubey.domain.model.onboard

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BusinessInfoTest {

    @Test
    fun `default is unverified INDIVIDUAL with PENDING status, no previous status`() {
        val info = BusinessInfo()
        assertThat(info.businessType).isEqualTo(BusinessType.INDIVIDUAL)
        assertThat(info.verificationStatus).isEqualTo(VerificationStatus.PENDING)
        assertThat(info.isVerified).isFalse()
        assertThat(info.previousStatus).isNull()
        assertThat(info.verificationDate).isNull()
        assertThat(info.verificationNotes).isNull()
    }

    @Test
    fun `toMap serializes businessType and verificationStatus by name`() {
        val map = BusinessInfo(
            businessType = BusinessType.LLC,
            verificationStatus = VerificationStatus.APPROVED,
        ).toMap()
        assertThat(map["businessType"]).isEqualTo("LLC")
        assertThat(map["verificationStatus"]).isEqualTo("APPROVED")
    }

    @Test
    fun `toMap collapses null previousStatus and date and notes to empty string`() {
        // CURRENT behavior: nullable persistence fields are stored as the
        // empty string, not null, so Firestore documents never carry the
        // null sentinel for these three keys. Pinned so we don't silently
        // change wire format when refactoring.
        val map = BusinessInfo().toMap()
        assertThat(map["previousStatus"]).isEqualTo("")
        assertThat(map["verificationDate"]).isEqualTo("")
        assertThat(map["verificationNotes"]).isEqualTo("")
    }

    @Test
    fun `toMap serializes non-null previousStatus by enum name`() {
        val map = BusinessInfo(previousStatus = VerificationStatus.REJECTED).toMap()
        assertThat(map["previousStatus"]).isEqualTo("REJECTED")
    }

    @Test
    fun `toMap contains every persisted field`() {
        val map = BusinessInfo().toMap()
        assertThat(map.keys).containsExactly(
            "businessName", "businessType",
            "businessDescription", "businessAddress",
            "businessPhone", "businessEmail",
            "taxId", "businessLicense",
            "bankAccountNumber", "routingNumber",
            "taxDocumentUri", "businessLicenseDocumentUri", "identityDocumentUri",
            "isVerified", "verificationStatus",
            "previousStatus", "verificationDate", "verificationNotes",
            "createdAt", "updatedAt",
        )
    }
}
