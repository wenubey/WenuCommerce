package com.wenubey.domain.model

data class IpLocation(
    val city: String? = null,
    val country: String? = null,
    val regionName: String? = null,
    val status: String? = null,
    val query: String? = null,
    val countryCode: String? = null,
    val region: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val timezone: String? = null,
    val isp: String? = null
)
