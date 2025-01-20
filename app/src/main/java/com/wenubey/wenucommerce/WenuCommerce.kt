package com.wenubey.wenucommerce

import android.app.Application
import com.google.firebase.FirebaseApp
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

        if(BuildConfig.DEBUG) { Timber.plant(Timber.DebugTree()) }
    }
}