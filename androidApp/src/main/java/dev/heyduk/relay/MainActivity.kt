package dev.heyduk.relay

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import dev.heyduk.relay.presentation.navigation.RelayNavGraph
import dev.heyduk.relay.presentation.theme.RelayTheme
import dev.heyduk.relay.util.RequestNotificationPermissionAndStartConnection
import kotlinx.coroutines.flow.map
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val dataStore: DataStore<Preferences> by inject()

    /**
     * Holds the deep-link kuerzel extracted from a notification tap intent.
     * Observed by a LaunchedEffect inside Compose to navigate after NavHost is ready.
     */
    private var pendingDeepLinkKuerzel by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Extract deep-link from the launching intent
        pendingDeepLinkKuerzel = extractKuerzelFromIntent(intent)

        setContent {
            RelayTheme {
                val navController = rememberNavController()

                // Determine if tokens are already configured
                val isConfigured by dataStore.data
                    .map { prefs ->
                        !prefs[stringPreferencesKey("server_secret")].isNullOrBlank()
                    }
                    .collectAsStateWithLifecycle(initialValue = false)

                // Navigate to deep-link target after NavHost is composed
                val kuerzel = pendingDeepLinkKuerzel
                LaunchedEffect(kuerzel) {
                    if (kuerzel != null) {
                        navController.navigate("chat/$kuerzel") {
                            launchSingleTop = true
                        }
                        pendingDeepLinkKuerzel = null
                    }
                }

                // Auto-start WebSocketService when server secret is configured
                if (isConfigured) {
                    RequestNotificationPermissionAndStartConnection()
                }

                RelayNavGraph(
                    navController = navController,
                    isConfigured = isConfigured
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Update pending deep-link; LaunchedEffect will pick it up
        pendingDeepLinkKuerzel = extractKuerzelFromIntent(intent)
    }

    /**
     * Parses relay://chat/{kuerzel} from the intent data URI.
     */
    private fun extractKuerzelFromIntent(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        if (uri.scheme == "relay" && uri.host == "chat") {
            return uri.pathSegments.firstOrNull()
        }
        return null
    }
}
