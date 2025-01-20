package com.wenubey.domain.repository

import com.google.firebase.auth.FirebaseUser
import com.wenubey.domain.util.AuthProvider

interface FirestoreRepository {

    suspend fun addUserToFirestore(
        firebaseUser: FirebaseUser?,
        authProvider: AuthProvider,
    ): Result<Boolean>
}