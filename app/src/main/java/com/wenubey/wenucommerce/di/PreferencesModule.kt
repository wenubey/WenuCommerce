package com.wenubey.wenucommerce.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module


const val DEVICE_ID_PREFERENCE_NAME = "deviceIdPreferences"
private val Context.deviceIdPreferences: DataStore<Preferences> by preferencesDataStore(
    name = DEVICE_ID_PREFERENCE_NAME
)

val preferencesModule = module {
    single {
        androidContext().deviceIdPreferences
    }
}