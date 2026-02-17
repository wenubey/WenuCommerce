package com.wenubey.domain.repository

import com.google.firebase.auth.FirebaseUser
import com.wenubey.domain.model.onboard.VerificationStatus
import com.wenubey.domain.model.user.User
import com.wenubey.domain.util.AuthProvider
import kotlinx.coroutines.flow.Flow

interface FirestoreRepository {

    suspend fun addUserToFirestore(
        firebaseUser: FirebaseUser?,
        authProvider: AuthProvider,
    ): Result<Boolean>

    suspend fun getUser(uid: String): Result<User>

    suspend fun updateSignedDevice(userUid: String?): Result<Unit>

    suspend fun onboardingComplete(user: User): Result<Unit>

    fun updateFcmToken(token: String): Result<Unit>

    fun observeSellersByStatus(status: VerificationStatus): Flow<List<User>>

    fun observePendingResubmittedSellerCount(): Flow<Int>

    suspend fun updateSellerApprovalStatus(
        sellerId: String,
        status: VerificationStatus,
        notes: String,
    ): Result<Unit>

}