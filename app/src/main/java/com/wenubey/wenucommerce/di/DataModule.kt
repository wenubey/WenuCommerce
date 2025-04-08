package com.wenubey.wenucommerce.di

import android.util.Base64
import androidx.credentials.CredentialManager
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.wenubey.data.BuildConfig
import com.wenubey.data.repository.AuthRepositoryImpl
import com.wenubey.data.repository.DispatcherProviderImpl
import com.wenubey.data.repository.FirestoreRepositoryImpl
import com.wenubey.data.repository.ProfileRepositoryImpl
import com.wenubey.data.util.DeviceIdProvider
import com.wenubey.data.util.DeviceInfoProvider
import com.wenubey.domain.repository.AuthRepository
import com.wenubey.domain.repository.DispatcherProvider
import com.wenubey.domain.repository.FirestoreRepository
import com.wenubey.domain.repository.ProfileRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import java.security.SecureRandom


val firebaseModule = module {
    single { Firebase.auth }
    single { Firebase.firestore }
    single { CredentialManager.create(get()) }
    single { FirebaseStorage.getInstance() }
}

val repositoryModule = module {
    singleOf(::FirestoreRepositoryImpl).bind<FirestoreRepository>()
    singleOf(::AuthRepositoryImpl).bind<AuthRepository>()
    singleOf(::ProfileRepositoryImpl).bind<ProfileRepository>()
}

val dispatcherModule = module {
    singleOf(::DispatcherProviderImpl).bind<DispatcherProvider>()
}

val googleIdOptionModule = module {
    factory {
        val nonceBytes = ByteArray(60)
        SecureRandom().nextBytes(nonceBytes)
        val nonce = Base64.encodeToString(nonceBytes, Base64.URL_SAFE)
        GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_ID_WEB_CLIENT)
            .setAutoSelectEnabled(true)
            .setNonce(nonce)
            .build()
    }
}

val deviceInfoModule = module {
    single { DeviceIdProvider(get()) }
    single { DeviceInfoProvider(get()) }
}
