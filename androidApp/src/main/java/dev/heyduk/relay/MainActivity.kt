package dev.heyduk.relay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import dev.heyduk.relay.presentation.navigation.RelayNavGraph
import kotlinx.coroutines.flow.map
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val dataStore: DataStore<Preferences> by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val navController = rememberNavController()

                // Determine if tokens are already configured
                val isConfigured by dataStore.data
                    .map { prefs ->
                        !prefs[stringPreferencesKey("relay_bot_token")].isNullOrBlank()
                                && !prefs[stringPreferencesKey("command_bot_token")].isNullOrBlank()
                                && !prefs[stringPreferencesKey("chat_id")].isNullOrBlank()
                    }
                    .collectAsStateWithLifecycle(initialValue = false)

                RelayNavGraph(
                    navController = navController,
                    isConfigured = isConfigured
                )
            }
        }
    }
}
