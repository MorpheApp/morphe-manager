/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.morphe.manager.domain.repository.SelectionSetSummary
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.patcher.patch.PatchLockState
import app.morphe.manager.patcher.patch.blocksToggle
import app.morphe.manager.util.Options
import app.morphe.manager.util.PatchSelection
import app.morphe.manager.util.PatchSelectionUtils
import app.morphe.manager.util.PatchSelectionUtils.bulkEnableHoldsUniversal
import app.morphe.manager.util.PatchSelectionUtils.bulkEnablePatches
import app.morphe.manager.util.PatchSelectionUtils.resetOptionsForPatch
import app.morphe.manager.util.PatchSelectionUtils.togglePatch
import app.morphe.manager.util.PatchSelectionUtils.updateOption
import app.morphe.manager.util.PatchSelectionUtils.withBundle
import kotlinx.coroutines.CoroutineScope

/**
 * The patches and options the expert dialog edits, with every edit the dialog makes on them.
 * Each screen that opens the dialog decides what locks a patch for its install target and the
 * rules an edit is settled by, so the dialog behaves the same wherever it is opened from.
 *
 * @param scope Scope the work of the selection panel runs in, sets and copies read or written.
 */
abstract class PatchSelectionEditor(protected val scope: CoroutineScope) : SelectionPanelHost {
    /** The patches picked, by source, as the install target's rules last settled them. */
    var selection by mutableStateOf<PatchSelection>(emptyMap())
        private set

    var options by mutableStateOf<Options>(emptyMap())
        protected set

    // Bundle and selection left behind by the last "Enable all". Universal patches are applied
    // only while this still matches the live selection, so any other edit disarms them again
    private var universalArmedFor by mutableStateOf<Pair<Int, Set<String>>?>(null)

    /**
     * Lock state of [patch] for the install target the selection is for.
     *
     * A REQUIRED patch only locks while the selection draws on one bundle, see
     * [PatchSelectionUtils.applyAvailability].
     */
    abstract fun lockState(patch: PatchInfo): PatchLockState

    /** [selection] as the install target's availability rules leave it. */
    protected abstract fun settle(selection: PatchSelection): PatchSelection

    /** The patch named [patchName] in [bundleUid], or null when the source does not hold one. */
    protected abstract fun patchOf(bundleUid: Int, patchName: String): PatchInfo?

    /**
     * What [bundleUid] selects on its own for the install target, or null when it is not shown.
     *
     * Read from the bundle rather than the dialog's list, which also carries the patches declaring
     * other app versions so they can be enabled by hand: those belong to the defaults only where
     * the run itself reaches them.
     */
    protected abstract fun defaultsOf(bundleUid: Int): Set<String>?

    /** Starts editing [selection] and [options] as they are, the way the dialog opens on them. */
    protected fun start(selection: PatchSelection, options: Options) {
        this.selection = selection
        this.options = options
        universalArmedFor = null
    }

    /** Takes [selection] as edited, settled by the install target's rules. */
    protected fun update(selection: PatchSelection) {
        this.selection = settle(selection)
    }

    /** Replaces what [bundleUid] has selected with [patches]. */
    protected fun replaceBundle(bundleUid: Int, patches: Set<String>) {
        update(selection.withBundle(bundleUid, patches))
    }

    /** Toggles a patch, from a bundle that may have nothing selected yet. */
    fun togglePatch(bundleUid: Int, patchName: String) {
        // Locked patches are toggled only through availability rules; no-op here
        val patch = patchOf(bundleUid, patchName)
        val selected = patchName in selection[bundleUid].orEmpty()
        if (patch != null && lockState(patch).blocksToggle(selected)) return

        update(selection.togglePatch(bundleUid, patchName))
    }

    /**
     * Selects every patch in [patches], the list the dialog currently shows, so an active search
     * narrows both stages. LOCKED_OFF patches are skipped, and universal patches are staged
     * behind the regular ones for a second call, see [PatchSelectionUtils.bulkEnablePatches].
     */
    fun selectAll(bundleUid: Int, patches: List<Pair<PatchInfo, Boolean>>) {
        val selected = selection[bundleUid].orEmpty()
        replaceBundle(
            bundleUid,
            bulkEnablePatches(patches, selected, universalArmed(bundleUid, selected), ::lockState)
        )
        // Armed against what the availability rules left behind, so the next tap sees the
        // selection it is compared to
        universalArmedFor = bundleUid to selection[bundleUid].orEmpty()
    }

    /** True when the next [selectAll] holds universal patches back for another tap. */
    fun selectAllHoldsUniversal(bundleUid: Int, patches: List<Pair<PatchInfo, Boolean>>): Boolean {
        val selected = selection[bundleUid].orEmpty()
        return bulkEnableHoldsUniversal(patches, universalArmed(bundleUid, selected), ::lockState)
    }

    private fun universalArmed(bundleUid: Int, selected: Set<String>) =
        universalArmedFor == (bundleUid to selected)

    /** Deselects every patch in [patches] but the LOCKED_ON ones. */
    fun deselectAll(bundleUid: Int, patches: List<Pair<PatchInfo, Boolean>>) {
        val removed = patches
            .filterNot { (patch, _) -> lockState(patch) == PatchLockState.LOCKED_ON }
            .mapTo(mutableSetOf()) { (patch, _) -> patch.name }
        replaceBundle(bundleUid, selection[bundleUid].orEmpty() - removed)
    }

    /** Switches [bundleUid] to what it selects on its own, see [defaultsOf]. */
    override fun resetToDefault(bundleUid: Int) {
        replaceBundle(bundleUid, defaultsOf(bundleUid) ?: return)
        panel.defaultsApplied()
    }

    fun updateOption(bundleUid: Int, patchName: String, optionKey: String, value: Any?) {
        options = options.updateOption(bundleUid, patchName, optionKey, value)
    }

    fun resetOptions(bundleUid: Int, patchName: String) {
        options = options.resetOptionsForPatch(bundleUid, patchName)
    }

    override fun deleteSet(set: SelectionSetSummary) {
        panel.deleteSet(scope, set)
    }

    override fun renameSet(set: SelectionSetSummary, name: String) {
        panel.renameSet(scope, set, name)
    }
}
