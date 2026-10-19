/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.patch

import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.bytecodePatch
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A selection is made against the patches the app lists and resolved by the patcher, so the key
 * the list gives a patch has to be the key the patcher finds it under.
 */
class PatchKeyParityTest {
    private val app = "com.example.app"

    // The description tells the patches apart, as several share a name
    private fun patch(name: String, description: String, vararg packages: String?) = bytecodePatch(
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
    private fun listedKeys(patches: List<Patch<*>>): Map<String, String?> {
        val listed = patches.map(::PatchInfo).filter { it.compatibleWith(app) }
        return uniqueNames(listed).zip(listed.map { it.description }).toMap()
    }

    private fun patcherKeys(patches: List<Patch<*>>): Map<String, String?> =
        patches.keyedFor(app).mapValues { (_, patch) -> patch.description }

    @Test
    fun `the app and the patcher key the same patches alike`() {
        val patches = listOf(
            patch("Ads", "universal", null),
            patch("Ads", "app", app),
            patch("Ads", "app and other", app, "com.other"),
            patch("Theme", "unrestricted"),
            patch("Other", "elsewhere", "com.other")
        )

        assertEquals(listedKeys(patches), patcherKeys(patches))
        assertEquals(listedKeys(patches.reversed()), patcherKeys(patches.reversed()))
    }
}
