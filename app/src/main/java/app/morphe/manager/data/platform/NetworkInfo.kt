/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/data/platform/NetworkInfo.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.data.platform

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import app.morphe.manager.network.service.ConnectionWatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

class NetworkInfo(app: Application) : ConnectionWatcher {
    private val connectivityManager = app.getSystemService<ConnectivityManager>()!!

    private fun getCapabilities() = connectivityManager.activeNetwork
        ?.let { connectivityManager.getNetworkCapabilities(it) }

    fun isConnected() = connectivityManager.activeNetwork != null
    fun isUnmetered() = getCapabilities()?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) != false

    /**
     * Returns true when the active network is metered (e.g. mobile data, hotspot).
     * Returns false when unmetered (Wi-Fi, Ethernet) or when there is no active network.
     */
    fun isMetered() = isConnected() && !isUnmetered()

    // Internet access alone, not validation: networks that block the system's connectivity check
    // never validate, and the manager still works on them
    override fun isOnline() =
        getCapabilities()?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

    /** [isOnline] as it changes, starting with the answer it gives now. */
    val online: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }

            // Another network taking over as the default reports its capabilities right after
            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        trySend(isOnline())
        connectivityManager.registerDefaultNetworkCallback(callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    /**
     * Whether the device is offline while [active] holds, false otherwise. The network is only
     * watched in the meantime, so a screen can ask without keeping a callback registered.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun offlineWhile(active: Flow<Boolean>): Flow<Boolean> = active
        .distinctUntilChanged()
        .flatMapLatest { watching -> if (watching) online.map { !it } else flowOf(false) }

    override suspend fun awaitOnline(timeout: Duration): Boolean =
        withTimeoutOrNull(timeout) { online.first { it } } != null
}
