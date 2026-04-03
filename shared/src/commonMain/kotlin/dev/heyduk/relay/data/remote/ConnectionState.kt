package dev.heyduk.relay.data.remote

/**
 * Represents the current state of the WebSocket connection to the relay server.
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}
