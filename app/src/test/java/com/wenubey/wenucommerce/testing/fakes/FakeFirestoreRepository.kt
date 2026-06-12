package com.wenubey.wenucommerce.testing.fakes

import com.google.firebase.auth.FirebaseUser
import com.wenubey.domain.model.onboard.VerificationStatus
import com.wenubey.domain.model.user.User
import com.wenubey.domain.repository.FirestoreRepository
import com.wenubey.domain.util.AuthProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeFirestoreRepository : FirestoreRepository {

    private val sellersByStatus = MutableStateFlow<Map<VerificationStatus, List<User>>>(emptyMap())
    private val pendingResubmittedCount = MutableStateFlow(0)

    val getUserCalls = mutableListOf<String>()
    val updateSignedDeviceCalls = mutableListOf<String?>()
    val onboardingCompleteCalls = mutableListOf<User>()
    val updateFcmTokenCalls = mutableListOf<String>()
    val updateSellerApprovalCalls = mutableListOf<Triple<String, VerificationStatus, String>>()

    var getUserResult: (String) -> Result<User> = {
        Result.failure(NoSuchElementException("not stubbed"))
    }
    var updateSignedDeviceResult: Result<Unit> = Result.success(Unit)
    var onboardingCompleteResult: Result<Unit> = Result.success(Unit)
    var updateSellerApprovalResult: Result<Unit> = Result.success(Unit)

    fun emitSellers(status: VerificationStatus, sellers: List<User>) {
        sellersByStatus.value = sellersByStatus.value + (status to sellers)
    }

    fun emitPendingResubmittedCount(count: Int) {
        pendingResubmittedCount.value = count
    }

    override suspend fun addUserToFirestore(
        firebaseUser: FirebaseUser?,
        authProvider: AuthProvider,
    ): Result<Boolean> = Result.success(true)

    override suspend fun getUser(uid: String): Result<User> {
        getUserCalls.add(uid)
        return getUserResult(uid)
    }

    override suspend fun updateSignedDevice(userUid: String?): Result<Unit> {
        updateSignedDeviceCalls.add(userUid)
        return updateSignedDeviceResult
    }

    override suspend fun onboardingComplete(user: User): Result<Unit> {
        onboardingCompleteCalls.add(user)
        return onboardingCompleteResult
    }

    override fun updateFcmToken(token: String): Result<Unit> {
        updateFcmTokenCalls.add(token)
        return Result.success(Unit)
    }

    override fun observeSellersByStatus(status: VerificationStatus): Flow<List<User>> =
        sellersByStatus.map { it[status].orEmpty() }

    override fun observePendingResubmittedSellerCount(): Flow<Int> = pendingResubmittedCount

    override suspend fun updateSellerApprovalStatus(
        sellerId: String,
        status: VerificationStatus,
        notes: String,
    ): Result<Unit> {
        updateSellerApprovalCalls.add(Triple(sellerId, status, notes))
        return updateSellerApprovalResult
    }
}
