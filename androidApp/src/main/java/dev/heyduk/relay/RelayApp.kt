package dev.heyduk.relay

import android.app.Application
import dev.heyduk.relay.di.androidModule
import dev.heyduk.relay.di.sharedModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import timber.log.Timber

class RelayApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        startKoin {
            androidContext(this@RelayApp)
            modules(sharedModule, androidModule)
        }
    }
}
