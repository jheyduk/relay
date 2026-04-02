package dev.heyduk.relay.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

/**
 * Wraps [ConnectivityManager.NetworkCallback] into a reactive [Flow<Boolean>].
 * Emits true when the device has an active network, false when connectivity is lost.
 */
class NetworkMonitor(context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val isConnected: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        // Emit current state immediately
        val current = connectivityManager.activeNetwork != null
        trySend(current)
        connectivityManager.registerDefaultNetworkCallback(callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    /**
     * Suspends until the device has an active network connection.
     */
    suspend fun awaitConnected() {
        isConnected.first { it }
    }
}
