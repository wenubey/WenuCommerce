package com.wenubey.data.repository

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.storage.FirebaseStorage
import com.wenubey.data.util.DeviceInfoProvider
import com.wenubey.data.util.getCurrentDate
import com.wenubey.data.util.safeApiCall
import com.wenubey.domain.model.Gender
import com.wenubey.domain.model.onboard.BusinessInfo
import com.wenubey.domain.model.onboard.BusinessType
import com.wenubey.domain.model.user.User
import com.wenubey.domain.model.user.UserRole
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.FirestoreRepository
import com.wenubey.domain.repository.ProfileRepository
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileRepositoryImpl(
    private val firestoreRepository: FirestoreRepository,
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage,
    dispatcherProvider: DispatcherProvider,
    private val deviceInfoProvider: DeviceInfoProvider,
): ProfileRepository {

    private val ioDispatcher = dispatcherProvider.io()
    private val firebaseUser: FirebaseUser? = auth.currentUser

    override suspend fun onboarding(
        name: String,
        surname: String,
        phoneNumber: String,
        dateOfBirth: String,
        address: String,
        gender: Gender,
        photoUrl: Uri,
        role: UserRole,
        // Seller-specific parameters
        businessName: String,
        taxId: String,
        businessLicense: String,
        businessAddress: String,
        businessPhone: String,
        businessEmail: String,
        bankAccountNumber: String,
        routingNumber: String,
        businessType: BusinessType,
        businessDescription: String,
        taxDocumentUri: Uri,
        businessLicenseDocumentUri: Uri,
        identityDocumentUri: Uri
    ): Result<Unit> = safeApiCall(ioDispatcher) {

        val userUuid = firebaseUser?.uid ?: throw IllegalStateException("User must be authenticated")

        // Upload profile photo first
        val uploadedProfilePhotoUrl = if (photoUrl != Uri.EMPTY) {
            uploadProfilePhoto(photoUrl, userUuid)
        } else ""

        // Create business info only if user is a seller
        val businessInfo = if (role == UserRole.SELLER) {
            // Upload seller documents to organized folder structure
            val uploadedTaxDocumentUrl = if (taxDocumentUri != Uri.EMPTY) {
                uploadSellerDocument(taxDocumentUri, userUuid, DOCUMENT_TYPE_TAX)
            } else ""

            val uploadedBusinessLicenseUrl = if (businessLicenseDocumentUri != Uri.EMPTY) {
                uploadSellerDocument(businessLicenseDocumentUri, userUuid, DOCUMENT_TYPE_BUSINESS_LICENSE)
            } else ""

            val uploadedIdentityDocumentUrl = if (identityDocumentUri != Uri.EMPTY) {
                uploadSellerDocument(identityDocumentUri, userUuid, DOCUMENT_TYPE_IDENTITY)
            } else ""

            BusinessInfo(
                businessName = businessName,
                businessType = businessType.name,
                businessDescription = businessDescription,
                businessAddress = businessAddress,
                businessPhone = businessPhone,
                businessEmail = businessEmail,
                taxId = taxId,
                businessLicense = businessLicense,
                bankAccountNumber = bankAccountNumber,
                routingNumber = routingNumber,
                taxDocumentUri = uploadedTaxDocumentUrl,
                businessLicenseDocumentUri = uploadedBusinessLicenseUrl,
                identityDocumentUri = uploadedIdentityDocumentUrl,
                isVerified = false, // Business verification pending
                verificationStatus = "PENDING",
                verificationDate = null,
                createdAt = getCurrentDate(),
                updatedAt = getCurrentDate()
            )
        } else null

        val user = User(
            uuid = userUuid,
            role = role,
            name = name,
            surname = surname,
            phoneNumber = phoneNumber,
            dateOfBirth = dateOfBirth,
            gender = gender,
            email = firebaseUser.email ?: "",
            address = address,
            isEmailVerified = firebaseUser.isEmailVerified,
            isPhoneNumberVerified = auth.currentUser?.phoneNumber != null,
            profilePhotoUri = uploadedProfilePhotoUrl,
            businessInfo = businessInfo, // Include business info for sellers
            purchaseHistory = listOf(),
            createdAt = getCurrentDate(),
            updatedAt = getCurrentDate(),
            signedAt = getCurrentDate(),
            signedDevices = listOf(deviceInfoProvider.getDeviceData())
        )

        Timber.d("Profile Repo User: ${user.uuid}")
        Timber.d("Profile Repo User Role: ${user.role}")
        Timber.d("Profile Repo User Business Info: ${user.businessInfo}")
        Timber.d("Profile Repo Firebase User: $firebaseUser")

        val result = firestoreRepository.onboardingComplete(user)
        result.getOrThrow() // Propagate any errors
    }

    /**
     * Upload profile photo to Firebase Storage
     * Path: profile_photos/{userUuid}/profile_image_{timestamp}.jpg
     */
    private suspend fun uploadProfilePhoto(photoUri: Uri, userUuid: String): String =
        withContext(ioDispatcher) {
            try {
                // Update Firebase Auth profile
                val photoUpdate = userProfileChangeRequest {
                    setPhotoUri(photoUri)
                }
                auth.currentUser?.updateProfile(photoUpdate)?.await()

                // Upload to Firebase Storage
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "profile_image_$timestamp.jpg"
                val photoRef = storage.reference
                    .child(PROFILE_PHOTOS_FOLDER)
                    .child(userUuid)
                    .child(fileName)

                photoRef.putFile(photoUri).await()
                val downloadUrl = photoRef.downloadUrl.await()

                Timber.d("Profile photo uploaded successfully: $downloadUrl")
                downloadUrl.toString()

            } catch (e: Exception) {
                Timber.e(e, "Failed to upload profile photo")
                ""
            }
        }

    /**
     * Upload seller document to Firebase Storage with organized folder structure
     * Path: seller_info/{userUuid}/{documentType}/{filename}
     */
    //TODO add document validation with AI
    private suspend fun uploadSellerDocument(
        documentUri: Uri,
        userUuid: String,
        documentType: String
    ): String = withContext(ioDispatcher) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileExtension = getFileExtension(documentUri)
            val fileName = "${documentType}_${timestamp}.$fileExtension"

            val documentRef = storage.reference
                .child(SELLER_INFO_FOLDER)
                .child(userUuid)
                .child(documentType)
                .child(fileName)

            documentRef.putFile(documentUri).await()
            val downloadUrl = documentRef.downloadUrl.await()

            Timber.d("Seller document uploaded successfully: $documentType -> $downloadUrl")
            downloadUrl.toString()

        } catch (e: Exception) {
            Timber.e(e, "Failed to upload seller document: $documentType")
            ""
        }
    }

    /**
     * Get file extension from URI
     */
    private fun getFileExtension(uri: Uri): String {
        val uriString = uri.toString()
        return when {
            uriString.contains(".pdf", ignoreCase = true) -> "pdf"
            uriString.contains(".jpg", ignoreCase = true) -> "jpg"
            uriString.contains(".jpeg", ignoreCase = true) -> "jpeg"
            uriString.contains(".png", ignoreCase = true) -> "png"
            else -> "pdf" // Default to PDF for documents
        }
    }

    /**
     * Delete seller documents (useful for re-upload or account deletion)
     */
    suspend fun deleteSellerDocuments(userUuid: String): Result<Unit> = safeApiCall(ioDispatcher) {
        try {
            val sellerFolderRef = storage.reference
                .child(SELLER_INFO_FOLDER)
                .child(userUuid)

            // List all files in the seller's folder
            val listResult = sellerFolderRef.listAll().await()

            // Delete each subfolder and its contents
            listResult.prefixes.forEach { prefix ->
                val subFolderFiles = prefix.listAll().await()
                subFolderFiles.items.forEach { file ->
                    file.delete().await()
                    Timber.d("Deleted seller document: ${file.name}")
                }
            }

            Timber.d("All seller documents deleted for user: $userUuid")
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete seller documents for user: $userUuid")
            throw e
        }
    }

    /**
     * Update specific seller document (for re-upload scenarios)
     */
    suspend fun updateSellerDocument(
        userUuid: String,
        documentType: String,
        newDocumentUri: Uri
    ): Result<String> = safeApiCall(ioDispatcher) {
        try {
            // Delete old documents of this type first
            val documentTypeRef = storage.reference
                .child(SELLER_INFO_FOLDER)
                .child(userUuid)
                .child(documentType)

            val existingFiles = documentTypeRef.listAll().await()
            existingFiles.items.forEach { file ->
                file.delete().await()
                Timber.d("Deleted old document: ${file.name}")
            }

            // Upload new document
            val newDownloadUrl = uploadSellerDocument(newDocumentUri, userUuid, documentType)

            // Update Firestore with new URL
            // This would require updating the FirestoreRepository to have an update method

            newDownloadUrl
        } catch (e: Exception) {
            Timber.e(e, "Failed to update seller document: $documentType")
            throw e
        }
    }

    companion object {
        // Firebase Storage folder structure
        private const val PROFILE_PHOTOS_FOLDER = "profile_photos"
        private const val SELLER_INFO_FOLDER = "seller_info"

        // Document types for organized storage
        const val DOCUMENT_TYPE_TAX = "tax_documents"
        const val DOCUMENT_TYPE_BUSINESS_LICENSE = "business_license"
        const val DOCUMENT_TYPE_IDENTITY = "identity_documents"
        const val DOCUMENT_TYPE_BANK_STATEMENTS = "bank_statements"
        const val DOCUMENT_TYPE_ADDITIONAL = "additional_documents"
    }
}