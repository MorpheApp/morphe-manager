/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.patch

import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.patch
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A selection is made against the patches the app lists and resolved by the patcher, so the key
 * the list gives a patch has to be the key the patcher finds it under.
 */
class PatchKeyParityTest {
    private val app = "com.example.app"

    // A compatibility list left empty names no app, which the patcher applies to none
    private val namingNone = patch(name = "Empty", description = "naming none") { compatibleWith() }

    // The description tells the patches apart, as several share a name
    private fun patchOf(name: String, description: String, vararg packages: String?) = patch(
        name = name,
        description = description
    ) {
        packages.forEach { packageName ->
            compatibleWith(
                if (packageName == null) Compatibility(description = "Any app")
                else Compatibility(packageName = packageName, name = packageName)
            )
        }
    }

    // Keyed the way PatchBundleInfo lists the patches of an app
    private fun listedKeys(patches: List<Patch>): Map<String, String?> {
        val listed = patches.map(::PatchInfo).filter { it.compatibleWith(app) }
        return uniqueNames(listed).zip(listed.map { it.description }).toMap()
    }

    private fun patcherKeys(patches: List<Patch>): Map<String, String?> =
        patches.keyedFor(app).mapValues { (_, patch) -> patch.description }

    @Test
    fun `the app and the patcher key the same patches alike`() {
        val patches = listOf(
            patchOf("Ads", "universal", null),
            patchOf("Ads", "app", app),
            patchOf("Ads", "app and other", app, "com.other"),
            patchOf("Theme", "unrestricted"),
            patchOf("Other", "elsewhere", "com.other"),
            namingNone
        )

        assertEquals(listedKeys(patches), patcherKeys(patches))
        assertEquals(listedKeys(patches.reversed()), patcherKeys(patches.reversed()))
    }

    @Test
    fun `a patch is universal where the patcher takes it for any app`() {
        val cases = mapOf(
            patchOf("Theme", "unrestricted") to true,
            patchOf("Ads", "universal", null) to true,
            patchOf("Ads", "app", app) to false,
            namingNone to false
        )

        cases.forEach { (patch, universal) ->
            assertEquals(universal, PatchInfo(patch).isUniversal, patch.description)
        }
    }
}
