/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.InstalledAppRepository
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.domain.repository.PatchSelectionRepository
import app.morphe.manager.patcher.patch.*
import app.morphe.manager.ui.model.ApkDownloadHelperHost
import app.morphe.manager.ui.model.toHelperFileType
import app.morphe.manager.util.*
import app.morphe.manager.util.PatchSelectionUtils.applyAvailability
import app.morphe.manager.util.PatchSelectionUtils.bulkEnableHoldsUniversal
import app.morphe.manager.util.PatchSelectionUtils.bulkEnablePatches
import app.morphe.manager.util.PatchSelectionUtils.resetOptionsForPatch
import app.morphe.manager.util.PatchSelectionUtils.spansMultipleBundles
import app.morphe.manager.util.PatchSelectionUtils.togglePatch
import app.morphe.manager.util.PatchSelectionUtils.updateOption
import app.morphe.patcher.patch.ApkArchitecture
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
    val itemId: String,
    val bundles: List<PatchBundleInfo.Scoped>,
    val savedSelection: PatchSelection,
    val newPatches: Map<Int, Set<String>>,
    initialOptions: Options,
    private val installerType: InstallerType,
    private val apkArchitecture: ApkArchitecture,
    // Whether the queue reaches patches that declare other app versions, so bulk actions offer
    // the same set the plan was resolved from
    private val allowIncompatible: Boolean
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

    val hasMultipleBundles get() = selection.spansMultipleBundles()

    private val patchesByName = bundles.associate { it.uid to it.patches.associateBy { patch -> patch.name } }

    /**
     * Lock state of [patch] for the install target this queue runs against.
     *
     * A REQUIRED patch only locks while the item draws its patches from one bundle, see
     * [PatchSelectionUtils.applyAvailability].
     */
    fun lockStateOf(patch: PatchInfo) =
        patch.lockState(installerType, apkArchitecture, !hasMultipleBundles)

    fun togglePatch(bundleUid: Int, patchName: String) {
        // Locked patches are toggled only through availability rules; no-op here
        val patch = bundles.firstOrNull { it.uid == bundleUid }
            ?.patches
            ?.firstOrNull { it.name == patchName }
        val selected = patchName in selection[bundleUid].orEmpty()
        if (patch != null && lockStateOf(patch).blocksToggle(selected)) return

        selection = selection.togglePatch(bundleUid, patchName).applyItemAvailability()
    }

    /**
     * Select all patches shown for a bundle, staging universal patches behind the regular ones
     * exactly like the single-app flow does, see [PatchSelectionUtils.bulkEnablePatches].
     */
    fun selectAll(bundleUid: Int, patches: List<Pair<PatchInfo, Boolean>>) {
        val selected = selection[bundleUid].orEmpty()
        val updated = bulkEnablePatches(patches, selected, universalArmed(bundleUid, selected), ::lockStateOf)

        replaceBundle(bundleUid, updated)
        // Armed against what the availability rules left behind, so the next tap sees the
        // selection it is compared to
        universalArmedFor = bundleUid to selection[bundleUid].orEmpty()
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

    /**
     * Reset a bundle's selection to what the plan would have resolved on its own.
     *
     * Defaults are read from the bundle instead of the dialog's list: the list also carries the
     * patches declaring other app versions so they can be enabled by hand, and those belong to
     * the defaults only where the run itself reaches them.
     */
    fun resetToDefault(bundleUid: Int) {
        val bundle = bundles.firstOrNull { it.uid == bundleUid } ?: return
        replaceBundle(
            bundleUid,
            bundle.patchSequence(allowIncompatible)
                .filter { it.defaultSelected(installerType, apkArchitecture) }
                .mapTo(mutableSetOf()) { it.name }
        )
    }

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
        selection = selection.toMutableMap()
            .apply { if (patches.isEmpty()) remove(bundleUid) else put(bundleUid, patches) }
            .applyItemAvailability()
    }

    /** Availability rules of the install target, so an edit lands on the plan already settled. */
    private fun PatchSelection.applyItemAvailability() =
        applyAvailability(installerType, apkArchitecture, patchesByName)
}

/**
 * Screen-level wrapper around [BatchPatchCoordinator].
 *
 * The run itself lives in the coordinator on the application scope, so leaving and reopening
 * the batch screen keeps a queue going. This ViewModel only owns screen state such as which
 * item a file picker was opened for.
 */
class BatchPatcherViewModel : ViewModel(), KoinComponent, ApkDownloadHelperHost {
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
    private val prefs: PreferencesManager by inject()

    val state = coordinator.state

    /** Package the attach-APK picker was opened for, null when no picker is pending. */
    var attachTarget: String? by mutableStateOf(null)
        private set

    /**
     * Plans the run once. Re-entering the screen while a queue is alive keeps the existing
     * state instead of throwing away progress, and a run that already covers exactly these
     * apps is reused so rotation does not restart planning.
     */
    fun ensurePlan(targets: List<BatchTarget>, useMount: Boolean) {
        val current = state.value
        if (current != null) {
            if (current.phase == BatchPhase.PLANNING || current.phase == BatchPhase.RUNNING) return
            if (current.items.map { it.target } == targets) return
            coordinator.clear()
        }
        coordinator.plan(targets, useMount, BatchInstallPolicy.SAVE_ONLY)
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
        coordinator.useSource(choice.item.id, preferInstalled)
    }

    /**
     * App the download instructions are open for, with the best URL known so far.
     *
     * The unfollowed search URL is published first and replaced once the redirect resolves,
     * which is what tells the dialog the destination is not known yet.
     */
    data class ApkSearch(
        val item: BatchPatchItem,
        val version: String?,
        val url: String,
        /**
         * Versions the sources cover, carried so a download helper can be told what else is
         * acceptable when the requested version is no longer offered anywhere.
         */
        val compatible: List<BundledAppTarget> = emptyList()
    )

    var apkSearch: ApkSearch? by mutableStateOf(null)
        private set

    /** [version] is what the user picked in the availability dialog, not just the recommended one. */
    fun beginApkSearch(item: BatchPatchItem, version: String?) {
        // Read before the choice is dropped, which is where the covered versions were resolved
        val compatible = apkChoice?.takeIf { it.item.id == item.id }?.compatible.orEmpty()
        apkChoice = null
        apkSearch = ApkSearch(
            item = item,
            version = version,
            url = downloadUrlResolver.apiSearchUrl(item.packageName, version),
            compatible = compatible
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

    override val helperSignatureCheckAvailable: Boolean
        get() {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) return false
            val packageName = apkSearch?.item?.packageName ?: return false
            return !patchBundleRepository.appMetadata.value[packageName]?.signatures.isNullOrEmpty()
        }

    /**
     * Build the request for an APK download helper, describing the original APK of the queued app
     * the download instructions are open for.
     */
    override fun createApkDownloadHelperIntent(component: ComponentName): Intent? {
        val search = apkSearch ?: return null
        val packageName = search.item.packageName
        val apkFileType = patchBundleRepository.appMetadata.value[packageName]?.apkFileType

        val requestedVersionCodes = search.compatible
            .filter { it.target.version == search.version }
            .flatMap { it.buildCodes.orEmpty() }
            .distinct()
            .map(Int::toLong)
            .toLongArray()

        return ApkDownloadHelperContract.createRequestIntent(
            component = component,
            callerPackage = app.packageName,
            packageName = packageName,
            appName = search.item.appName,
            versionName = search.version,
            versionCodes = requestedVersionCodes,
            compatibleVersionNames = search.compatible.mapNotNull { it.target.version }.distinct(),
            supportedAbis = Build.SUPPORTED_ABIS,
            fileType = apkFileType?.toHelperFileType(),
            // Mirrors the single-app request - only a required plain APK rules split archives out
            allowSplitArchive = !(apkFileType?.isApk == true && apkFileType.isRequired),
            stockInstallRequired = state.value?.useMount == true &&
                    search.item.source !is BatchApkSource.Installed,
            fallbackWebUrl = downloadUrlResolver.webSearchUrl(packageName, search.version)
        )
    }

    /**
     * Takes the archive a helper downloaded for the queued app, which then goes through the same
     * package, version and signature checks an attached file does.
     */
    override fun onHelperApkReceived(uri: Uri) {
        val item = apkSearch?.item ?: return
        apkSearch = null
        attachPrompt = null
        requestAttach(item.id)
        onApkPicked(uri)
    }

    override fun onHelperInstalledAppChosen(packageName: String) {
        val item = apkSearch?.item ?: return
        if (packageName != item.packageName) {
            // The helper answered about an app the queue never asked to patch
            app.toast(app.getString(R.string.home_apk_helper_wrong_package))
            return
        }

        apkSearch = null
        attachPrompt = null
        // Re-resolved rather than taken on the helper's word: an installed app is only accepted
        // while it still looks like the stock one, see BatchPlanResolver
        coordinator.useSource(item.id, preferInstalled = true)
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
                val seen = patchSelectionRepository.getSeenPatches(item.configurationKey, bundle.uid)
                bundle.uid to bundle.patches
                    .filter { seen != null && it.name !in seen }
                    .mapTo(mutableSetOf()) { it.name }
            }.filterValues { it.isNotEmpty() }

            edit = BatchPatchEdit(
                itemId = item.id,
                bundles = bundles,
                savedSelection = item.selection,
                newPatches = newPatches,
                initialOptions = item.options,
                // The queue resolved its plan against one install target, so the editor has to
                // lock patches by the same rules the run will be executed with
                installerType = installerTypeFor(state.value?.useMount == true),
                // Read from the same APK the plan was resolved against, so an edit answers the
                // architecture rules with what the run will hand the patcher
                apkArchitecture = source.apkArchitecture(),
                // Same rule the plan was resolved with, see BatchPlanResolver
                allowIncompatible = item.forceVersionMismatch || prefs.disablePatchVersionCompatCheck.get()
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
        coordinator.narrowToSource(item.id, bundleUid)
    }

    fun applyEdit() {
        val current = edit ?: return
        coordinator.updateSelection(current.itemId, current.selection, current.options)
        edit = null
    }

    /**
     * Copies the picked APK into the manager's private storage before handing it to the
     * resolver, because the content URI is not readable once the picker session ends.
     */
    fun onApkPicked(uri: Uri?) {
        val itemId = attachTarget
        attachTarget = null
        if (uri == null || itemId == null) return

        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) { copyToWorkspace(uri) }
            if (file == null) {
                app.toast(app.getString(R.string.home_invalid_apk_io_error))
                return@launch
            }
            coordinator.attachApk(itemId, file)
        }
    }

    fun toggleExcluded(itemId: String) = coordinator.toggleExcluded(itemId)

    /**
     * Accepts an unsupported version. Confirmed with a toast because the card only swaps a
     * badge, which does not say what was just taken on.
     */
    fun forceVersion(itemId: String) {
        coordinator.forceVersion(itemId)
        app.toast(app.getString(R.string.batch_patch_force_version_done))
    }

    /**
     * Accepts an APK signed by someone the sources do not vouch for. Confirmed with a toast for
     * the same reason as [forceVersion]: the card only swaps a badge.
     */
    fun acceptUnverifiedSignature(itemId: String) {
        coordinator.acceptUnverifiedSignature(itemId)
        app.toast(app.getString(R.string.batch_patch_accept_signature_done))
    }

    fun setPolicy(policy: BatchInstallPolicy) = coordinator.setPolicy(policy)

    fun markInstalled(itemId: String, installedPackageName: String) =
        coordinator.markInstallResult(
            itemId = itemId,
            outcome = BatchInstallOutcome.INSTALLED,
            installedPackageName = installedPackageName
        )

    fun markInstallFailed(itemId: String, message: String?) =
        coordinator.markInstallResult(itemId, BatchInstallOutcome.FAILED, message)

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
        val targets = current.items
            .filter { it.state == BatchItemState.FAILED || it.state == BatchItemState.CANCELLED }
            .map { it.target }
        if (targets.isEmpty()) return

        val useMount = current.useMount
        val policy = current.policy
        coordinator.clear()
        coordinator.plan(targets, useMount, policy)
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
            isClone = item.producesCloneAs(installedPackageName),
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
