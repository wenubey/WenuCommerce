package com.wenubey.data.util

import android.os.Build
import com.google.firebase.messaging.FirebaseMessaging
import com.wenubey.domain.model.Device
import com.wenubey.domain.repository.LocationService
import kotlinx.coroutines.tasks.await

class DeviceInfoProvider(
    private val deviceIdProvider: DeviceIdProvider,
    private val firebaseMessaging: FirebaseMessaging,
    private val locationService: LocationService,
) {

    suspend fun getDeviceData(): Device {
        val deviceId = deviceIdProvider.getDeviceId()
        val deviceName = Build.MODEL ?: "Unknown Device"
        val osVersion = "Android ${Build.VERSION.RELEASE}"
        val timeStamp = getCurrentDate()
        val approximateLocation = locationService.getApproximateLocation()
        val cityLocation = approximateLocation.city ?: "Unknown City"
        val countryLocation = approximateLocation.country ?: "Unknown Country"
        val location = "$cityLocation, $countryLocation"

        return Device(
            deviceId = deviceId,
            deviceName = deviceName,
            osVersion = osVersion,
            timeStamp = timeStamp,
            fcmToken = firebaseMessaging.token.await(),
            location = location,
        )
    }
}