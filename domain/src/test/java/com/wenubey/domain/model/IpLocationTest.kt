package com.wenubey.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IpLocationTest {

    @Test
    fun `default has every field null`() {
        val loc = IpLocation()
        assertThat(loc.city).isNull()
        assertThat(loc.country).isNull()
        assertThat(loc.regionName).isNull()
        assertThat(loc.status).isNull()
        assertThat(loc.query).isNull()
        assertThat(loc.countryCode).isNull()
        assertThat(loc.region).isNull()
        assertThat(loc.lat).isNull()
        assertThat(loc.lon).isNull()
        assertThat(loc.timezone).isNull()
        assertThat(loc.isp).isNull()
    }
}
