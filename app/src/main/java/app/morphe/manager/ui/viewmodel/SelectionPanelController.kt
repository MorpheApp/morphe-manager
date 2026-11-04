/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.morphe.manager.R
import app.morphe.manager.domain.manager.loadCopySelectionCandidates
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.domain.repository.PatchOptionsRepository
import app.morphe.manager.domain.repository.PatchSelectionRepository
import app.morphe.manager.domain.repository.SelectionSetRepository
import app.morphe.manager.domain.repository.SelectionSetSummary
import app.morphe.manager.domain.repository.SelectionSnapshot
import app.morphe.manager.patcher.patch.PatchBundleInfo
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.ui.screen.shared.CopySelectionCandidate
import app.morphe.manager.ui.screen.shared.UserMessages
import app.morphe.manager.util.AppDataResolver
import app.morphe.manager.util.Options
import app.morphe.manager.util.PatchSelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Patches and options one copy contributes, already filtered against the target bundle. */
data class CopiedSelection(
    val patches: Set<String>,
    val options: Map<String, Map<String, Any?>>
)

/**
 * A selection being edited before it is used, as the selection panel acts on it. Changes land in
 * the selection held in memory, which reaches the database only once the host's edit is confirmed.
 */
interface SelectionPanelHost {
    val panel: SelectionPanelController

    /** Package of the app the sets are kept for. */
    val setsPackageName: String

    /** Whether there is a selection to restore, the one the editor opened with. */
    val hasSavedSelection: Boolean

    /** Puts back the selection the editor opened with, every source at once. */
    fun restoreSaved()

    /** Switches every source to what [set] holds, a source it leaves out left unused. */
    fun applySet(set: SelectionSetSummary)

    /** Keeps the selection being edited as a set named [name]. */
    fun saveSet(name: String)

    fun deleteSet(set: SelectionSetSummary)

    /** Names [set] [name], in place of any other set of the app under that name. */
    fun renameSet(set: SelectionSetSummary, name: String)

    /** Switches [bundleUid] to the patches it recommends for this app. */
    fun resetToDefault(bundleUid: Int)

    /** Loads what [targetBundleUid] can copy its selection from. */
    fun loadCopyCandidates(targetBundleUid: Int)

    /** Copies the selection of [candidate] into the source the panel loaded candidates for. */
    fun applyCopy(candidate: CopySelectionCandidate)
}

/**
 * State behind the selection panel docked under a patch list: the sets kept for the app and the
 * selections of other apps and sources a source's patches can be copied from. Shared by every
 * screen that edits a selection. It stops at resolving what a set or a copy holds, since where the
 * result lands differs per host.
 */
@Stable
class SelectionPanelController(
    private val app: Application,
    private val messages: UserMessages,
    private val patchSelectionRepository: PatchSelectionRepository,
    private val setRepository: SelectionSetRepository,
    private val patchBundleRepository: PatchBundleRepository,
    private val optionsRepository: PatchOptionsRepository,
    private val appDataResolver: AppDataResolver
) {
    var isOpen by mutableStateOf(false)
        private set

    /** Source the copy section copies into; null until the panel asks for its candidates. */
    var copyTargetUid by mutableStateOf<Int?>(null)
        private set

    /** Selections [copyTargetUid] can copy from; null while they load. */
    var candidates by mutableStateOf<List<CopySelectionCandidate>?>(null)
        private set

    // The selection the candidates were loaded for, as one source can be a target for several apps
    private var loadedFor: Pair<String, Int>? = null

    fun open() {
        isOpen = true
    }

    fun close() {
        isOpen = false
        copyTargetUid = null
        candidates = null
        loadedFor = null
    }

    fun toggle() = if (isOpen) close() else open()

    /** The sets kept for the app saved under [packageName]. */
    fun setsFor(packageName: String): Flow<List<SelectionSetSummary>> = setRepository.summariesFor(packageName)

    /**
     * Loads what [targetBundleUid] can copy from, off the main thread. [targetPackageName] is the key
     * the host saves under, so the target leaves itself out. Asking again for the target already
     * loaded keeps what is there, so the list does not flash back to loading.
     */
    fun loadCandidates(
        scope: CoroutineScope,
        targetPackageName: String,
        targetBundleUid: Int,
        targetPatchNames: Set<String>
    ) {
        val target = targetPackageName to targetBundleUid
        if (loadedFor == target) return
        loadedFor = target
        copyTargetUid = targetBundleUid
        candidates = null
        scope.launch(Dispatchers.IO) {
            val loaded = loadCopySelectionCandidates(
                patchSelectionRepository = patchSelectionRepository,
                setRepository = setRepository,
                patchBundleRepository = patchBundleRepository,
                appDataResolver = appDataResolver,
                targetPackageName = targetPackageName,
                targetBundleUid = targetBundleUid,
                targetPatchNames = targetPatchNames
            )
            withContext(Dispatchers.Main) {
                // Discard the result if the panel closed or moved to another source while loading
                if (loadedFor == target) candidates = loaded
            }
        }
    }

    /**
     * What [candidate] contributes, filtered against [targetPatches] so entries missing under the
     * target bundle are dropped. Null when nothing survives, which the user is told about.
     */
    suspend fun resolveCopy(
        candidate: CopySelectionCandidate,
        targetPatches: Map<String, PatchInfo>
    ): CopiedSelection? {
        val copied = withContext(Dispatchers.IO) {
            if (candidate.setUid != null) {
                val set = setRepository.load(candidate.setUid, mapOf(candidate.bundleUid to targetPatches))
                return@withContext CopiedSelection(
                    patches = set.patches[candidate.bundleUid].orEmpty(),
                    options = set.options[candidate.bundleUid].orEmpty()
                )
            }

            val patches = patchSelectionRepository
                .exportForPackageAndBundle(candidate.packageName, candidate.bundleUid)
                .filterTo(mutableSetOf()) { it in targetPatches }

            // Read as live values rather than through the raw export, which hands back
            // JSON encoded strings
            val options = optionsRepository.getOptionsForBundle(
                packageName = candidate.packageName,
                bundleUid = candidate.bundleUid,
                bundlePatchInfo = targetPatches
            ).filterValues { it.isNotEmpty() }

            CopiedSelection(patches, options)
        }

        if (copied.patches.isEmpty() && copied.options.isEmpty()) {
            messages.show(R.string.expert_mode_copy_from_bundle_no_patches)
            return null
        }
        return copied
    }

    /** Reports a copy of [patchCount] patches. */
    fun copied(patchCount: Int) {
        messages.show(
            app.resources.getQuantityString(
                R.plurals.expert_mode_copy_from_bundle_done,
                patchCount,
                patchCount.toString()
            )
        )
    }

    /** Reports the recommended patches of a source applied. */
    fun defaultsApplied() {
        messages.show(R.string.expert_mode_reset_to_default_done)
    }

    /** Reports the saved selection put back. */
    fun restored() {
        messages.show(R.string.expert_mode_restore_saved_done)
    }

    /** What [set] holds, read against the patches [bundles] offer the app today. */
    suspend fun resolveSet(set: SelectionSetSummary, bundles: List<PatchBundleInfo.Scoped>): SelectionSnapshot =
        withContext(Dispatchers.IO) {
            val offered = bundles.associate { bundle -> bundle.uid to bundle.patches.associateBy { it.name } }
            setRepository.load(set.uid, offered)
        }

    /** Reports [set] applied. */
    fun applied(set: SelectionSetSummary) {
        messages.show(app.getString(R.string.selection_set_applied, set.name))
    }

    /** Keeps [selection] and its [options] as a set named [name] for the app saved under [packageName]. */
    fun saveSet(
        scope: CoroutineScope,
        packageName: String,
        name: String,
        selection: PatchSelection,
        options: Options
    ) = scope.launch {
        withContext(Dispatchers.IO) { setRepository.save(packageName, name, selection, options) }
        messages.show(app.getString(R.string.selection_set_saved, name))
    }

    fun deleteSet(scope: CoroutineScope, set: SelectionSetSummary) = scope.launch {
        withContext(Dispatchers.IO) { setRepository.delete(set.uid) }
        messages.show(app.getString(R.string.selection_set_deleted, set.name))
    }

    fun renameSet(scope: CoroutineScope, set: SelectionSetSummary, name: String) = scope.launch {
        withContext(Dispatchers.IO) { setRepository.rename(set, name) }
        messages.show(app.getString(R.string.selection_set_renamed, name))
    }
}
