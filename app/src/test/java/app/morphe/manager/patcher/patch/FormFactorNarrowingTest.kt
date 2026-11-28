/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.patch

import app.morphe.patcher.patch.FormFactor
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A phone app and its TV or watch app can share one package name, and only the patches declared
 * for the build in hand may be offered for it.
 */
class FormFactorNarrowingTest {
    private fun entry(formFactor: FormFactor?, vararg versions: String, packageName: String = APP) =
        CompatiblePackage(
            packageName = packageName,
            versions = versions.takeIf { it.isNotEmpty() }?.let { persistentSetOf(*it) },
            formFactor = formFactor
        )

    private fun patch(name: String, vararg entries: CompatiblePackage) = PatchInfo(
        name = name,
        description = null,
        include = true,
        compatiblePackages = entries.toList().toPersistentList(),
        options = null
    )

    private val universal = PatchInfo(
        name = "Universal",
        description = null,
        include = true,
        compatiblePackages = null,
        options = null
    )

    private val phoneOnly = patch("Phone", entry(FormFactor.MOBILE, "2.0"))
    private val tvOnly = patch("TV", entry(FormFactor.TV, "5.0"))
    private val both = patch("Both", entry(FormFactor.MOBILE, "2.0"), entry(FormFactor.TV, "5.0"))
    private val anyBuild = patch("Any build", entry(null))

    private fun bundle(vararg patches: PatchInfo) = PatchBundleInfo.Global(
        name = "Bundle",
        version = null,
        uid = 1,
        enabled = true,
        patches = patches.toList()
    )

    @Test
    fun `a TV build is offered the TV patches only`() {
        val scoped = bundle(phoneOnly, tvOnly, both, anyBuild, universal)
            .forPackage(APP, "5.0", formFactor = FormFactor.TV)

        assertEquals(listOf("TV", "Both", "Any build", "Universal"), scoped.patches.map { it.name })
        assertEquals(listOf("TV", "Both", "Any build"), scoped.compatible.map { it.name })
    }

    @Test
    fun `the versions of a patch for both builds are those of the build in hand`() {
        val tvScoped = bundle(both).forPackage(APP, "2.0", formFactor = FormFactor.TV)
        val phoneScoped = bundle(both).forPackage(APP, "2.0", formFactor = FormFactor.MOBILE)

        assertEquals(listOf("Both"), tvScoped.incompatible.map { it.name })
        assertEquals(listOf("Both"), phoneScoped.compatible.map { it.name })
    }

    @Test
    fun `no form factor keeps the patches of every build`() {
        val scoped = bundle(phoneOnly, tvOnly).forPackage(APP, null)

        assertEquals(listOf("Phone", "TV"), scoped.patches.map { it.name })
    }

    @Test
    fun `narrowing leaves the entries for other apps`() {
        val shared = patch("Shared", entry(FormFactor.TV), entry(FormFactor.MOBILE, packageName = OTHER_APP))
        val narrowed = shared.narrowedTo(APP, FormFactor.MOBILE)

        assertFalse(narrowed.compatibleWith(APP))
        assertTrue(narrowed.compatibleWith(OTHER_APP))
    }

    @Test
    fun `a patch with nothing to cut stays the same instance`() {
        assertSame(anyBuild, anyBuild.narrowedTo(APP, FormFactor.WEARABLE))
    }

    @Test
    fun `the form factors of a package are those its entries declare`() {
        assertEquals(setOf(FormFactor.MOBILE, FormFactor.TV), sequenceOf(phoneOnly, tvOnly, anyBuild).formFactorsOf(APP))
        assertEquals(setOf(FormFactor.MOBILE), sequenceOf(phoneOnly, anyBuild, universal).formFactorsOf(APP))
        assertEquals(emptySet(), sequenceOf(phoneOnly).formFactorsOf(OTHER_APP))
    }

    @Test
    fun `the patches only another build gets are named so what was saved for them is kept`() {
        val tvScoped = bundle(phoneOnly, tvOnly, both, universal).forPackage(APP, "5.0", formFactor = FormFactor.TV)
        val unnarrowed = bundle(phoneOnly, tvOnly).forPackage(APP, "5.0")

        assertEquals(setOf("Phone"), tvScoped.otherBuildPatches)
        assertEquals(emptySet(), unnarrowed.otherBuildPatches)
    }

    @Test
    fun `a patch of another build named like one of this build is not kept apart`() {
        val tvTwin = patch("Phone", entry(FormFactor.TV))
        val tvScoped = bundle(phoneOnly, tvTwin).forPackage(APP, null, formFactor = FormFactor.TV)

        assertEquals(emptySet(), tvScoped.otherBuildPatches)
    }

    private companion object {
        const val APP = "com.example"
        const val OTHER_APP = "com.example.other"
    }
}
