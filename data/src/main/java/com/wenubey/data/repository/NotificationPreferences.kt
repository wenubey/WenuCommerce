package com.wenubey.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first

class NotificationPreferences(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val KEY_EMAIL_VERIFICATION_HIDDEN = booleanPreferencesKey("email_verification_hidden")
    }

    suspend fun isEmailVerificationPermanentlyHiddenSync(): Boolean {
        return dataStore.data.first()[KEY_EMAIL_VERIFICATION_HIDDEN] ?: false
    }

    suspend fun setEmailVerificationPermanentlyHidden(hidden: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_EMAIL_VERIFICATION_HIDDEN] = hidden
        }
    }
}