package com.wenubey.wenucommerce.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.wenubey.data.repository.NotificationPreferences
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module


const val DEVICE_ID_PREFERENCE_NAME = "deviceIdPreferences"
private val Context.deviceIdPreferences: DataStore<Preferences> by preferencesDataStore(
    name = DEVICE_ID_PREFERENCE_NAME
)

const val NOTIFICATION_PREFERENCE_NAME = "notificationPreferences"
private val Context.notificationPreferences: DataStore<Preferences> by preferencesDataStore(
    name = NOTIFICATION_PREFERENCE_NAME
)

val preferencesModule = module {
    single(named("deviceId")) {
        androidContext().deviceIdPreferences
    }

    single(named("notification")) {
        androidContext().notificationPreferences
    }

    single {
        NotificationPreferences(get(named("notification")))
    }
}