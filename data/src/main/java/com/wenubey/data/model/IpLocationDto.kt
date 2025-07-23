package com.wenubey.data.model

import com.wenubey.domain.model.IpLocation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class IpLocationDto(
    @SerialName("city") val city: String? = null,
    @SerialName("country") val country: String? = null,
    @SerialName("regionName") val regionName: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("query") val query: String? = null,
    @SerialName("countryCode") val countryCode: String? = null,
    @SerialName("region") val region: String? = null,
    @SerialName("lat") val lat: Double? = null,
    @SerialName("lon") val lon: Double? = null,
    @SerialName("timezone") val timezone: String? = null,
    @SerialName("isp") val isp: String? = null
) {
    fun toIpLocation(): IpLocation {
        return IpLocation(
            city = city,
            country = country,
            regionName = regionName,
            status = status,
            query = query,
            countryCode = countryCode,
            region = region,
            lat = lat,
            lon = lon,
            timezone = timezone,
            isp = isp
        )
    }
}

