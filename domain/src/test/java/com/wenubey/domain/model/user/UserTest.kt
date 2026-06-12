package com.wenubey.domain.model.user

import com.google.common.truth.Truth.assertThat
import com.wenubey.domain.model.Gender
import com.wenubey.domain.model.onboard.BusinessInfo
import org.junit.Test

class UserTest {

    @Test
    fun `primary constructor default has null uuid, CUSTOMER role, MALE gender, null businessInfo`() {
        val user = User()
        assertThat(user.uuid).isNull()
        assertThat(user.role).isEqualTo(UserRole.CUSTOMER)
        assertThat(user.gender).isEqualTo(Gender.MALE)
        assertThat(user.businessInfo).isNull()
        assertThat(user.purchaseHistory).isEmpty()
        assertThat(user.signedDevices).isEmpty()
        assertThat(user.products).isEmpty()
        assertThat(user.isEmailVerified).isFalse()
        assertThat(user.isPhoneNumberVerified).isFalse()
    }

    @Test
    fun `User dot default differs from primary constructor by businessInfo`() {
        // INTENTIONAL pin: User() returns null businessInfo, but User.default()
        // returns BusinessInfo() (non-null empty instance). Two call sites use
        // different shapes; if you change either, search for the other and
        // confirm the migration is consistent.
        val ctorDefault = User()
        val factoryDefault = User.default()
        assertThat(ctorDefault.businessInfo).isNull()
        assertThat(factoryDefault.businessInfo).isEqualTo(BusinessInfo())
    }

    @Test
    fun `User dot default matches primary constructor on every other field`() {
        val ctor = User()
        val factory = User.default()
        // Equalize the only known divergence
        assertThat(factory.copy(businessInfo = null)).isEqualTo(ctor)
    }
}
