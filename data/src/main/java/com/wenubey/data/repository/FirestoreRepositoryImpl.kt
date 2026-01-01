package com.wenubey.data.repository

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.wenubey.data.util.AdminUtils
import com.wenubey.data.util.DeviceInfoProvider
import com.wenubey.data.util.safeApiCall
import com.wenubey.domain.model.toMap
import com.wenubey.domain.model.user.User
import com.wenubey.domain.model.user.UserRole
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.FirestoreRepository
import com.wenubey.domain.util.AuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber

class FirestoreRepositoryImpl(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth,
    dispatcherProvider: DispatcherProvider,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val firebaseFunctions: FirebaseFunctions,
    //private val authRepository: AuthRepository,
) : FirestoreRepository {

    private val ioDispatcher = dispatcherProvider.io()

    override suspend fun addUserToFirestore(
        firebaseUser: FirebaseUser?,
        authProvider: AuthProvider
    ): Result<Boolean> {
        return Result.success(true)
    }

    override suspend fun updateSignedDevice(userUid: String?): Result<Unit> =
        safeApiCall(ioDispatcher) {
            val signedDevice = deviceInfoProvider.getDeviceData()
            userUid?.let {
                val data = mapOf("uuid" to userUid, "newDevice" to signedDevice.toMap())

                firebaseFunctions.getHttpsCallable("handleDeviceLogin").call(data)
                    .addOnSuccessListener { Timber.d("Device Updated Successfully") }
                    .addOnFailureListener { Timber.e(it, "Device Update Failed: ${it.message}") }
                    .await()
            } ?: Result.failure<Unit>(Exception("User not found"))

        }

    override suspend fun onboardingComplete(user: User): Result<Unit> = safeApiCall(ioDispatcher) {
        user.uuid?.let { uuid ->
            val userRef = firestore.collection(USER_COLLECTION).document(uuid)

            // Upload photo with error handling - don't let photo upload failure block user save
            val photoDownloadUri = try {
                updateProfilePhoto(user.profilePhotoUri, user.uuid)
            } catch (e: Exception) {
                Timber.e(e, "Photo upload failed, continuing with empty photo URI")
                "" // Continue even if photo upload fails
            }

            val userRole = if (AdminUtils.isAdminUser(user.email)) {
                UserRole.ADMIN
            } else {
                user.role
            }
            val updatedUser = user.copy(profilePhotoUri = photoDownloadUri, role = userRole)

            try {
                userRef.set(updatedUser).await()
                Timber.d("User saved successfully to Firestore")
                // Notify AuthRepository that onboarding is complete
                CoroutineScope(ioDispatcher).launch {
                    //authRepository.setCurrentUserAfterOnboarding(updatedUser)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to save user to Firestore")
                throw e // Re-throw to ensure error is propagated
            }
        } ?: throw Exception("User UUID is null")
    }

    override fun updateFcmToken(token: String): Result<Unit> {
        val user = auth.currentUser
        user?.uid?.let { uid ->
            firestore.collection(USER_COLLECTION).document(uid).update(
                mapOf(
                    "fcmToken" to token
                )
            ).addOnSuccessListener {
                Timber.d("FCM Token updated successfully")
            }.addOnFailureListener {
                Timber.e(it, "FCM Token update failed")
            }
        }
        return Result.success(Unit)
    }

    override suspend fun getUser(uid: String): Result<User> = safeApiCall(ioDispatcher) {
        val userDoc = firestore.collection(USER_COLLECTION).document(uid).get().await()
        if (userDoc.exists()) {
            userDoc.toObject(User::class.java) ?: throw Exception("Failed to parse user data")
        } else {
            throw Exception("User document not found")
        }
    }

    // TODO handle file not found exception
    private suspend fun updateProfilePhoto(profilePhotoUri: String, userUid: String?): String =
        withContext(ioDispatcher) {
            val photoUpdate = userProfileChangeRequest {
                photoUri = Uri.parse(profilePhotoUri)
            }

            auth.currentUser?.updateProfile(photoUpdate)
                ?.addOnSuccessListener { Timber.d("Update Profile Photo Success") }
                ?.addOnFailureListener { Timber.e(it, "Update Profile Photo Error") }
                ?.await()

            val storageRef = storage.reference

            val imageFileName = userUid + IMAGE_FILE_SUFFIX
            val imageRef = storageRef.child("$PROFILE_IMAGES_FOLDER/$imageFileName")
            val uploadTask = imageRef.putFile(Uri.parse(profilePhotoUri))

            uploadTask.addOnSuccessListener {
                Timber.d("Image uploaded successfully")
            }.addOnFailureListener {
                Timber.e(it, "Image upload failed")
            }.await()

            val downloadUrl = imageRef.downloadUrl.await().toString()

            downloadUrl
        }

    companion object {
        private const val USER_COLLECTION = "USERS"
        const val IMAGE_FILE_SUFFIX = "_profile_image.jpeg"
        const val PROFILE_IMAGES_FOLDER = "profile_images"

    }
}