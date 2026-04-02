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
import dev.heyduk.relay.data.remote.OffsetProvider
import dev.heyduk.relay.data.remote.RelayMessageParser
import dev.heyduk.relay.data.remote.TelegramApiImpl
import dev.heyduk.relay.data.remote.TelegramPoller
import dev.heyduk.relay.db.RelayDatabase
import dev.heyduk.relay.domain.model.RelayMessageType
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import timber.log.Timber

/**
 * Foreground service that hosts the Telegram long-polling loop.
 *
 * Reads bot tokens from DataStore at start time and constructs TelegramApi
 * and TelegramPoller directly -- tokens are NOT injected via Koin singletons.
 * Guards against missing configuration by calling [stopSelf].
 */
class PollingService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "relay_polling"
    }

    private val dataStore: DataStore<Preferences> by inject()
    private val offsetProvider: OffsetProvider by inject()
    private val networkMonitor: NetworkMonitor by inject()
    private val httpClient: HttpClient by inject()
    private val database: RelayDatabase by inject()
    private val notificationHelper: NotificationHelper by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = buildNotification("Relay connected")
        startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)

        serviceScope.launch {
            val prefs = dataStore.data.first()
            val relayToken = prefs[stringPreferencesKey("relay_bot_token")] ?: ""
            val cmdToken = prefs[stringPreferencesKey("command_bot_token")] ?: ""
            val chatId = prefs[stringPreferencesKey("chat_id")] ?: ""

            if (relayToken.isBlank() || cmdToken.isBlank() || chatId.isBlank()) {
                Timber.e("PollingService: tokens not configured, stopping")
                updateNotification("Missing configuration")
                stopSelf()
                return@launch
            }

            val relayApi = TelegramApiImpl(
                httpClient = httpClient,
                botToken = relayToken,
                chatId = chatId
            )
            val poller = TelegramPoller(
                api = relayApi,
                parser = RelayMessageParser,
                offsetProvider = offsetProvider
            )

            // Collect updates and insert into the database
            launch {
                poller.updates.collect { update ->
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

            // Update notification based on connectivity
            launch {
                networkMonitor.isConnected.collect { connected ->
                    val text = if (connected) "Relay connected" else "Reconnecting..."
                    updateNotification(text)
                }
            }

            // Polling loop with network awareness
            while (isActive) {
                networkMonitor.awaitConnected()
                try {
                    poller.pollLoop() // Runs until error or cancellation
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Poll loop error, awaiting reconnect")
                }
            }
        }

        return START_STICKY
    }

    /**
     * Called on Android 15+ when the system wants to stop the foreground service.
     */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onTimeout(startId: Int, fgsType: Int) {
        Timber.w("PollingService: system timeout for foreground service (startId=$startId)")
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Relay Polling",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows Relay polling connection status"
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
