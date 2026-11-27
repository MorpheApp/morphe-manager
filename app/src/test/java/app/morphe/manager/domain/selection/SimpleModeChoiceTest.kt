/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.selection

import app.morphe.manager.patcher.patch.PatchBundleInfo
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.patcher.patch.ApkArchitecture
import app.morphe.patcher.patch.InstallerType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How simple mode settles a run's patches without asking, or with one question at most: which
 * source to patch from when several have any.
 */
class SimpleModeChoiceTest {
    private val target = PatchTarget(InstallerType.STANDARD, ApkArchitecture.UNIVERSAL, ignoresRules = false)

    private fun patch(name: String, include: Boolean = true) = PatchInfo(
        name = name,
        description = null,
        include = include,
        compatiblePackages = null,
        options = null
    )

    // Patches listed as compatible are offered without allowing incompatible ones, the rest only with it
    private fun bundle(
        uid: Int,
        compatible: List<PatchInfo>,
        incompatible: List<PatchInfo> = emptyList(),
        enabled: Boolean = true
    ) = PatchBundleInfo.Scoped(
        name = "Source $uid",
        version = null,
        uid = uid,
        enabled = enabled,
        patches = compatible + incompatible,
        compatible = compatible,
        incompatible = incompatible,
        universal = emptyList()
    )

    private fun choose(
        contributing: List<PatchBundleInfo.Scoped>,
        allBundles: List<PatchBundleInfo.Scoped> = contributing,
        chosenUid: Int? = null,
        allowIncompatible: Boolean = false
    ) = simpleModeChoice(contributing, allBundles, chosenUid, target, allowIncompatible)

    @Test
    fun `a single source runs with its default patches`() {
        val source = bundle(1, listOf(patch("On"), patch("Off", include = false)))

        assertEquals(
            SimpleChoice.Patches(mapOf(1 to setOf("On")), allowIncompatible = false),
            choose(listOf(source))
        )
    }

    @Test
    fun `several sources with patches are asked about`() {
        val first = bundle(1, listOf(patch("A")))
        val second = bundle(2, listOf(patch("B")))

        assertEquals(
            SimpleChoice.Choose(listOf(first to setOf("A"), second to setOf("B"))),
            choose(listOf(first, second))
        )
    }

    @Test
    fun `a source with nothing on by default is left to be set up by hand`() {
        assertEquals(
            SimpleChoice.NothingByDefault,
            choose(listOf(bundle(1, listOf(patch("Opt-in", include = false)))))
        )
    }

    @Test
    fun `a disabled source takes no part`() {
        val disabled = bundle(1, listOf(patch("A")), enabled = false)
        val enabled = bundle(2, listOf(patch("B")))

        assertEquals(
            SimpleChoice.Patches(mapOf(2 to setOf("B")), allowIncompatible = false),
            choose(listOf(disabled, enabled))
        )
    }

    @Test
    fun `a chosen source is used alone with its incompatible patches too`() {
        val first = bundle(1, listOf(patch("A")))
        val chosen = bundle(2, compatible = listOf(patch("B")), incompatible = listOf(patch("Other version")))

        assertEquals(
            SimpleChoice.Patches(mapOf(2 to setOf("B", "Other version")), allowIncompatible = true),
            choose(listOf(first, chosen), chosenUid = 2)
        )
    }

    @Test
    fun `a chosen source without default patches falls through to the others`() {
        val first = bundle(1, listOf(patch("A")))
        val chosen = bundle(2, listOf(patch("Opt-in", include = false)))

        assertEquals(
            SimpleChoice.Patches(mapOf(1 to setOf("A")), allowIncompatible = false),
            choose(listOf(first, chosen), chosenUid = 2)
        )
    }
}
