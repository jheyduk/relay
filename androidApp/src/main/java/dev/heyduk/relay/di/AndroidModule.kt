package dev.heyduk.relay.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.heyduk.relay.data.DataStoreOffsetProvider
import dev.heyduk.relay.data.remote.OffsetProvider
import dev.heyduk.relay.db.RelayDatabase
import dev.heyduk.relay.presentation.chat.ChatViewModel
import dev.heyduk.relay.presentation.session.SessionListViewModel
import dev.heyduk.relay.presentation.setup.SetupViewModel
import dev.heyduk.relay.presentation.status.StatusViewModel
import dev.heyduk.relay.service.NetworkMonitor
import dev.heyduk.relay.service.NotificationHelper
import dev.heyduk.relay.voice.AudioRecorder
import dev.heyduk.relay.voice.TtsManager
import dev.heyduk.relay.voice.WhisperManager
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
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

    // Read bot tokens from DataStore when singletons are first resolved.
    // runBlocking is acceptable here — called once, after user completes setup.
    single(named("relayBotToken")) {
        runBlocking {
            val prefs = get<DataStore<Preferences>>().data.first()
            prefs[stringPreferencesKey("relay_bot_token")] ?: ""
        }
    }
    single(named("commandBotToken")) {
        runBlocking {
            val prefs = get<DataStore<Preferences>>().data.first()
            prefs[stringPreferencesKey("command_bot_token")] ?: ""
        }
    }
    single(named("chatId")) {
        runBlocking {
            val prefs = get<DataStore<Preferences>>().data.first()
            prefs[stringPreferencesKey("chat_id")] ?: ""
        }
    }

    // OffsetProvider (DataStore-backed)
    single<OffsetProvider> { DataStoreOffsetProvider(get()) }

    // NetworkMonitor
    single { NetworkMonitor(androidContext()) }

    // NotificationHelper
    single { NotificationHelper(androidContext()) }

    // SQLDelight database driver and database
    single { AndroidSqliteDriver(RelayDatabase.Schema, androidContext(), "relay.db") }
    single { RelayDatabase(get<AndroidSqliteDriver>()) }

    // Voice
    single { WhisperManager(androidContext()) }
    single { TtsManager(androidContext()) }
    single { AudioRecorder(androidContext().cacheDir) }

    // ViewModels
    viewModel { SetupViewModel(get(), get()) }
    viewModel { StatusViewModel(get(), get()) }
    viewModel { SessionListViewModel(get(), get(), get()) }
    viewModel { params -> ChatViewModel(get(), get(), get(), get(), params.get<String>()) }
}
