/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import java.net.InetAddress
import kotlin.test.*

/**
 * Android 17 drops connections to the local network unless the permission is granted, so a source
 * hosted there has to be told apart from one on the internet, by the ranges Android counts as local.
 */
class LocalNetworkTest {

    private fun isLocal(literal: String) = LocalNetwork.isLocalAddress(InetAddress.getByName(literal))

    @Test
    fun `private, shared and link-local IPv4 ranges are local`() {
        listOf(
            "10.0.0.5", "172.16.0.1", "172.31.255.255", "192.168.1.20",
            "169.254.10.1", "100.64.0.1", "100.127.255.255"
        ).forEach { assertTrue(isLocal(it), it) }
    }

    @Test
    fun `IPv4 multicast and broadcast are local`() {
        assertTrue(isLocal("224.0.0.251"))
        assertTrue(isLocal("255.255.255.255"))
    }

    @Test
    fun `public and loopback IPv4 addresses are not local`() {
        listOf("8.8.8.8", "172.32.0.1", "100.63.255.255", "100.128.0.1", "127.0.0.1")
            .forEach { assertFalse(isLocal(it), it) }
    }

    @Test
    fun `unique local, link-local and multicast IPv6 addresses are local`() {
        listOf("fd12:3456::1", "fc00::1", "fe80::1", "ff02::fb").forEach { assertTrue(isLocal(it), it) }
    }

    @Test
    fun `global and loopback IPv6 addresses are not local`() {
        listOf("2001:4860:4860::8888", "::1").forEach { assertFalse(isLocal(it), it) }
    }

    @Test
    fun `link to a local address is local`() {
        assertTrue(LocalNetwork.isLocalUrl("https://192.168.1.20/patches/patches-bundle.json"))
        assertTrue(LocalNetwork.isLocalUrl("http://10.0.0.2:8080/patches-bundle.json"))
        assertTrue(LocalNetwork.isLocalUrl("http://[fd00::2]:8080/patches-bundle.json"))
    }

    @Test
    fun `link to a multicast DNS name is local`() {
        assertTrue(LocalNetwork.isLocalUrl("https://nas.local/patches-bundle.json"))
        assertTrue(LocalNetwork.isLocalUrl("https://NAS.LOCAL./patches-bundle.json"))
    }

    @Test
    fun `link to a public address or code host is not local`() {
        assertFalse(LocalNetwork.isLocalUrl("https://8.8.8.8/patches-bundle.json"))
        assertFalse(LocalNetwork.isLocalUrl("https://raw.githubusercontent.com/owner/repo/main/patches-bundle.json"))
        assertFalse(LocalNetwork.isLocalUrl("https://gitlab.com/owner/repo/-/raw/main/patches-bundle.json"))
    }

    @Test
    fun `link without a host is not local`() {
        assertFalse(LocalNetwork.isLocalUrl("not a link"))
        assertFalse(LocalNetwork.isLocalUrl(""))
    }
}
