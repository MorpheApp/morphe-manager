/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.batch

import app.morphe.manager.domain.bundles.BundledAppTarget
import app.morphe.patcher.patch.AppTarget
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Whether the automatic helper pass should fetch an APK for a queued app, and which versions it
 * may choose from. A wrong yes spends the user's data on a download they did not need or replaces
 * a choice they made, while a wrong no leaves an app behind the version its patches support.
 */
class BatchApkAutoDownloadTest {
    @Test
    fun `an app without an APK is always worth a download`() {
        assertTrue(item(BatchItemState.NEEDS_APK).autoDownloadWanted(target("20.0"), canVerifySignature = true))
        // Universal patches name no version, so any APK the helper finds will do
        assertTrue(item(BatchItemState.NEEDS_APK).autoDownloadWanted(null, canVerifySignature = true))
    }

    @Test
    fun `an app already at the newest version is left alone`() {
        val current = item(BatchItemState.READY, source = saved("20.0"))

        assertFalse(current.autoDownloadWanted(target("20.0"), canVerifySignature = true))
    }

    @Test
    fun `an app behind the newest version is downloaded again`() {
        val current = item(BatchItemState.READY, source = saved("19.0"))

        assertTrue(current.autoDownloadWanted(target("20.0"), canVerifySignature = true))
    }

    @Test
    fun `an app past the newest version is never downgraded`() {
        val current = item(BatchItemState.READY, source = saved("21.0"))

        assertFalse(current.autoDownloadWanted(target("20.0"), canVerifySignature = true))
    }

    @Test
    fun `universal patches have nothing to fetch for an existing APK`() {
        val current = item(BatchItemState.READY, source = saved("19.0"))

        assertFalse(current.autoDownloadWanted(null, canVerifySignature = true))
    }

    @Test
    fun `a version mismatch older than the newest is repaired`() {
        val current = item(BatchItemState.VERSION_MISMATCH, source = saved("19.0"))

        assertTrue(current.autoDownloadWanted(target("20.0"), canVerifySignature = true))
    }

    @Test
    fun `a build code the patches do not accept is fetched again`() {
        val current = item(BatchItemState.VERSION_MISMATCH, source = saved("20.0", versionCode = 100))

        assertTrue(current.autoDownloadWanted(target("20.0", codes = setOf(200)), canVerifySignature = true))
        // Without build code constraints the same version is patchable
        assertFalse(current.autoDownloadWanted(target("20.0"), canVerifySignature = true))
    }

    @Test
    fun `a READY app with the newest version is not downloaded again for a build code`() {
        // READY means one of the patches already accepts the build code, even if the catalog
        // carries another set for the same version
        val current = item(BatchItemState.READY, source = saved("20.0", versionCode = 100))

        assertFalse(current.autoDownloadWanted(target("20.0", codes = setOf(200)), canVerifySignature = true))
    }

    @Test
    fun `an APK whose signature cannot be checked is left to the user`() {
        assertFalse(item(BatchItemState.NEEDS_APK).autoDownloadWanted(target("20.0"), canVerifySignature = false))
    }

    @Test
    fun `an app whose signature was questioned is updated when it is behind`() {
        val current = item(BatchItemState.UNVERIFIED_SIGNATURE, source = saved("19.0"))

        assertTrue(current.autoDownloadWanted(target("20.0"), canVerifySignature = true))
    }

    @Test
    fun `an app with nothing to gain from a download is left alone`() {
        val behind = saved("19.0")

        assertFalse(item(BatchItemState.NO_PATCHES, source = behind).autoDownloadWanted(target("20.0"), true))
        assertFalse(item(BatchItemState.NO_SELECTION, source = behind).autoDownloadWanted(target("20.0"), true))
        assertFalse(item(BatchItemState.EXCLUDED, source = behind).autoDownloadWanted(target("20.0"), true))
    }

    @Test
    fun `a selection edited by hand survives the pass`() {
        val current = item(BatchItemState.READY, source = saved("19.0"), selectionEdited = true)

        assertFalse(current.autoDownloadWanted(target("20.0"), canVerifySignature = true))
    }

    @Test
    fun `a version accepted despite the patches is left alone`() {
        val current = item(BatchItemState.READY, source = saved("19.0"), forceVersionMismatch = true)

        assertFalse(current.autoDownloadWanted(target("20.0"), canVerifySignature = true))
    }

    @Test
    fun `a version turned down on the home screen is not fetched`() {
        val behind = item(BatchItemState.READY, source = saved("19.0"))
        val missing = item(BatchItemState.NEEDS_APK)

        assertFalse(behind.autoDownloadWanted(target("20.0"), canVerifySignature = true, ignoredVersion = "20.0"))
        assertFalse(missing.autoDownloadWanted(target("20.0"), canVerifySignature = true, ignoredVersion = "20.0"))
        // Another version the user did not turn down is still fair game
        assertTrue(behind.autoDownloadWanted(target("21.0"), canVerifySignature = true, ignoredVersion = "20.0"))
    }

    @Test
    fun `the pass follows the sources the app is patched from`() {
        val patchedFromFirst = item(BatchItemState.READY, source = saved("19.0"), bundleUids = listOf(1))
        val all = listOf(target("21.0", bundleUid = 0), target("20.0", bundleUid = 1))

        assertEquals(listOf(1), batchAutoDownloadTargets(patchedFromFirst, all).map { it.bundleUid })
        assertEquals(
            listOf(1),
            batchAutoDownloadTargets(item(BatchItemState.NEEDS_APK, chosenBundleUid = 1), all)
                .map { it.bundleUid }
        )
        assertEquals(all, batchAutoDownloadTargets(item(BatchItemState.NEEDS_APK), all))
        // A source that left the catalog falls back to everything rather than showing nothing
        assertEquals(
            all,
            batchAutoDownloadTargets(item(BatchItemState.NEEDS_APK, bundleUids = listOf(9)), all)
        )
    }

    private fun target(
        version: String,
        codes: Set<Int>? = null,
        bundleUid: Int = 0
    ) = BundledAppTarget(
        target = AppTarget(version = version),
        bundleUid = bundleUid,
        bundleName = "Test",
        buildCodes = codes
    )

    private fun saved(version: String, versionCode: Long? = null) =
        BatchApkSource.SavedOriginal(File("app.apk"), version, versionCode)

    private fun item(
        state: BatchItemState,
        source: BatchApkSource? = null,
        selectionEdited: Boolean = false,
        forceVersionMismatch: Boolean = false,
        bundleUids: List<Int> = emptyList(),
        chosenBundleUid: Int? = null
    ) = BatchPatchItem(
        target = BatchTarget(packageName = "com.example.app"),
        appName = "Example",
        source = source,
        selection = emptyMap(),
        options = emptyMap(),
        bundles = bundleUids.map { uid ->
            BatchBundleRef(uid, "Test", version = null, patchNames = emptySet(), renamingPatchNames = emptySet())
        },
        state = state,
        selectionEdited = selectionEdited,
        forceVersionMismatch = forceVersionMismatch,
        chosenBundleUid = chosenBundleUid
    )
}
