package com.wenubey.domain.repository

import com.wenubey.domain.model.IpLocation

interface LocationService {
    suspend fun getApproximateLocation(): IpLocation
}