package com.wenubey.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Device(
    val deviceId: String = "",
    val deviceName: String = "",
    val osVersion: String = "",
    val timeStamp: String = "",
    val location: String = "",
    val fcmToken: String = "",
)

fun Device.toMap(): Map<String, Any> = mapOf(
    "deviceId" to deviceId,
    "deviceName" to deviceName,
    "osVersion" to osVersion,
    "timeStamp" to timeStamp,
    "location" to location,
    "fcmToken" to fcmToken,
)
