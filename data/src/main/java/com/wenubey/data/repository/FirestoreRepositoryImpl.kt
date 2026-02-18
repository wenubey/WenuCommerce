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
import com.wenubey.data.util.IMAGE_FILE_SUFFIX
import com.wenubey.data.util.PROFILE_IMAGES_FOLDER
import com.wenubey.data.util.USER_COLLECTION
import com.wenubey.data.util.safeApiCall
import com.wenubey.domain.model.onboard.VerificationStatus
import com.wenubey.domain.model.toMap
import com.wenubey.domain.model.user.User
import com.wenubey.domain.model.user.UserRole
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.FirestoreRepository
import com.wenubey.domain.util.AuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
            try {
                userDoc.toObject(User::class.java) ?: throw Exception("Failed to parse user data")
            } catch (e: RuntimeException) {
                Timber.e(e, "Failed to deserialize user document: $uid")
                throw Exception("Failed to parse user data: ${e.message}")
            }
        } else {
            throw Exception("User document not found")
        }
    }

    private suspend fun updateProfilePhoto(profilePhotoUri: String, userUid: String?): String =
        withContext(ioDispatcher) {
            val uri = Uri.parse(profilePhotoUri)

            // If the URI is already a remote URL, no need to upload again
            val scheme = uri.scheme
            if (scheme == "http" || scheme == "https") {
                Timber.d("Profile photo is already a remote URL, skipping upload")
                return@withContext profilePhotoUri
            }

            val photoUpdate = userProfileChangeRequest {
                photoUri = uri
            }

            auth.currentUser?.updateProfile(photoUpdate)
                ?.addOnSuccessListener { Timber.d("Update Profile Photo Success") }
                ?.addOnFailureListener { Timber.e(it, "Update Profile Photo Error") }
                ?.await()

            val storageRef = storage.reference

            val imageFileName = userUid + IMAGE_FILE_SUFFIX
            val imageRef = storageRef.child("$PROFILE_IMAGES_FOLDER/$imageFileName")
            val uploadTask = imageRef.putFile(uri)

            uploadTask.addOnSuccessListener {
                Timber.d("Image uploaded successfully")
            }.addOnFailureListener {
                Timber.e(it, "Image upload failed")
            }.await()

            val downloadUrl = imageRef.downloadUrl.await().toString()

            downloadUrl
        }


    override suspend fun updateSellerApprovalStatus(
        sellerId: String,
        status: VerificationStatus,
        notes: String
    ): Result<Unit> = safeApiCall(ioDispatcher) {
        val currentTime = System.currentTimeMillis().toString()
        val isVerified = status == VerificationStatus.APPROVED

        // Read current status to preserve as previousStatus
        val currentDoc = firestore.collection(USER_COLLECTION)
            .document(sellerId)
            .get()
            .await()
        val currentStatusName = currentDoc.getString("businessInfo.verificationStatus")

        val updates = mutableMapOf<String, Any>(
            "businessInfo.verificationStatus" to status.name,
            "businessInfo.verificationNotes" to notes,
            "businessInfo.verificationDate" to currentTime,
            "businessInfo.isVerified" to isVerified,
            "updatedAt" to currentTime
        )
        if (currentStatusName != null) {
            updates["businessInfo.previousStatus"] = currentStatusName
        }

        firestore.collection(USER_COLLECTION)
            .document(sellerId)
            .update(updates)
            .addOnSuccessListener {
                Timber.d("Seller approval status updated successfully for seller: $sellerId")
            }
            .addOnFailureListener {
                Timber.e(
                    it,
                    "Seller approval status update failed for seller: $sellerId with error: ${it.message}"
                )
            }
            .await()
    }

    override fun observeSellersByStatus(status: VerificationStatus): Flow<List<User>> =
        callbackFlow {
            val query = firestore.collection(USER_COLLECTION)
                .whereEqualTo("role", UserRole.SELLER.name)
                .whereEqualTo("businessInfo.verificationStatus", status.name)

            val listener = query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "Error observing sellers by status: $status")
                    close(error)
                    return@addSnapshotListener
                }

                Timber.d("isFromCache: ${snapshot?.metadata?.isFromCache}")

                val sellers = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(User::class.java)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to deserialize seller document: ${doc.id}")
                        null
                    }
                } ?: emptyList()

                Timber.d("Sellers updated for status $status: ${sellers.size} sellers")
                trySend(sellers)
            }

            awaitClose {
                Timber.d("Removing seller listener for status: $status")
                listener.remove()
            }
        }

    override fun observePendingResubmittedSellerCount(): Flow<Int> = callbackFlow {
        val query = firestore.collection(USER_COLLECTION)
            .whereEqualTo("role", UserRole.SELLER.name)
            .whereIn(
                "businessInfo.verificationStatus",
                listOf(VerificationStatus.PENDING.name, VerificationStatus.RESUBMITTED.name)
            )

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Timber.e(error, "Error observing pending resubmitted sellers")
                close(error)
                return@addSnapshotListener
            }

            val count = snapshot?.documents?.size ?: -1
            Timber.d("Pending Resubmitted seller count updated: $count")
            trySend(count)
        }

        awaitClose { listener.remove() }

    }
}