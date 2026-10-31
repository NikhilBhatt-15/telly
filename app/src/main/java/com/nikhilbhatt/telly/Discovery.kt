package com.nikhilbhatt.telly

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class FoundTv(val name: String, val host: String)

/**
 * Finds Android TV / Google TV devices on the Wi-Fi via mDNS. Every device running the remote
 * service advertises `_androidtvremote2._tcp`. Emits the growing list of devices found so far.
 */
fun discoverTvs(context: Context): Flow<List<FoundTv>> = callbackFlow {
    val nsd = context.getSystemService(NsdManager::class.java)
    val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    val lock = wifi?.createMulticastLock("telly-discovery")?.apply {
        setReferenceCounted(false)
        acquire()
    }
    val found = linkedMapOf<String, FoundTv>()
    // Older Android versions resolve only one service at a time, so queue them up.
    val toResolve = Channel<NsdServiceInfo>(Channel.UNLIMITED)

    val listener = object : NsdManager.DiscoveryListener {
        override fun onServiceFound(info: NsdServiceInfo) {
            toResolve.trySend(info)
        }

        override fun onServiceLost(info: NsdServiceInfo) {
            synchronized(found) {
                if (found.remove(info.serviceName) != null) trySend(found.values.toList())
            }
        }

        override fun onDiscoveryStarted(serviceType: String) = Unit
        override fun onDiscoveryStopped(serviceType: String) = Unit
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            close(IllegalStateException("Discovery failed ($errorCode)"))
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
    }

    launch {
        for (info in toResolve) {
            val resolved = resolve(nsd, info) ?: continue
            @Suppress("DEPRECATION")
            val host = resolved.host?.hostAddress ?: continue
            synchronized(found) {
                found[resolved.serviceName] = FoundTv(resolved.serviceName, host)
                trySend(found.values.toList())
            }
        }
    }

    trySend(emptyList())
    nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    awaitClose {
        runCatching { nsd.stopServiceDiscovery(listener) }
        lock?.release()
        toResolve.close()
    }
}

@Suppress("DEPRECATION")
private suspend fun resolve(nsd: NsdManager, info: NsdServiceInfo): NsdServiceInfo? =
    suspendCancellableCoroutine { cont ->
        nsd.resolveService(
            info,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    if (cont.isActive) cont.resume(null)
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    if (cont.isActive) cont.resume(serviceInfo)
                }
            },
        )
    }

private const val SERVICE_TYPE = "_androidtvremote2._tcp."
