package com.wenubey.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeviceTest {

    @Test
    fun `default has all empty fields`() {
        val device = Device()
        assertThat(device.deviceId).isEmpty()
        assertThat(device.deviceName).isEmpty()
        assertThat(device.osVersion).isEmpty()
        assertThat(device.timeStamp).isEmpty()
        assertThat(device.location).isEmpty()
        assertThat(device.fcmToken).isEmpty()
    }

    @Test
    fun `toMap contains every persisted field`() {
        val device = Device(
            deviceId = "d-1",
            deviceName = "Pixel 9",
            osVersion = "Android 15",
            timeStamp = "2026-01-01",
            location = "Istanbul",
            fcmToken = "tok-abc",
        )
        val map = device.toMap()
        assertThat(map.keys).containsExactly(
            "deviceId", "deviceName", "osVersion", "timeStamp", "location", "fcmToken",
        )
        assertThat(map["deviceId"]).isEqualTo("d-1")
        assertThat(map["deviceName"]).isEqualTo("Pixel 9")
        assertThat(map["osVersion"]).isEqualTo("Android 15")
        assertThat(map["fcmToken"]).isEqualTo("tok-abc")
    }
}
