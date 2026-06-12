package com.wenubey.data.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class IpLocationDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `toIpLocation copies every field`() {
        val dto = IpLocationDto(
            city = "Istanbul",
            country = "Turkey",
            regionName = "Marmara",
            status = "success",
            query = "1.2.3.4",
            countryCode = "TR",
            region = "34",
            lat = 41.0,
            lon = 28.97,
            timezone = "Europe/Istanbul",
            isp = "ACME",
        )
        val loc = dto.toIpLocation()
        assertThat(loc.city).isEqualTo("Istanbul")
        assertThat(loc.country).isEqualTo("Turkey")
        assertThat(loc.regionName).isEqualTo("Marmara")
        assertThat(loc.status).isEqualTo("success")
        assertThat(loc.query).isEqualTo("1.2.3.4")
        assertThat(loc.countryCode).isEqualTo("TR")
        assertThat(loc.region).isEqualTo("34")
        assertThat(loc.lat).isEqualTo(41.0)
        assertThat(loc.lon).isEqualTo(28.97)
        assertThat(loc.timezone).isEqualTo("Europe/Istanbul")
        assertThat(loc.isp).isEqualTo("ACME")
    }

    @Test
    fun `toIpLocation preserves nulls`() {
        val dto = IpLocationDto()
        val loc = dto.toIpLocation()
        assertThat(loc.city).isNull()
        assertThat(loc.country).isNull()
        assertThat(loc.lat).isNull()
        assertThat(loc.lon).isNull()
    }

    @Test
    fun `deserializes from ip-api dot com sample payload`() {
        val payload = """
            {"status":"success","country":"Turkey","countryCode":"TR",
             "region":"34","regionName":"Istanbul","city":"Istanbul",
             "lat":41.0082,"lon":28.9784,"timezone":"Europe/Istanbul",
             "isp":"Test ISP","query":"1.2.3.4"}
        """.trimIndent()
        val dto = json.decodeFromString<IpLocationDto>(payload)
        assertThat(dto.status).isEqualTo("success")
        assertThat(dto.city).isEqualTo("Istanbul")
        assertThat(dto.lat).isEqualTo(41.0082)
    }

    @Test
    fun `deserialization tolerates missing fields and unknown keys`() {
        val payload = """{"city":"Ankara","foo":"unknown"}"""
        val dto = json.decodeFromString<IpLocationDto>(payload)
        assertThat(dto.city).isEqualTo("Ankara")
        assertThat(dto.country).isNull()
    }
}
