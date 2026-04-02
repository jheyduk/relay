package dev.heyduk.relay.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import dev.heyduk.relay.data.remote.OffsetProvider
import kotlinx.coroutines.flow.first

/**
 * [OffsetProvider] implementation that persists the polling offset
 * in AndroidX DataStore Preferences.
 */
class DataStoreOffsetProvider(private val dataStore: DataStore<Preferences>) : OffsetProvider {
    private val offsetKey = longPreferencesKey("polling_offset")

    override suspend fun getOffset(): Long =
        dataStore.data.first()[offsetKey] ?: 0L

    override suspend fun setOffset(offset: Long) {
        dataStore.edit { prefs ->
            prefs[offsetKey] = offset
        }
    }
}
