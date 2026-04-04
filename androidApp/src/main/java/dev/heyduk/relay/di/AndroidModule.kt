package dev.heyduk.relay.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.heyduk.relay.db.RelayDatabase
import dev.heyduk.relay.presentation.chat.ChatViewModel
import dev.heyduk.relay.presentation.session.CreateSessionViewModel
import dev.heyduk.relay.presentation.session.SessionListViewModel
import dev.heyduk.relay.presentation.setup.SetupViewModel
import dev.heyduk.relay.presentation.status.StatusViewModel
import dev.heyduk.relay.service.NetworkMonitor
import dev.heyduk.relay.service.NotificationHelper
import dev.heyduk.relay.service.NsdDiscovery
import dev.heyduk.relay.voice.AudioRecorder
import dev.heyduk.relay.voice.TtsManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

// Top-level DataStore delegate (recommended single-instance pattern)
private val Context.dataStore by preferencesDataStore(name = "relay_prefs")

/**
 * Android-specific Koin module providing platform bindings.
 *
 * WebSocketService reads server_secret from DataStore at start time.
 * All Telegram-specific singletons (bot tokens, offset provider) have been removed.
 */
val androidModule = module {
    // DataStore
    single { androidContext().dataStore }

    // Network discovery and monitoring
    single { NsdDiscovery(androidContext()) }
    single { NetworkMonitor(androidContext()) }

    // NotificationHelper
    single { NotificationHelper(androidContext()) }

    // SQLDelight database driver and database
    single { AndroidSqliteDriver(RelayDatabase.Schema, androidContext(), "relay.db") }
    single { RelayDatabase(get<AndroidSqliteDriver>()) }

    // Voice
    single { TtsManager(androidContext()) }
    single { AudioRecorder(androidContext().cacheDir) }

    // ViewModels
    viewModel { SetupViewModel(get()) }
    viewModel { StatusViewModel(get(), get()) }
    viewModel { SessionListViewModel(get(), get(), get(), get()) }
    viewModel { CreateSessionViewModel(get()) }
    viewModel { params -> ChatViewModel(get(), get(), get(), params.get<String>()) }
}
