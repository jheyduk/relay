package dev.heyduk.relay.service

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber

/**
 * Discovers relay-server instances on the local network via mDNS (NSD).
 * Looks for services advertising `_relay._tcp.` and resolves their host/port.
 */
class NsdDiscovery(private val context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    /**
     * Emits resolved [NsdServiceInfo] objects for each `_relay._tcp` service found.
     * The flow completes when the collector cancels (which also stops discovery).
     */
    fun discover(): Flow<NsdServiceInfo> = callbackFlow {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceType.contains("_relay._tcp")) {
                    val resolveListener = object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Timber.w("mDNS resolve failed for %s (error=%d)", serviceInfo.serviceName, errorCode)
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            Timber.d("mDNS resolved: %s at %s:%d", serviceInfo.serviceName, serviceInfo.host?.hostAddress, serviceInfo.port)
                            trySend(serviceInfo)
                        }
                    }
                    nsdManager.resolveService(info, resolveListener)
                }
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                Timber.d("mDNS service lost: %s", info.serviceName)
            }

            override fun onDiscoveryStarted(serviceType: String) {
                Timber.d("mDNS discovery started for %s", serviceType)
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Timber.d("mDNS discovery stopped for %s", serviceType)
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Timber.e("mDNS start discovery failed (error=%d)", errorCode)
                close()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Timber.w("mDNS stop discovery failed (error=%d)", errorCode)
            }
        }

        nsdManager.discoverServices("_relay._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)

        awaitClose {
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                Timber.w(e, "Stop discovery failed")
            }
        }
    }

    /**
     * Converts a resolved [NsdServiceInfo] into a WebSocket URL string.
     */
    fun resolveToUrl(info: NsdServiceInfo): String {
        return "ws://${info.host.hostAddress}:${info.port}"
    }
}
