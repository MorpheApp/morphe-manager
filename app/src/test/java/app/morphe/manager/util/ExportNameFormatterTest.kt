/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.data.room.apps.installed.SelectionPayload
import app.morphe.manager.patcher.patch.PatchSourceRef
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The file name is what tells saved builds apart once they leave the manager, so every source has
 * to stay paired with the version it patched with.
 */
class ExportNameFormatterTest {
    private fun name(vararg sources: PatchSourceRef, appName: String? = "YouTube Morphe", isClone: Boolean = false) =
        ExportNameFormatter.format(
            ApkExportData(
                appName = appName,
                packageName = "app.morphe.android.youtube",
                appVersion = "21.16.256",
                sources = sources.toList(),
                isClone = isClone
            )
        )

    @Test
    fun `names the source along with its version`() {
        assertEquals(
            "YouTube_Morphe-v21.16.256-Morphe_Patches-v1.47.0-dev.5.apk",
            name(PatchSourceRef("Morphe Patches", "v1.47.0-dev.5"))
        )
    }

    @Test
    fun `keeps each version with its own source`() {
        assertEquals(
            "YouTube_Morphe-v21.16.256-Morphe_Patches-v1.48.0+kveld9_Patches-v2.8.0.apk",
            name(PatchSourceRef("Morphe Patches", "1.48.0"), PatchSourceRef("kveld9 Patches", "2.8.0"))
        )
    }

    @Test
    fun `counts the sources past the second`() {
        assertEquals(
            "YouTube_Morphe-v21.16.256-A-v1+B-v2+2.apk",
            name(
                PatchSourceRef("A", "1"),
                PatchSourceRef("B", "2"),
                PatchSourceRef("C", "3"),
                PatchSourceRef("D", null)
            )
        )
    }

    @Test
    fun `names a source without a version on its own`() {
        assertEquals("YouTube_Morphe-v21.16.256-Local.apk", name(PatchSourceRef("Local", null)))
    }

    @Test
    fun `leaves sources out when there are none`() {
        assertEquals("YouTube_Morphe-v21.16.256.apk", name())
    }

    @Test
    fun `marks a clone apart from the app it was built from`() {
        assertEquals(
            "YouTube_Morphe-clone-v21.16.256-Morphe_Patches-v1.48.0.apk",
            name(PatchSourceRef("Morphe Patches", "1.48.0"), isClone = true)
        )
    }

    @Test
    fun `names a saved build after the sources it was patched with`() {
        val app = InstalledApp(
            currentPackageName = "app.morphe.android.youtube.clone",
            originalPackageName = "com.google.android.youtube",
            version = "21.16.256",
            installType = InstallType.SAVED,
            selectionPayload = SelectionPayload(
                listOf(
                    SelectionPayload.BundleSelection(1, listOf("A"), bundleName = "Morphe Patches", bundleVersion = "1.48.0"),
                    // Recorded before sources were, so there is nothing to name it by
                    SelectionPayload.BundleSelection(2, listOf("B"))
                )
            ),
            appLabel = "YouTube Morphe",
            isClone = true
        )
        assertEquals("YouTube_Morphe-clone-v21.16.256-Morphe_Patches-v1.48.0.apk", app.exportFileName())
    }

    @Test
    fun `falls back to the package for a name of symbols only`() {
        assertEquals("app.morphe.android.youtube-v21.16.256.apk", name(appName = "★"))
    }

    @Test
    fun `keeps names in any script`() {
        assertEquals("Ютуб-v21.16.256.apk", name(appName = "Ютуб"))
    }
}
