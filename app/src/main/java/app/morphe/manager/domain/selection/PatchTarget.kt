/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.selection

import app.morphe.manager.patcher.patch.PatchBundleInfo
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.patcher.patch.PatchLockState
import app.morphe.manager.util.PatchSelection
import app.morphe.manager.util.PatchSelectionUtils.applyAvailability
import app.morphe.patcher.patch.ApkArchitecture
import app.morphe.patcher.patch.InstallerType

/**
 * What a run picks its patches against: the install target and the architecture of the APK it
 * starts from. [ignoresRules] leaves every patch to be picked by hand regardless.
 */
data class PatchTarget(
    val installerType: InstallerType,
    val apkArchitecture: ApkArchitecture,
    val ignoresRules: Boolean
) {
    fun defaultSelected(patch: PatchInfo) = patch.defaultSelected(installerType, apkArchitecture)

    /** Lock state of [patch], which a REQUIRED patch only reaches while the run is [singleBundle]. */
    fun lockState(patch: PatchInfo, singleBundle: Boolean) =
        if (ignoresRules) PatchLockState.NONE else patch.lockState(installerType, apkArchitecture, singleBundle)

    /**
     * [selection] under this target's availability rules, which have the final say on what the run
     * starts with. A source not among [bundles] sits them out and is put back untouched: whether a
     * REQUIRED patch locks turns on the run drawing from one source or several, and a source the
     * run cannot reach is not one of them.
     */
    fun applyTo(selection: PatchSelection, bundles: List<PatchBundleInfo.Scoped>): PatchSelection {
        if (ignoresRules) return selection
        val taking = bundles.mapTo(mutableSetOf()) { it.uid }
        val heldBack = selection.filterKeys { it !in taking }
        return selection.filterKeys { it in taking }
            .applyAvailability(
                installerType,
                apkArchitecture,
                bundles.associate { it.uid to it.patches.associateBy { patch -> patch.name } }
            ) + heldBack
    }
}
