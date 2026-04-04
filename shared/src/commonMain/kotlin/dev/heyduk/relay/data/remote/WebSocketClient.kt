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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

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
        println("WebSocketClient@${hashCode()}: connectWithRetry starting")
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
        val s = session
        println("WebSocketClient@${hashCode()}: send() called, session=${s != null}, state=${_connectionState.value}, msg=${message.take(80)}")
        if (s == null) {
            println("WebSocketClient: send DROPPED (no session)")
            return
        }
        try {
            s.send(Frame.Text(message))
            println("WebSocketClient: send OK")
        } catch (e: Exception) {
            println("WebSocketClient: send ERROR: ${e.message}")
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
    /**
     * Sends a file attachment as base64-encoded data.
     * Server saves it and types the path into the session.
     */
    suspend fun sendAttachment(kuerzel: String, filename: String, base64Data: String) {
        val payload = buildJsonObject {
            put("action", "attachment")
            put("kuerzel", kuerzel)
            put("filename", filename)
            put("data", base64Data)
        }
        send(payload.toString())
    }

    suspend fun sendRawCommand(command: String) {
        val payload = json.encodeToString(
            mapOf("action" to "raw_command", "command" to command)
        )
        send(payload)
    }

    /**
     * Sends a structured answer payload for an AskUserQuestion prompt.
     *
     * @param kuerzel Session short name
     * @param type Answer type: "single", "multi", or "text"
     * @param selections 1-based option indices selected by the user
     * @param text Free text for "text" type answers
     * @param optionCount Total number of options in the question
     */
    suspend fun sendAnswer(
        kuerzel: String,
        type: String,
        selections: List<Int>,
        text: String?,
        optionCount: Int
    ) {
        val payload = buildJsonObject {
            put("action", "answer")
            put("kuerzel", kuerzel)
            put("type", type)
            putJsonArray("selections") { selections.forEach { add(JsonPrimitive(it)) } }
            put("option_count", optionCount)
            if (text != null) put("text", text)
        }
        send(payload.toString())
    }

    /**
     * Sends audio data as a binary WebSocket frame with the kuerzel+WAV protocol.
     *
     * Binary frame layout:
     * - Bytes 0-1: uint16 big-endian = kuerzel UTF-8 byte length
     * - Bytes 2..(2+len-1): kuerzel as UTF-8 bytes
     * - Bytes (2+len)..end: WAV audio data
     *
     * @param kuerzel Session short name
     * @param audioData Raw WAV audio bytes
     */
    suspend fun sendAudio(kuerzel: String, audioData: ByteArray) {
        try {
            val kuerzelBytes = kuerzel.encodeToByteArray()
            val payload = ByteArray(2 + kuerzelBytes.size + audioData.size)
            // Write kuerzel length as uint16 big-endian
            payload[0] = (kuerzelBytes.size shr 8).toByte()
            payload[1] = (kuerzelBytes.size and 0xFF).toByte()
            // Write kuerzel bytes
            kuerzelBytes.copyInto(payload, destinationOffset = 2)
            // Write audio data
            audioData.copyInto(payload, destinationOffset = 2 + kuerzelBytes.size)
            session?.send(Frame.Binary(true, payload))
        } catch (_: Exception) {
            // WebSocket may be closing -- ignore send failures
        }
    }

    /**
     * Requests the directory list from the server for session creation.
     */
    suspend fun sendListDirectories() {
        val payload = json.encodeToString(mapOf("action" to "list_directories"))
        send(payload)
    }

    /**
     * Requests creation of a new Claude Code session.
     * @param path Absolute path to the project directory
     * @param kuerzel Short session name (e.g. "relay")
     * @param flags CLI flags (e.g. "--dangerously-skip-permissions"), empty string for none
     */
    suspend fun sendCreateSession(path: String, kuerzel: String, flags: String) {
        val payload = buildJsonObject {
            put("action", "create_session")
            put("path", path)
            put("kuerzel", kuerzel)
            put("flags", flags)
        }
        send(payload.toString())
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
