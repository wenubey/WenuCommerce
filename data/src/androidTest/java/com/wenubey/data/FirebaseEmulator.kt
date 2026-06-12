package com.wenubey.data

import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Shared Firebase emulator configuration for `:data` instrumentation tests.
 *
 * Prerequisites the developer must satisfy before running these tests:
 *   1. Firebase CLI installed (`npm i -g firebase-tools`)
 *   2. Emulators running in a separate terminal:
 *        firebase emulators:start --only firestore,auth,functions
 *      Ports come from firebase.json — Firestore 8080, Auth 9099, Functions 5001.
 *   3. A connected Android emulator (AVD) or physical device — instrumentation
 *      tests need a runtime; from a connected device, the host machine is
 *      reachable as 10.0.2.2.
 *
 * Each test class should call [FirebaseEmulator.useEmulator] in @BeforeClass /
 * companion object to point the global FirebaseApp at the emulator BEFORE any
 * test code touches the SDK. Use [clearFirestore] in @Before to isolate tests.
 */
object FirebaseEmulator {

    private const val EMULATOR_HOST = "10.0.2.2"
    const val FIRESTORE_PORT = 8080
    const val AUTH_PORT = 9099
    const val FUNCTIONS_PORT = 5001

    /** Idempotent guard so multiple test classes don't double-point the SDK. */
    @Volatile
    private var configured = false

    @Synchronized
    fun useEmulator() {
        if (configured) return
        // Make sure FirebaseApp is initialised (instrumentation context, not
        // the production manifest auto-init).
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        if (FirebaseApp.getApps(ctx).isEmpty()) {
            FirebaseApp.initializeApp(ctx)
        }
        val firestore = FirebaseFirestore.getInstance()
        firestore.useEmulator(EMULATOR_HOST, FIRESTORE_PORT)
        firestore.firestoreSettings = firestoreSettings {
            // Memory cache only — avoids stale persistence between tests.
            setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
        }
        Firebase.auth.useEmulator(EMULATOR_HOST, AUTH_PORT)
        Firebase.functions.useEmulator(EMULATOR_HOST, FUNCTIONS_PORT)
        configured = true
    }

    /**
     * Wipes Firestore between tests via the emulator's REST API. Avoids
     * collection-level delete-loops that need recursive listing of subcollections.
     * The 'demo-project' default ID matches singleProjectMode in firebase.json.
     */
    fun clearFirestore(projectId: String = projectId()) {
        val url = "http://$EMULATOR_HOST:$FIRESTORE_PORT/" +
            "emulator/v1/projects/$projectId/databases/(default)/documents"
        val client = OkHttpClient()
        client.newCall(
            Request.Builder().url(url).delete().build()
        ).execute().use { response ->
            check(response.isSuccessful) {
                "Failed to clear Firestore emulator: ${response.code}"
            }
        }
    }

    fun clearAuth(projectId: String = projectId()) {
        val url = "http://$EMULATOR_HOST:$AUTH_PORT/" +
            "emulator/v1/projects/$projectId/accounts"
        val client = OkHttpClient()
        client.newCall(
            Request.Builder().url(url).delete().build()
        ).execute().use { /* best-effort */ }
    }

    private fun projectId(): String =
        FirebaseApp.getInstance().options.projectId
            ?: error("FirebaseApp projectId is not configured")
}
