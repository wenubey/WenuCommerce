package com.wenubey.data

import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
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
    const val STORAGE_PORT = 9199

    /**
     * Storage bucket the emulator accepts. The emulator runs in
     * singleProjectMode and accepts any bucket under '<projectId>.appspot.com'.
     */
    private const val STORAGE_BUCKET = "wenucommerce.appspot.com"

    /** Idempotent guard so multiple test classes don't double-point the SDK. */
    @Volatile
    private var configured = false

    /**
     * Project ID the emulator accepts under singleProjectMode. Must match the
     * project ID the Firebase CLI starts the emulators for (taken from
     * .firebaserc / google-services.json — this codebase uses 'wenucommerce').
     * Mismatched IDs are rejected when singleProjectMode is on.
     */
    private const val EMULATOR_PROJECT_ID = "wenucommerce"

    @Synchronized
    fun useEmulator() {
        if (configured) return
        // :data is a library module without google-services.json; the SDK can't
        // auto-init from a missing google-services config block. Provide the
        // minimum FirebaseOptions explicitly — the emulator only checks
        // projectId, so the other fields can be any non-null placeholder.
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        if (FirebaseApp.getApps(ctx).isEmpty()) {
            FirebaseApp.initializeApp(
                ctx,
                FirebaseOptions.Builder()
                    .setApplicationId("1:1:android:emulator")
                    .setProjectId(EMULATOR_PROJECT_ID)
                    .setApiKey("emulator-fake-api-key")
                    // FirebaseStorage.getInstance() requires a bucket to be
                    // present in FirebaseOptions before any useEmulator call.
                    .setStorageBucket(STORAGE_BUCKET)
                    .build(),
            )
        }
        val firestore = FirebaseFirestore.getInstance()
        firestore.useEmulator(EMULATOR_HOST, FIRESTORE_PORT)
        firestore.firestoreSettings = firestoreSettings {
            // Memory cache only — avoids stale persistence between tests.
            setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
        }
        Firebase.auth.useEmulator(EMULATOR_HOST, AUTH_PORT)
        Firebase.functions.useEmulator(EMULATOR_HOST, FUNCTIONS_PORT)
        FirebaseStorage.getInstance().useEmulator(EMULATOR_HOST, STORAGE_PORT)
        configured = true
    }

    /**
     * Wipes Storage between tests via the emulator's REST API. Recursively
     * deletes every object stored under the bucket so each test starts clean.
     */
    fun clearStorage(bucket: String = STORAGE_BUCKET) {
        val url = "http://$EMULATOR_HOST:$STORAGE_PORT/" +
            "storage/v1/b/$bucket/o"
        val client = OkHttpClient()
        // List + delete-each is the only emulator-supported path; there is no
        // bulk-delete endpoint. Best effort: if any deletion fails, the next
        // test's clear cycle will retry.
        val listResponse = client.newCall(
            Request.Builder().url(url).get().build()
        ).execute()
        listResponse.use { resp ->
            if (!resp.isSuccessful) return
            val body = resp.body?.string() ?: return
            // Extract object names with a simple regex — avoiding a JSON dep.
            val names = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"")
                .findAll(body)
                .map { it.groupValues[1] }
                .toList()
            names.forEach { name ->
                val encoded = java.net.URLEncoder.encode(name, "UTF-8")
                client.newCall(
                    Request.Builder()
                        .url("$url/$encoded")
                        .delete()
                        .build()
                ).execute().close()
            }
        }
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

    /**
     * Signs a deterministic test user into the Auth emulator. Returns the
     * resulting user's uid. Subsequent tests that need a different identity
     * should call [clearAuth] and re-invoke. Idempotent on repeat calls within
     * the same test method.
     */
    suspend fun signInAnonymous(): String {
        val auth = Firebase.auth
        val current = auth.currentUser
        if (current != null) return current.uid
        val result = auth.signInAnonymously().await()
        return result.user?.uid ?: error("Anonymous sign-in returned null user")
    }

    private fun projectId(): String =
        FirebaseApp.getInstance().options.projectId
            ?: EMULATOR_PROJECT_ID
}
