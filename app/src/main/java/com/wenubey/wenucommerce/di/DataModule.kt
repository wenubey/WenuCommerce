package com.wenubey.wenucommerce.di

import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.wenubey.data.repository.EmailAuthRepositoryImpl
import com.wenubey.data.repository.FirestoreRepositoryImpl
import com.wenubey.domain.repository.EmailAuthRepository
import com.wenubey.domain.repository.FirestoreRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module


val firebaseModule = module {
    single { Firebase.auth }
    single { Firebase.firestore }
}

val repositoryModule = module {
    singleOf(::EmailAuthRepositoryImpl).bind<EmailAuthRepository>()
    singleOf(::FirestoreRepositoryImpl).bind<FirestoreRepository>()
}

