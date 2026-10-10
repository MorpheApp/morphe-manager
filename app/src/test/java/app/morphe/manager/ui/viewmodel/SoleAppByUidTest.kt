/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.viewmodel

import app.morphe.manager.patcher.patch.CompatiblePackage
import app.morphe.manager.patcher.patch.PatchBundleInfo
import app.morphe.manager.patcher.patch.PatchInfo
import kotlinx.collections.immutable.toPersistentList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A bundle made for one app has every release badged on that app, changelog scopes or not,
 * so which bundles count as made for one app is worth pinning down.
 */
class SoleAppByUidTest {
    private fun patch(vararg packageNames: String) = PatchInfo(
        name = "Patch",
        description = null,
        include = true,
        compatiblePackages = packageNames
            .map { CompatiblePackage(packageName = it, versions = null) }
            .toPersistentList(),
        options = null
    )

    private val universal = PatchInfo(
        name = "Universal",
        description = null,
        include = true,
        compatiblePackages = null,
        options = null
    )

    private fun bundle(uid: Int, vararg patches: PatchInfo) = uid to PatchBundleInfo.Global(
        name = "Bundle $uid",
        version = null,
        uid = uid,
        enabled = true,
        patches = patches.toList()
    )

    @Test
    fun `a bundle for one app is mapped to it`() {
        val bundles = mapOf(bundle(1, patch("facebook"), patch("facebook")))
        assertEquals(mapOf(1 to "facebook"), soleAppByUid(bundles))
    }

    @Test
    fun `universal patches do not stop a bundle from being for one app`() {
        val bundles = mapOf(bundle(1, patch("facebook"), universal))
        assertEquals(mapOf(1 to "facebook"), soleAppByUid(bundles))
    }

    @Test
    fun `a bundle for several apps is left out`() {
        val bundles = mapOf(bundle(1, patch("youtube", "music")), bundle(2, patch("youtube"), patch("reddit")))
        assertTrue(soleAppByUid(bundles).isEmpty())
    }

    @Test
    fun `a bundle of only universal patches is left out`() {
        val bundles = mapOf(bundle(1, universal))
        assertTrue(soleAppByUid(bundles).isEmpty())
    }
}
