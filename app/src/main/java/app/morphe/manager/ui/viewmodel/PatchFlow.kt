/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.morphe.manager.R
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.data.platform.NetworkInfo
import app.morphe.manager.domain.apk.*
import app.morphe.manager.domain.batch.BatchPatchCoordinator
import app.morphe.manager.domain.batch.mergeNewlyAdded
import app.morphe.manager.domain.bundles.*
import app.morphe.manager.domain.installer.InstallerManager
import app.morphe.manager.domain.installer.RootInstaller
import app.morphe.manager.domain.manager.*
import app.morphe.manager.domain.repository.*
import app.morphe.manager.patcher.patch.*
import app.morphe.manager.patcher.runtime.PatcherProcesses
import app.morphe.manager.patcher.patch.PatchBundleInfo.Extensions.contributingAt
import app.morphe.manager.patcher.patch.PatchBundleInfo.Extensions.toPatchSelection
import app.morphe.manager.patcher.split.SplitApkInspector
import app.morphe.manager.patcher.split.SplitApkPreparer
import app.morphe.manager.ui.model.*
import app.morphe.manager.ui.model.navigation.Patcher
import app.morphe.manager.ui.screen.shared.UserMessages
import app.morphe.manager.ui.screen.shared.readableLabel
import app.morphe.manager.util.*
import app.morphe.manager.util.PatchSelectionUtils.applyAvailability
import app.morphe.manager.util.PatchSelectionUtils.restrictTo
import app.morphe.manager.util.PatchSelectionUtils.sanitizeForPatcher
import app.morphe.manager.util.PatchSelectionUtils.validatePatchOptions
import app.morphe.manager.util.PatchSelectionUtils.validatePatchSelection
import app.morphe.patcher.patch.ApkArchitecture
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.InstallerType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import java.io.File

/** Free storage below which patching is warned against before it starts. */
const val LOW_DISK_SPACE_THRESHOLD_BYTES = 1_000_000_000L

/** * Dialog state for unsupported version warning. */
data class UnsupportedVersionDialogState(
    val packageName: String,
    val version: String,
    val versionCode: Long? = null,
    val recommendedVersion: AppTarget?,
    /** The versions offered instead, each under the source that names it. */
    val compatibleVersions: List<BundledAppTarget> = emptyList(),
    /** True if the selected version is marked as experimental in the patch bundle. */
    val isExperimental: Boolean = false
)

/** Dialog state for wrong package warning. */
data class WrongPackageDialogState(
    val expectedPackage: String,
    val actualPackage: String
)

/**
 * Dialog state for APK signature mismatch warning.
 * Shown when the selected APK's signing certificate does not match
 * the expected signatures declared in the patch bundle.
 */
data class InvalidSignatureDialogState(
    val packageName: String,
    val appName: String,
)

/** An installed app entry shown in the universal-patch app picker. */
data class InstalledAppPickerItem(
    val packageName: String,
    val label: String,
    val packageInfo: PackageInfo,
    val isSystemApp: Boolean,
    val info: InstalledApkInfo
)

/**
 * What the patch flow knows of the run it sets up, from the app it was started for to where an
 * APK of it can be had. Gone once the run starts or is given up, so nothing of one run carries
 * into the next.
 *
 * @param packageName The app being patched, null for one no source names.
 * @param repatchPackageName The tracked install the run rebuilds, or null when it produces a
 *   separate one. Patching an app that is already installed keeps that install, so a run has to
 *   say which of an app's installs it is aimed at before it can replace anything.
 * @param selectedVersion The version an APK is looked for at, the recommended one until another
 *   is picked. Sources share versions, so the pick names its source too, which answers for an
 *   APK of that version.
 * @param bundleUid The source picked in simple mode before the APK, which the run patches from.
 * @param installedVersion The unpatched version the device has right now: [installedApk] is
 *   dropped for a version the patches do not target, which is exactly when the version itself is
 *   worth showing.
 * @param stockAppInstalled Whether a mount install would have the app itself to overlay, which a
 *   patched build under the same package name is not. Null until it is looked up.
 * @param downloadUrl Where the APK is downloaded from, the unfollowed search link until the
 *   redirect lands.
 */
data class PatchFlowSession(
    val packageName: String?,
    val appName: String,
    val repatchPackageName: String? = null,
    val recommendedVersion: AppTarget? = null,
    val compatibleVersions: List<BundledAppTarget> = emptyList(),
    val selectedVersion: BundledAppTarget? = null,
    val bundleUid: Int? = null,
    val savedApk: SavedApkInfo? = null,
    val installedApk: InstalledApkInfo? = null,
    val installedVersion: String? = null,
    val stockAppInstalled: Boolean? = null,
    val downloadUrl: String? = null,
    /** Sources the app is kept from, which the APK dialog says how to bring back. */
    val hiddenSources: Int = 0
) {
    /** The version an APK is looked for at. */
    val requestedVersion: String? get() = (selectedVersion?.target ?: recommendedVersion)?.version
}

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

/**
 * Sets up a run of the patcher for one app: finds the APK through the dialogs in [dialogs], checks
 * it, settles the patches, and hands the run to [onStartQuickPatch]. Also opens the information on
 * an installed app, which a run can be started from.
 */
class PatchFlow(
    private val scope: CoroutineScope,
    private val app: Application,
    private val messages: UserMessages,
    private val patchBundleRepository: PatchBundleRepository,
    private val originalApkRepository: OriginalApkRepository,
    private val patchSelectionRepository: PatchSelectionRepository,
    private val sourceMuteRepository: SourceMuteRepository,
    private val optionsRepository: PatchOptionsRepository,
    private val networkInfo: NetworkInfo,
    private val prefs: PreferencesManager,
    private val pm: PM,
    private val rootInstaller: RootInstaller,
    private val installerManager: InstallerManager,
    private val filesystem: Filesystem,
    private val appDataResolver: AppDataResolver,
    private val batchPatchCoordinator: BatchPatchCoordinator,
    private val downloadUrlResolver: DownloadUrlResolver,
    private val localApkSources: LocalApkSources,
    private val compatibleVersions: StateFlow<Map<String, List<BundledAppTarget>>>,
    private val recommendedVersions: StateFlow<Map<String, AppTarget>>,
    expertModeCopy: CopySelectionController,
    private val patcherProcesses: PatcherProcesses
) : ApkDownloadHelperHost {
    /** The dialogs the flow has open, see [HomeDialog]. */
    val dialogs = HomeDialogStack()

    /** The run being set up, null while there is none. */
    var session by mutableStateOf<PatchFlowSession?>(null)
        private set

    /** The patches picked by hand for the run, while the expert dialog is open. */
    val expertMode = ExpertModeSelection(scope, expertModeCopy)

    /** Starts the patcher on a run once it is set up. */
    var onStartQuickPatch: ((Patcher.ViewModelParams) -> Unit)? = null

    /** True while a picked APK is loaded and checked in the background. */
    var processingApkSelection by mutableStateOf(false)
        private set

    var loadingInstalledApps by mutableStateOf(false)
        private set
    var installedAppsForPicker by mutableStateOf<List<InstalledAppPickerItem>>(emptyList())
        private set

    /** Patch mode of the run being set up, chosen before patching on a rooted device. */
    var usingMountInstall: Boolean = false
        private set

    // An APK waiting on a dialog to decide how its run goes on, which owns its file until then
    private var heldApp: SelectedApp? = null
    // Whether the run of the held APK reaches patches that declare other app versions
    private var heldAllowsIncompatible = false

    // The patching held back by a guard dialog until the user answers it
    private var pendingPatchAction: (suspend () -> Unit)? = null

    // The step the run is moving on with. The dialog that started it stays up until the next one
    // takes its place, so it is not answered twice in the meantime
    private var stepJob: Job? = null

    // The redirect being resolved, given up for the next one when the user comes back for another version
    private var downloadRedirectJob: Job? = null

    // The file picked last, with the icon and label read from it
    private var pickedApk by mutableStateOf<ApkLoadResult.Success?>(null)

    /** Icon read from the file picked for expert mode, for an app no source has one for yet. */
    val expertModeAppIcon: Drawable?
        get() = pickedApk
            ?.takeIf { it.app.file == (expertMode.app as? SelectedApp.Local)?.file }
            ?.icon

    // Counts the openings of the app information, so each one starts afresh
    private var appInfoOpenings = 0

    private val appMetadata get() = patchBundleRepository.appMetadata.value

    private suspend fun isExpertMode() = prefs.useExpertMode.get()

    fun openInstalledAppInfo(packageName: String) {
        dismissInstalledAppInfo()
        dialogs.open(HomeDialog.AppInfo(packageName, ++appInfoOpenings))
    }

    fun dismissInstalledAppInfo() {
        dialogs.closeAll { it is HomeDialog.AppInfo }
    }

    /**
     * Show patch dialog.
     *
     * Dialog logic:
     * - SHOW dialog when:
     *   1. New app (not installed yet) - shows download button, no saved APK button
     *   2. Expert mode - always show with all options
     *   3. Simple mode + no saved APK - shows download button, no saved APK button
     *   4. Simple mode + saved APK != recommended - shows all options
     *
     * - SKIP dialog and auto-use saved APK when:
     *   - Simple mode + saved APK == recommended version
     *
     * @param repatchedPackageName The tracked install to rebuild. Null starts an install of its
     *   own, which is how an app that is already patched gets a second, cloned copy.
     */
    fun showPatchDialog(packageName: String, repatchedPackageName: String? = null) {
        session = sessionFor(packageName, repatchedPackageName)
        // Started while the user picks the APK and patches, so patching does not wait for it
        patcherProcesses.startAhead()

        // Guard: if there is a pending bundle update on metered data, show the outdated-patches
        // dialog before proceeding with the actual APK selection flow.
        guardPatching { showPatchDialogInternal(packageName) }
    }

    /**
     * What the run calls [packageName]: the name the sources give it, see
     * [PatchBundleRepository.appDisplayNames], or else the label of the file picked for it, which
     * is at hand before the sources are and names an app they do not.
     *
     * @param names The names the sources give, passed in by a caller that observes them.
     */
    fun appNameFor(
        packageName: String,
        names: Map<String, String> = patchBundleRepository.appDisplayNames.value
    ): String = names[packageName]
        ?: pickedApk?.takeIf { it.app.packageName == packageName }?.label
        ?: KnownApps.getAppName(packageName)

    /** A fresh run for [packageName], seeded from the current bundle data. */
    private fun sessionFor(packageName: String, repatchedPackageName: String? = null): PatchFlowSession {
        val versions = compatibleVersions.value[packageName].orEmpty()
        return PatchFlowSession(
            packageName = packageName,
            appName = appNameFor(packageName),
            repatchPackageName = repatchedPackageName,
            recommendedVersion = recommendedVersions.value[packageName],
            compatibleVersions = versions,
            // The dialog shows it picked, so it answers for the APK as a pick by hand would
            selectedVersion = versions.recommendedEntry()
        )
    }

    /**
     * The source answering for [app]: the one picked in simple mode, or in expert mode the one the
     * version was picked under, once the APK turns out to be that version. Null leaves it to every
     * source.
     */
    private fun chosenBundleUidFor(app: SelectedApp): Int? {
        val current = session ?: return null
        current.bundleUid?.let { return it }
        // Simple mode offers no versions to pick from, so its recommended one stands for no choice
        if (!prefs.useExpertMode.getBlocking()) return null
        return current.selectedVersion?.takeIf { it.target.version == app.version }?.bundleUid
    }

    private suspend fun showPatchDialogInternal(packageName: String) {
        val savedInfo = withContext(Dispatchers.IO) {
            localApkSources.saved(packageName)
        }
        session = session?.copy(savedApk = savedInfo)

        // Check if every declared version is incompatible with the current device SDK
        val versions = compatibleVersions.value[packageName].orEmpty().offered()
        val allIncompatible = versions.isNotEmpty() && versions.none { it.installableOnDevice() }
        if (allIncompatible) {
            dialogs.advanceTo(HomeDialog.NoCompatibleVersions(packageName))
            return
        }

        // In simple mode: if multiple bundles cover this package, ask the user to pick one
        // before showing the APK selection dialog so the correct recommended version is used
        if (!isExpertMode() && session?.bundleUid == null) {
            val candidates = withContext(Dispatchers.IO) {
                patchBundleRepository.simpleSourceChoices(packageName)
            }
            if (candidates.size > 1) {
                dialogs.advanceTo(HomeDialog.SimpleBundleSelect(packageName, candidates))
                return
            }
        }

        continueApkSelectionFlow(packageName)
    }

    /**
     * Called when the user picks a source in the simple mode bundle selection. Before the APK is
     * known, the source settles the version to look for and the APK selection goes on with it;
     * after, the held APK is patched from it.
     */
    fun proceedWithSelectedBundle(dialog: HomeDialog.SimpleBundleSelect, bundleUid: Int, rememberChoice: Boolean = false) = moveOn {
        val packageName = dialog.packageName

        // Settles the question for good rather than for this run: the sources turned down here
        // stop being offered for this app, so the dialog has nothing left to ask next time.
        // Written before moving on, so the APK dialog already counts them as hidden
        if (rememberChoice) {
            val offeredUids = dialog.candidates.mapTo(mutableSetOf()) { (bundle, _) -> bundle.uid }
            withContext(Dispatchers.IO) {
                sourceMuteRepository.keepOnly(packageName, bundleUid, offeredUids)
            }
        }

        val app = heldApp
        if (app != null) {
            heldApp = null
            startPatchingWithApp(app, heldAllowsIncompatible, bundleUid)
            return@moveOn
        }

        // Update recommended version to the one the chosen bundle will be used at
        val bundleRecommended = compatibleVersions.value[packageName]
            .orEmpty()
            .filter { it.bundleUid == bundleUid }
            .recommendedEntry()
        session = session?.let { current ->
            if (bundleRecommended == null) current.copy(bundleUid = bundleUid)
            else current.copy(
                bundleUid = bundleUid,
                recommendedVersion = bundleRecommended.target,
                selectedVersion = bundleRecommended
            )
        }

        continueApkSelectionFlow(packageName)
    }

    /**
     * Second half of the patch-dialog flow: runs after bundle selection (or immediately when
     * no bundle disambiguation is needed). Decides whether to auto-use the saved APK or to
     * open the APK availability dialog.
     */
    private suspend fun continueApkSelectionFlow(packageName: String) {
        // Load saved APK (Room + AppDataResolver) and installed app (PackageManager) in
        // parallel. The device is asked about in both modes, because the version it carries is
        // worth showing wherever the installed APK itself is not offered as a source
        val expertMode = isExpertMode()
        coroutineScope {
            val savedJob = if (session?.savedApk == null) {
                async(Dispatchers.IO) { localApkSources.saved(packageName) }
            } else null
            val installedJob = async(Dispatchers.IO) { localApkSources.installed(packageName) }
            val hiddenJob = async(Dispatchers.IO) { patchBundleRepository.hiddenSourceCount(packageName) }
            savedJob?.await()?.let { saved -> session = session?.copy(savedApk = saved) }
            applyInstalledAppSource(installedJob.await(), offersApk = expertMode)
            session = session?.copy(hiddenSources = hiddenJob.await())
        }

        val current = session ?: return
        val shouldAutoUseSaved = !expertMode &&
                current.savedApk != null &&
                current.recommendedVersion != null &&
                current.savedApk.version == current.recommendedVersion.version

        if (shouldAutoUseSaved) {
            // Skip dialog and use saved APK directly
            handleSavedApkSelection()
        } else {
            // Show dialog
            dialogs.advanceTo(HomeDialog.ApkAvailability)
        }
    }

    /**
     * Stores the installed-app lookup, keeping the APK only when its version can be patched
     * so every caller offers the installed source under the same condition. [offersApk] is what
     * the flow itself allows: simple mode hides the button rather than the app behind it.
     */
    private fun applyInstalledAppSource(source: InstalledAppSource, offersApk: Boolean = true) {
        session = session?.let { current ->
            current.copy(
                stockAppInstalled = source.hasStockInstall,
                installedVersion = source.version,
                installedApk = source.apk
                    .takeIf { offersApk }
                    .patchableBy(current.compatibleVersions)
            )
        }
    }

    /** Picks [version] to look an APK up at. */
    fun selectVersion(version: BundledAppTarget) {
        session = session?.copy(selectedVersion = version)
    }

    /**
     * Handle selection of the currently installed APK from the APK availability dialog.
     * For single APKs: copies to a temp file. For split APKs: packs into a temp .apks archive.
     * The source is NOT saved to the original-APK repository.
     */
    fun handleInstalledApkSelection() {
        val current = session
        val installedInfo = current?.installedApk
        val packageName = current?.packageName

        if (installedInfo == null || packageName == null) {
            endRun()
            return
        }

        scope.launch {
            processingApkSelection = true
            try {
                val selectedApp = withContext(Dispatchers.IO) {
                    try {
                        copyInstalledApk(packageName, installedInfo)
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to prepare installed APK", e)
                        null
                    }
                }

                if (selectedApp != null) {
                    // Skip signature check: the installed APK may already be patched with our key
                    processSelectedAppIgnoringSignature(selectedApp)
                } else {
                    messages.show(R.string.home_invalid_apk_io_error)
                    endRun()
                }
            } finally {
                processingApkSelection = false
            }
        }
    }

    /**
     * The APK [info] describes of the installed [packageName], copied into a file of the run's
     * own. Throws when it has gone from the device.
     */
    private fun copyInstalledApk(packageName: String, info: InstalledApkInfo) = SelectedApp.Local(
        packageName = packageName,
        version = info.version,
        versionCode = info.versionCode,
        file = SplitApkPreparer.copyInstalledApp(packageName, info.apkPath, info.splitPaths, filesystem.uiTempDir),
        temporary = true,
        fromInstalledDevice = true
    )

    /**
     * Handles a helper result where the user installed the target app outside Morphe.
     *
     * Reached in simple mode too, where the installed source is otherwise hidden: the helper is
     * opt-in and the hand-off was confirmed, so the app store is the whole point of the request.
     */
    private fun handleHelperInstalledAppSelection(packageName: String) {
        scope.launch {
            val pending = session?.packageName
            if (pending != null && pending != packageName) {
                // The helper answered about an app the user never asked to patch
                stopHelperHandoff(pending, R.string.home_apk_helper_wrong_package)
                return@launch
            }

            // A hand-off through an app store can outlive Morphe, so the flow is rebuilt from the
            // bundle data. Unknown packages are refused because without their compatible versions
            // there is nothing left to check the installed build against
            if (pending == null) {
                if (packageName !in compatibleVersions.value) {
                    messages.show(R.string.home_apk_helper_installed_unavailable)
                    endRun()
                    return@launch
                }
                session = sessionFor(packageName)
            }

            processingApkSelection = true
            val source = withContext(Dispatchers.IO) {
                localApkSources.installed(packageName)
            }
            applyInstalledAppSource(source)

            val installedInfo = session?.installedApk
            if (installedInfo == null) {
                // An app store hands out the newest build, which is regularly one no bundle covers
                stopHelperHandoff(
                    packageName,
                    if (source.apk == null) {
                        R.string.home_apk_helper_installed_unavailable
                    } else {
                        R.string.home_apk_helper_installed_incompatible
                    }
                )
                return@launch
            }

            // Continuing here would swallow the warning the availability dialog carries when the
            // certificate could not be read, so that case goes back to the user
            if (installedInfo.patchStateUnknown) {
                stopHelperHandoff(packageName)
                return@launch
            }

            handleInstalledApkSelection()
        }
    }

    /**
     * Ends a helper hand-off at the APK availability dialog, so the remaining sources stay one tap
     * away instead of the user landing back on the home screen with nothing to act on.
     */
    private suspend fun stopHelperHandoff(packageName: String, @StringRes message: Int? = null) {
        processingApkSelection = false
        message?.let { messages.show(it) }

        if (session?.savedApk == null) {
            val saved = withContext(Dispatchers.IO) { localApkSources.saved(packageName) }
            session = session?.copy(savedApk = saved)
        }
        dialogs.advanceTo(HomeDialog.ApkAvailability)
    }

    /**
     * Loads all user-installed apps and opens the picker dialog.
     * Called from the "Other apps" file-picker prompt when the user taps "Use installed app".
     */
    fun loadInstalledAppsForPicker() {
        if (loadingInstalledApps) return
        scope.launch {
            installedAppsForPicker = emptyList()
            loadingInstalledApps = true
            dialogs.open(HomeDialog.InstalledAppPicker)
            try {
                val items = withContext(Dispatchers.IO) {
                    try {
                        val candidates = pm.getInstalledPackages()
                            .mapNotNull { pkgInfo ->
                                if (pkgInfo.packageName == app.packageName) return@mapNotNull null
                                val appInfo = pkgInfo.applicationInfo ?: return@mapNotNull null
                                val sourceDir = appInfo.sourceDir ?: return@mapNotNull null
                                if (!File(sourceDir).exists()) return@mapNotNull null
                                val version = pkgInfo.versionName?.takeUnless { it.isBlank() }
                                    ?: return@mapNotNull null
                                val label = with(pm) { pkgInfo.label() }
                                val splitPaths = appInfo.splitSourceDirs
                                    ?.filter { File(it).exists() }
                                    .orEmpty()
                                InstalledAppPickerItem(
                                    packageName = pkgInfo.packageName,
                                    label = label,
                                    packageInfo = pkgInfo,
                                    isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                                    info = InstalledApkInfo(
                                        version = version,
                                        versionCode = pm.getVersionCode(pkgInfo),
                                        apkPath = sourceDir,
                                        splitPaths = splitPaths
                                    )
                                )
                            }
                        // A patched build is no app to patch, it is the app's own install
                        val patched = localApkSources.patchedInstalls(candidates.map { it.packageInfo })
                        candidates
                            .filterNot { it.packageName in patched }
                            .sortedBy { it.label.lowercase() }
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to load installed apps for picker", e)
                        emptyList()
                    }
                }
                installedAppsForPicker = items
            } finally {
                loadingInstalledApps = false
            }
        }
    }

    /**
     * Handles selection of an app from the universal-patch installed-app picker.
     * Extracts the APK (or packs splits into an archive) and sends it through the patch flow.
     */
    fun handleInstalledAppPickerSelection(item: InstalledAppPickerItem) {
        session = session?.copy(appName = item.label)
        scope.launch {
            processingApkSelection = true
            try {
                val selectedApp = withContext(Dispatchers.IO) {
                    try {
                        copyInstalledApk(item.packageName, item.info)
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to prepare APK from picker", e)
                        null
                    }
                }
                if (selectedApp != null) {
                    // The picker only offers unpatched installs - skip split and signature checks.
                    // Version/versionCode check still runs via processSelectedApp.
                    processSelectedApp(selectedApp, skipSplitCheck = true)
                } else {
                    messages.show(R.string.home_invalid_apk_io_error)
                    endRun()
                }
            } finally {
                processingApkSelection = false
            }
        }
    }

    /**
     * Handle APK file selection.
     */
    fun handleApkSelection(uri: Uri?) {
        // Nothing picked leaves the run at the dialog the picker was opened from, if any is left
        if (uri == null) {
            if (dialogs.entries.all { it is HomeDialog.AppInfo }) endRun()
            return
        }
        scope.launch {
            processingApkSelection = true
            try {
                val result = withContext(Dispatchers.IO) {
                    loadLocalApk(uri)
                }

                when (result) {
                    is ApkLoadResult.Success -> {
                        pickedApk = result
                        // Picked in place of the one held for a warning, which it takes the run over from
                        heldApp?.discard()
                        heldApp = null
                        processSelectedApp(result.app)
                    }
                    is ApkLoadResult.Unreadable -> messages.show(R.string.home_invalid_apk_unreadable)
                    is ApkLoadResult.NotAnApk -> messages.show(R.string.home_invalid_apk_not_an_apk)
                    is ApkLoadResult.AlreadyPatched -> messages.show(R.string.home_invalid_apk_already_patched)
                    is ApkLoadResult.IoError -> messages.show(R.string.home_invalid_apk_io_error)
                }
            } finally {
                processingApkSelection = false
            }
        }
    }

    /**
     * Handle selection of saved APK from APK availability dialog.
     */
    fun handleSavedApkSelection() {
        val savedInfo = session?.savedApk
        val packageName = session?.packageName

        if (savedInfo == null || packageName == null) {
            messages.show(R.string.home_app_info_repatch_no_original_apk)
            endRun()
            return
        }

        scope.launch {
            processingApkSelection = true
            try {
                // Create SelectedApp from saved APK file
                val selectedApp = withContext(Dispatchers.IO) {
                    try {
                        val file = File(savedInfo.filePath)
                        if (!file.exists()) {
                            messages.show(R.string.home_app_info_repatch_no_original_apk)
                            return@withContext null
                        }

                        // Mark as used
                        originalApkRepository.markUsed(packageName)

                        SelectedApp.Local(
                            packageName = packageName,
                            version = savedInfo.version,
                            versionCode = savedInfo.versionCode,
                            file = file,
                            temporary = false // Don't delete saved APK files
                        )
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to load saved APK", e)
                        null
                    }
                }

                if (selectedApp != null) {
                    // Saved file may be signed with our keystore - skip signature check.
                    // Version/versionCode check still runs via processSelectedApp.
                    processSelectedApp(selectedApp, skipSplitCheck = true)
                } else {
                    endRun()
                }
            } finally {
                processingApkSelection = false
            }
        }
    }

    /**
     * Process selected APK file.
     *
     * This function only answers: "do any patches EXIST for this APK?"
     * The include/selection logic is handled in [startPatchingWithApp].
     */
    private suspend fun processSelectedApp(
        selectedApp: SelectedApp,
        skipSplitCheck: Boolean = false
    ) {
        // Validate package name if expected (known-app flow sets the session's package)
        val expectedPackage = session?.packageName
        if (expectedPackage != null && selectedApp.packageName != expectedPackage) {
            selectedApp.discard()
            endRun()
            dialogs.open(
                HomeDialog.WrongPackage(
                    WrongPackageDialogState(
                        expectedPackage = expectedPackage,
                        actualPackage = selectedApp.packageName
                    )
                )
            )
            return
        }

        // Warn when the selected file is a split APK while the bundle requires a full APK.
        // This must happen BEFORE signature verification - split archives (.apkm/.apks/.xapk)
        // are not valid APKs so PackageManager cannot read their signature, which would cause
        // a false "invalid signature" dialog instead of the correct "split APK" warning.
        if (selectedApp is SelectedApp.Local && !skipSplitCheck) {
            val requiredApkFileType = appMetadata[selectedApp.packageName]?.apkFileType

            val isSplitFile = SplitApkPreparer.isSplitArchive(selectedApp.file)

            if (isSplitFile && requiredApkFileType?.isApk == true && requiredApkFileType.isRequired) {
                holdFor(HomeDialog.SplitApkWarning(selectedApp.packageName), selectedApp)
                return
            }

            // Verify APK signature against the expected signatures declared in the patch bundle.
            // GET_SIGNING_CERTIFICATES (API 28+) is required for reliable archive signature reads.
            // On Android 8–10 the legacy GET_SIGNATURES path cannot read signatures from
            // archive files correctly, so we skip verification there to avoid false-blocking users.
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                val expectedSignatures = appMetadata[selectedApp.packageName]?.signatures
                if (!expectedSignatures.isNullOrEmpty()) {
                    val signatureMatch = withContext(Dispatchers.IO) {
                        SplitApkInspector.withRepresentativeApk(
                            source = selectedApp.file,
                            workspace = filesystem.uiTempDir
                        ) { apk ->
                            pm.getApkFileSignatureHashes(apk).any { it in expectedSignatures }
                        }
                    }
                    if (!signatureMatch) {
                        holdFor(
                            HomeDialog.InvalidSignature(
                                InvalidSignatureDialogState(
                                    packageName = selectedApp.packageName,
                                    appName = session?.appName ?: appNameFor(selectedApp.packageName)
                                )
                            ),
                            selectedApp
                        )
                        return
                    }
                }
            }
        }

        val allowIncompatible = prefs.disablePatchVersionCompatCheck.getBlocking()

        // Get scoped bundles for this APK (package + version).
        // Scoped.patches contains every patch that is compatible with this packageName
        // (including universal patches where compatiblePackages == null).
        // Scoped.compatible = version matches, Scoped.incompatible = package matches but wrong version,
        // Scoped.universal = compatiblePackages == null (applies to any package/version).
        val bundles = withContext(Dispatchers.IO) {
            patchBundleRepository.resolvingBundleInfo(
                selectedApp.packageName, selectedApp.version, selectedApp.versionCode, chosenBundleUidFor(selectedApp)
            )
        }
        val support = VersionSupport(bundles)

        if (!support.hasPatches) {
            // Truly no patches exist for this package in any enabled bundle
            messages.show(R.string.home_no_patches_available)
            selectedApp.discard()
            endRun()
            return
        }

        // Show the unsupported-version warning when:
        //   - version-specific patches exist for this package (incompatible list is non-empty)
        //   - BUT none of them match this APK version (compatible list is empty)
        //   - AND the user has NOT disabled the version compat check
        // Universal patches do not suppress this warning - the user should still be informed
        // that the APK version is not officially supported.
        // Note: experimental versions are compatible (they pass the version check) but show an
        // additional "Experimental" badge in the warning dialog.
        if (support.isMismatch && !allowIncompatible) {
            holdFor(HomeDialog.UnsupportedVersion(unsupportedVersionState(selectedApp, support.isExperimental)), selectedApp)
            return
        }

        // Experimental check is independent - a version can be experimental AND compatible.
        // If it is, show the appropriate warning for the sources marking it so:
        // - Experimental mode ON → ExperimentalVersionWarningDialog
        // - Experimental mode OFF → UnsupportedVersionWarningDialog
        if (support.isExperimental && !allowIncompatible) {
            val experimentalEnabledUids = prefs.bundleExperimentalVersionsEnabled.getBlocking()
            val isExperimentalModeEnabled = support.experimentalUids.any { uid ->
                uid.toString() in experimentalEnabledUids
            }
            val state = unsupportedVersionState(selectedApp, isExperimental = true)
            holdFor(
                if (isExperimentalModeEnabled) {
                    HomeDialog.ExperimentalVersion(state)
                } else {
                    HomeDialog.UnsupportedVersion(state)
                },
                selectedApp
            )
            return
        }

        // Patches exist and are applicable → proceed.
        // For root-capable devices, we must know the patch mode BEFORE patching
        // because it is the install target patches declare their availability against.
        // Show the pre-patching mode dialog so the user can choose.
        // For non-root devices, just proceed - installer selection happens after patching.
        processSelectedAppIgnoringSignature(selectedApp)
    }

    /**
     * The way out of a version the sources do not stand behind: what the user picked, and the
     * versions they can go for instead. Shared by both warnings, which differ only in tone.
     */
    private fun unsupportedVersionState(
        selectedApp: SelectedApp,
        isExperimental: Boolean
    ): UnsupportedVersionDialogState {
        // A chosen source answers for the APK alone
        val chosenBundleUid = chosenBundleUidFor(selectedApp)
        val offered = compatibleVersions.value[selectedApp.packageName].orEmpty().offered()
            .let { all -> all.filter { it.bundleUid == chosenBundleUid }.ifEmpty { all } }

        return UnsupportedVersionDialogState(
            packageName = selectedApp.packageName,
            version = selectedApp.version ?: "unknown",
            versionCode = selectedApp.versionCode,
            recommendedVersion = offered.recommended(),
            compatibleVersions = offered,
            isExperimental = isExperimental
        )
    }

    /**
     * Puts [app] on hold for [dialog], which decides how its run goes on. The steps of getting
     * the APK are done with by then, while the run keeps what it knows for the dialog to show.
     */
    private fun holdFor(dialog: HomeDialog, app: SelectedApp, allowIncompatible: Boolean = false) {
        heldApp = app
        heldAllowsIncompatible = allowIncompatible
        dialogs.advanceTo(dialog)
    }

    /** Goes on with the run of the held APK as the dialog on screen decided. */
    private fun resumeHeld(next: suspend (app: SelectedApp, allowIncompatible: Boolean) -> Unit) = moveOn {
        val app = heldApp ?: return@moveOn
        heldApp = null
        next(app, heldAllowsIncompatible)
    }

    /**
     * Moves the run on with [step] while the dialog that asked for it stays up, for the next one
     * to take its place or the run to end. Further answers to it are ignored until then.
     */
    private fun moveOn(step: suspend () -> Unit) {
        if (stepJob?.isActive == true) return
        stepJob = scope.launch { step() }
    }

    /** User chose to proceed with patching despite the split APK warning. */
    fun proceedWithSplitApk() = resumeHeld { app, _ ->
        processSelectedApp(app, skipSplitCheck = true)
    }

    /**
     * User chose to proceed past a version warning. An unsupported version patches with the
     * patches that declare other versions, while an experimental one is supported and only
     * flagged as such in the patch bundle.
     */
    fun proceedDespiteVersion(dialog: HomeDialog) = resumeHeld { app, _ ->
        startPatchingInPatchMode(app, allowIncompatible = dialog is HomeDialog.UnsupportedVersion)
    }

    /** User chose to proceed patching despite the signature mismatch warning. */
    fun proceedIgnoringSignature() = resumeHeld { app, _ ->
        processSelectedAppIgnoringSignature(app)
    }

    /**
     * Called when the user selects a patch mode from the pre-patching dialog.
     * Sets [usingMountInstall] and starts patching with the correct patch configuration.
     */
    fun resolvePrePatchInstallerChoice(useMount: Boolean) = resumeHeld { app, allowIncompatible ->
        usingMountInstall = useMount
        startPatchingWithApp(app, allowIncompatible)
    }

    /**
     * Gives up the run the dialog on screen was asked for, the APK held for it included. Every
     * dialog of the flow that is turned down ends the run this way.
     */
    fun dismiss() {
        pendingPatchAction = null
        endRun()
    }

    /**
     * Skips all preliminary checks (signature, version, bundle) and routes directly to patching.
     * Used when the user confirms proceeding despite a signature mismatch, or when patching
     * from the installed app where checks are not applicable.
     */
    private suspend fun processSelectedAppIgnoringSignature(selectedApp: SelectedApp) {
        val allowIncompatible = prefs.disablePatchVersionCompatCheck.getBlocking()
        startPatchingInPatchMode(selectedApp, allowIncompatible)
    }

    /**
     * Starts patching in the mode the run is set up for. A rooted device asks for it first while
     * the installer prompt is on, and otherwise mounts whenever root mount is the default
     * installer. Anywhere else the build is a standard one, its installer asked for after patching.
     */
    private suspend fun startPatchingInPatchMode(selectedApp: SelectedApp, allowIncompatible: Boolean) {
        val rooted = rootInstaller.isDeviceRooted()
        if (rooted && prefs.promptInstallerOnInstall.get()) {
            holdFor(HomeDialog.PrePatchInstaller, selectedApp, allowIncompatible)
            return
        }
        usingMountInstall = rooted && installerManager.parseToken(prefs.installerPrimary.get()).isRootMount
        startPatchingWithApp(selectedApp, allowIncompatible)
    }

    /**
     * Start patching flow.
     *
     * @param bundleUid The source chosen for the APK, see [chosenBundleUidFor]. Simple mode
     *   patches from it alone, and expert mode opens on it.
     */
    private suspend fun startPatchingWithApp(
        selectedApp: SelectedApp,
        allowIncompatible: Boolean,
        bundleUid: Int? = chosenBundleUidFor(selectedApp)
    ) {
        // Read before the first selection is resolved, so every rule below and every later edit
        // in the expert dialog is answered for the APK this run actually starts from. The rules
        // are only ignored where the patches are shown, so nothing they would have kept out is
        // built in unseen
        val target = PatchTarget(
            installerType = installerTypeFor(usingMountInstall),
            apkArchitecture = ApkArchitectureResolver.resolve(selectedApp, pm),
            ignoresRules = isExpertMode() &&
                    rootInstaller.isDeviceRooted() &&
                    prefs.ignoreInstallTargetRules.get()
        )

        // Everything this app has patches in, the sources it is kept from included: the expert
        // dialog offers those back, so it has to be told what it is hiding
        val availableBundles = patchBundleRepository
            .scopedBundleInfoFlow(selectedApp.packageName, selectedApp.version, selectedApp.versionCode)
            .first()

        if (availableBundles.isEmpty()) {
            messages.show(R.string.home_no_patches_available)
            selectedApp.discard()
            endRun()
            return
        }

        val mutedSources = withContext(Dispatchers.IO) {
            sourceMuteRepository.getMutedFor(selectedApp.packageName)
        }
        // Every rule below reads this rather than the full list, so a source the app is kept from
        // contributes no defaults, no new-patch badges and no tab
        val allBundles = availableBundles.withoutMutedSources(mutedSources) { it.uid }

        // Spans every source, hidden ones included: what the user saved for a hidden one has to
        // survive validation. Offering the source back would otherwise hand the run an empty
        // selection, and then write that emptiness over what was saved
        val bundlesMap = availableBundles.associate { it.uid to it.patches.associateBy { patch -> patch.name } }
        val currentBundleUids = availableBundles.mapTo(mutableSetOf()) { it.uid }

        val configurationKey = configurationKeyFor(selectedApp.packageName)

        // What was saved for the app from the sources it still has patches in, read only where a
        // selection is opened by hand
        suspend fun savedSelections() = withContext(Dispatchers.IO) {
            patchSelectionRepository.getAllSelectionsForPackage(configurationKey)
                .filterKeys { it in currentBundleUids }
        }
        suspend fun savedOptions() = withContext(Dispatchers.IO) {
            optionsRepository.getAllOptionsForPackage(configurationKey, bundlesMap)
                .filterKeys { it in currentBundleUids }
        }

        // A source not standing behind this version, one marking it experimental with those
        // switched off, contributes nothing unless the user adds it back by hand
        val contributingBundles = allBundles.contributingAt(
            prefs.bundleExperimentalVersionsEnabled.get(),
            chosenUid = bundleUid
        )

        // Opens the selection by hand, which the patches' own availability for the install
        // target has the final say on unless the run is set to ignore it
        fun openExpertMode(
            patches: PatchSelection,
            options: Options,
            newPatches: Map<Int, Set<String>> = emptyMap()
        ) {
            expertMode.open(
                app = selectedApp,
                allBundles = availableBundles,
                mutedSources = mutedSources,
                // The dialog opens on the source answering for the APK, or on the first one
                // contributing when the others were left out
                leadingBundleUid = bundleUid ?: contributingBundles
                    .takeIf { it.size < allBundles.size }
                    ?.firstOrNull()?.uid,
                patches = target.applyTo(patches, allBundles),
                options = options,
                newPatches = newPatches,
                allowIncompatible = allowIncompatible,
                target = target
            )
            dialogs.advanceTo(HomeDialog.ExpertMode)
        }

        if (isExpertMode()) {
            val contributingUids = contributingBundles.mapTo(mutableSetOf()) { it.uid }
            val savedSelections = savedSelections().filterKeys { it in contributingUids }
            val savedOptions = savedOptions()

            // The seen-patch snapshot is what tells a genuinely new patch from one the user
            // deselected. Not read without a saved selection, so a first run flags nothing as new
            val seenByBundle = if (savedSelections.isEmpty()) emptyMap() else withContext(Dispatchers.IO) {
                contributingBundles.associate {
                    it.uid to patchSelectionRepository.getSeenPatches(configurationKey, it.uid)
                }
            }
            val newPatchesMap = contributingBundles.mapNotNull { bundle ->
                val seen = seenByBundle[bundle.uid] ?: return@mapNotNull null
                val new = bundle.patches.mapTo(mutableSetOf()) { it.name } - seen
                new.takeIf { it.isNotEmpty() }?.let { bundle.uid to it }
            }.toMap()

            // Use saved selections or create new ones
            val patches = if (savedSelections.isNotEmpty()) {
                // Count patches before validation
                val patchesBeforeValidation = savedSelections.values.sumOf { it.size }

                // Validate saved selections against available patches
                val validatedPatches = validatePatchSelection(savedSelections, bundlesMap)

                // Count patches after validation
                val patchesAfterValidation = validatedPatches.values.sumOf { it.size }

                // Tell the user if patches were removed
                val removedCount = patchesBeforeValidation - patchesAfterValidation
                if (removedCount > 0) {
                    messages.show(app.resources.getQuantityString(
                        R.plurals.home_app_info_repatch_cleaned_invalid_data,
                        removedCount,
                        removedCount.toString()
                    ))
                }

                // Runs after validation, so patches the sources dropped never sneak back in. The
                // saved selection stands in for a snapshot that does not exist yet
                mergeNewlyAdded(
                    bundles = contributingBundles,
                    validated = validatedPatches,
                    known = { uid -> seenByBundle[uid] ?: savedSelections[uid] },
                    installerType = target.installerType,
                    apkArchitecture = target.apkArchitecture
                )
            } else {
                // No saved selections - use default for the contributing bundles
                contributingBundles.toPatchSelection(allowIncompatible) { _, patch -> target.defaultSelected(patch) }
            }

            // Validate options
            val validatedOptions = validatePatchOptions(savedOptions, bundlesMap)

            // Save validated options if anything changed
            if (validatedOptions != savedOptions) {
                withContext(Dispatchers.IO) {
                    optionsRepository.saveOptions(configurationKey, validatedOptions)
                }
            }

            openExpertMode(patches, validatedOptions, newPatchesMap)
            return
        }

        // Simple Mode: collect patches from all enabled bundles, then either use the sole result directly
        // or ask the user to pick one if multiple bundles have applicable patches.
        // A patch is applicable if:
        //   - compatiblePackages == null (universal), OR
        //   - compatiblePackages contains this packageName
        val bundleWithPatches = contributingBundles
            .filter { it.enabled }
            .map { bundle ->
                val patchNames = bundle.patchSequence(allowIncompatible)
                    .filter(target::defaultSelected)
                    .mapTo(mutableSetOf()) { it.name }
                bundle to patchNames
            }
            .filter { (_, patches) -> patches.isNotEmpty() }

        if (bundleWithPatches.isEmpty()) {
            // No patches have include=true (use=true in the bundle JSON).
            // This is the case for third-party bundles where all universal patches
            // ship with use=false and require explicit user configuration.
            // Fall through to expert mode so the user can select and configure patches.
            // Hidden sources are read here too, for the same reason the expert branch reads
            // them: the dialog can offer one back, and it has to have something to offer
            openExpertMode(savedSelections(), savedOptions())
            return
        }

        // If the user pre-selected a bundle via SimpleBundleSelectDialog, use it directly.
        // Use allowIncompatible=true unconditionally: the user explicitly picked this bundle
        // AND explicitly picked the APK version, so we must respect both choices regardless
        // of whether the version is in the bundle's supported list
        val bundle = bundleUid?.let { uid -> allBundles.find { it.uid == uid } }
        if (bundle != null) {
            val patchNames = bundle.patchSequence(allowIncompatible = true)
                .filter(target::defaultSelected)
                .mapTo(mutableSetOf()) { it.name }
            if (patchNames.isNotEmpty()) {
                val patches = target.applyTo(mapOf(bundle.uid to patchNames), allBundles)
                proceedWithPatching(selectedApp, patches, emptyMap(), allowIncompatible = true)
                return
            }
            // Pre-selected bundle has no patches at all - fall through
        }

        // Simple mode: if more than one bundle has applicable patches, ask the user which single bundle to use
        if (bundleWithPatches.size > 1) {
            holdFor(HomeDialog.SimpleBundleSelect(selectedApp.packageName, bundleWithPatches), selectedApp, allowIncompatible)
            return
        }

        // Only one bundle has patches - use it directly (no prompt needed)
        val patches = target.applyTo(
            bundleWithPatches.associate { (bundle, patches) -> bundle.uid to patches },
            allBundles
        )

        proceedWithPatching(selectedApp, patches, emptyMap(), allowIncompatible)
    }

    /**
     * The package the run reads its saved patches and options from.
     *
     * Patches belong to the install they were applied to, so rebuilding one reads what that
     * install was built with. Anything else reads the app itself: a run producing a separate
     * install starts from what the app was last patched with, and so does an install that
     * predates configurations of their own.
     */
    private suspend fun configurationKeyFor(originalPackageName: String): String =
        patchSelectionRepository.configurationKeyFor(originalPackageName, session?.repatchPackageName)

    /** Hands the run to the patcher, which takes it from here. */
    private fun proceedWithPatching(
        selectedApp: SelectedApp,
        patches: PatchSelection,
        options: Options,
        allowIncompatible: Boolean
    ) {
        onStartQuickPatch?.invoke(
            Patcher.ViewModelParams(
                selectedApp = selectedApp,
                selectedPatches = patches,
                options = options,
                // Handed over before the run is let go of below, since the patcher has no other
                // way to learn which install it was started for
                targetPackageName = session?.repatchPackageName,
                allowIncompatible = allowIncompatible,
                useMount = usingMountInstall
            )
        )

        // Closed as the patcher opens over them, the app information the flow may have started from
        // included, so they leave along with the home screen
        dialogs.closeAll()
        endRun()
    }

    /**
     * Reopens the selection a failed [run] was started from, so the patch that failed it can be
     * dropped without picking the APK again. The selection itself was saved before the run.
     */
    fun reopenPatchSelection(run: Patcher.ViewModelParams) {
        // A failed run leaves its input in place, but storage cleanup may have taken it since
        if ((run.selectedApp as? SelectedApp.Local)?.file?.exists() == false) {
            messages.show(R.string.home_invalid_apk_io_error)
            return
        }
        session = sessionFor(run.selectedApp.packageName, run.targetPackageName)
        usingMountInstall = run.useMount
        scope.launch {
            startPatchingWithApp(run.selectedApp, run.allowIncompatible)
        }
    }

    /**
     * Called when the user confirms the ExpertModeDialog. Persists the final selection
     * and options, then navigates to the patcher.
     */
    fun proceedExpertMode() = moveOn {
        val selectedApp = expertMode.app ?: return@moveOn
        val bundles = expertMode.bundles
        val finalPatches = expertMode.runSelection
        val finalOptions = expertMode.options
        val allowIncompatible = expertMode.allowIncompatible
        // Strip UI-only empty strings (fields cleared via ✕) so the patcher engine
        // receives null / no key for those options and falls back to its own default,
        // rather than receiving a literal empty string.
        val patcherOptions = finalOptions.sanitizeForPatcher()

        // A path that leads nowhere fails the run, and by then this dialog is gone and the
        // option it belongs to is out of reach. So it is asked about while the selection is
        // still open and the value can be fixed or dropped on the spot
        val failures = withContext(Dispatchers.IO) {
            validateOptionPaths(patcherOptions.restrictTo(finalPatches))
        }

        if (failures.isNotEmpty()) {
            expertMode.reportUnreadablePaths(failures)
            return@moveOn
        }

        withContext(Dispatchers.IO) {
                // Saved before patching runs so a long selection survives a failed run. It lands
                // on the install being rebuilt, and follows it from there if patching renames it.
                // Only the bundles offered by the dialog are covered by this selection, so bundles
                // that were disabled at patch time keep whatever the user saved for them earlier
                val configurationKey = configurationKeyFor(selectedApp.packageName)
                patchSelectionRepository.updateSelection(
                    packageName = configurationKey,
                    selection = finalPatches,
                    scope = bundles.mapTo(mutableSetOf()) { it.uid }
                )
                optionsRepository.saveOptions(configurationKey, finalOptions)
                // Snapshot all bundle patch names so next open can detect genuinely new patches
                bundles.forEach { bundle ->
                    patchSelectionRepository.saveSeenPatches(
                        packageName = configurationKey,
                        bundleUid = bundle.uid,
                        patchNames = bundle.patches.mapTo(mutableSetOf()) { it.name }
                    )
                }
            }

        proceedWithPatching(selectedApp, finalPatches, patcherOptions, allowIncompatible)
        expertMode.clear()
    }

    /**
     * Drops the options behind the unreadable paths and goes on with the run, which then uses
     * the defaults the patches declare.
     */
    fun proceedExpertModeWithoutUnreadablePaths() {
        expertMode.dropUnreadablePaths()
        proceedExpertMode()
    }

    /** Closes the expert dialog, giving up the run it was setting up. */
    fun dismissExpertMode() {
        expertMode.app?.discard()
        expertMode.clear()
        dismiss()
    }

    /**
     * Guard entry-point for all patching flows.
     * Shows MeteredPatchingDialog when on metered network with updates disabled,
     * so the user can choose to update patches first or patch anyway.
     * Otherwise, launches [action] immediately.
     */
    private fun guardPatching(action: suspend () -> Unit) {
        // A batch run owns the patcher worker for its whole duration, so starting a single
        // patch would replace the app the queue is currently working on
        if (batchPatchCoordinator.isRunning) {
            messages.show(R.string.batch_patch_in_progress)
            return
        }

        // Check available storage first - low disk space is the most common cause of
        // cryptic "file not found" errors and corrupt output APKs during patching.
        val freeBytes = StatFs(app.filesDir.absolutePath).availableBytes
        if (freeBytes < LOW_DISK_SPACE_THRESHOLD_BYTES) {
            pendingPatchAction = action
            dialogs.advanceTo(HomeDialog.LowDiskSpace(freeBytes))
            return
        }
        guardMetered(action)
    }

    // The metered check of guardPatching, which the low disk space warning resumes into
    private fun guardMetered(action: suspend () -> Unit) {
        if (isOnMeteredWithUpdatesDisabled()) {
            pendingPatchAction = action
            dialogs.advanceTo(HomeDialog.MeteredPatching)
        } else {
            moveOn(action)
        }
    }

    /**
     * Returns `true` when the user has disabled metered updates AND is currently on
     * a metered (mobile data) connection - meaning patches may not be up to date.
     */
    private fun isOnMeteredWithUpdatesDisabled(): Boolean =
        !prefs.allowMeteredUpdates.getBlocking() && networkInfo.isMetered()

    // Takes the patching a guard dialog held back, which the dialog stays up for until it moves on
    private fun takePendingPatchAction(): (suspend () -> Unit)? =
        pendingPatchAction.also { pendingPatchAction = null }

    /**
     * User chose to update patches first, then automatically continue patching. The update can
     * take a while with nothing to show for it, so the dialog does not wait on it.
     */
    fun refreshBundlesAndContinuePatching() {
        val action = takePendingPatchAction() ?: return
        dialogs.close(HomeDialog.MeteredPatching)
        moveOn {
            // User explicitly requested update - bypass metered check and wait for completion
            patchBundleRepository.updateCheckAndAwait(allowUnsafeNetwork = true)
            action()
        }
    }

    /**
     * User chose to patch with the currently cached patches despite being on metered network.
     */
    fun dismissMeteredPatchingDialogAndProceed() {
        val action = takePendingPatchAction() ?: return
        moveOn(action)
    }

    /**
     * User chose to proceed with patching despite low disk space.
     * Continues to the metered network check if applicable, then launches the action.
     */
    fun dismissLowDiskSpaceDialogAndProceed() {
        val action = takePendingPatchAction() ?: return
        guardMetered(action)
    }

    /**
     * Resolve download redirect.
     */
    private fun resolveDownloadRedirect() {
        val current = session ?: return
        val packageName = current.packageName ?: return
        val version = current.requestedVersion

        // Marks the destination as unknown, which is what the dialog waits on before it can
        // say anything about the download or let the user leave for it
        session = current.copy(downloadUrl = downloadUrlResolver.apiSearchUrl(packageName, version))

        downloadRedirectJob?.cancel()
        downloadRedirectJob = scope.launch {
            val resolved = withContext(Dispatchers.IO) {
                downloadUrlResolver.resolve(packageName, version)
            }
            session = session?.copy(downloadUrl = resolved)
        }
    }

    override val helperSignatureCheckAvailable: Boolean
        get() = session?.packageName?.let { helperSignatureCheckAvailable(appMetadata[it]) } == true

    /**
     * Build the request for an APK download helper, describing the original APK of the pending app.
     */
    override fun createApkDownloadHelperIntent(component: ComponentName): Intent? {
        val current = session ?: return null
        val packageName = current.packageName ?: return null
        val requestedVersion = current.requestedVersion

        return createApkDownloadHelperRequest(
            component = component,
            callerPackage = app.packageName,
            packageName = packageName,
            appName = current.appName,
            versionName = requestedVersion,
            compatible = current.compatibleVersions,
            metadata = appMetadata[packageName],
            stockInstallRequired = usingMountInstall && current.stockAppInstalled != true,
            fallbackWebUrl = downloadUrlResolver.webSearchUrl(packageName, requestedVersion)
        )
    }

    override fun onHelperApkReceived(uri: Uri) {
        handleApkSelection(uri)
    }

    override fun onHelperInstalledAppChosen(packageName: String) {
        handleHelperInstalledAppSelection(packageName)
    }

    /** Opens the download instructions over the APK availability, which going back returns to. */
    fun showDownloadInstructions() {
        dialogs.open(HomeDialog.DownloadInstructions)
        resolveDownloadRedirect()
    }

    /**
     * Handle download instructions continue.
     */
    fun handleDownloadInstructionsContinue(handOff: (String) -> Boolean) {
        val urlToOpen = session?.downloadUrl ?: return

        if (handOff(urlToOpen)) {
            dialogs.open(HomeDialog.FilePickerPrompt)
        } else {
            Log.w(tag, "Failed to open URL")
            messages.show(R.string.sources_management_failed_to_open_url)
            endRun()
        }
    }

    /** Starts the flow for [appName], an app no source names, at picking its APK. */
    fun startOtherAppsFlow(appName: String) {
        endRun()
        session = PatchFlowSession(packageName = null, appName = appName)
        patcherProcesses.startAhead()
        dialogs.open(HomeDialog.FilePickerPrompt)
    }

    /** Goes back a step of getting the APK, which from the first one leaves the flow. */
    fun backFromApkSource() {
        (dialogs.top as? HomeDialog.ApkSource)?.let(dialogs::close)
        if (dialogs.entries.none { it is HomeDialog.ApkSource }) endRun()
    }

    /** Gives up the run being set up, the APK held for it and the dialogs it went through included. */
    private fun endRun() {
        downloadRedirectJob?.cancel()
        heldApp?.discard()
        heldApp = null
        session = null
        dialogs.closeAll { it !is HomeDialog.AppInfo }
    }

    /** Closes every dialog of the flow and gives up the run, for the user to start afresh. */
    fun cancel() {
        stepJob?.cancel()
        expertMode.app?.discard()
        expertMode.clear()
        pendingPatchAction = null
        dialogs.closeAll()
        endRun()
    }

    /** Deletes the APK held for a run, as nothing is left to patch it. */
    fun close() {
        heldApp?.discard()
        expertMode.app?.discard()
    }

    /**
     * Load local APK and extract package info.
     * Supports both single APK and split APK archives (`apkm`, `apks`, `xapk`).
     *
     * The file is stored in [Filesystem.uiTempDir] (app_ui_ephemeral).
     * CacheDir can be cleared by Android at any time - even while the app is running and
     * patching is in progress - which would cause a FileNotFoundException mid-patch.
     * uiTempDir uses getDir() which is part of the app's private files and is never
     * cleared by the system automatically.
     */
    private suspend fun loadLocalApk(uri: Uri): ApkLoadResult = withContext(Dispatchers.IO) {
        try {
            val tempFile = uri.copyApkInto(filesystem.uiTempDir, "temp_apk", app.contentResolver)
                ?: return@withContext ApkLoadResult.Unreadable

            // A split archive is read through its base module, deleted after the call, so the icon
            // and the label are read in it
            val result = SplitApkInspector.withRepresentativeApk(
                source = tempFile,
                workspace = filesystem.uiTempDir
            ) { apk ->
                val packageInfo = pm.getPackageInfo(apk)
                when {
                    packageInfo == null -> ApkLoadResult.NotAnApk
                    localApkSources.isPatchedApk(apk, packageInfo.packageName) -> ApkLoadResult.AlreadyPatched
                    else -> ApkLoadResult.Success(
                        SelectedApp.Local(
                            packageName = packageInfo.packageName,
                            version = packageInfo.versionName ?: "unknown",
                            versionCode = pm.getVersionCode(packageInfo),
                            file = tempFile,
                            temporary = true
                        ),
                        icon = appDataResolver.detachedArchiveIcon(packageInfo),
                        label = packageInfo.readableLabel(app.packageManager)
                    )
                }
            }

            if (result == ApkLoadResult.NotAnApk) {
                Log.w(tag, "Picked file ${tempFile.name} could not be parsed as an APK")
            }
            if (result !is ApkLoadResult.Success) tempFile.delete()
            result
        } catch (e: Exception) {
            Log.e(tag, "Failed to load APK", e)
            ApkLoadResult.IoError
        }
    }
}

/** Deletes the file of a picked APK that was copied for the run alone. */
private fun SelectedApp.discard() {
    if (this is SelectedApp.Local && temporary) file.delete()
}

/** Result of attempting to load a local APK file. */
private sealed interface ApkLoadResult {
    /** File was read and parsed successfully, with the app's icon and label where it carries them. */
    data class Success(val app: SelectedApp.Local, val icon: Drawable?, val label: String?) : ApkLoadResult
    /** File could not be read - provider returned null stream or zero bytes. */
    data object Unreadable : ApkLoadResult
    /** File was read but is not a valid APK/split archive. */
    data object NotAnApk : ApkLoadResult
    /** File is a patched build, which is never patched again. */
    data object AlreadyPatched : ApkLoadResult
    /** An unexpected IO or system exception occurred while copying or parsing. */
    data object IoError : ApkLoadResult
}
