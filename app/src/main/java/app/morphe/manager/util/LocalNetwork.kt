/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * The local network as Android 17 gates it: from there on an app that targets it reaches no device
 * on the network it is connected to, such as a server hosting a patch source, without
 * [Manifest.permission.ACCESS_LOCAL_NETWORK]. The connections are dropped rather than refused, so
 * without the permission they only time out.
 *
 * See https://developer.android.com/privacy-and-security/local-network-permission
 */
object LocalNetwork {
    /**
     * Whether reaching the local network waits on the user granting the permission. Below Android 17
     * the internet permission alone reaches it, and so it does for an app targeting an older one,
     * which is granted the permission without asking.
     */
    fun needsPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) !=
                PackageManager.PERMISSION_GRANTED

    /**
     * Whether [url] points at a host on the local network. A name is looked up, which the local
     * network permission leaves alone, so call this off the main thread. A link to a public code
     * host, or one that does not resolve, is taken as not local.
     */
    fun isLocalUrl(url: String): Boolean {
        val source = SourceUrl.parse(url) ?: return false
        if (source.codeHost != null) return false
        // Literal IPv6 hosts keep their brackets
        val host = source.host.removePrefix("[").removeSuffix("]")
        // Multicast DNS names are answered by the devices on the network themselves
        if (host.trimEnd('.').endsWith(MDNS_SUFFIX)) return true
        val addresses = runCatching { InetAddress.getAllByName(host) }.getOrNull() ?: return false
        return addresses.any(::isLocalAddress)
    }

    /**
     * Whether [address] is on the local network: the private, shared and link-local IPv4 ranges,
     * unique local and link-local IPv6 addresses, and multicast and broadcast ones.
     */
    fun isLocalAddress(address: InetAddress): Boolean = when (address) {
        is Inet4Address -> address.isSiteLocalAddress || address.isLinkLocalAddress ||
                address.isMulticastAddress || address.isSharedAddress || address.isBroadcastAddress
        is Inet6Address -> address.isSiteLocalAddress || address.isLinkLocalAddress ||
                address.isMulticastAddress || address.isUniqueLocalAddress
        else -> false
    }

    // 100.64.0.0/10, carrier-grade NAT
    private val Inet4Address.isSharedAddress: Boolean
        get() = (address[0].toInt() and 0xFF) == 100 && (address[1].toInt() and 0xC0) == 64

    // 255.255.255.255
    private val Inet4Address.isBroadcastAddress: Boolean
        get() = address.all { it == 0xFF.toByte() }

    // fc00::/7
    private val Inet6Address.isUniqueLocalAddress: Boolean
        get() = (address[0].toInt() and 0xFE) == 0xFC

    private const val MDNS_SUFFIX = ".local"
}
