package com.wenubey.data.repository

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.wenubey.data.util.DeviceInfoProvider
import com.wenubey.data.util.getCurrentDate
import com.wenubey.data.util.safeApiCall
import com.wenubey.domain.model.Gender
import com.wenubey.domain.model.User
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.FirestoreRepository
import com.wenubey.domain.repository.ProfileRepository
import timber.log.Timber

class ProfileRepositoryImpl(
    private val firestoreRepository: FirestoreRepository,
    private val auth: FirebaseAuth,
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
    ): Result<Unit> = safeApiCall(ioDispatcher) {
        val user = User(
            uuid = firebaseUser?.uid,
            role = "User",
            name = name,
            surname = surname,
            phoneNumber = phoneNumber,
            dateOfBirth = dateOfBirth,
            gender = gender,
            email = firebaseUser?.email ?: "",
            address = address,
            isEmailVerified = firebaseUser?.isEmailVerified ?: false,
            isPhoneNumberVerified = auth.currentUser?.phoneNumber != null,
            profilePhotoUri = photoUrl,
            purchaseHistory = listOf(),
            createdAt = getCurrentDate(),
            updatedAt = getCurrentDate(),
            signedAt = getCurrentDate(),
            signedDevices = listOf(deviceInfoProvider.getDeviceData())
        )
        Timber.d("Profile Repo User: ${user.uuid}")
        Timber.d("Profile Repo User: $firebaseUser")
        firestoreRepository.onboardingComplete(user)
    }



}