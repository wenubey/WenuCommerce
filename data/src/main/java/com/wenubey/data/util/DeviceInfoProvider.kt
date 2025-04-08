package com.wenubey.data.util

import android.os.Build
import com.wenubey.domain.model.Device

class DeviceInfoProvider(
    private val deviceIdProvider: DeviceIdProvider,
) {

    fun getDeviceData(): Device {
        val deviceId = deviceIdProvider.getDeviceId()
        val deviceName = Build.MODEL ?: "Unknown Device"
        val osVersion = "Android ${Build.VERSION.RELEASE}"
        val timeStamp = getCurrentDate()

        return Device(
            deviceId = deviceId,
            deviceName = deviceName,
            osVersion = osVersion,
            timeStamp = timeStamp
        )
    }
}