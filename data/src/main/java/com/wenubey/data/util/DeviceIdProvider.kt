package com.wenubey.data.util

import android.util.Log
import androidx.compose.runtime.collectAsState
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.util.UUID

class DeviceIdProvider(
    private val deviceIdPreferences: DataStore<Preferences>
) {
    fun getDeviceId(): String = runBlocking {
        deviceIdPreferences.data
            .catch { Timber.tag(TAG).e("getDeviceId: ${it.message}") }
            .map { preferences ->
                preferences[DEVICE_ID]
            }.first() ?: run {
            val newDeviceId = UUID.randomUUID().toString()
            deviceIdPreferences.edit { preferences ->
                preferences[DEVICE_ID] = newDeviceId
            }
            newDeviceId
        }
    }


    companion object {
        private const val TAG = "DeviceIdProvider"
        private val DEVICE_ID = stringPreferencesKey("device_id")
    }
}