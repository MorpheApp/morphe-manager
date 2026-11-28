/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.selection

import app.morphe.manager.patcher.patch.PatchBundleInfo
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.util.PatchSelection
import app.morphe.patcher.patch.ApkArchitecture
import app.morphe.patcher.patch.InstallerType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The rule the queue merges a saved selection with by, which decides what a plan patches with
 * without ever asking. It has to answer exactly as the expert dialog does, or the same app comes
 * out of the queue patched differently than it would have been from the home screen.
 */
class SelectionMergeTest {
    private val installer = InstallerType.STANDARD
    private val architecture = ApkArchitecture.UNIVERSAL

    private fun patch(name: String, include: Boolean) = PatchInfo(
        name = name,
        description = null,
        include = include,
        compatiblePackages = null,
        options = null
    )

    private val patches = listOf(
        patch("Old default", include = true),
        patch("Old opt-in", include = false),
        patch("New default", include = true),
        patch("New opt-in", include = false)
    )

    private val known = setOf("Old default", "Old opt-in")

    @Test
    fun `a patch added since the configuration was saved follows its own default`() {
        assertEquals(
            setOf("New default"),
            newlyAddedDefaults(patches, known, installer, architecture)
        )
    }

    @Test
    fun `a patch the user deselected is not enabled again`() {
        val seen = patches.mapTo(mutableSetOf()) { it.name }

        assertEquals(
            emptySet(),
            newlyAddedDefaults(patches, seen, installer, architecture)
        )
    }

    @Test
    fun `a source added after the app was configured contributes nothing`() {
        assertEquals(
            emptySet(),
            newlyAddedDefaults(patches, null, installer, architecture)
        )
    }
}

/**
 * Bringing a saved selection up to date with the patches added since. The expert dialog and the
 * queue both resolve a selection through this, so a source that contributes nothing to a run has
 * to come out of it the same way in either.
 */
class MergeNewlyAddedTest {
    private val installer = InstallerType.STANDARD
    private val architecture = ApkArchitecture.UNIVERSAL

    private fun patch(name: String, include: Boolean) = PatchInfo(
        name = name,
        description = null,
        include = include,
        compatiblePackages = null,
        options = null
    )

    private fun bundle(uid: Int, vararg patches: PatchInfo) = PatchBundleInfo.Scoped(
        name = "Source $uid",
        version = null,
        uid = uid,
        enabled = true,
        patches = patches.toList(),
        compatible = emptyList(),
        incompatible = emptyList(),
        universal = emptyList()
    )

    private val source = bundle(1, patch("Old", include = true), patch("New", include = true))

    private fun merge(
        bundles: List<PatchBundleInfo.Scoped>,
        validated: PatchSelection,
        known: Map<Int, Set<String>?> = mapOf(1 to setOf("Old"))
    ) = mergeNewlyAdded(bundles, validated, { known[it] }, installer, architecture)

    @Test
    fun `a patch added since the selection was saved joins it`() {
        assertEquals(
            mapOf(1 to setOf("Old", "New")),
            merge(listOf(source), mapOf(1 to setOf("Old")))
        )
    }

    @Test
    fun `every source in the run contributes what was added to it`() {
        val other = bundle(2, patch("Other old", include = true), patch("Other new", include = true))

        assertEquals(
            mapOf(1 to setOf("Old", "New"), 2 to setOf("Other old", "Other new")),
            merge(
                bundles = listOf(source, other),
                validated = mapOf(1 to setOf("Old"), 2 to setOf("Other old")),
                known = mapOf(1 to setOf("Old"), 2 to setOf("Other old"))
            )
        )
    }

    @Test
    fun `a source with nothing new leaves the selection as it was`() {
        assertEquals(
            mapOf(1 to setOf("Old")),
            merge(listOf(source), mapOf(1 to setOf("Old")), known = mapOf(1 to setOf("Old", "New")))
        )
    }

    @Test
    fun `a selection without a snapshot stands as it was saved`() {
        // A restored backup carries no snapshot, so a default the user turned off cannot read as new
        assertEquals(
            mapOf(1 to setOf("Old")),
            merge(listOf(source), mapOf(1 to setOf("Old")), known = emptyMap())
        )
    }

    @Test
    fun `a source the run leaves out keeps the selection made from it`() {
        // What the expert dialog relies on to offer a hidden source back with its patches intact
        assertEquals(
            mapOf(2 to setOf("Kept")),
            merge(bundles = emptyList(), validated = mapOf(2 to setOf("Kept")))
        )
    }

    @Test
    fun `a source the run leaves out contributes no new patches of its own`() {
        assertEquals(
            mapOf(1 to setOf("Old", "New")),
            merge(listOf(source), mapOf(1 to setOf("Old"), 2 to emptySet()))
        )
    }

    @Test
    fun `a source nothing is selected from is left out entirely`() {
        assertEquals(
            emptyMap(),
            merge(
                bundles = listOf(bundle(1, patch("Opt-in", include = false))),
                validated = emptyMap(),
                known = mapOf(1 to setOf("Opt-in"))
            )
        )
    }
}

/**
 * Builds of one app for different devices share a saved configuration, and a run for one build
 * offers only its own patches. What the user chose for the other build has to survive that run.
 */
class KeepingOtherBuildsTest {
    private val stored = mapOf(1 to setOf("Shared", "TV only"))
    private val otherBuild = mapOf(1 to setOf("TV only"))

    @Test
    fun `a run for one build keeps what was chosen for the other`() {
        assertEquals(
            mapOf(1 to setOf("Phone only", "TV only")),
            keepingOtherBuilds(mapOf(1 to setOf("Phone only")), stored, otherBuild)
        )
    }

    @Test
    fun `a patch of the other build left unchosen stays unchosen`() {
        assertEquals(
            mapOf(1 to setOf("Shared")),
            keepingOtherBuilds(mapOf(1 to setOf("Shared")), mapOf(1 to setOf("Shared")), otherBuild)
        )
    }

    @Test
    fun `a source the run takes nothing from still keeps the other build's choice`() {
        assertEquals(mapOf(1 to setOf("TV only")), keepingOtherBuilds(emptyMap(), stored, otherBuild))
    }

    @Test
    fun `without another build the selection is saved as it is`() {
        assertEquals(mapOf(1 to setOf("Shared")), keepingOtherBuilds(mapOf(1 to setOf("Shared")), stored, emptyMap()))
    }
}
