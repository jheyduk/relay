package dev.heyduk.relay.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.heyduk.relay.data.remote.ConnectionState
import dev.heyduk.relay.data.remote.WebSocketClient
import dev.heyduk.relay.db.RelayDatabase
import dev.heyduk.relay.domain.model.RelayMessageType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.android.inject
import timber.log.Timber

/**
 * Foreground service that maintains the WebSocket connection to the relay-server.
 *
 * Discovers the server via mDNS (NsdManager) with a WireGuard IP fallback,
 * reads the shared secret from DataStore, then connects using [WebSocketClient].
 * Inserts received updates into the database and triggers notifications.
 */
class WebSocketService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "relay_connection"
    }

    private val dataStore: DataStore<Preferences> by inject()
    private val networkMonitor: NetworkMonitor by inject()
    private val webSocketClient: WebSocketClient by inject()
    private val database: RelayDatabase by inject()
    private val notificationHelper: NotificationHelper by inject()
    private val nsdDiscovery: NsdDiscovery by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var connectionStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = buildNotification("Connecting...")
        startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)

        // Guard against multiple connection loops from repeated startService() calls
        if (connectionStarted) {
            return START_STICKY
        }
        connectionStarted = true

        serviceScope.launch {
            val prefs = dataStore.data.first()
            val secret = prefs[stringPreferencesKey("server_secret")] ?: ""
            val wireguardIp = prefs[stringPreferencesKey("wireguard_ip")] ?: ""

            if (secret.isBlank()) {
                Timber.e("WebSocketService: server secret not configured, stopping")
                updateNotification("Missing configuration")
                stopSelf()
                return@launch
            }

            // Discover the relay server
            val serverUrl = discoverServer(wireguardIp)
            Timber.i("WebSocketService: connecting to %s", serverUrl)

            // Collect updates and insert into the database
            launch {
                webSocketClient.updates.collect { update ->
                    database.messagesQueries.insertOrIgnore(
                        update_id = update.updateId,
                        session = update.session,
                        type = update.type.name,
                        message = update.message,
                        status = update.status?.name,
                        tool_name = update.toolName,
                        command = update.command,
                        file_path = update.filePath,
                        timestamp = update.timestamp,
                        is_from_relay = if (update.isFromRelay) 1L else 0L,
                        callback_response = null
                    )

                    // Trigger notifications for permission and completion messages
                    when (update.type) {
                        RelayMessageType.PERMISSION -> notificationHelper.showPermissionNotification(update)
                        RelayMessageType.COMPLETION -> notificationHelper.showCompletionNotification(update)
                        else -> { /* no notification for other types */ }
                    }
                }
            }

            // Update notification based on connection state
            launch {
                webSocketClient.connectionState.collect { state ->
                    val text = when (state) {
                        ConnectionState.CONNECTED -> "Relay connected"
                        ConnectionState.CONNECTING -> "Connecting..."
                        ConnectionState.DISCONNECTED -> "Disconnected — reconnecting"
                    }
                    updateNotification(text)
                }
            }

            // Connection loop with network awareness
            while (isActive) {
                networkMonitor.awaitConnected()
                try {
                    webSocketClient.connectWithRetry(serverUrl, secret)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "WebSocket error, awaiting reconnect")
                }
            }
        }

        return START_STICKY
    }

    /**
     * Discovers the relay server URL.
     * Tries mDNS discovery first (5 second timeout), falls back to configured WireGuard IP.
     */
    private suspend fun discoverServer(wireguardIp: String): String {
        // Try mDNS discovery with a 5-second timeout
        val mDnsResult = withTimeoutOrNull(5000) {
            nsdDiscovery.discover().firstOrNull()
        }

        if (mDnsResult != null) {
            val url = nsdDiscovery.resolveToUrl(mDnsResult)
            Timber.i("Server found via mDNS: %s", url)
            return url
        }

        // Fall back to WireGuard IP
        if (wireguardIp.isNotBlank()) {
            val url = "ws://$wireguardIp:9784"
            Timber.i("Using WireGuard fallback: %s", url)
            return url
        }

        throw IllegalStateException("No server found — configure WireGuard IP or ensure local network")
    }

    /**
     * Called on Android 15+ when the system wants to stop the foreground service.
     */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onTimeout(startId: Int, fgsType: Int) {
        Timber.w("WebSocketService: system timeout for foreground service (startId=%d)", startId)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Relay Connection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows relay WebSocket connection status"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Relay")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }
}
