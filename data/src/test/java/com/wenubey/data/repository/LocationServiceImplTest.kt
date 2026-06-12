package com.wenubey.data.repository

import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

class LocationServiceImplTest {

    private fun client(mockEngine: MockEngine): HttpClient = HttpClient(mockEngine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    @Test
    fun `success response maps to IpLocation`() = runTest {
        val payload = """
            {"status":"success","country":"Turkey","countryCode":"TR",
             "city":"Istanbul","regionName":"Istanbul","lat":41.0,"lon":29.0,
             "timezone":"Europe/Istanbul","isp":"ISP","query":"1.2.3.4"}
        """.trimIndent()
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(payload),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val service = LocationServiceImpl(client(engine))

        val loc = service.getApproximateLocation()

        assertThat(loc.country).isEqualTo("Turkey")
        assertThat(loc.city).isEqualTo("Istanbul")
        assertThat(loc.status).isEqualTo("success")
        assertThat(loc.lat).isEqualTo(41.0)
    }

    @Test
    fun `http error returns unknown sentinel instead of throwing`() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }
        val service = LocationServiceImpl(client(engine))

        val loc = service.getApproximateLocation()

        assertThat(loc.city).isEqualTo("Unknown")
        assertThat(loc.country).isEqualTo("Unknown")
        assertThat(loc.regionName).isEqualTo("Unknown")
        assertThat(loc.status).isEqualTo("Unknown")
        assertThat(loc.lat).isNull()
    }

    @Test
    fun `malformed json returns unknown sentinel instead of throwing`() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel("not json"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val service = LocationServiceImpl(client(engine))

        val loc = service.getApproximateLocation()

        assertThat(loc.country).isEqualTo("Unknown")
    }
}
