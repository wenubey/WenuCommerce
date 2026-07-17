package com.wenubey.wenucommerce

import android.app.Application
import com.google.firebase.FirebaseApp
import com.stripe.android.PaymentConfiguration
import com.wenubey.data.local.SyncManager
import com.wenubey.wenucommerce.di.appModules
import com.wenubey.wenucommerce.notification.NotificationChannels
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import timber.log.Timber

class WenuCommerce: Application() {
    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this@WenuCommerce)

        PaymentConfiguration.init(applicationContext, BuildConfig.STRIPE_PUBLISHABLE_KEY)

        // Phase 8 (08-03 / NOTF-06): create the three notification channels once,
        // before Koin/first-notification, replacing per-service channel creation.
        NotificationChannels.createAll(this)

        startKoin {
            androidLogger()
            androidContext(this@WenuCommerce)
            workManagerFactory()
            modules(appModules)
        }

        // Start Firestore-to-Room sync listeners
        val syncManager: SyncManager = org.koin.java.KoinJavaComponent.get(SyncManager::class.java)
        syncManager.startSync()

        if(BuildConfig.DEBUG) { Timber.plant(Timber.DebugTree()) }
    }
}
