package com.wenubey.domain.repository

import com.google.firebase.auth.FirebaseUser

interface EmailAuthRepository {
    val currentUser: FirebaseUser?

    suspend fun signUpWithEmailAndPassword(email: String, password: String): Result<Boolean>

    suspend fun sendEmailVerification(): Result<Boolean>

    suspend fun signInWithEmailAndPassword(email: String, password: String): Result<Boolean>

    suspend fun sendPasswordResetEmail(email: String): Result<Boolean>

    fun getAuthState(): Boolean
}