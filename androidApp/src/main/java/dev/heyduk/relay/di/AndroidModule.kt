package dev.heyduk.relay.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.heyduk.relay.data.DataStoreOffsetProvider
import dev.heyduk.relay.data.remote.OffsetProvider
import dev.heyduk.relay.db.RelayDatabase
import dev.heyduk.relay.service.NetworkMonitor
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

// Top-level DataStore delegate (recommended single-instance pattern)
private val Context.dataStore by preferencesDataStore(name = "relay_prefs")

/**
 * Android-specific Koin module providing platform bindings.
 *
 * Does NOT provide bot token strings as Koin singletons.
 * PollingService reads tokens from DataStore at start time.
 */
val androidModule = module {
    // DataStore
    single { androidContext().dataStore }

    // OffsetProvider (DataStore-backed)
    single<OffsetProvider> { DataStoreOffsetProvider(get()) }

    // NetworkMonitor
    single { NetworkMonitor(androidContext()) }

    // SQLDelight database driver and database
    single { AndroidSqliteDriver(RelayDatabase.Schema, androidContext(), "relay.db") }
    single { RelayDatabase(get<AndroidSqliteDriver>()) }
}
