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

        // Phase 8 (08-04 / NOTF-05 / D-02): once-per-install POST_NOTIFICATIONS rationale gate.
        // Set to true after the user taps "Enable" or "Not now" so the dialog never re-nags.
        val KEY_NOTIFICATION_PERMISSION_REQUESTED =
            booleanPreferencesKey("notification_permission_requested")
    }

    suspend fun isEmailVerificationPermanentlyHidden(): Boolean {
        return dataStore.data.first()[KEY_EMAIL_VERIFICATION_HIDDEN] ?: false
    }

    suspend fun setEmailVerificationPermanentlyHidden(hidden: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_EMAIL_VERIFICATION_HIDDEN] = hidden
        }
    }

    /** Returns true if the POST_NOTIFICATIONS rationale dialog has already been shown once. */
    suspend fun isNotificationPermissionRequested(): Boolean {
        return dataStore.data.first()[KEY_NOTIFICATION_PERMISSION_REQUESTED] ?: false
    }

    /** Call after "Enable" or "Not now" to prevent re-nagging (T-08-14 / NOTF-05). */
    suspend fun setNotificationPermissionRequested(requested: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_NOTIFICATION_PERMISSION_REQUESTED] = requested
        }
    }
}