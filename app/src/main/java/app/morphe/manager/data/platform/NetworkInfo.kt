package app.morphe.manager.data.platform

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService

class NetworkInfo(app: Application) {
    private val connectivityManager = app.getSystemService<ConnectivityManager>()!!

    private fun getCapabilities() = connectivityManager.activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
    fun isConnected() = connectivityManager.activeNetwork != null
    fun isUnmetered() = getCapabilities()?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) != false

    /**
     * Returns true when the active network is metered (e.g. mobile data, hotspot).
     * Returns false when unmetered (Wi-Fi, Ethernet) or when there is no active network.
     */
    fun isMetered() = isConnected() && !isUnmetered()
}
