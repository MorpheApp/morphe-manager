/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.morphe.manager.domain.repository.SelectionSetSummary
import app.morphe.manager.domain.repository.withoutMutedSources
import app.morphe.manager.domain.selection.PatchTarget
import app.morphe.manager.patcher.patch.*
import app.morphe.manager.ui.model.SelectedApp
import app.morphe.manager.ui.screen.shared.CopySelectionCandidate
import app.morphe.manager.util.*
import app.morphe.manager.util.PatchSelectionUtils.listedWith
import app.morphe.manager.util.PatchSelectionUtils.mergeBundleOptions
import app.morphe.manager.util.PatchSelectionUtils.spansMultipleBundles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The patches and options of a run picked by hand in the expert dialog, for the APK the run starts
 * from. [PatchFlow] opens it once the APK is known and patches with what it holds on proceeding.
 *
 * @param panel State of the panel the dialog docks under its list, with the sets kept for the app
 * and the selections a source can be copied from.
 */
class ExpertModeSelection(
    scope: CoroutineScope,
    override val panel: SelectionPanelController
) : PatchSelectionEditor(scope) {
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

    // The selection and options as the dialog opened, which restoring the saved selection goes
    // back to. Options too, as switching to a set replaces them
    private var savedPatches by mutableStateOf<PatchSelection>(emptyMap())
    private var savedOptions: Options = emptyMap()

    /** Patches that are new in the current bundle version relative to the last saved selection. */
    var newPatches by mutableStateOf<Map<Int, Set<String>>>(emptyMap())
        private set

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
        start(patches, options)
        savedPatches = patches
        savedOptions = options
        this.newPatches = newPatches
        this.allowIncompatible = allowIncompatible
        this.target = target
    }

    /** Drops everything the selection holds, as the dialog closes. */
    fun clear() {
        app = null
        bundles = emptyList()
        allBundles = emptyList()
        mutedSources = emptySet()
        leadingBundleUid = null
        start(emptyMap(), emptyMap())
        savedPatches = emptyMap()
        savedOptions = emptyMap()
        newPatches = emptyMap()
        allowIncompatible = false
        unreadablePaths = emptyList()
        panel.close()
    }

    /**
     * All patches per bundle with their enabled state, in the order the dialog lists them, see
     * [PatchSelectionUtils.listedWith]. The final per-section ordering (new patches first) is
     * applied in the UI layer.
     */
    val allPatchesInfo: List<Pair<PatchBundleInfo.Scoped, List<Pair<PatchInfo, Boolean>>>>
        get() = bundles.listedWith(selection, openedWith = savedPatches, leadingBundleUid = leadingBundleUid)

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
            if (mutedSources.isEmpty()) return selection
            val shown = bundles.mapTo(mutableSetOf()) { it.uid }
            return selection.filterKeys { it in shown }
        }

    /** Total number of currently selected patches across all bundles. */
    val totalSelectedCount: Int
        get() = runSelection.values.sumOf { it.size }

    /** True when patches from more than one bundle are selected (triggers warning on proceed). */
    val hasMultipleBundles: Boolean
        get() = runSelection.spansMultipleBundles()

    override fun lockState(patch: PatchInfo) = target.lockState(patch, singleBundle = !hasMultipleBundles)

    // The install target's rules have the last say on every edit, scoped to the sources shown
    override fun settle(selection: PatchSelection) = target.applyTo(selection, bundles)

    override fun patchOf(bundleUid: Int, patchName: String) = bundlePatches(bundleUid)[patchName]

    /** The patches the run would have started with, the bundle's defaults for the install target. */
    override fun defaultsOf(bundleUid: Int) = bundles.firstOrNull { it.uid == bundleUid }
        ?.patchSequence(allowIncompatible)
        ?.filter(target::defaultSelected)
        ?.mapTo(mutableSetOf()) { it.name }

    override val setsPackageName get() = app?.packageName.orEmpty()

    override val hasSavedSelection get() = savedPatches.values.any { it.isNotEmpty() }

    override fun restoreSaved() {
        update(savedPatches)
        options = savedOptions
        panel.restored()
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

    override fun loadCopyCandidates(targetBundleUid: Int) {
        val selectedApp = app ?: return
        panel.loadCandidates(
            scope = scope,
            targetPackageName = selectedApp.packageName,
            targetBundleUid = targetBundleUid,
            targetPatchNames = bundlePatches(targetBundleUid).keys
        )
    }

    /** Changes reach the database only when the user proceeds to patching. */
    override fun applyCopy(candidate: CopySelectionCandidate) {
        val forApp = app ?: return
        val targetBundleUid = panel.copyTargetUid ?: return

        scope.launch {
            val copied = panel.resolveCopy(
                candidate = candidate,
                targetPatches = bundlePatches(targetBundleUid)
            ) ?: return@launch
            // The dialog may have closed or moved to another app while the copy was read
            if (app !== forApp) return@launch

            // The copy comes from a run that may have targeted another installer
            replaceBundle(targetBundleUid, copied.patches)
            options = options.mergeBundleOptions(targetBundleUid, copied.options)

            panel.copied(copied.patches.size)
        }
    }

    /** Covers every source the app has patches in, the hidden ones included. */
    override fun applySet(set: SelectionSetSummary) {
        val forApp = app ?: return

        scope.launch {
            val snapshot = panel.resolveSet(set, allBundles)
            if (app !== forApp) return@launch

            update(snapshot.patches)
            options = snapshot.options
            panel.applied(set)
        }
    }

    /** Keeps what the run would patch with, so a source held back is left out of the set. */
    override fun saveSet(name: String) {
        val selectedApp = app ?: return
        panel.saveSet(scope, selectedApp.packageName, name, runSelection, options)
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

    private fun bundlePatches(bundleUid: Int): Map<String, PatchInfo> =
        bundles.firstOrNull { it.uid == bundleUid }
            ?.patches
            ?.associateBy { it.name }
            ?: emptyMap()
}
