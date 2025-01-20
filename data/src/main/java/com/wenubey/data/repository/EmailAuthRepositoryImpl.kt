package com.wenubey.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.wenubey.domain.util.AuthProvider
import com.wenubey.data.util.safeApiCall
import com.wenubey.domain.repository.EmailAuthRepository
import com.wenubey.domain.repository.FirestoreRepository
import kotlinx.coroutines.tasks.await
import timber.log.Timber

class EmailAuthRepositoryImpl(
    private val auth: FirebaseAuth,
    private val firestoreRepo: FirestoreRepository,
) : EmailAuthRepository {

    override val currentUser: FirebaseUser?
        get() = auth.currentUser

    override suspend fun signUpWithEmailAndPassword(
        email: String,
        password: String
    ): Result<Boolean> =
        safeApiCall {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val isNewUser = result.additionalUserInfo?.isNewUser ?: false
            if (isNewUser) {
                firestoreRepo.addUserToFirestore(currentUser, AuthProvider.EMAIL)
            }
            true
        }

    override suspend fun sendEmailVerification(): Result<Boolean> =
        safeApiCall {
            auth.currentUser?.sendEmailVerification()?.await()
            true
        }

    override suspend fun signInWithEmailAndPassword(
        email: String,
        password: String
    ): Result<Boolean> = safeApiCall {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { Timber.w("signInWithEmailAndPassword:Success") }
            .addOnFailureListener { Timber.e("signInWithEmailAndPassword:Error", it) }
            .await()
        true
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Boolean> =
        safeApiCall {
            auth.sendPasswordResetEmail(email).await()
            true
        }

    override fun getAuthState(): Boolean {
        return auth.currentUser == null
    }

}