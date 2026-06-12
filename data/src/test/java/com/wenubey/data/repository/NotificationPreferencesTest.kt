package com.wenubey.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationPreferencesTest {

    private lateinit var tempFile: File
    private lateinit var prefs: NotificationPreferences

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        tempFile = File(ctx.cacheDir, "test-notifications-${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create { tempFile }
        prefs = NotificationPreferences(dataStore)
    }

    @After
    fun tearDown() {
        tempFile.delete()
    }

    @Test
    fun `isEmailVerificationPermanentlyHidden returns false by default`() = runTest {
        assertThat(prefs.isEmailVerificationPermanentlyHidden()).isFalse()
    }

    @Test
    fun `setEmailVerificationPermanentlyHidden persists true`() = runTest {
        prefs.setEmailVerificationPermanentlyHidden(true)
        assertThat(prefs.isEmailVerificationPermanentlyHidden()).isTrue()
    }

    @Test
    fun `setEmailVerificationPermanentlyHidden persists false`() = runTest {
        prefs.setEmailVerificationPermanentlyHidden(true)
        prefs.setEmailVerificationPermanentlyHidden(false)
        assertThat(prefs.isEmailVerificationPermanentlyHidden()).isFalse()
    }
}
