package dev.heyduk.relay.data.remote

import dev.heyduk.relay.domain.model.RelayUpdate
import dev.heyduk.relay.util.currentTimeMillis
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * WebSocket client that connects to the Mac-side relay-server.
 * Parses incoming JSON frames via [RelayMessageParser] into [RelayUpdate] objects
 * emitted on the [updates] SharedFlow. Reconnects automatically with exponential backoff.
 *
 * Backoff schedule on disconnect: 1s -> 2s -> 4s -> 8s -> 16s -> 30s (capped).
 */
class WebSocketClient(
    private val httpClient: HttpClient,
    private val parser: RelayMessageParser
) {
    private val _updates = MutableSharedFlow<RelayUpdate>(extraBufferCapacity = 64)
    val updates: SharedFlow<RelayUpdate> = _updates

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val json = Json { encodeDefaults = true }

    @Volatile
    private var session: io.ktor.websocket.DefaultWebSocketSession? = null

    /**
     * Connects to the relay server and loops forever, reconnecting on failure.
     * This is a suspend function that never returns (unless cancelled).
     *
     * @param serverUrl WebSocket URL, e.g. "ws://192.168.1.10:9080"
     * @param secret Authentication token appended as query parameter
     */
    suspend fun connectWithRetry(serverUrl: String, secret: String) {
        var backoffMs = 0L

        while (currentCoroutineContext().isActive) {
            if (backoffMs > 0) {
                delay(backoffMs)
            }

            _connectionState.value = ConnectionState.CONNECTING

            try {
                httpClient.webSocket("$serverUrl?token=$secret") {
                    _connectionState.value = ConnectionState.CONNECTED
                    session = this
                    // Reset backoff on successful connection
                    backoffMs = 0L

                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            val updateId = currentTimeMillis()
                            val parsed = parser.parse(
                                updateId = updateId,
                                messageText = text,
                                timestamp = currentTimeMillis() / 1000
                            )
                            if (parsed != null) {
                                _updates.emit(parsed)
                            }
                        }
                    }

                    // Connection closed normally
                    session = null
                }
            } catch (e: CancellationException) {
                session = null
                _connectionState.value = ConnectionState.DISCONNECTED
                throw e // CRITICAL: never swallow cancellation
            } catch (e: Exception) {
                session = null
                println("WebSocketClient: connection error: ${e::class.simpleName}: ${e.message}")
            }

            _connectionState.value = ConnectionState.DISCONNECTED
            // After disconnect, set backoff for reconnect attempt
            backoffMs = if (backoffMs == 0L) 1000L
                        else (backoffMs * 2).coerceAtMost(30_000L)
        }
    }

    /**
     * Sends a raw text frame over the active WebSocket session.
     * Silently fails if the session is not connected or closing.
     */
    suspend fun send(message: String) {
        try {
            session?.send(Frame.Text(message))
        } catch (_: Exception) {
            // WebSocket may be closing — ignore send failures
        }
    }

    /**
     * Sends a structured command to a specific session via the relay server.
     *
     * @param kuerzel Session short name (e.g. "infra")
     * @param message Command or message text
     */
    suspend fun sendCommand(kuerzel: String, message: String) {
        val payload = json.encodeToString(
            mapOf("action" to "command", "kuerzel" to kuerzel, "message" to message)
        )
        send(payload)
    }

    /**
     * Sends a raw command string (e.g. "/ls") via the relay server.
     *
     * @param command The raw command to send
     */
    suspend fun sendRawCommand(command: String) {
        val payload = json.encodeToString(
            mapOf("action" to "raw_command", "command" to command)
        )
        send(payload)
    }

    /**
     * Gracefully closes the WebSocket connection.
     */
    suspend fun disconnect() {
        try {
            session?.close()
        } catch (_: Exception) {
            // Ignore close errors
        }
        session = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}
