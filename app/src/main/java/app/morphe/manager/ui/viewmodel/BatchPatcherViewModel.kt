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
import app.morphe.manager.domain.apk.InstalledApkInfo
import app.morphe.manager.domain.apk.LocalApkSources
import app.morphe.manager.domain.apk.SavedApkInfo
import app.morphe.manager.domain.batch.*
import app.morphe.manager.domain.bundles.AppVersionCatalog
import app.morphe.manager.domain.bundles.BundleRecommendation
import app.morphe.manager.domain.bundles.BundledAppTarget
import app.morphe.manager.domain.manager.DownloadUrlResolver
import app.morphe.manager.domain.repository.InstalledAppRepository
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.domain.repository.PatchSelectionRepository
import app.morphe.manager.patcher.patch.*
import app.morphe.manager.util.*
import app.morphe.manager.util.PatchSelectionUtils.bulkEnableHoldsUniversal
import app.morphe.manager.util.PatchSelectionUtils.bulkEnablePatches
import app.morphe.manager.util.PatchSelectionUtils.resetOptionsForPatch
import app.morphe.manager.util.PatchSelectionUtils.togglePatch
import app.morphe.manager.util.PatchSelectionUtils.updateOption
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.InstallerType
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
    initialOptions: Options,
    private val installerType: InstallerType
) {
    var selection by mutableStateOf(savedSelection)
        private set

    var options by mutableStateOf(initialOptions)
        private set

    // Bundle and selection left behind by the last "Enable all". Universal patches are applied
    // only while this still matches the live selection, so any other edit disarms them again
    private var universalArmedFor by mutableStateOf<Pair<Int, Set<String>>?>(null)

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

    /** Lock state of [patch] for the install target this queue runs against. */
    fun lockStateOf(patch: PatchInfo) = patch.lockState(installerType, SELECTION_APK_ARCHITECTURE)

    fun togglePatch(bundleUid: Int, patchName: String) {
        // Locked patches are toggled only through availability rules; no-op here
        val patch = bundles.firstOrNull { it.uid == bundleUid }
            ?.patches
            ?.firstOrNull { it.name == patchName }
        if (patch != null && lockStateOf(patch) != PatchLockState.NONE) return

        selection = selection.togglePatch(bundleUid, patchName)
    }

    /**
     * Select all patches shown for a bundle, staging universal patches behind the regular ones
     * exactly like the single-app flow does, see [PatchSelectionUtils.bulkEnablePatches].
     */
    fun selectAll(bundleUid: Int, patches: List<Pair<PatchInfo, Boolean>>) {
        val selected = selection[bundleUid].orEmpty()
        val updated = bulkEnablePatches(patches, selected, universalArmed(bundleUid, selected), ::lockStateOf)

        replaceBundle(bundleUid, updated)
        universalArmedFor = bundleUid to updated
    }

    /** True when the next [selectAll] holds universal patches back for another tap. */
    fun selectAllHoldsUniversal(bundleUid: Int, patches: List<Pair<PatchInfo, Boolean>>): Boolean {
        val selected = selection[bundleUid].orEmpty()
        return bulkEnableHoldsUniversal(patches, universalArmed(bundleUid, selected), ::lockStateOf)
    }

    private fun universalArmed(bundleUid: Int, selected: Set<String>) =
        universalArmedFor == (bundleUid to selected)

    fun deselectAll(bundleUid: Int, patches: List<Pair<PatchInfo, Boolean>>) {
        val removed = patches
            .filterNot { (patch, _) -> lockStateOf(patch) == PatchLockState.LOCKED_ON }
            .mapTo(mutableSetOf()) { (patch, _) -> patch.name }
        replaceBundle(bundleUid, selection[bundleUid].orEmpty() - removed)
    }

    fun resetToDefault(bundleUid: Int, allPatches: List<Pair<PatchInfo, Boolean>>) =
        replaceBundle(
            bundleUid,
            allPatches.filter { (patch, _) -> patch.defaultSelected(installerType, SELECTION_APK_ARCHITECTURE) }
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
    private val downloadUrlResolver: DownloadUrlResolver by inject()
    private val versionCatalog: AppVersionCatalog by inject()
    private val localApkSources: LocalApkSources by inject()

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

    /**
     * Everything the APK availability dialog needs about one queued app: which versions the
     * sources cover, and what is already on the device to patch from.
     */
    data class ApkChoice(
        val item: BatchPatchItem,
        val recommended: AppTarget?,
        val compatible: List<BundledAppTarget>,
        val recommendedByBundle: Map<Int, BundleRecommendation>,
        val saved: SavedApkInfo?,
        val installed: InstalledApkInfo?,
        val installedOnDevice: Boolean,
        val selectedVersion: AppTarget?
    )

    var apkChoice: ApkChoice? by mutableStateOf(null)
        private set

    fun beginApkChoice(item: BatchPatchItem) {
        viewModelScope.launch {
            val recommended = versionCatalog.recommendedVersions.first()[item.packageName]
            val (onDevice, installed) = withContext(Dispatchers.IO) {
                localApkSources.installed(item.packageName)
            }

            apkChoice = ApkChoice(
                item = item,
                recommended = recommended,
                compatible = versionCatalog.compatibleVersions.first()[item.packageName].orEmpty(),
                recommendedByBundle = versionCatalog.recommendedVersionsByBundle.first()[item.packageName].orEmpty(),
                saved = withContext(Dispatchers.IO) { localApkSources.saved(item.packageName) },
                installed = installed,
                installedOnDevice = onDevice,
                selectedVersion = recommended
            )
        }
    }

    fun selectApkVersion(target: AppTarget) {
        apkChoice = apkChoice?.copy(selectedVersion = target)
    }

    fun cancelApkChoice() {
        apkChoice = null
    }

    /** Keeps the APK already on hand, re-resolving in case the user switched between the two. */
    fun useApkSource(preferInstalled: Boolean) {
        val choice = apkChoice ?: return
        apkChoice = null
        coordinator.useSource(choice.item.packageName, preferInstalled)
    }

    /**
     * App the download instructions are open for, with the best URL known so far.
     *
     * The API redirect takes a moment, so the unresolved search URL is published first and
     * replaced once it resolves. That way the dialog is never waiting on the network.
     */
    data class ApkSearch(val item: BatchPatchItem, val version: String?, val url: String)

    var apkSearch: ApkSearch? by mutableStateOf(null)
        private set

    /** [version] is what the user picked in the availability dialog, not just the recommended one. */
    fun beginApkSearch(item: BatchPatchItem, version: String?) {
        apkChoice = null
        apkSearch = ApkSearch(
            item = item,
            version = version,
            url = downloadUrlResolver.apiSearchUrl(item.packageName, version)
        )
        viewModelScope.launch {
            val resolved = withContext(Dispatchers.IO) {
                downloadUrlResolver.resolve(item.packageName, version)
            }
            apkSearch = apkSearch?.takeIf { it.item.packageName == item.packageName }?.copy(url = resolved)
        }
    }

    fun cancelApkSearch() {
        apkSearch = null
    }

    /** App waiting for its downloaded file, null when nothing was sent to the browser. */
    var attachPrompt: BatchPatchItem? by mutableStateOf(null)
        private set

    /**
     * Hands the download page to the browser and leaves a prompt behind.
     *
     * The file picker deliberately waits for that prompt rather than opening straight away:
     * the browser is coming to the front at this moment, and Android does not let a
     * backgrounded app reliably start anything on top of it.
     */
    fun confirmApkSearch(openUrl: (String) -> Boolean) {
        val search = apkSearch ?: return
        apkSearch = null
        if (openUrl(search.url)) {
            attachPrompt = search.item
        } else {
            app.toast(app.getString(R.string.sources_management_failed_to_open_url))
        }
    }

    fun dismissAttachPrompt() {
        attachPrompt = null
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
                initialOptions = item.options,
                // The queue resolved its plan against one install target, so the editor has to
                // lock patches by the same rules the run will be executed with
                installerType = installerTypeFor(state.value?.useMount == true)
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

    /**
     * Accepts an unsupported version. Confirmed with a toast because the card only swaps a
     * badge, which does not say what was just taken on.
     */
    fun forceVersion(packageName: String) {
        coordinator.forceVersion(packageName)
        app.toast(app.getString(R.string.batch_patch_force_version_done))
    }

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
