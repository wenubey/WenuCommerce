package com.wenubey.wenucommerce

import android.app.Application
import com.google.firebase.FirebaseApp
import com.wenubey.data.local.SyncManager
import com.wenubey.wenucommerce.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import timber.log.Timber

class WenuCommerce: Application() {
    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this@WenuCommerce)

        startKoin {
            androidLogger()
            androidContext(this@WenuCommerce)
            modules(appModules)
        }

        // Start Firestore-to-Room sync listeners
        val syncManager: SyncManager = org.koin.java.KoinJavaComponent.get(SyncManager::class.java)
        syncManager.startSync()

        if(BuildConfig.DEBUG) { Timber.plant(Timber.DebugTree()) }
    }
}