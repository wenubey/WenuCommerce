package com.wenubey.data.repository

import com.google.firebase.auth.FirebaseUser
import com.wenubey.domain.repository.FirestoreRepository
import com.wenubey.domain.util.AuthProvider

class FirestoreRepositoryImpl(): FirestoreRepository {

    override suspend fun addUserToFirestore(
        firebaseUser: FirebaseUser?,
        authProvider: AuthProvider
    ): Result<Boolean> {
        return Result.success(true)
    }
}