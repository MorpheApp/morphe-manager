/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.bundles

import app.morphe.manager.domain.bundles.RemotePatchBundle.Companion.addSourceLinkForEndpoint
import kotlin.test.*

/**
 * The share button hands out an add-source link, which names only the repository. It must be
 * offered only for endpoints that link recreates, or the recipient adds a different source.
 */
class AddSourceLinkTest {

    @Test
    fun `github root bundle on stable branch is shared`() {
        assertEquals(
            "https://morphe.software/add-source?github=crimera/piko",
            addSourceLinkForEndpoint(
                "https://raw.githubusercontent.com/crimera/piko/main/patches-bundle.json",
                name = null
            )
        )
    }

    @Test
    fun `github root bundle on dev branch is shared`() {
        assertEquals(
            "https://morphe.software/add-source?github=hoo-dles/morphe-patches",
            addSourceLinkForEndpoint(
                "https://raw.githubusercontent.com/hoo-dles/morphe-patches/dev/patches-bundle.json",
                name = null
            )
        )
    }

    @Test
    fun `gitlab root bundle is shared`() {
        assertEquals(
            "https://morphe.software/add-source?gitlab=owner/repo",
            addSourceLinkForEndpoint(
                "https://gitlab.com/owner/repo/-/raw/main/patches-bundle.json",
                name = null
            )
        )
    }

    @Test
    fun `codeberg root bundle is shared`() {
        assertEquals(
            "https://morphe.software/add-source?codeberg=owner/repo",
            addSourceLinkForEndpoint(
                "https://codeberg.org/owner/repo/raw/branch/dev/patches-bundle.json",
                name = null
            )
        )
    }

    @Test
    fun `name is encoded as a query value`() {
        assertEquals(
            "https://morphe.software/add-source?github=owner/repo&name=Piko%20%26%20Co",
            addSourceLinkForEndpoint(
                "https://raw.githubusercontent.com/owner/repo/main/patches-bundle.json",
                name = "Piko & Co"
            )
        )
    }

    @Test
    fun `blank name is left out`() {
        assertEquals(
            "https://morphe.software/add-source?github=owner/repo",
            addSourceLinkForEndpoint(
                "https://raw.githubusercontent.com/owner/repo/main/patches-bundle.json",
                name = " "
            )
        )
    }

    @Test
    fun `custom bundle file is not shared`() {
        assertNull(
            addSourceLinkForEndpoint(
                "https://raw.githubusercontent.com/owner/repo/main/piko-latest-patches-bundle.json",
                name = null
            )
        )
    }

    @Test
    fun `bundle in a subfolder is not shared`() {
        assertNull(
            addSourceLinkForEndpoint(
                "https://raw.githubusercontent.com/owner/repo/main/bundles/patches-bundle.json",
                name = null
            )
        )
    }

    @Test
    fun `other branch is not shared`() {
        assertNull(
            addSourceLinkForEndpoint(
                "https://gitlab.com/owner/repo/-/raw/beta/patches-bundle.json",
                name = null
            )
        )
    }

    @Test
    fun `direct release asset is not shared`() {
        assertNull(
            addSourceLinkForEndpoint(
                "https://github.com/crimera/piko/releases/download/v3.9.0/patches-3.9.0.mpp",
                name = null
            )
        )
    }

    @Test
    fun `unknown host is not shared`() {
        assertNull(addSourceLinkForEndpoint("https://example.com/patches-bundle.json", name = null))
    }

    @Test
    fun `malformed endpoint is not shared`() {
        assertNull(addSourceLinkForEndpoint("not a url", name = null))
    }
}
