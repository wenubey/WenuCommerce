package com.wenubey.data.repository

import com.wenubey.data.model.IpLocationDto
import com.wenubey.domain.model.IpLocation
import com.wenubey.domain.repository.LocationService
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import timber.log.Timber


class LocationServiceImpl(
    private val client: HttpClient,
): LocationService {
    companion object {
        private const val IP_API_URL = "http://ip-api.com/json/"
    }

    override suspend fun getApproximateLocation(): IpLocation {
        return try {
            client.get(IP_API_URL).body<IpLocationDto>().toIpLocation()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get location")
            errorIpLocation
        }
    }

    private val errorIpLocation = IpLocation(
        city = "Unknown",
        country = "Unknown",
        regionName = "Unknown",
        status = "Unknown",
    )
}