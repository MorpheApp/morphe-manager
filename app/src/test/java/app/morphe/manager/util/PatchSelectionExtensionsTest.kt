/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import app.morphe.manager.data.room.apps.installed.SelectionPayload
import app.morphe.manager.domain.bundles.LocalPatchBundle
import app.morphe.manager.domain.bundles.PatchBundleSource
import app.morphe.manager.patcher.patch.Option
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.util.PatchSelectionUtils.applyAvailability
import app.morphe.manager.util.PatchSelectionUtils.resetOptionsForPatch
import app.morphe.manager.util.PatchSelectionUtils.sanitizeForPatcher
import app.morphe.manager.util.PatchSelectionUtils.togglePatch
import app.morphe.manager.util.PatchSelectionUtils.updateOption
import app.morphe.manager.util.PatchSelectionUtils.validatePatchOptions
import app.morphe.manager.util.PatchSelectionUtils.validatePatchSelection
import app.morphe.patcher.patch.ApkArchitecture
import app.morphe.patcher.patch.AvailabilityResolver
import app.morphe.patcher.patch.InstallerType
import app.morphe.patcher.patch.PatchAvailability
import kotlinx.collections.immutable.toImmutableList
import java.io.File
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * These functions decide what actually ends up in a patched APK, so a regression here produces a
 * wrong build rather than a visible failure.
 */
class PatchSelectionExtensionsTest {
    private val installer = InstallerType.STANDARD
    private val architecture = ApkArchitecture.ARM64_V8A

    private fun option(key: String) = Option(
        title = key,
        key = key,
        description = "",
        required = false,
        type = typeOf<String>(),
        default = null,
        presets = null,
        validator = { true }
    )

    private fun patch(
        name: String,
        options: List<Option<*>>? = null,
        availability: AvailabilityResolver? = null
    ) = PatchInfo(
        name = name,
        description = null,
        include = true,
        compatiblePackages = null,
        options = options?.toImmutableList(),
        availabilityResolver = availability
    )

    private fun source(uid: Int): PatchBundleSource = LocalPatchBundle(
        name = "bundle-$uid",
        uid = uid,
        displayName = null,
        createdAt = null,
        updatedAt = null,
        error = null,
        // Never read: an absent patches.jar simply leaves the source in its missing state
        directory = File("build/tmp/patch-bundle-$uid"),
        enabled = true
    )

    private fun payload(vararg bundles: Pair<Int, List<String>>) = SelectionPayload(
        bundles = bundles.map { (uid, patches) ->
            SelectionPayload.BundleSelection(bundleUid = uid, patches = patches)
        }
    )

    @Test
    fun `payload conversion drops blank names and empty bundles`() {
        val selection = payload(
            0 to listOf("Patch A", "", "Patch B"),
            1 to emptyList()
        ).toPatchSelection()

        assertEquals(mapOf(0 to setOf("Patch A", "Patch B")), selection)
    }

    @Test
    fun `remapping keeps only bundles that still have a source`() {
        val (remapped, selection) = payload(
            0 to listOf("Patch A"),
            2 to listOf("Patch B"),
            5 to listOf("Patch C")
        ).remapAndExtractSelection(listOf(source(0), source(2)))

        assertEquals(listOf(0, 2), remapped.bundles.map { it.bundleUid })
        assertEquals(
            mapOf(
                0 to setOf("Patch A"),
                2 to setOf("Patch B")
            ),
            selection
        )
    }

    @Test
    fun `remapping returns an empty selection when no source matches`() {
        val (remapped, selection) = payload(5 to listOf("Patch B"))
            .remapAndExtractSelection(listOf(source(0)))

        assertEquals(emptyList(), remapped.bundles)
        assertEquals(emptyMap(), selection)
    }

    @Test
    fun `toggling adds to a bundle that is not in the selection yet`() {
        assertEquals(
            mapOf(0 to setOf("Patch A")),
            emptyMap<Int, Set<String>>().togglePatch(0, "Patch A")
        )
    }

    @Test
    fun `toggling the last patch off removes the bundle entry`() {
        assertEquals(
            emptyMap(),
            mapOf(0 to setOf("Patch A")).togglePatch(0, "Patch A")
        )
        assertEquals(
            mapOf(0 to setOf("Patch B")),
            mapOf(0 to setOf("Patch A", "Patch B")).togglePatch(0, "Patch A")
        )
    }

    @Test
    fun `an option value is stored under its bundle and patch`() {
        assertEquals(
            mapOf(0 to mapOf("Patch A" to mapOf("key" to "value"))),
            emptyMap<Int, Map<String, Map<String, Any?>>>().updateOption(0, "Patch A", "key", "value")
        )
    }

    @Test
    fun `clearing a field keeps the key so the default is not re-injected`() {
        assertEquals(
            mapOf(0 to mapOf("Patch A" to mapOf("key" to ""))),
            emptyMap<Int, Map<String, Map<String, Any?>>>().updateOption(0, "Patch A", "key", "")
        )
    }

    @Test
    fun `resetting a single option prunes the maps it leaves empty`() {
        val options = mapOf(0 to mapOf("Patch A" to mapOf("key" to "value")))

        assertEquals(emptyMap(), options.updateOption(0, "Patch A", "key", null))
    }

    @Test
    fun `resetting one option keeps the others`() {
        val options = mapOf(0 to mapOf("Patch A" to mapOf("kept" to "value", "gone" to "value")))

        assertEquals(
            mapOf(0 to mapOf("Patch A" to mapOf("kept" to "value"))),
            options.updateOption(0, "Patch A", "gone", null)
        )
    }

    @Test
    fun `blank strings are stripped before the options reach the patcher`() {
        val options = mapOf(
            0 to mapOf(
                "Patch A" to mapOf("cleared" to "", "blank" to "   ", "kept" to "value"),
                "Patch B" to mapOf("cleared" to "")
            )
        )

        assertEquals(
            mapOf(0 to mapOf("Patch A" to mapOf("kept" to "value"))),
            options.sanitizeForPatcher()
        )
    }

    @Test
    fun `sanitizing keeps values that are not strings`() {
        val options = mapOf(0 to mapOf("Patch A" to mapOf("flag" to true, "count" to 0)))

        assertEquals(options, options.sanitizeForPatcher())
    }

    @Test
    fun `resetting a patch removes its options and prunes the bundle`() {
        val options = mapOf(0 to mapOf("Patch A" to mapOf("key" to "value")))

        assertEquals(emptyMap(), options.resetOptionsForPatch(0, "Patch A"))
    }

    @Test
    fun `resetting a patch of an unknown bundle changes nothing`() {
        val options: Options = mapOf(0 to mapOf("Patch A" to mapOf("key" to "value")))

        assertSame(options, options.resetOptionsForPatch(5, "Patch A"))
    }

    @Test
    fun `validation drops bundles and patches that no longer exist`() {
        val selection = mapOf(
            0 to setOf("Patch A", "Removed patch"),
            1 to setOf("Patch B"),
            5 to setOf("Patch C")
        )
        val bundles = mapOf(
            0 to mapOf("Patch A" to patch("Patch A")),
            1 to mapOf("Other patch" to patch("Other patch"))
        )

        assertEquals(mapOf(0 to setOf("Patch A")), validatePatchSelection(selection, bundles))
    }

    @Test
    fun `option validation drops unknown bundles patches and keys`() {
        val options = mapOf(
            0 to mapOf(
                "Patch A" to mapOf("known" to "value", "unknown" to "value"),
                "Removed patch" to mapOf("known" to "value")
            ),
            5 to mapOf("Patch A" to mapOf("known" to "value"))
        )
        // The patch also declares an option the user never set, which must not be invented here
        val bundles = mapOf(
            0 to mapOf(
                "Patch A" to patch(
                    name = "Patch A",
                    options = listOf(option("known"), option("untouched"))
                )
            )
        )

        assertEquals(
            mapOf(0 to mapOf("Patch A" to mapOf("known" to "value"))),
            validatePatchOptions(options, bundles)
        )
    }

    @Test
    fun `a required patch is added even when the user did not pick it`() {
        val bundles = mapOf(
            0 to mapOf(
                "Required patch" to patch("Required patch") { _, _ -> PatchAvailability.REQUIRED }
            )
        )

        assertEquals(
            mapOf(0 to setOf("Required patch")),
            emptyMap<Int, Set<String>>().applyAvailability(installer, architecture, bundles)
        )
    }

    @Test
    fun `an unavailable patch is removed even when the user did pick it`() {
        val bundles = mapOf(
            0 to mapOf(
                "Blocked patch" to patch("Blocked patch") { _, _ -> PatchAvailability.UNAVAILABLE }
            )
        )

        assertEquals(
            emptyMap(),
            mapOf(0 to setOf("Blocked patch")).applyAvailability(installer, architecture, bundles)
        )
    }

    @Test
    fun `availability leaves the user's choice alone when it is not forced`() {
        val bundles = mapOf(
            0 to mapOf(
                "Enabled patch" to patch("Enabled patch") { _, _ -> PatchAvailability.ENABLED },
                "Disabled patch" to patch("Disabled patch") { _, _ -> PatchAvailability.DISABLED },
                "Plain patch" to patch("Plain patch")
            )
        )
        val selection = mapOf(0 to setOf("Disabled patch", "Plain patch"))

        assertEquals(selection, selection.applyAvailability(installer, architecture, bundles))
    }

    @Test
    fun `availability resolves against the current installer`() {
        val bundles = mapOf(
            0 to mapOf(
                "GmsCore support" to patch("GmsCore support") { installerType, _ ->
                    if (installerType == InstallerType.MOUNT) {
                        PatchAvailability.UNAVAILABLE
                    } else {
                        PatchAvailability.REQUIRED
                    }
                }
            )
        )
        val selection = mapOf(0 to setOf("GmsCore support"))

        assertEquals(
            selection,
            selection.applyAvailability(InstallerType.STANDARD, architecture, bundles)
        )
        assertEquals(
            emptyMap(),
            selection.applyAvailability(InstallerType.MOUNT, architecture, bundles)
        )
    }
}
