package com.wenubey.data.repository

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.wenubey.data.util.DeviceInfoProvider
import com.wenubey.data.util.safeApiCall
import com.wenubey.domain.model.User
import com.wenubey.domain.model.toMap
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.FirestoreRepository
import com.wenubey.domain.util.AuthProvider
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber

class FirestoreRepositoryImpl(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth,
    dispatcherProvider: DispatcherProvider,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val firebaseFunctions: FirebaseFunctions
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
            val photoDownloadUri = updateProfilePhoto(user.profilePhotoUri, user.uuid)
            Timber.d("Photo Download Uri: $photoDownloadUri")
            val updatedUser = user.copy(profilePhotoUri = photoDownloadUri)
            Timber.d("Updated User: ${updatedUser.name}")
            userRef.set(updatedUser)
                .addOnSuccessListener {
                    Timber.d("User added successfully to Firestore")
                }.addOnFailureListener {
                    Timber.e(it, "Failed to add user to Firestore")
                }
        } ?: Result.failure<Unit>(Exception("User not found"))

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

    // TODO handle file not found exception
    private suspend fun updateProfilePhoto(profilePhotoUri: Uri, userUid: String?): Uri =
        withContext(ioDispatcher) {
            val photoUpdate = userProfileChangeRequest {
                photoUri = profilePhotoUri
            }

            auth.currentUser?.updateProfile(photoUpdate)
                ?.addOnSuccessListener { Timber.d("Update Profile Photo Success") }
                ?.addOnFailureListener { Timber.e(it, "Update Profile Photo Error") }
                ?.await()

            val storageRef = storage.reference

            val imageFileName = userUid + IMAGE_FILE_SUFFIX
            val imageRef = storageRef.child("$PROFILE_IMAGES_FOLDER/$imageFileName")
            val uploadTask = imageRef.putFile(profilePhotoUri)

            uploadTask.addOnSuccessListener {
                Timber.d("Image uploaded successfully")
            }.addOnFailureListener {
                Timber.e(it, "Image upload failed")
            }.await()

            val downloadUrl = imageRef.downloadUrl.await()

            downloadUrl
        }

    companion object {
        private const val USER_COLLECTION = "USERS"
        const val IMAGE_FILE_SUFFIX = "_profile_image.jpeg"
        const val PROFILE_IMAGES_FOLDER = "profile_images"

    }
}