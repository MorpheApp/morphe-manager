/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.R
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.domain.batch.*
import app.morphe.manager.domain.repository.InstalledAppRepository
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.domain.repository.PatchSelectionRepository
import app.morphe.manager.patcher.patch.PatchBundleInfo
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.util.*
import app.morphe.manager.util.PatchSelectionUtils.resetOptionsForPatch
import app.morphe.manager.util.PatchSelectionUtils.togglePatch
import app.morphe.manager.util.PatchSelectionUtils.updateOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

/**
 * Live patch selection for one queued app while its editor is open.
 *
 * Mirrors the state the expert dialog drives in the single-app flow, so the batch screen can
 * reuse that dialog instead of growing a second patch list. Edits stay here until they are
 * applied, which keeps a canceled edit from touching the plan.
 */
class BatchPatchEdit(
    val packageName: String,
    val bundles: List<PatchBundleInfo.Scoped>,
    val savedSelection: PatchSelection,
    val newPatches: Map<Int, Set<String>>,
    initialOptions: Options
) {
    var selection by mutableStateOf(savedSelection)
        private set

    var options by mutableStateOf(initialOptions)
        private set

    val allPatchesInfo: List<Pair<PatchBundleInfo.Scoped, List<Pair<PatchInfo, Boolean>>>>
        get() = bundles.map { bundle ->
            val selected = selection[bundle.uid].orEmpty()
            val patches = bundle.patchSequence(true)
                .map { patch -> patch to (patch.name in selected) }
                .sortedBy { (patch, _) -> patch.name }
                .toList()
            bundle to patches
        }.filter { it.second.isNotEmpty() }
            .sortedByDescending { (bundle, _) -> bundle.compatible.size }

    val totalSelectedCount get() = selection.values.sumOf { it.size }

    val totalPatchesCount get() = allPatchesInfo.sumOf { it.second.size }

    val hasMultipleBundles get() = selection.count { (_, patches) -> patches.isNotEmpty() } > 1

    fun togglePatch(bundleUid: Int, patchName: String) {
        selection = selection.togglePatch(bundleUid, patchName)
    }

    fun selectAll(bundleUid: Int, patches: List<Pair<PatchInfo, Boolean>>) =
        replaceBundle(bundleUid, patches.mapTo(mutableSetOf()) { (patch, _) -> patch.name })

    fun deselectAll(bundleUid: Int, patches: List<Pair<PatchInfo, Boolean>>) {
        val removed = patches.mapTo(mutableSetOf()) { (patch, _) -> patch.name }
        replaceBundle(bundleUid, selection[bundleUid].orEmpty() - removed)
    }

    fun resetToDefault(bundleUid: Int, allPatches: List<Pair<PatchInfo, Boolean>>) =
        replaceBundle(
            bundleUid,
            allPatches.filter { (patch, _) -> patch.include }
                .mapTo(mutableSetOf()) { (patch, _) -> patch.name }
        )

    fun restoreSaved(bundleUid: Int) {
        replaceBundle(bundleUid, savedSelection[bundleUid] ?: return)
    }

    fun updateOption(bundleUid: Int, patchName: String, optionKey: String, value: Any?) {
        options = options.updateOption(bundleUid, patchName, optionKey, value)
    }

    fun resetOptions(bundleUid: Int, patchName: String) {
        options = options.resetOptionsForPatch(bundleUid, patchName)
    }

    private fun replaceBundle(bundleUid: Int, patches: Set<String>) {
        selection = selection.toMutableMap().apply {
            if (patches.isEmpty()) remove(bundleUid) else put(bundleUid, patches)
        }
    }
}

/**
 * Screen-level wrapper around [BatchPatchCoordinator].
 *
 * The run itself lives in the coordinator on the application scope, so leaving and reopening
 * the batch screen keeps a queue going. This ViewModel only owns screen state such as which
 * item a file picker was opened for.
 */
class BatchPatcherViewModel : ViewModel(), KoinComponent {
    private val app: Application by inject()
    private val fs: Filesystem by inject()
    private val pm: PM by inject()
    private val coordinator: BatchPatchCoordinator by inject()
    private val installedAppRepository: InstalledAppRepository by inject()
    private val patchBundleRepository: PatchBundleRepository by inject()
    private val patchSelectionRepository: PatchSelectionRepository by inject()

    val state = coordinator.state

    /** Package the attach-APK picker was opened for, null when no picker is pending. */
    var attachTarget: String? by mutableStateOf(null)
        private set

    /**
     * Plans the run once. Re-entering the screen while a queue is alive keeps the existing
     * state instead of throwing away progress, and a run that already covers exactly these
     * apps is reused so rotation does not restart planning.
     */
    fun ensurePlan(packageNames: List<String>, useMount: Boolean) {
        val current = state.value
        if (current != null) {
            if (current.phase == BatchPhase.PLANNING || current.phase == BatchPhase.RUNNING) return
            if (current.items.map { it.packageName } == packageNames) return
            coordinator.clear()
        }
        coordinator.plan(packageNames, useMount, BatchInstallPolicy.SAVE_ONLY)
    }

    fun requestAttach(packageName: String) {
        attachTarget = packageName
    }

    /** Patch selection editor for one queued app, null when none is open. */
    var edit: BatchPatchEdit? by mutableStateOf(null)
        private set

    /**
     * Opens the editor for [item], scoping the patch list to the exact APK version the queue
     * resolved so the user never sees patches that could not run against it anyway.
     */
    fun beginEdit(item: BatchPatchItem) {
        val source = item.source ?: return
        viewModelScope.launch {
            val bundles = patchBundleRepository
                .scopedBundleInfoFlow(item.packageName, source.version, source.versionCode)
                .first()
                .filter { it.enabled }

            // A patch counts as new when it was absent from the snapshot taken at the last
            // run. Without a snapshot there is no "last run" to compare against, so nothing
            // is badged rather than everything
            val newPatches = bundles.associate { bundle ->
                val seen = patchSelectionRepository.getSeenPatches(item.packageName, bundle.uid)
                bundle.uid to bundle.patches
                    .filter { seen != null && it.name !in seen }
                    .mapTo(mutableSetOf()) { it.name }
            }.filterValues { it.isNotEmpty() }

            edit = BatchPatchEdit(
                packageName = item.packageName,
                bundles = bundles,
                savedSelection = item.selection,
                newPatches = newPatches,
                initialOptions = item.options
            )
        }
    }

    fun cancelEdit() {
        edit = null
    }

    /**
     * App whose patch source is being chosen, null when no picker is open.
     *
     * Simple mode is asked which source to use in the single-app flow too. The queue cannot
     * ask mid-run, so the same question is answered here instead.
     */
    var sourcePick: BatchPatchItem? by mutableStateOf(null)
        private set

    fun beginSourcePick(item: BatchPatchItem) {
        sourcePick = item
    }

    fun cancelSourcePick() {
        sourcePick = null
    }

    fun pickSource(bundleUid: Int) {
        val item = sourcePick ?: return
        sourcePick = null
        coordinator.narrowToSource(item.packageName, bundleUid)
    }

    fun applyEdit() {
        val current = edit ?: return
        coordinator.updateSelection(current.packageName, current.selection, current.options)
        edit = null
    }

    /**
     * Copies the picked APK into the manager's private storage before handing it to the
     * resolver, because the content URI is not readable once the picker session ends.
     */
    fun onApkPicked(uri: Uri?) {
        val packageName = attachTarget
        attachTarget = null
        if (uri == null || packageName == null) return

        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) { copyToWorkspace(uri) }
            if (file == null) {
                app.toast(app.getString(R.string.home_invalid_apk_io_error))
                return@launch
            }
            coordinator.attachApk(packageName, file)
        }
    }

    fun toggleExcluded(packageName: String) = coordinator.toggleExcluded(packageName)

    fun forceVersion(packageName: String) = coordinator.forceVersion(packageName)

    fun setPolicy(policy: BatchInstallPolicy) = coordinator.setPolicy(policy)

    fun markInstalled(packageName: String, installedPackageName: String) =
        coordinator.markInstallResult(
            packageName = packageName,
            outcome = BatchInstallOutcome.INSTALLED,
            installedPackageName = installedPackageName
        )

    fun markInstallFailed(packageName: String, message: String?) =
        coordinator.markInstallResult(packageName, BatchInstallOutcome.FAILED, message)

    /** Launches an app the summary just installed. */
    fun openApp(packageName: String) {
        pm.launch(packageName)
    }

    fun start() = coordinator.start()

    fun cancel() = coordinator.cancel()

    fun clear() = coordinator.clear()

    /**
     * Re-plans the apps that failed or were canceled so the user can retry without
     * rebuilding the selection from the home screen.
     */
    fun retryUnfinished() {
        val current = state.value ?: return
        val packages = current.items
            .filter { it.state == BatchItemState.FAILED || it.state == BatchItemState.CANCELLED }
            .map { it.packageName }
        if (packages.isEmpty()) return

        val useMount = current.useMount
        val policy = current.policy
        coordinator.clear()
        coordinator.plan(packages, useMount, policy)
    }

    /**
     * Records the [InstallType] of a patched app once it is installed from the summary,
     * replacing the SAVED record the queue wrote after patching.
     */
    suspend fun persistInstalled(
        item: BatchPatchItem,
        installedPackageName: String,
        installType: InstallType
    ): Boolean = withContext(Dispatchers.IO) {
        val selectionPayload = patchBundleRepository.snapshotSelection(item.selection)
        val version = item.patchedFile
            ?.let { pm.getPackageInfo(it)?.versionName?.takeUnless { name -> name.isBlank() } }
            ?: item.version
            ?: return@withContext false

        installedAppRepository.addOrUpdate(
            currentPackageName = installedPackageName,
            originalPackageName = item.packageName,
            version = version,
            installType = installType,
            patchSelection = item.selection,
            selectionPayload = selectionPayload
        )
        true
    }

    private fun copyToWorkspace(uri: Uri): File? = try {
        val displayName = app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index != -1) cursor.getString(index) else null
        }
        val extension = displayName?.substringAfterLast('.', "apk")?.lowercase() ?: "apk"
        val target = fs.uiTempDir.resolve("batch_input_${System.currentTimeMillis()}.$extension")

        val copied = app.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        if (copied == null || copied == 0L) {
            target.delete()
            null
        } else {
            target
        }
    } catch (e: Exception) {
        Log.e(tag, "Failed to copy attached APK", e)
        null
    }
}
