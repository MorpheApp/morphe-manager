/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.morphe.manager.domain.repository.withoutMutedSources
import app.morphe.manager.patcher.patch.*
import app.morphe.manager.ui.model.SelectedApp
import app.morphe.manager.ui.screen.shared.CopySelectionCandidate
import app.morphe.manager.util.*
import app.morphe.manager.util.PatchSelectionUtils.bulkEnableHoldsUniversal
import app.morphe.manager.util.PatchSelectionUtils.bulkEnablePatches
import app.morphe.manager.util.PatchSelectionUtils.listedWith
import app.morphe.manager.util.PatchSelectionUtils.mergeBundleOptions
import app.morphe.manager.util.PatchSelectionUtils.resetOptionsForPatch
import app.morphe.manager.util.PatchSelectionUtils.spansMultipleBundles
import app.morphe.manager.util.PatchSelectionUtils.togglePatch
import app.morphe.manager.util.PatchSelectionUtils.updateOption
import app.morphe.manager.util.PatchSelectionUtils.withBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The patches and options of a run picked by hand in the expert dialog, for the APK the run starts
 * from. [PatchFlow] opens it once the APK is known and patches with what it holds on proceeding.
 *
 * @param copy Picker behind the copy-from-another-bundle action of the dialog.
 */
class ExpertModeSelection(
    private val scope: CoroutineScope,
    val copy: CopySelectionController
) {
    /** The APK the selection is for, null while the dialog is closed. */
    var app by mutableStateOf<SelectedApp?>(null)
        private set

    /** The sources the dialog shows, those the app is kept from left out until revealed. */
    var bundles by mutableStateOf<List<PatchBundleInfo.Scoped>>(emptyList())
        private set

    // Everything the app has patches in, so a source it is kept from can be offered back without
    // reopening the dialog. Only ever a superset of bundles
    private var allBundles by mutableStateOf<List<PatchBundleInfo.Scoped>>(emptyList())

    // What the dialog is holding back. Starts as what is stored for the app and is emptied by a
    // reveal, which the store never hears about: the sources come back for this run only
    private var mutedSources by mutableStateOf<Set<Int>>(emptySet())

    /**
     * How many sources the dialog is holding back, which is what the notice offering them counts.
     * Read off the two lists rather than off the stored set, because muting every source of an app
     * hides none of them.
     */
    val hiddenSources get() = allBundles.size - bundles.size

    private var patches by mutableStateOf<PatchSelection>(emptyMap())

    /** The selection as the dialog opened, which "Restore saved" goes back to. */
    var savedPatches by mutableStateOf<PatchSelection>(emptyMap())
        private set

    var options by mutableStateOf<Options>(emptyMap())
        private set

    /** Patches that are new in the current bundle version relative to the last saved selection. */
    var newPatches by mutableStateOf<Map<Int, Set<String>>>(emptyMap())
        private set

    // Bundle and selection left behind by the last "Enable all". Universal patches are applied
    // only while this still matches the live selection, so any other edit disarms them again
    private var universalArmedFor by mutableStateOf<Pair<Int, Set<String>>?>(null)

    /**
     * Whether the run reaches patches that declare other app versions, so bulk actions offer the
     * same set the selection was built from.
     */
    var allowIncompatible = false
        private set

    private lateinit var target: PatchTarget

    /**
     * Option paths of the selection that cannot be read, raised over the dialog rather than left
     * for the run to fail on. Empty while there is nothing to answer.
     */
    var unreadablePaths by mutableStateOf<List<PathValidationResult>>(emptyList())
        private set

    /** The source chosen for the APK, whose tab leads so the dialog opens on it. */
    private var leadingBundleUid: Int? by mutableStateOf(null)

    /**
     * Opens the selection for [app], over every source the app has patches in, [allBundles], with
     * those in [mutedSources] held back.
     */
    fun open(
        app: SelectedApp,
        allBundles: List<PatchBundleInfo.Scoped>,
        mutedSources: Set<Int>,
        leadingBundleUid: Int?,
        patches: PatchSelection,
        options: Options,
        newPatches: Map<Int, Set<String>>,
        allowIncompatible: Boolean,
        target: PatchTarget
    ) {
        this.app = app
        this.allBundles = allBundles
        this.mutedSources = mutedSources
        this.leadingBundleUid = leadingBundleUid
        applyMutes()
        this.patches = patches
        savedPatches = patches
        this.options = options
        this.newPatches = newPatches
        this.allowIncompatible = allowIncompatible
        this.target = target
        universalArmedFor = null
    }

    /** Drops everything the selection holds, as the dialog closes. */
    fun clear() {
        app = null
        bundles = emptyList()
        allBundles = emptyList()
        mutedSources = emptySet()
        leadingBundleUid = null
        patches = emptyMap()
        savedPatches = emptyMap()
        options = emptyMap()
        newPatches = emptyMap()
        universalArmedFor = null
        allowIncompatible = false
        unreadablePaths = emptyList()
        copy.close()
    }

    /**
     * All patches per bundle with their enabled state, in the order the dialog lists them, see
     * [PatchSelectionUtils.listedWith]. The final per-section ordering (new patches first) is
     * applied in the UI layer.
     */
    val allPatchesInfo: List<Pair<PatchBundleInfo.Scoped, List<Pair<PatchInfo, Boolean>>>>
        get() = bundles.listedWith(patches, leadingBundleUid)

    /**
     * The selection as the run would use it: what the dialog shows, minus what it holds back.
     *
     * A hidden source keeps its patches in the selection so that offering it back returns the
     * selection made from it rather than an empty tab. Everything that speaks for the run - the
     * counter, the multiple-sources warning, the patches handed to the patcher - reads this
     * instead, or it would answer for a source the run never reaches.
     */
    val runSelection: PatchSelection
        get() {
            if (mutedSources.isEmpty()) return patches
            val shown = bundles.mapTo(mutableSetOf()) { it.uid }
            return patches.filterKeys { it in shown }
        }

    /** Total number of currently selected patches across all bundles. */
    val totalSelectedCount: Int
        get() = runSelection.values.sumOf { it.size }

    /** True when patches from more than one bundle are selected (triggers warning on proceed). */
    val hasMultipleBundles: Boolean
        get() = runSelection.spansMultipleBundles()

    /**
     * Toggle a patch.
     * Supports adding patches from bundles not yet in the selection.
     */
    fun toggle(bundleUid: Int, patchName: String) {
        // Locked patches are toggled only through availability rules; no-op here
        val patch = bundlePatches(bundleUid)[patchName]
        val selected = patchName in patches[bundleUid].orEmpty()
        if (patch != null && lockState(patch).blocksToggle(selected)) return

        update(patches.togglePatch(bundleUid, patchName))
    }

    /**
     * Select all given patches for a bundle.
     * Only adds patches that are not already selected. LOCKED_OFF patches are skipped.
     *
     * Universal patches are staged behind the regular ones and need a second call, see
     * [PatchSelectionUtils.bulkEnablePatches]. [patches] is the list the dialog currently
     * shows, so an active search narrows the scope of both stages.
     */
    fun selectAll(bundleUid: Int, patches: List<Pair<PatchInfo, Boolean>>) {
        val selected = this.patches[bundleUid].orEmpty()
        val updated = bulkEnablePatches(
            patches,
            selected,
            universalArmed(bundleUid, selected),
            ::lockState
        )

        update(this.patches.withBundle(bundleUid, updated))
        // Armed against what the availability rules left behind, so the next tap sees the
        // selection it is compared to
        universalArmedFor = bundleUid to this.patches[bundleUid].orEmpty()
    }

    /** True when the next [selectAll] holds universal patches back for another tap. */
    fun selectAllHoldsUniversal(bundleUid: Int, patches: List<Pair<PatchInfo, Boolean>>): Boolean {
        val selected = this.patches[bundleUid].orEmpty()
        return bulkEnableHoldsUniversal(
            patches,
            universalArmed(bundleUid, selected),
            ::lockState
        )
    }

    /**
     * Lock state of [patch] for the install target the dialog is configuring.
     *
     * A REQUIRED patch only locks while the selection stays within one bundle, see
     * [PatchSelectionUtils.applyAvailability].
     */
    fun lockState(patch: PatchInfo) = target.lockState(patch, singleBundle = !hasMultipleBundles)

    private fun universalArmed(bundleUid: Int, selected: Set<String>) =
        universalArmedFor == (bundleUid to selected)

    /**
     * Deselect all given patches for a bundle.
     * Removes the bundle entry entirely if nothing remains selected. LOCKED_ON patches are kept.
     */
    fun deselectAll(bundleUid: Int, patches: List<Pair<PatchInfo, Boolean>>) {
        val kept = this.patches[bundleUid]?.toMutableSet() ?: mutableSetOf()
        patches.forEach { (patch, enabled) ->
            if (lockState(patch) == PatchLockState.LOCKED_ON) return@forEach
            if (enabled) kept.remove(patch.name)
        }
        update(this.patches.withBundle(bundleUid, kept))
    }

    /**
     * Reset a bundle's selection to the default patches for the current install target.
     *
     * Defaults are read from the bundle itself rather than the dialog's list, so they cover the
     * complete set instead of just search results and reach only the patches the run would have
     * started with: the dialog lists patches declaring other app versions so they can be enabled
     * by hand, but they are no more part of the defaults here than they were on first open.
     */
    fun resetToDefault(bundleUid: Int) {
        val bundle = bundles.firstOrNull { it.uid == bundleUid } ?: return
        val defaults = bundle.patchSequence(allowIncompatible)
            .filter(target::defaultSelected)
            .mapTo(mutableSetOf()) { it.name }
        update(patches.withBundle(bundleUid, defaults))
    }

    /**
     * Restores the DB-persisted patch selection for a bundle.
     * No-op if there is no saved selection for the given bundle.
     */
    fun restoreSaved(bundleUid: Int) {
        val savedForBundle = savedPatches[bundleUid] ?: return
        update(patches.withBundle(bundleUid, savedForBundle))
    }

    fun updateOption(bundleUid: Int, patchName: String, optionKey: String, value: Any?) {
        options = options.updateOption(bundleUid, patchName, optionKey, value)
    }

    fun resetOptions(bundleUid: Int, patchName: String) {
        options = options.resetOptionsForPatch(bundleUid, patchName)
    }

    /**
     * Brings the sources this app is kept from back into the open dialog, for this run only.
     *
     * Nothing is written: the app stays kept from them, and the next run hides them again. Which
     * sources an app is patched from is a standing decision made where the apps are, and a look at
     * what was left out is not the place to overturn it by accident. The patches revealed here do
     * take part in this run, since the dialog counts them and the user can edit them.
     */
    fun revealHiddenSources() {
        app ?: return

        mutedSources = emptySet()
        applyMutes()
    }

    private fun applyMutes() {
        bundles = allBundles.withoutMutedSources(mutedSources) { it.uid }
    }

    /** Opens the copy-from-another-bundle picker for [targetBundleUid]. */
    fun openCopy(targetBundleUid: Int) {
        val selectedApp = app ?: return
        copy.open(
            scope = scope,
            targetPackageName = selectedApp.packageName,
            targetBundleUid = targetBundleUid,
            targetPatchNames = bundlePatches(targetBundleUid).keys
        )
    }

    /**
     * Applies a picked [candidate] to the in-memory selection. Changes reach the database only
     * when the user proceeds to patching.
     */
    fun applyCopy(candidate: CopySelectionCandidate) {
        val forApp = app ?: return
        val targetBundleUid = copy.targetBundleUid ?: return

        scope.launch {
            val copied = copy.resolve(
                candidate = candidate,
                targetPatches = bundlePatches(targetBundleUid)
            ) ?: return@launch
            // The dialog may have closed or moved to another app while the copy was read
            if (app !== forApp || copy.targetBundleUid != targetBundleUid) return@launch

            // The copy comes from a run that may have targeted another installer
            update(patches.withBundle(targetBundleUid, copied.patches))
            options = options.mergeBundleOptions(targetBundleUid, copied.options)

            copy.finish(copied.patches.size)
        }
    }

    /** Raises [failures] over the dialog, for the user to fix or drop before the run goes on. */
    fun reportUnreadablePaths(failures: List<PathValidationResult>) {
        unreadablePaths = failures
    }

    /** Leaves the values alone and puts the user back in the selection to deal with them. */
    fun dismissUnreadablePaths() {
        unreadablePaths = emptyList()
    }

    /**
     * Drops the options behind the unreadable paths, so the run uses the defaults the patches
     * declare and the selection is saved without them as well.
     */
    fun dropUnreadablePaths() {
        options = options.withoutFailingPaths(unreadablePaths)
        unreadablePaths = emptyList()
    }

    // The install target's rules have the last say on every edit, scoped to the sources shown
    private fun update(selection: PatchSelection) {
        patches = target.applyTo(selection, bundles)
    }

    private fun bundlePatches(bundleUid: Int): Map<String, PatchInfo> =
        bundles.firstOrNull { it.uid == bundleUid }
            ?.patches
            ?.associateBy { it.name }
            ?: emptyMap()
}
