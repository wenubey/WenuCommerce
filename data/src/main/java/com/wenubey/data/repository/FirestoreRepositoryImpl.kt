package com.wenubey.data.repository

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.wenubey.domain.repository.FirestoreRepository
import com.wenubey.domain.util.AuthProvider

class FirestoreRepositoryImpl(
    private val firestore: FirebaseFirestore,
): FirestoreRepository {

    override suspend fun addUserToFirestore(
        firebaseUser: FirebaseUser?,
        authProvider: AuthProvider
    ): Result<Boolean> {
        TODO("Not yet implemented")
    }
}