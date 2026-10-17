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
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.ui.screen.shared.CopySelectionCandidate
import app.morphe.manager.ui.screen.shared.UserMessages
import app.morphe.manager.util.AppDataResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Patches and options one copy contributes, already filtered against the target bundle. */
data class CopiedSelection(
    val patches: Set<String>,
    val options: Map<String, Map<String, Any?>>
)

/**
 * Picker state behind the "copy selection from another bundle" action, shared by the screens
 * that host an expert-mode dialog. It stops at resolving what would be copied, since where the
 * result lands differs per host.
 */
@Stable
class CopySelectionController(
    private val app: Application,
    private val messages: UserMessages,
    private val patchSelectionRepository: PatchSelectionRepository,
    private val patchBundleRepository: PatchBundleRepository,
    private val optionsRepository: PatchOptionsRepository,
    private val appDataResolver: AppDataResolver
) {
    /** Target bundle uid for the in-flight picker; null while it is closed. */
    var targetBundleUid by mutableStateOf<Int?>(null)
        private set

    /** Loaded candidates; null while the initial load is in progress. */
    var candidates by mutableStateOf<List<CopySelectionCandidate>?>(null)
        private set

    /**
     * Opens the picker for [targetBundleUid], loading its candidates off the main thread.
     * [targetPackageName] is the key the host saves under, so the target excludes itself.
     */
    fun open(
        scope: CoroutineScope,
        targetPackageName: String,
        targetBundleUid: Int,
        targetPatchNames: Set<String>
    ) {
        this.targetBundleUid = targetBundleUid
        candidates = null
        scope.launch(Dispatchers.IO) {
            val loaded = loadCopySelectionCandidates(
                patchSelectionRepository = patchSelectionRepository,
                patchBundleRepository = patchBundleRepository,
                appDataResolver = appDataResolver,
                targetPackageName = targetPackageName,
                targetBundleUid = targetBundleUid,
                targetPatchNames = targetPatchNames
            )
            withContext(Dispatchers.Main) {
                // Discard the result if the user closed or retargeted the picker while loading
                if (this@CopySelectionController.targetBundleUid == targetBundleUid) {
                    candidates = loaded
                }
            }
        }
    }

    fun close() {
        targetBundleUid = null
        candidates = null
    }

    /**
     * What [candidate] contributes, filtered against [targetPatches] so entries missing under
     * the new bundle uid are dropped. Null when nothing survives, which closes the picker.
     */
    suspend fun resolve(
        candidate: CopySelectionCandidate,
        targetPatches: Map<String, PatchInfo>
    ): CopiedSelection? {
        val copied = withContext(Dispatchers.IO) {
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
            close()
            return null
        }
        return copied
    }

    /** Reports a copy of [patchCount] patches and closes the picker. */
    fun finish(patchCount: Int) {
        messages.show(
            app.resources.getQuantityString(
                R.plurals.expert_mode_copy_from_bundle_done,
                patchCount,
                patchCount.toString()
            )
        )
        close()
    }
}
