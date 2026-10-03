/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.graphics.drawable.Drawable
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.R
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.data.platform.NetworkInfo
import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.domain.apk.*
import app.morphe.manager.domain.batch.BatchPatchCoordinator
import app.morphe.manager.domain.batch.BatchRunState
import app.morphe.manager.domain.batch.mergeNewlyAdded
import app.morphe.manager.domain.bundles.*
import app.morphe.manager.domain.installer.InstallerManager
import app.morphe.manager.domain.installer.RootInstaller
import app.morphe.manager.domain.installer.UninstallCancelledException
import app.morphe.manager.domain.manager.*
import app.morphe.manager.domain.repository.*
import app.morphe.manager.domain.repository.PatchBundleRepository.LocalFileCheck
import app.morphe.manager.patcher.patch.*
import app.morphe.manager.patcher.patch.PatchBundleInfo.Extensions.toPatchSelection
import app.morphe.manager.patcher.split.SplitApkInspector
import app.morphe.manager.patcher.split.SplitApkPreparer
import app.morphe.manager.ui.model.*
import app.morphe.manager.ui.screen.shared.CopySelectionCandidate
import app.morphe.manager.util.*
import app.morphe.manager.util.PatchSelectionUtils.applyAvailability
import app.morphe.manager.util.PatchSelectionUtils.bulkEnableHoldsUniversal
import app.morphe.manager.util.PatchSelectionUtils.bulkEnablePatches
import app.morphe.manager.util.PatchSelectionUtils.mergeBundleOptions
import app.morphe.manager.util.PatchSelectionUtils.resetOptionsForPatch
import app.morphe.manager.util.PatchSelectionUtils.restrictTo
import app.morphe.manager.util.PatchSelectionUtils.sanitizeForPatcher
import app.morphe.manager.util.PatchSelectionUtils.spansMultipleBundles
import app.morphe.manager.util.PatchSelectionUtils.togglePatch
import app.morphe.manager.util.PatchSelectionUtils.updateOption
import app.morphe.manager.util.PatchSelectionUtils.withBundle
import app.morphe.manager.util.PatchSelectionUtils.validatePatchOptions
import app.morphe.manager.util.PatchSelectionUtils.validatePatchSelection
import app.morphe.patcher.patch.ApkArchitecture
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.InstallerType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** How long an added source is waited on before its app list is no longer opened for it. */
private val NEW_SOURCE_LOAD_TIMEOUT = 10.minutes

/** Bundle update status for snackbar display. */
enum class BundleUpdateStatus {
    Updating, // Update in progress
    Success,  // Update completed successfully
    Warning,  // Patches may be outdated (on metered network, updates disabled)
    Error     // Error occurred (including no internet)
}

/** * Dialog state for unsupported version warning. */
data class UnsupportedVersionDialogState(
    val packageName: String,
    val version: String,
    val versionCode: Long? = null,
    val recommendedVersion: AppTarget?,
    val compatibleVersionNames: List<String> = emptyList(),
    val compatibleVersionDescriptions: Map<String, String> = emptyMap(),
    val compatibleVersionCodes: Map<String, Set<Int>> = emptyMap(),
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

/**
 * Quick patch parameters.
 *
 * @param targetPackageName The install being rebuilt when that is a clone rather than the app's
 *   own, so the run can tell the name it was aimed at from the one its patches produce.
 */
data class QuickPatchParams(
    val selectedApp: SelectedApp,
    val patches: PatchSelection,
    val options: Options,
    val targetPackageName: String? = null
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
 * Manages all dialogs, user interactions, APK processing, and bundle management.
 */
class HomeViewModel(
    private val app: Application,
    val patchBundleRepository: PatchBundleRepository,
    private val installedAppRepository: InstalledAppRepository,
    private val originalApkRepository: OriginalApkRepository,
    private val patchSelectionRepository: PatchSelectionRepository,
    private val sourceMuteRepository: SourceMuteRepository,
    private val optionsRepository: PatchOptionsRepository,
    private val managerUpdateRepository: ManagerUpdateRepository,
    private val networkInfo: NetworkInfo,
    val prefs: PreferencesManager,
    private val pm: PM,
    val rootInstaller: RootInstaller,
    private val installerManager: InstallerManager,
    private val filesystem: Filesystem,
    private val homeAppButtonPrefs: HomeAppButtonPreferences,
    private val appDataResolver: AppDataResolver,
    private val batchPatchCoordinator: BatchPatchCoordinator,
    private val downloadUrlResolver: DownloadUrlResolver,
    versionCatalog: AppVersionCatalog,
    private val localApkSources: LocalApkSources
) : ViewModel(), ApkDownloadHelperHost {
    /** The app list on the home screen. */
    val apps = HomeApps(
        scope = viewModelScope,
        app = app,
        patchBundleRepository = patchBundleRepository,
        installedAppRepository = installedAppRepository,
        originalApkRepository = originalApkRepository,
        sourceMuteRepository = sourceMuteRepository,
        prefs = prefs,
        pm = pm,
        filesystem = filesystem,
        homeAppButtonPrefs = homeAppButtonPrefs,
        appDataResolver = appDataResolver,
        versionCatalog = versionCatalog,
        localApkSources = localApkSources
    )

    val availablePatches = patchBundleRepository.bundleInfoFlow.map { it.values.sumOf { bundle -> bundle.patches.size } }
    val bundleUpdateProgress = patchBundleRepository.bundleUpdateProgress
    private val contentResolver: ContentResolver = app.contentResolver

    /** Becomes true once the bundle repository has finished its initial DB load. */
    /** Android 11 kills the app process after granting the "install apps" permission. */
    val android11BugActive get() = Build.VERSION.SDK_INT == Build.VERSION_CODES.R && !pm.canInstallPackages()

    var updatedManagerVersion: String? by mutableStateOf(null)
        private set

    // Dialog visibility states
    var showAndroid11Dialog by mutableStateOf(false)
    var showBundleManagementSheet by mutableStateOf(false)
    var showAddSourceDialog by mutableStateOf(false)
    var bundleToRename by mutableStateOf<PatchBundleSource?>(null)
    var showRenameBundleDialog by mutableStateOf(false)

    /** Sources added with their apps to be chosen, whose app lists open one after another. */
    private var sourceAppsQueue by mutableStateOf<List<Int>>(emptyList())

    /** Source whose app list is open, the first of [sourceAppsQueue]. */
    val sourceAppsDialogUid: Int? get() = sourceAppsQueue.firstOrNull()

    /** Closes the open app list, which brings up the next queued one. */
    fun dismissSourceApps() {
        sourceAppsQueue = sourceAppsQueue.drop(1)
    }

    // Installed App Info dialog state
    var showInstalledAppInfoDialog: String? by mutableStateOf(null)
        private set
    var installedAppDialogToken by mutableIntStateOf(0)
        private set

    fun openInstalledAppInfo(packageName: String) {
        showInstalledAppInfoDialog = packageName
        installedAppDialogToken++
    }

    fun dismissInstalledAppInfo() {
        showInstalledAppInfoDialog = null
    }

    // Deep link: pending bundle to add via confirmation dialog
    var deepLinkPendingBundle by mutableStateOf<DeepLinkBundle?>(null)
        private set

    data class DeepLinkBundle(val url: String, val name: String?)

    // .mpp file opened from file manager: pending confirmation dialog
    var pendingMppUri by mutableStateOf<Uri?>(null)
    var pendingMppFileName by mutableStateOf<String?>(null)
    var pendingMppManifest by mutableStateOf<MppManifest?>(null)

    fun setPendingMpp(uri: Uri) {
        dismissOpenDialogs()
        pendingMppUri = uri
        pendingMppFileName = uri.displayName(contentResolver)
        pendingMppManifest = null
        viewModelScope.launch(Dispatchers.IO) {
            pendingMppManifest = uri.readMppManifest(contentResolver)
        }
    }

    fun confirmMppImport(chooseApps: Boolean) {
        val uri = pendingMppUri ?: return
        pendingMppUri = null
        pendingMppFileName = null
        pendingMppManifest = null
        importLocalSources(listOf(uri to null), chooseApps)
    }

    fun dismissMppImport() {
        pendingMppUri = null
        pendingMppFileName = null
        pendingMppManifest = null
    }

    // Expert mode state
    var showExpertModeDialog by mutableStateOf(false)
    var expertModeSelectedApp by mutableStateOf<SelectedApp?>(null)

    private var pickedApkIcon by mutableStateOf<Pair<File, Drawable>?>(null)

    /** Icon read from the file picked for expert mode, for an app no source has one for yet. */
    val expertModeAppIcon: Drawable?
        get() = pickedApkIcon
            ?.takeIf { (file, _) -> (expertModeSelectedApp as? SelectedApp.Local)?.file == file }
            ?.second
    var expertModeBundles by mutableStateOf<List<PatchBundleInfo.Scoped>>(emptyList())
    // Everything the app has patches in, so a source it is kept from can be offered back without
    // reopening the dialog. Only ever a superset of expertModeBundles
    private var expertModeAllBundles by mutableStateOf<List<PatchBundleInfo.Scoped>>(emptyList())
    // What the dialog is holding back. Starts as what is stored for the app and is emptied by a
    // reveal, which the store never hears about: the sources come back for this run only
    private var expertModeMutedSources by mutableStateOf<Set<Int>>(emptySet())
    /**
     * How many sources the dialog is holding back, which is what the notice offering them counts.
     * Read off the two lists rather than off the stored set, because muting every source of an app
     * hides none of them.
     */
    val expertModeHiddenSources get() = expertModeAllBundles.size - expertModeBundles.size
    var expertModePatches by mutableStateOf<PatchSelection>(emptyMap())
    /** Snapshot of the selection at the moment the ExpertMode dialog was opened. Used by "Restore saved". */
    var expertModeInitialPatches by mutableStateOf<PatchSelection>(emptyMap())
        private set
    var expertModeOptions by mutableStateOf<Options>(emptyMap())
    // Patches that are new in the current bundle version relative to the last saved selection
    var expertModeNewPatches by mutableStateOf<Map<Int, Set<String>>>(emptyMap())
    // Bundle and selection left behind by the last "Enable all". Universal patches are applied
    // only while this still matches the live selection, so any other edit disarms them again
    private var expertModeUniversalArmedFor by mutableStateOf<Pair<Int, Set<String>>?>(null)
    // Whether the run this dialog configures reaches patches that declare other app versions,
    // so bulk actions offer the same set the selection was built from
    private var expertModeAllowIncompatible = false

    /** Picker behind the copy-from-another-bundle action of the expert-mode dialog. */
    val expertModeCopy = CopySelectionController()

    /**
     * A file picked in the add source dialog, with the bundle name its manifest declares and what
     * importing it would do, both null while they are read.
     */
    data class PickedBundle(val uri: Uri, val name: String, val bundleName: String?, val check: LocalFileCheck?)

    /** Files picked in the add source dialog, kept here so they outlive the picker's round trip. */
    private var pickedBundles by mutableStateOf<List<PickedBundle>>(emptyList())

    /**
     * [pickedBundles] as they would import together: a file landing on the same source as an
     * earlier one adds nothing of its own. Two versions of one new bundle would each add a source,
     * so they are told apart by the bundle name, and the first picked is the one kept.
     */
    val pickedBundleImports: List<PickedBundle>
        get() {
            val targets = mutableSetOf<String>()
            return pickedBundles.map { picked ->
                val target = when (val check = picked.check) {
                    is LocalFileCheck.New -> picked.bundleName?.lowercase(Locale.US) ?: "uid:${check.uid}"
                    is LocalFileCheck.Update -> "uid:${check.uid}"
                    else -> null
                }
                if (target != null && !targets.add(target)) picked.copy(check = LocalFileCheck.Duplicate) else picked
            }
        }

    /** Adds files to the add source dialog and reads what importing each would do. */
    fun pickBundles(uris: List<Uri>) {
        val fresh = uris.distinct().filter { uri -> pickedBundles.none { it.uri == uri } }.map { uri ->
            val name = uri.displayName(contentResolver) ?: uri.lastPathSegment ?: uri.toString()
            val isBundle = name.endsWith(".mpp", ignoreCase = true)
            PickedBundle(uri, name, bundleName = null, check = if (isBundle) null else LocalFileCheck.NotBundle)
        }
        pickedBundles = pickedBundles + fresh
        fresh.filter { it.check == null }.forEach { picked ->
            viewModelScope.launch {
                val bundleName = withContext(Dispatchers.IO) { picked.uri.readMppManifest(contentResolver)?.name }
                val check = runCatching {
                    patchBundleRepository.checkLocal(bundleName) {
                        // Closed by checkLocal, which reads it through use
                        @SuppressLint("Recycle")
                        val stream = contentResolver.openInputStream(picked.uri)
                        stream ?: throw FileNotFoundException("Unable to open ${picked.uri}")
                    }
                }.getOrElse { LocalFileCheck.NotBundle }
                pickedBundles = pickedBundles.map {
                    if (it.uri == picked.uri) it.copy(bundleName = bundleName, check = check) else it
                }
            }
        }
    }

    fun unpickBundle(uri: Uri) {
        pickedBundles = pickedBundles.filterNot { it.uri == uri }
    }

    fun clearPickedBundles() {
        pickedBundles = emptyList()
    }

    /**
     * Imports the picked files: a new bundle as a source of its own, and another version of a local
     * source into it, as its update action would. Files already added are left out.
     */
    fun importPickedBundles(chooseApps: Boolean) {
        val imports = pickedBundleImports.mapNotNull { picked ->
            when (val check = picked.check) {
                is LocalFileCheck.New -> picked.uri to null
                is LocalFileCheck.Update -> picked.uri to check.uid
                else -> null
            }
        }
        pickedBundles = emptyList()
        importLocalSources(imports, chooseApps)
    }

    /** Local source waiting for a replacement file, so the picker result knows what it updates. */
    var localBundleUpdateUid by mutableStateOf<Int?>(null)

    // APK selection flow dialogs
    var showApkAvailabilityDialog by mutableStateOf(false)
    var showDownloadInstructionsDialog by mutableStateOf(false)
    var showFilePickerPromptDialog by mutableStateOf(false)
    var showInstalledAppPickerDialog by mutableStateOf(false)
    var loadingInstalledApps by mutableStateOf(false)
    var installedAppsForPicker by mutableStateOf<List<InstalledAppPickerItem>>(emptyList())
    // True while APK loading/processing runs in the background
    var processingApkSelection by mutableStateOf(false)

    // Error/warning dialogs
    var showUnsupportedVersionDialog by mutableStateOf<UnsupportedVersionDialogState?>(null)
    var showExperimentalVersionDialog by mutableStateOf<UnsupportedVersionDialogState?>(null)
    var showWrongPackageDialog by mutableStateOf<WrongPackageDialogState?>(null)
    var showSplitApkWarningDialog by mutableStateOf(false)
    var showInvalidSignatureDialog by mutableStateOf<InvalidSignatureDialogState?>(null)
    var showNoCompatibleVersionsDialog by mutableStateOf<String?>(null) // packageName

    // Pending data during APK selection
    var pendingPackageName by mutableStateOf<String?>(null)
    /**
     * The tracked install the pending flow rebuilds, or null when it produces a separate one.
     * Patching an app that is already installed keeps that install, so a run has to say which
     * of an app's installs it is aimed at before it can replace anything.
     */
    var pendingRepatchPackageName by mutableStateOf<String?>(null)
        private set
    var pendingAppName by mutableStateOf<String?>(null)
    var pendingRecommendedVersion by mutableStateOf<AppTarget?>(null)
    var pendingCompatibleVersions by mutableStateOf<List<BundledAppTarget>>(emptyList())
    // Version selected by the user in Dialog 1 for the APK search query. Defaults to pendingRecommendedVersion
    var pendingSelectedDownloadVersion by mutableStateOf<AppTarget?>(null)
    var pendingSelectedApp by mutableStateOf<SelectedApp?>(null)
    var resolvedDownloadUrl by mutableStateOf<String?>(null)
    var pendingSavedApkInfo by mutableStateOf<SavedApkInfo?>(null)
    var pendingInstalledApkInfo by mutableStateOf<InstalledApkInfo?>(null)
    // The unpatched version the device has right now: [pendingInstalledApkInfo] is dropped for a
    // version the patches do not target, which is exactly when the version itself is worth showing
    var pendingInstalledAppVersion by mutableStateOf<String?>(null)
    // Whether a mount install would have the app itself to overlay, which a patched build under
    // the same package name is not. null = not yet loaded, true/false = loaded result
    var pendingStockAppInstalled by mutableStateOf<Boolean?>(null)

    // Bundle update snackbar state
    var showBundleUpdateSnackbar by mutableStateOf(false)
    var snackbarStatus by mutableStateOf(BundleUpdateStatus.Updating)

    // Latches when an update cycle was skipped due to metered network; cleared on the next
    // successful/no-change update. Independent of the transient BundleUpdateSnackbar so the
    // user still sees a persistent alert after the transient snackbar fades
    var updatesSkippedDueToMetered by mutableStateOf(false)

    // Simple mode bundle selection dialog: shown when 2+ bundles have patches for the same app
    var showSimpleBundleSelectDialog by mutableStateOf(false)
    var simpleBundleSelectApp by mutableStateOf<SelectedApp?>(null)
    var simpleBundleSelectCandidates by mutableStateOf<List<Pair<PatchBundleInfo.Scoped, Set<String>>>>(emptyList())
    // Bundle pre-selected by the user before the APK selection flow (simple mode)
    var pendingSelectedBundleUid by mutableStateOf<Int?>(null)
        private set

    fun dismissSimpleBundleSelectDialog() {
        showSimpleBundleSelectDialog = false
        simpleBundleSelectApp = null
        simpleBundleSelectCandidates = emptyList()
        cleanupPendingData()
    }

    /**
     * Called when the user picks a bundle in [app.morphe.manager.ui.screen.home.SimpleBundleSelectDialog].
     * Instead of patching immediately, stores the chosen bundle uid and continues
     * to the APK selection flow so the correct recommended version is shown.
     */
    fun proceedWithSelectedBundle(bundleUid: Int, rememberChoice: Boolean = false) {
        val packageName = pendingPackageName ?: return
        val offeredUids = simpleBundleSelectCandidates.mapTo(mutableSetOf()) { (bundle, _) -> bundle.uid }
        showSimpleBundleSelectDialog = false
        simpleBundleSelectApp = null
        simpleBundleSelectCandidates = emptyList()

        pendingSelectedBundleUid = bundleUid

        // Settles the question for good rather than for this run: the sources turned down here
        // stop being offered for this app, so the dialog has nothing left to ask next time
        if (rememberChoice) {
            viewModelScope.launch(Dispatchers.IO) {
                sourceMuteRepository.keepOnly(packageName, bundleUid, offeredUids)
            }
        }

        // Update recommended version to the one the chosen bundle will be used at
        val bundleRecommended = compatibleVersions[packageName]
            .orEmpty()
            .filter { it.bundleUid == bundleUid }
            .recommended()
        if (bundleRecommended != null) {
            pendingRecommendedVersion = bundleRecommended
            pendingSelectedDownloadVersion = bundleRecommended
        }

        viewModelScope.launch {
            continueApkSelectionFlow(packageName)
        }
    }

    // Metered network dialog: shown when user tries to patch on mobile data with updates disabled
    var showMeteredPatchingDialog by mutableStateOf(false)
        private set

    // Low disk space warning dialog: shown when free storage is below the threshold before patching starts
    val lowDiskSpaceThresholdBytes = 1_000_000_000L // Minimum free storage required before patching
    var showLowDiskSpaceDialog by mutableStateOf(false)
        private set
    var lowDiskSpaceFreeBytes by mutableLongStateOf(0L)
        private set

    // Pending patching action captured when the guard dialog is shown
    private var pendingPatchAction: (suspend () -> Unit)? = null

    // Loading state for installed apps
    var installedAppsLoading by mutableStateOf(true)

    // Bundle data - reactive StateFlows derived directly from bundleInfoFlow
    val compatibleVersionsFlow: StateFlow<Map<String, List<BundledAppTarget>>> =
        versionCatalog.compatibleVersions
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val recommendedVersionsFlow: StateFlow<Map<String, AppTarget>> =
        versionCatalog.recommendedVersions
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // Convenience accessors - read current value synchronously for non-reactive call sites
    val recommendedVersions: Map<String, AppTarget> get() = recommendedVersionsFlow.value
    val compatibleVersions: Map<String, List<BundledAppTarget>> get() = compatibleVersionsFlow.value

    /** Convenience accessor - reads expert mode preference without blocking. */
    private suspend fun isExpertMode() = prefs.useExpertMode.get()

    // Using mount install (set externally)
    var usingMountInstall: Boolean = false

    // Install target passed to patch availability resolvers
    val currentInstallerType: InstallerType
        get() = installerTypeFor(usingMountInstall)

    // Architecture of the APK the run starts from, read from it before patches are selected
    var currentApkArchitecture: ApkArchitecture = ApkArchitecture.UNIVERSAL
        private set

    // Controls the pre-patching mode selection dialog for root-capable devices.
    var showPrePatchInstallerDialog by mutableStateOf(false)

    // Stores the pending arguments while the pre-patching mode dialog is visible.
    private var pendingPatchApp: SelectedApp? = null
    private var pendingPatchAllowIncompatible: Boolean = false

    /**
     * Called when a root-capable device triggers patching. Instead of starting immediately,
     * opens the pre-patching mode dialog so the user can choose Root Mount vs Standard.
     */
    fun requestPrePatchInstallerSelection(
        selectedApp: SelectedApp,
        allowIncompatible: Boolean
    ) {
        pendingPatchApp = selectedApp
        pendingPatchAllowIncompatible = allowIncompatible
        showPrePatchInstallerDialog = true
    }

    /**
     * Called when the user selects a patch mode from the pre-patching dialog.
     * Sets [usingMountInstall] and starts patching with the correct patch configuration.
     */
    fun resolvePrePatchInstallerChoice(useMount: Boolean) {
        showPrePatchInstallerDialog = false
        usingMountInstall = useMount

        val selectedApp = pendingPatchApp ?: return
        val allowIncompatible = pendingPatchAllowIncompatible
        pendingPatchApp = null

        viewModelScope.launch {
            startPatchingWithApp(selectedApp, allowIncompatible)
        }
    }

    /**
     * Dismisses the pre-patching mode dialog without starting patching.
     */
    fun dismissPrePatchInstallerDialog() {
        showPrePatchInstallerDialog = false
        pendingPatchApp = null
    }

    /**
     * User chose to proceed with patching despite the split APK warning.
     * Resumes [processSelectedApp] with the split check skipped.
     */
    fun proceedWithSplitApk() {
        val app = pendingSelectedApp ?: return
        showSplitApkWarningDialog = false
        pendingSelectedApp = null
        viewModelScope.launch {
            processSelectedApp(app, skipSplitCheck = true)
        }
    }

    private fun clearPendingApp() {
        val app = pendingSelectedApp
        pendingSelectedApp = null
        if (app is SelectedApp.Local && app.temporary) {
            app.file.delete()
        }
    }

    /**
     * User dismissed the split APK warning without proceeding.
     * Cleans up the temporary file if needed.
     */
    fun dismissSplitApkWarning() {
        showSplitApkWarningDialog = false
        clearPendingApp()
    }

    /**
     * User dismissed the unsupported version dialog.
     * Discards the pending selection and cleans up the temporary file if needed.
     */
    fun dismissUnsupportedVersionDialog() {
        showUnsupportedVersionDialog = null
        clearPendingApp()
    }

    private fun proceedWithPendingApp(allowIncompatible: Boolean) {
        val app = pendingSelectedApp ?: return
        pendingSelectedApp = null
        viewModelScope.launch {
            if (rootInstaller.isDeviceRooted()) {
                requestPrePatchInstallerSelection(app, allowIncompatible = allowIncompatible)
            } else {
                usingMountInstall = false
                startPatchingWithApp(app, allowIncompatible = allowIncompatible)
            }
        }
    }

    /**
     * User chose to proceed patching with an unsupported app version.
     * Starts patching with allowIncompatible=true so version-incompatible patches are included.
     */
    fun proceedWithUnsupportedVersion() {
        showUnsupportedVersionDialog = null
        proceedWithPendingApp(allowIncompatible = true)
    }

    /**
     * User dismissed the experimental version warning dialog.
     * Discards the pending selection and cleans up the temporary file if needed.
     */
    fun dismissExperimentalVersionDialog() {
        showExperimentalVersionDialog = null
        clearPendingApp()
    }

    /**
     * User acknowledged the experimental version warning and chose to proceed.
     * Starts patching with allowIncompatible=false - the version is supported,
     * just flagged as experimental in the patch bundle.
     */
    fun proceedWithExperimentalVersion() {
        showExperimentalVersionDialog = null
        proceedWithPendingApp(allowIncompatible = false)
    }

    /**
     * User dismissed the wrong package dialog.
     */
    fun dismissWrongPackageDialog() {
        showWrongPackageDialog = null
    }

    /**
     * User dismissed the invalid signature dialog.
     * Discards the pending selection and cleans up the temporary file if needed.
     */
    fun dismissInvalidSignatureDialog() {
        showInvalidSignatureDialog = null
        clearPendingApp()
    }

    /**
     * User chose to proceed patching despite the signature mismatch warning.
     * Skips signature verification and resumes the patching flow.
     */
    fun proceedIgnoringSignature() {
        showInvalidSignatureDialog = null
        val app = pendingSelectedApp ?: return
        pendingSelectedApp = null
        viewModelScope.launch {
            processSelectedAppIgnoringSignature(app)
        }
    }

    // Callback for starting patch
    var onStartQuickPatch: ((QuickPatchParams) -> Unit)? = null

    init {
        observeManagerUpdate()
        triggerUpdateCheck()
        observeLoadingState()
        observeSnackbarState()
    }

    /**
     * Reactively updates [installedAppsLoading] based on bundle update progress and app list state.
     */
    private fun observeLoadingState() {
        viewModelScope.launch {
            combine(
                patchBundleRepository.bundleUpdateProgress,
                patchBundleRepository.sources,
                installedAppRepository.getAll(),
                availablePatches
            ) { progress, sources, installedApps, patchCount ->
                val isBundleUpdateInProgress =
                    progress?.result == PatchBundleRepository.BundleUpdateResult.None
                val hasEnabledSources = sources.any { it.enabled }
                // Guard: sources list is empty on the very first emission before the DB is read.
                // Treat that transient state as "still loading" so we never flash the empty-state
                // UI before the real bundle configuration is known.
                val sourcesInitialized = sources.isNotEmpty() || patchCount > 0
                // If no sources are enabled (and we know the DB has been read), there is nothing
                // to load - this is a valid terminal state, not a loading state.
                val hasLoadedData = sourcesInitialized &&
                        (!hasEnabledSources || installedApps.isNotEmpty() || patchCount > 0)
                isBundleUpdateInProgress || !hasLoadedData
            }
                .distinctUntilChanged()
                .collect { loading ->
                    installedAppsLoading = loading
                }
        }
    }

    /**
     * Reactively maps bundle update progress to snackbar visibility and status.
     */
    private fun observeSnackbarState() {
        viewModelScope.launch {
            patchBundleRepository.bundleUpdateProgress.collect { progress ->
                if (progress == null) {
                    showBundleUpdateSnackbar = false
                    return@collect
                }
                showBundleUpdateSnackbar = true
                snackbarStatus = when (progress.result) {
                    PatchBundleRepository.BundleUpdateResult.Success,
                    PatchBundleRepository.BundleUpdateResult.NoUpdates -> BundleUpdateStatus.Success
                    PatchBundleRepository.BundleUpdateResult.NoInternet,
                    PatchBundleRepository.BundleUpdateResult.Error -> BundleUpdateStatus.Error
                    PatchBundleRepository.BundleUpdateResult.None -> BundleUpdateStatus.Updating
                    PatchBundleRepository.BundleUpdateResult.SkippedMetered -> BundleUpdateStatus.Warning
                }
                updatesSkippedDueToMetered = when (progress.result) {
                    PatchBundleRepository.BundleUpdateResult.SkippedMetered -> true
                    PatchBundleRepository.BundleUpdateResult.Success,
                    PatchBundleRepository.BundleUpdateResult.NoUpdates -> false
                    else -> updatesSkippedDueToMetered
                }
            }
        }
    }

    /** Pull-to-refresh state. */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /**
     * Triggers a manual refresh: updates bundles and checks for manager updates.
     * Guard against double-trigger if user swipes while refresh is in progress.
     */
    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                patchBundleRepository.updateCheck()
                checkForManagerUpdates()
                delay(500.milliseconds)
            } finally {
                _isRefreshing.value = false
            }
            apps.reload()
        }
    }

    /**
     * Returns `true` when the user has disabled metered updates AND is currently on
     * a metered (mobile data) connection - meaning patches may not be up to date.
     */
    fun isOnMeteredWithUpdatesDisabled(): Boolean {
        if (prefs.allowMeteredUpdates.getBlocking()) return false
        return networkInfo.isMetered()
    }

    /** True while a batch queue is patching, so callers can explain why a start was ignored. */
    val batchPatchRunning: Boolean get() = batchPatchCoordinator.isRunning

    /**
     * The batch queue, which outlives its screen: a deep link or shortcut returns to home while
     * it keeps patching, and home is then the only way back to it.
     */
    val batchRun: StateFlow<BatchRunState?> = batchPatchCoordinator.state

    /**
     * Guard entry-point for all patching flows.
     * Shows MeteredPatchingDialog when on metered network with updates disabled,
     * so the user can choose to update patches first or patch anyway.
     * Otherwise, launches [action] immediately.
     */
    fun guardPatching(action: suspend () -> Unit) {
        // A batch run owns the patcher worker for its whole duration, so starting a single
        // patch would replace the app the queue is currently working on
        if (batchPatchCoordinator.isRunning) {
            app.toast(app.getString(R.string.batch_patch_in_progress))
            return
        }

        // Check available storage first - low disk space is the most common cause of
        // cryptic "file not found" errors and corrupt output APKs during patching.
        val freeBytes = StatFs(app.filesDir.absolutePath).availableBytes
        if (freeBytes < lowDiskSpaceThresholdBytes) {
            pendingPatchAction = action
            lowDiskSpaceFreeBytes = freeBytes
            showLowDiskSpaceDialog = true
            return
        }
        if (isOnMeteredWithUpdatesDisabled()) {
            pendingPatchAction = action
            showMeteredPatchingDialog = true
        } else {
            viewModelScope.launch { action() }
        }
    }

    /**
     * User chose to update patches first, then automatically continue patching.
     */
    fun refreshBundlesAndContinuePatching() {
        showMeteredPatchingDialog = false
        val action = pendingPatchAction ?: return
        pendingPatchAction = null
        viewModelScope.launch {
            // User explicitly requested update - bypass metered check and wait for completion
            patchBundleRepository.updateCheckAndAwait(allowUnsafeNetwork = true)
            action()
        }
    }

    /**
     * User chose to patch with the currently cached patches despite being on metered network.
     */
    fun dismissMeteredPatchingDialogAndProceed() {
        showMeteredPatchingDialog = false
        val action = pendingPatchAction ?: return
        pendingPatchAction = null
        viewModelScope.launch { action() }
    }

    /**
     * User canceled patching from the metered network dialog.
     */
    fun dismissMeteredPatchingDialog() {
        showMeteredPatchingDialog = false
        pendingPatchAction = null
    }

    /**
     * User chose to proceed with patching despite low disk space.
     * Continues to the metered network check if applicable, then launches the action.
     */
    fun dismissLowDiskSpaceDialogAndProceed() {
        showLowDiskSpaceDialog = false
        val action = pendingPatchAction ?: return
        pendingPatchAction = null
        if (isOnMeteredWithUpdatesDisabled()) {
            pendingPatchAction = action
            showMeteredPatchingDialog = true
        } else {
            viewModelScope.launch { action() }
        }
    }

    /**
     * User canceled patching from the low disk space dialog.
     */
    fun dismissLowDiskSpaceDialog() {
        showLowDiskSpaceDialog = false
        pendingPatchAction = null
    }

    /**
     * Mirrors the resolved update into [updatedManagerVersion] so the banner also appears when
     * a background check finds the release. A failed check is ignored rather than hiding a
     * banner the user is already looking at.
     */
    private fun observeManagerUpdate() = viewModelScope.launch {
        managerUpdateRepository.availableUpdate.collect { update ->
            update?.let { updatedManagerVersion = it.version }
        }
    }

    /**
     * Checks for a manager update. The repository only reports releases whose APK is already
     * downloadable, so the banner can never point at an asset that is still uploading.
     */
    suspend fun checkForManagerUpdates() {
        uiSafe(app, R.string.failed_to_check_updates, "Failed to check for updates") {
            managerUpdateRepository.refresh()
        }
    }

    /**
     * Launches [checkForManagerUpdates] on [viewModelScope] so it survives composition changes.
     * Safe to call from UI without a coroutine scope.
     */
    fun triggerUpdateCheck() {
        viewModelScope.launch {
            checkForManagerUpdates()
        }
    }

    @SuppressLint("ShowToast")
    private suspend fun <T> withPersistentImportToast(block: suspend () -> T): T = coroutineScope {
        val progressToast = withContext(Dispatchers.Main) {
            Toast.makeText(
                app,
                app.getString(R.string.importing_ellipsis),
                Toast.LENGTH_SHORT
            )
        }
        withContext(Dispatchers.Main) { progressToast.show() }

        val toastRepeater = launch(Dispatchers.Main) {
            try {
                while (isActive) {
                    delay(1_750.milliseconds)
                    progressToast.show()
                }
            } catch (_: CancellationException) {
                // Ignore cancellation
            }
        }

        try {
            val result = block()
            withContext(Dispatchers.Main) {
                app.toast(app.getString(R.string.imported_successfully))
            }
            result
        } finally {
            toastRepeater.cancel()
            withContext(Dispatchers.Main) { progressToast.cancel() }
        }
    }

    /**
     * Imports each file, as a new local source or into the one its uid names, one after another
     * under one import toast. With [chooseApps] the app list of each new source opens once its
     * patches have loaded.
     */
    private fun importLocalSources(imports: List<Pair<Uri, Int?>>, chooseApps: Boolean) = viewModelScope.launch {
        val added = withContext(NonCancellable) {
            withPersistentImportToast {
                imports.mapNotNull { (uri, replacingUid) ->
                    importLocalFile(uri, replacingUid).takeIf { replacingUid == null }
                }
            }
        }
        // Two files carrying the same bundle land on the same source
        onSourcesAdded(added.distinct(), chooseApps)
    }

    /**
     * Adds a remote source for each of [urls], downloaded together as one update. With
     * [chooseApps] the app list of each added source opens once its patches have loaded.
     */
    fun createRemoteSources(urls: List<String>, chooseApps: Boolean = false) = viewModelScope.launch {
        val added = withContext(NonCancellable) {
            patchBundleRepository.createRemotes(urls, autoUpdate = true)
        }
        onSourcesAdded(added, chooseApps)
    }

    /**
     * Queues the app list of each of [uids] that has apps to list as its patches load, then hints
     * at the swipe gestures of the source cards.
     *
     * A source only appears among the loaded ones once its patches are in, which is also what the
     * list is built from. The sources load together, so they are taken as each one arrives rather
     * than in turn, and one that never loads holds back none of the others.
     */
    private suspend fun onSourcesAdded(uids: List<Int>, chooseApps: Boolean) {
        if (uids.isEmpty()) return

        val pending = uids.toMutableList()
        withTimeoutOrNull(NEW_SOURCE_LOAD_TIMEOUT) {
            patchBundleRepository.allBundlesInfoFlow.first { info ->
                pending.removeAll { uid ->
                    val loaded = info[uid] ?: return@removeAll false
                    // Universal patches put no app on the home screen, so there would be nothing to list
                    if (chooseApps && loaded.listedApps().isNotEmpty()) sourceAppsQueue += uid
                    true
                }
                pending.isEmpty()
            }
        }
        delay(1.5.seconds)
        apps.triggerSwipeGestureHint()
    }

    /**
     * Points an existing local source at a newly picked file. Adding the updated file instead
     * would create a second source and strand the patch selection on the old one.
     */
    fun updateLocalSource(uid: Int, patchBundle: Uri) = importLocalSources(listOf(patchBundle to uid), chooseApps = false)

    /** @return The uid of the source [patchBundle] landed under, or null when it could not be read. */
    @SuppressLint("Recycle")
    private suspend fun importLocalFile(patchBundle: Uri, replacingUid: Int?): Int? {
        val permissionFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        var persistedPermission = false
        val size = runCatching {
            contentResolver.openFileDescriptor(patchBundle, "r")
                ?.use { it.statSize.takeIf { sz -> sz > 0 } }
                ?: contentResolver.query(
                    patchBundle,
                    arrayOf(OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )
                    ?.use { cursor ->
                        val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (index != -1 && cursor.moveToFirst()) cursor.getLong(index) else null
                    }
        }.getOrNull()?.takeIf { it > 0L }
        try {
            contentResolver.takePersistableUriPermission(patchBundle, permissionFlags)
            persistedPermission = true
        } catch (_: SecurityException) {
            // Provider may not support persistable permissions; fall back to transient grant
        }

        val openStream: suspend () -> InputStream = {
            contentResolver.openInputStream(patchBundle)
                ?: throw FileNotFoundException("Unable to open $patchBundle")
        }
        return try {
            if (replacingUid != null) {
                patchBundleRepository.replaceLocal(replacingUid, size, openStream)
            } else {
                patchBundleRepository.createLocal(size, openStream)
            }
        } finally {
            if (persistedPermission) {
                try {
                    contentResolver.releasePersistableUriPermission(
                        patchBundle,
                        permissionFlags
                    )
                } catch (_: SecurityException) {
                    // Ignore if provider revoked or already released
                }
            }
        }
    }

    /**
     * Called when the app is opened via a deep link containing a bundle URL.
     * Shows a confirmation dialog instead of adding silently.
     */
    fun handleDeepLinkAddSource(url: String, name: String?) {
        dismissOpenDialogs()
        deepLinkPendingBundle = DeepLinkBundle(url = url, name = name)
    }

    /** User confirmed adding the bundle from the deep link confirmation dialog. */
    fun confirmDeepLinkBundle(chooseApps: Boolean) {
        val bundle = deepLinkPendingBundle ?: return
        deepLinkPendingBundle = null
        createRemoteSources(listOf(bundle.url), chooseApps)
    }

    /** User dismissed the deep link confirmation dialog. */
    fun dismissDeepLinkBundle() {
        deepLinkPendingBundle = null
    }

    /**
     * Closes every open dialog and sheet so an incoming source confirmation is not stacked behind
     * one, and so the flow the user resumes afterward is rebuilt from the new set of sources.
     */
    private fun dismissOpenDialogs() {
        cleanupExpertModeData()
        dismissSimpleBundleSelectDialog()
        dismissSplitApkWarning()
        dismissUnsupportedVersionDialog()
        dismissExperimentalVersionDialog()
        dismissWrongPackageDialog()
        dismissInvalidSignatureDialog()
        dismissPrePatchInstallerDialog()
        dismissMeteredPatchingDialog()
        dismissLowDiskSpaceDialog()
        dismissInstalledAppInfo()
        showNoCompatibleVersionsDialog = null
        showAndroid11Dialog = false
        showBundleManagementSheet = false
        showRenameBundleDialog = false
        bundleToRename = null
        showAddSourceDialog = false
        sourceAppsQueue = emptyList()
        pickedBundles = emptyList()
        cleanupPendingData()
    }

    /**
     * Metadata for all apps across enabled bundles - display names, icon colors, signatures, etc.
     * Delegates to [PatchBundleRepository.appMetadata] as the single source of truth.
     */
    val bundleAppMetadataFlow: StateFlow<Map<String, BundleAppMetadata>> =
        patchBundleRepository.appMetadata

    /**
     * [bundleAppMetadataFlow] widened to every bundle, for the tracked records whose own source is
     * no longer enabled and which have nothing else left describing them.
     */
    val allBundleAppMetadataFlow: StateFlow<Map<String, BundleAppMetadata>> =
        patchBundleRepository.allAppMetadata

    /**
     * Snapshot of all bundle info (including disabled) as a [StateFlow] for synchronous reads.
     * Used by [getPatchesForPackage] which is called from Compose (non-suspend context).
     */
    private val allBundlesInfoState: StateFlow<Map<Int, PatchBundleInfo.Global>> =
        patchBundleRepository.allBundlesInfoFlow
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val patchSourcesState: StateFlow<Map<Int, PatchBundleSource>> =
        patchBundleRepository.sources
            .map { list -> list.associateBy { it.uid } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Returns the [PatchBundleSource] with [uid], or null if it has not been loaded yet. */
    fun getPatchSource(uid: Int): PatchBundleSource? = patchSourcesState.value[uid]

    /**
     * Returns all patches available for [packageName] across all enabled bundles.
     * Groups them as Map<BundleUid, List<PatchInfo>> for the swipe-right patches dialog.
     */
    fun getPatchesForPackage(packageName: String): Map<Int, List<PatchInfo>> {
        val bundleInfo = allBundlesInfoState.value
        return buildMap {
            bundleInfo
                .filter { (_, info) -> info.enabled }
                .forEach { (uid, info) ->
                    val patches = info.forPackage(packageName, null).patches
                    if (patches.isNotEmpty()) put(uid, patches)
                }
        }
    }

    /**
     * Returns patches actually applied to [packageName], grouped as Map<BundleUid, List<PatchInfo>>.
     * Used by the swipe-right dialog for cards patched via "Other apps" (universal patches),
     * where getPatchesForPackage would return empty because no bundle declares them compatible.
     * Names that reference bundles no longer available are silently dropped.
     */
    suspend fun getAppliedPatchesForPackage(packageName: String): Map<Int, List<PatchInfo>> {
        val bundleInfo = allBundlesInfoState.value
        val applied = installedAppRepository.getAppliedPatches(packageName)
        return applied.entries.mapNotNull { (uid, patchNames) ->
            if (patchNames.isEmpty()) return@mapNotNull null
            val patchInfos = bundleInfo[uid]?.forPackage(packageName, null)?.patches
                ?.filter { it.name in patchNames }
                ?.sortedBy { it.name }
                ?: return@mapNotNull null
            if (patchInfos.isEmpty()) null else uid to patchInfos
        }.toMap()
    }

    /**
     * Returns the display name of the bundle with [uid], or null.
     */
    fun getBundleDisplayName(uid: Int): String? =
        allBundlesInfoState.value[uid]?.name

    /**
     * Returns the set of experimental version strings for a package from all currently enabled
     * bundles, narrowed to the versions the user is actually offered. Used by the UI to show
     * "Experimental" badges on specific versions.
     */
    fun getExperimentalVersionsForPackage(packageName: String): Set<String> =
        compatibleVersions[packageName].orEmpty().offered().experimentalVersions()

    suspend fun persistReinstalledApp(
        app: InstalledApp,
        packageName: String,
        installType: InstallType
    ): Boolean = withContext(Dispatchers.IO) {
        val appliedPatches = installedAppRepository.getAppliedPatches(app.currentPackageName)
        installedAppRepository.addOrUpdate(
            currentPackageName = packageName,
            originalPackageName = app.originalPackageName,
            isClone = app.isClone,
            version = app.version,
            installType = installType,
            patchSelection = appliedPatches,
            selectionPayload = app.selectionPayload,
            patchedAt = app.patchedAt
        )
        apps.notifyAppStateChanged(packageName)
        if (packageName != app.currentPackageName) apps.notifyAppStateChanged(app.currentPackageName)
        true
    }

    fun uninstallApps(items: Collection<HomeAppItem>) {
        val installs = items.mapNotNull { it.installedApp }.distinctBy { it.currentPackageName }
        if (installs.isEmpty()) return

        viewModelScope.launch {
            var completed = 0
            var skipped = 0
            var unverified = 0
            for (installed in installs) {
                runCatching {
                    when (installed.installType) {
                        // Unmounting only removes the bind mount and the module files, so it is
                        // safe even when the underlying package can no longer be identified
                        InstallType.MOUNT -> {
                            installerManager.uninstallPackage(installed.currentPackageName, installed.installType)
                            installedAppRepository.delete(installed)
                        }
                        else -> {
                            if (localApkSources.trackedPatchState(installed) != InstalledPatchState.Patched) {
                                unverified++
                                return@runCatching false
                            }
                            val removed = withTimeoutOrNull(BATCH_UNINSTALL_TIMEOUT) {
                                installerManager.uninstallPackage(installed.currentPackageName, installed.installType)
                                true
                            } == true
                            if (!removed) error(app.getString(R.string.uninstall_timeout))
                        }
                    }
                    true
                }.onSuccess { removed ->
                    if (removed) completed++ else skipped++
                    apps.notifyAppStateChanged(installed.currentPackageName)
                }.onFailure { error ->
                    skipped++
                    if (error !is UninstallCancelledException) {
                        app.toast(app.getString(R.string.uninstall_app_fail, error.simpleMessage()))
                    }
                }
            }

            if (unverified > 0) app.toast(app.getString(R.string.uninstall_app_unverified))
            app.batchActionSummary(R.plurals.batch_uninstall_summary, completed, skipped)
                ?.let { app.toast(it) }
        }
    }

    /**
     * Handle app button click.
     */
    fun handleAppClick(
        packageName: String,
        availablePatches: Int,
        bundleUpdateInProgress: Boolean,
        android11BugActive: Boolean,
        installedApp: InstalledApp?
    ) {
        // If app is installed, allow click even during updates
        if (installedApp != null) {
            return // Caller will handle navigation
        }

        // Check if patches are being fetched
        if (availablePatches <= 0 || bundleUpdateInProgress) {
            app.toast(app.getString(R.string.home_sources_are_loading))
            return
        }

        // Check for Android 11 installation bug
        if (android11BugActive) {
            showAndroid11Dialog = true
            return
        }

        showPatchDialog(packageName)
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
        preparePendingFlow(packageName, repatchedPackageName)

        // Guard: if there is a pending bundle update on metered data, show the outdated-patches
        // dialog before proceeding with the actual APK selection flow.
        guardPatching { showPatchDialogInternal(packageName) }
    }

    /** Seeds the pending APK selection state for [packageName] from the current bundle data. */
    private fun preparePendingFlow(packageName: String, repatchedPackageName: String? = null) {
        pendingPackageName = packageName
        pendingRepatchPackageName = repatchedPackageName
        pendingAppName = bundleAppMetadataFlow.value[packageName]?.displayName
            ?: KnownApps.getAppName(packageName)
        pendingRecommendedVersion = recommendedVersions[packageName]
        pendingCompatibleVersions = compatibleVersions[packageName] ?: emptyList()
        pendingSelectedDownloadVersion = pendingRecommendedVersion
        // Reset per-package cached state so a new flow always loads fresh data
        pendingSavedApkInfo = null
        pendingInstalledApkInfo = null
        pendingInstalledAppVersion = null
        pendingStockAppInstalled = null
    }

    private suspend fun showPatchDialogInternal(packageName: String) {
        val savedInfo = withContext(Dispatchers.IO) {
            localApkSources.saved(packageName)
        }
        pendingSavedApkInfo = savedInfo

        // Check if every declared version is incompatible with the current device SDK
        val versions = compatibleVersions[packageName].orEmpty().offered()
        val allIncompatible = versions.isNotEmpty() && versions.none { it.installableOnDevice() }
        if (allIncompatible) {
            showNoCompatibleVersionsDialog = packageName
            return
        }

        // In simple mode: if multiple bundles cover this package, ask the user to pick one
        // before showing the APK selection dialog so the correct recommended version is used
        if (!isExpertMode() && pendingSelectedBundleUid == null) {
            val allBundlesForCheck = withContext(Dispatchers.IO) {
                patchBundleRepository
                    .offeredBundleInfoFlow(packageName, version = null)
                    .first()
            }
            val candidates = allBundlesForCheck
                .filter { it.enabled }
                .map { bundle ->
                    val patchNames = bundle.patchSequence(allowIncompatible = true)
                        .filter { it.include }
                        .mapTo(mutableSetOf()) { it.name }
                    bundle to patchNames
                }
                .filter { (_, patches) -> patches.isNotEmpty() }

            if (candidates.size > 1) {
                simpleBundleSelectCandidates = candidates
                showSimpleBundleSelectDialog = true
                return
            }
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
            val savedJob = if (pendingSavedApkInfo == null) {
                async(Dispatchers.IO) { localApkSources.saved(packageName) }
            } else null
            val installedJob = async(Dispatchers.IO) { localApkSources.installed(packageName) }
            savedJob?.await()?.let { pendingSavedApkInfo = it }
            applyInstalledAppSource(installedJob.await(), offersApk = expertMode)
        }

        val recommendedVersion = pendingRecommendedVersion

        val shouldAutoUseSaved = !expertMode &&
                pendingSavedApkInfo != null &&
                recommendedVersion != null &&
                pendingSavedApkInfo!!.version == recommendedVersion.version

        if (shouldAutoUseSaved) {
            // Skip dialog and use saved APK directly
            handleSavedApkSelection()
        } else {
            // Show dialog
            showApkAvailabilityDialog = true
        }
    }

    /**
     * Stores the installed-app lookup, keeping the APK only when its version can be patched
     * so every caller offers the installed source under the same condition. [offersApk] is what
     * the flow itself allows: simple mode hides the button rather than the app behind it.
     */
    private fun applyInstalledAppSource(source: InstalledAppSource, offersApk: Boolean = true) {
        pendingStockAppInstalled = source.hasStockInstall
        pendingInstalledAppVersion = source.version
        pendingInstalledApkInfo = source.apk
            .takeIf { offersApk }
            .patchableBy(pendingCompatibleVersions)
    }

    /**
     * Handle selection of the currently installed APK from the APK availability dialog.
     * For single APKs: copies to a temp file. For split APKs: packs into a temp .apks archive.
     * The source is NOT saved to the original-APK repository.
     */
    fun handleInstalledApkSelection() {
        val installedInfo = pendingInstalledApkInfo
        val packageName = pendingPackageName

        if (installedInfo == null || packageName == null) {
            cleanupPendingData()
            return
        }

        viewModelScope.launch {
            showApkAvailabilityDialog = false
            processingApkSelection = true
            try {
                val selectedApp = withContext(Dispatchers.IO) {
                    try {
                        if (installedInfo.isSplit) {
                            val archive = File(filesystem.uiTempDir, "${packageName}_installed.apks")
                            createApksArchive(installedInfo, archive)
                            SelectedApp.Local(
                                packageName = packageName,
                                version = installedInfo.version,
                                versionCode = installedInfo.versionCode,
                                file = archive,
                                temporary = true,
                                fromInstalledDevice = true
                            )
                        } else {
                            val source = File(installedInfo.apkPath)
                            if (!source.exists()) return@withContext null
                            val tempFile = File(filesystem.uiTempDir, "${packageName}_installed.apk")
                            source.copyTo(tempFile, overwrite = true)
                            SelectedApp.Local(
                                packageName = packageName,
                                version = installedInfo.version,
                                versionCode = installedInfo.versionCode,
                                file = tempFile,
                                temporary = true,
                                fromInstalledDevice = true
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to prepare installed APK", e)
                        null
                    }
                }

                if (selectedApp != null) {
                    // Skip signature check: the installed APK may already be patched with our key
                    processSelectedAppIgnoringSignature(selectedApp)
                } else {
                    app.toast(app.getString(R.string.home_invalid_apk_io_error))
                    cleanupPendingData()
                }
            } finally {
                processingApkSelection = false
            }
        }
    }

    /**
     * Handles a helper result where the user installed the target app outside Morphe.
     *
     * Reached in simple mode too, where the installed source is otherwise hidden: the helper is
     * opt-in and the hand-off was confirmed, so the app store is the whole point of the request.
     */
    fun handleHelperInstalledAppSelection(packageName: String) {
        viewModelScope.launch {
            val pending = pendingPackageName
            if (pending != null && pending != packageName) {
                // The helper answered about an app the user never asked to patch
                stopHelperHandoff(pending, R.string.home_apk_helper_wrong_package)
                return@launch
            }

            // A hand-off through an app store can outlive Morphe, so the flow is rebuilt from the
            // bundle data. Unknown packages are refused because without their compatible versions
            // there is nothing left to check the installed build against
            if (pending == null) {
                if (packageName !in compatibleVersions) {
                    app.toast(app.getString(R.string.home_apk_helper_installed_unavailable))
                    cleanupPendingData()
                    return@launch
                }
                preparePendingFlow(packageName)
            }

            processingApkSelection = true
            val source = withContext(Dispatchers.IO) {
                localApkSources.installed(packageName)
            }
            applyInstalledAppSource(source)

            val installedInfo = pendingInstalledApkInfo
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
        message?.let { app.toast(app.getString(it)) }

        if (pendingSavedApkInfo == null) {
            pendingSavedApkInfo = withContext(Dispatchers.IO) { localApkSources.saved(packageName) }
        }
        showApkAvailabilityDialog = true
    }

    /**
     * Loads all user-installed apps and opens the picker dialog.
     * Called from the "Other apps" file-picker prompt when the user taps "Use installed app".
     */
    fun loadInstalledAppsForPicker() {
        if (loadingInstalledApps) return
        viewModelScope.launch {
            installedAppsForPicker = emptyList()
            loadingInstalledApps = true
            showFilePickerPromptDialog = false
            showInstalledAppPickerDialog = true
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
        showInstalledAppPickerDialog = false
        pendingAppName = item.label
        viewModelScope.launch {
            processingApkSelection = true
            try {
                val selectedApp = withContext(Dispatchers.IO) {
                    try {
                        if (item.info.isSplit) {
                            val archive = File(filesystem.uiTempDir, "${item.packageName}_installed.apks")
                            createApksArchive(item.info, archive)
                            SelectedApp.Local(
                                packageName = item.packageName,
                                version = item.info.version,
                                versionCode = item.info.versionCode,
                                file = archive,
                                temporary = true,
                                fromInstalledDevice = true
                            )
                        } else {
                            val source = File(item.info.apkPath)
                            if (!source.exists()) return@withContext null
                            val tempFile = File(filesystem.uiTempDir, "${item.packageName}_installed.apk")
                            source.copyTo(tempFile, overwrite = true)
                            SelectedApp.Local(
                                packageName = item.packageName,
                                version = item.info.version,
                                versionCode = item.info.versionCode,
                                file = tempFile,
                                temporary = true,
                                fromInstalledDevice = true
                            )
                        }
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
                    app.toast(app.getString(R.string.home_invalid_apk_io_error))
                    cleanupPendingData()
                }
            } finally {
                processingApkSelection = false
            }
        }
    }

    /**
     * Packs [info]'s base APK and all split APKs into an APKS archive (ZIP).
     * Entry names preserve the original filenames so [SplitApkPreparer] can
     * identify the base entry by the "base" substring and filter ABI/density splits.
     */
    private fun createApksArchive(info: InstalledApkInfo, output: File) {
        output.parentFile?.mkdirs()
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            fun addEntry(file: File) {
                // APKs are already compressed ZIPs - use STORED to avoid wasting CPU on deflate.
                // STORED requires CRC32 and size known upfront, so we read the file twice.
                val crc = CRC32()
                val buf = ByteArray(65536)
                FileInputStream(file).use { input ->
                    var n: Int
                    while (input.read(buf).also { n = it } >= 0) crc.update(buf, 0, n)
                }
                val entry = ZipEntry(file.name).apply {
                    method = ZipEntry.STORED
                    size = file.length()
                    compressedSize = file.length()
                    this.crc = crc.value
                }
                zip.putNextEntry(entry)
                FileInputStream(file).use { it.copyTo(zip) }
                zip.closeEntry()
            }
            addEntry(File(info.apkPath))
            info.splitPaths.forEach { addEntry(File(it)) }
        }
    }

    /**
     * Called when an APK is shared to Morphe via the system share sheet.
     * Shows a toast and returns early if expert mode is disabled.
     * Otherwise, waits until [installedAppsLoading] is false before triggering [handleApkSelection].
     */
    fun handleExternalApkUri(uri: Uri) {
        viewModelScope.launch {
            if (!isExpertMode()) {
                app.toast(app.getString(R.string.home_external_apk_expert_mode_required))
                return@launch
            }
            // Wait for patches to be ready. Cap at 30 s to avoid hanging forever when
            // no patch sources are configured (installedAppsLoading never clears in that case)
            withTimeoutOrNull(30.seconds) {
                snapshotFlow { installedAppsLoading }.first { !it }
            }
            handleApkSelection(uri)
        }
    }

    /**
     * Handle APK file selection.
     */
    fun handleApkSelection(uri: Uri?) {
        if (uri == null) {
            cleanupPendingData()
            return
        }

        viewModelScope.launch {
            processingApkSelection = true
            try {
                val result = withContext(Dispatchers.IO) {
                    loadLocalApk(app, uri)
                }

                when (result) {
                    is ApkLoadResult.Success -> {
                        pickedApkIcon = result.icon?.let { result.app.file to it }
                        processSelectedApp(result.app)
                    }
                    is ApkLoadResult.Unreadable -> app.toast(app.getString(R.string.home_invalid_apk_unreadable))
                    is ApkLoadResult.NotAnApk -> app.toast(app.getString(R.string.home_invalid_apk_not_an_apk))
                    is ApkLoadResult.AlreadyPatched -> app.toast(app.getString(R.string.home_invalid_apk_already_patched))
                    is ApkLoadResult.IoError -> app.toast(app.getString(R.string.home_invalid_apk_io_error))
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
        val savedInfo = pendingSavedApkInfo
        val packageName = pendingPackageName

        if (savedInfo == null || packageName == null) {
            app.toast(app.getString(R.string.home_app_info_repatch_no_original_apk))
            cleanupPendingData()
            return
        }

        viewModelScope.launch {
            showApkAvailabilityDialog = false
            processingApkSelection = true
            try {
                // Create SelectedApp from saved APK file
                val selectedApp = withContext(Dispatchers.IO) {
                    try {
                        val file = File(savedInfo.filePath)
                        if (!file.exists()) {
                            app.toast(app.getString(R.string.home_app_info_repatch_no_original_apk))
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
                    cleanupPendingData()
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
        // Validate package name if expected (known-app flow sets pendingPackageName)
        if (pendingPackageName != null && selectedApp.packageName != pendingPackageName) {
            showWrongPackageDialog = WrongPackageDialogState(
                expectedPackage = pendingPackageName!!,
                actualPackage = selectedApp.packageName
            )
            if (selectedApp is SelectedApp.Local && selectedApp.temporary) {
                selectedApp.file.delete()
            }
            cleanupPendingData()
            return
        }

        // Warn when the selected file is a split APK while the bundle requires a full APK.
        // This must happen BEFORE signature verification - split archives (.apkm/.apks/.xapk)
        // are not valid APKs so PackageManager cannot read their signature, which would cause
        // a false "invalid signature" dialog instead of the correct "split APK" warning.
        if (selectedApp is SelectedApp.Local && !skipSplitCheck) {
            val requiredApkFileType = bundleAppMetadataFlow.value[selectedApp.packageName]?.apkFileType

            val isSplitFile = SplitApkPreparer.isSplitArchive(selectedApp.file)

            if (isSplitFile && requiredApkFileType?.isApk == true && requiredApkFileType.isRequired) {
                pendingSelectedApp = selectedApp
                showSplitApkWarningDialog = true
                cleanupPendingData(keepSelectedApp = true, keepBundleUid = true)
                return
            }

            // Verify APK signature against the expected signatures declared in the patch bundle.
            // GET_SIGNING_CERTIFICATES (API 28+) is required for reliable archive signature reads.
            // On Android 8–10 the legacy GET_SIGNATURES path cannot read signatures from
            // archive files correctly, so we skip verification there to avoid false-blocking users.
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                val expectedSignatures = bundleAppMetadataFlow.value[selectedApp.packageName]?.signatures
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
                        pendingSelectedApp = selectedApp
                        showInvalidSignatureDialog = InvalidSignatureDialogState(
                            packageName = selectedApp.packageName,
                            appName = pendingAppName ?: KnownApps.getAppName(selectedApp.packageName)
                        )
                        cleanupPendingData(keepSelectedApp = true, keepBundleUid = true)
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
            patchBundleRepository
                .offeredBundleInfoFlow(selectedApp.packageName, selectedApp.version, selectedApp.versionCode)
                .first()
        }

        val enabledBundles = bundles.filter { it.enabled }

        // Categorize what exists across all enabled bundles for this APK:
        val hasCompatible   = enabledBundles.any { it.compatible.isNotEmpty() }   // right pkg + right version
        val hasIncompatible = enabledBundles.any { it.incompatible.isNotEmpty() } // right pkg, wrong version
        val hasUniversal    = enabledBundles.any { it.universal.isNotEmpty() }    // no pkg restriction
        val hasAnything     = hasCompatible || hasIncompatible || hasUniversal

        if (!hasAnything) {
            // Truly no patches exist for this package in any enabled bundle
            app.toast(app.getString(R.string.home_no_patches_available))
            if (selectedApp is SelectedApp.Local && selectedApp.temporary) {
                selectedApp.file.delete()
            }
            cleanupPendingData()
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
        val versionMismatch = !hasCompatible && hasIncompatible
        // Experimental check is independent - a version can be experimental AND compatible
        val isVersionExperimental = enabledBundles.any { it.isVersionExperimental }

        // Check if the user has enabled experimental-version mode for this package's bundle
        val experimentalEnabledUids = prefs.bundleExperimentalVersionsEnabled.getBlocking()
        val isExperimentalModeEnabled = enabledBundles.any { bundle ->
            bundle.uid.toString() in experimentalEnabledUids
        }

        if (versionMismatch && !allowIncompatible) {
            pendingSelectedApp = selectedApp
            showUnsupportedVersionDialog = unsupportedVersionState(selectedApp, isVersionExperimental)
            cleanupPendingData(keepSelectedApp = true, keepBundleUid = true)
            return
        }

        // If the version is experimental, show the appropriate warning:
        // - Experimental mode ON → ExperimentalVersionWarningDialog
        // - Experimental mode OFF → UnsupportedVersionWarningDialog
        if (isVersionExperimental && !allowIncompatible) {
            pendingSelectedApp = selectedApp
            val state = unsupportedVersionState(selectedApp, isExperimental = true)
            if (isExperimentalModeEnabled) {
                showExperimentalVersionDialog = state
            } else {
                showUnsupportedVersionDialog = state
            }
            cleanupPendingData(keepSelectedApp = true, keepBundleUid = true)
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
        val offered = compatibleVersions[selectedApp.packageName].orEmpty().offered()

        return UnsupportedVersionDialogState(
            packageName = selectedApp.packageName,
            version = selectedApp.version ?: "unknown",
            versionCode = selectedApp.versionCode,
            recommendedVersion = recommendedVersions[selectedApp.packageName],
            compatibleVersionNames = offered.mapNotNull { it.target.version },
            compatibleVersionDescriptions = offered.mapNotNull { b ->
                val v = b.target.version ?: return@mapNotNull null
                val d = b.target.description ?: return@mapNotNull null
                v to d
            }.toMap(),
            compatibleVersionCodes = offered.mapNotNull { b ->
                val v = b.target.version ?: return@mapNotNull null
                val codes = b.buildCodes ?: return@mapNotNull null
                v to codes
            }.toMap(),
            isExperimental = isExperimental
        )
    }

    /**
     * Skips all preliminary checks (signature, version, bundle) and routes directly to patching.
     * Used when the user confirms proceeding despite a signature mismatch, or when patching
     * from the installed app where checks are not applicable.
     */
    suspend fun processSelectedAppIgnoringSignature(selectedApp: SelectedApp) {
        val allowIncompatible = prefs.disablePatchVersionCompatCheck.getBlocking()
        if (rootInstaller.isDeviceRooted()) {
            requestPrePatchInstallerSelection(selectedApp, allowIncompatible)
        } else {
            usingMountInstall = false
            startPatchingWithApp(selectedApp, allowIncompatible)
        }
    }

    /**
     * Start patching flow.
     */
    suspend fun startPatchingWithApp(
        selectedApp: SelectedApp,
        allowIncompatible: Boolean
    ) {
        // Read before the first selection is resolved, so every rule below and every later edit
        // in the expert dialog is answered for the APK this run actually starts from
        currentApkArchitecture = ApkArchitectureResolver.resolve(selectedApp, pm)

        // Everything this app has patches in, the sources it is kept from included: the expert
        // dialog offers those back, so it has to be told what it is hiding
        val availableBundles = patchBundleRepository
            .scopedBundleInfoFlow(selectedApp.packageName, selectedApp.version, selectedApp.versionCode)
            .first()

        if (availableBundles.isEmpty()) {
            app.toast(app.getString(R.string.home_no_patches_available))
            cleanupPendingData()
            return
        }

        val mutedSources = withContext(Dispatchers.IO) {
            sourceMuteRepository.getMutedFor(selectedApp.packageName)
        }
        // Every rule below reads this rather than the full list, so a source the app is kept from
        // contributes no defaults, no new-patch badges and no tab
        val allBundles = availableBundles.withoutMutedSources(mutedSources) { it.uid }

        expertModeAllBundles = availableBundles
        expertModeMutedSources = mutedSources

        // Spans every source, hidden ones included: what the user saved for a hidden one has to
        // survive validation. Offering the source back would otherwise hand the run an empty
        // selection, and then write that emptiness over what was saved
        val bundlesMap = availableBundles.associate { it.uid to it.patches.associateBy { patch -> patch.name } }

        val configurationKey = configurationKeyFor(selectedApp.packageName)

        // Whatever selection is reached below, the patches' own availability for the install
        // target has the final say on what the run starts with. A hidden source sits it out and
        // is put back untouched: it takes no part in a run, so it does not get to decide whether
        // the run draws from one source or several
        fun PatchSelection.applyInstallerRules(): PatchSelection {
            val heldBack = filterKeys { it in mutedSources }
            return filterKeys { it !in mutedSources }
                .applyAvailability(currentInstallerType, currentApkArchitecture, bundlesMap) + heldBack
        }

        if (isExpertMode()) {
            // Expert Mode: Load saved selections and options only for current bundles
            val currentBundleUids = availableBundles.map { it.uid }.toSet()

            // Load selections
            val savedSelections = withContext(Dispatchers.IO) {
                patchSelectionRepository.getAllSelectionsForPackage(configurationKey)
                    .filterKeys { it in currentBundleUids }
            }

            // Load options
            val savedOptions = withContext(Dispatchers.IO) {
                optionsRepository.getAllOptionsForPackage(configurationKey, bundlesMap)
                    .filterKeys { it in currentBundleUids }
            }

            // Use saved selections or create new ones
            val patches = if (savedSelections.isNotEmpty()) {
                // Count patches before validation
                val patchesBeforeValidation = savedSelections.values.sumOf { it.size }

                // Validate saved selections against available patches
                val validatedPatches = validatePatchSelection(savedSelections, bundlesMap)

                // Count patches after validation
                val patchesAfterValidation = validatedPatches.values.sumOf { it.size }

                // Show toast if patches were removed
                val removedCount = patchesBeforeValidation - patchesAfterValidation
                if (removedCount > 0) {
                    app.toast(app.resources.getQuantityString(
                        R.plurals.home_app_info_repatch_cleaned_invalid_data,
                        removedCount,
                        removedCount.toString()
                    ))
                }

                // The seen-patch snapshot is what tells a genuinely new patch from one the
                // user deselected, and the saved selection stands in for it on the first run,
                // before any snapshot exists. Read in one go rather than per bundle inside the
                // merge, which would cross to the IO dispatcher once per source
                val seenByBundle = withContext(Dispatchers.IO) {
                    allBundles.associate {
                        it.uid to patchSelectionRepository.getSeenPatches(configurationKey, it.uid)
                    }
                }

                // Runs after validation, so patches the sources dropped never sneak back in
                mergeNewlyAdded(
                    bundles = allBundles,
                    validated = validatedPatches,
                    known = { uid -> seenByBundle[uid] ?: savedSelections[uid] },
                    installerType = currentInstallerType,
                    apkArchitecture = currentApkArchitecture
                )
            } else {
                // No saved selections - use default for all current bundles
                allBundles.toPatchSelection(allowIncompatible) { _, patch ->
                    patch.defaultSelected(currentInstallerType, currentApkArchitecture)
                }
            }.applyInstallerRules()

            // Compute new patches map for the dialog to highlight.
            // Only populated when a previous selection exists - on first run there is nothing
            // to compare against so we keep it empty to avoid false "New" badges
            // A patch is genuinely "new" if it was absent from the seen-patches snapshot
            // saved at the end of the previous patching session. Comparing against savedSelections
            // (which contains only *selected* patches) would incorrectly flag deselected patches
            // as new on every subsequent open.
            val newPatchesMap: Map<Int, Set<String>> = if (savedSelections.isNotEmpty()) {
                buildMap {
                    allBundles.forEach { bundle ->
                        val seenForBundle = withContext(Dispatchers.IO) {
                            patchSelectionRepository.getSeenPatches(configurationKey, bundle.uid)
                        }
                        // No snapshot yet → first time opening expert mode for this package,
                        // nothing to flag as new.
                        val seen = seenForBundle ?: return@forEach
                        val currentPatchNames = bundle.patches.map { it.name }.toSet()
                        val newForBundle = currentPatchNames - seen
                        if (newForBundle.isNotEmpty()) put(bundle.uid, newForBundle)
                    }
                }
            } else {
                emptyMap()
            }

            // Validate options
            val validatedOptions = validatePatchOptions(savedOptions, bundlesMap)

            // Save validated options if anything changed
            if (validatedOptions != savedOptions) {
                withContext(Dispatchers.IO) {
                    optionsRepository.saveOptions(configurationKey, validatedOptions)
                }
            }

            expertModeSelectedApp = selectedApp
            expertModeBundles = allBundles
            expertModeAllowIncompatible = allowIncompatible
            patches.toMutableMap().also { expertModePatches = it; expertModeInitialPatches = it }
            expertModeOptions = validatedOptions.toMutableMap()
            expertModeNewPatches = newPatchesMap
            showExpertModeDialog = true
        } else {
            // Simple Mode: collect patches from all enabled bundles, then either use the sole result directly
            // or ask the user to pick one if multiple bundles have applicable patches.
            // A patch is applicable if:
            //   - compatiblePackages == null (universal), OR
            //   - compatiblePackages contains this packageName
            val bundleWithPatches = allBundles
                .filter { it.enabled }
                .map { bundle ->
                    val patchNames = bundle.patchSequence(allowIncompatible)
                        .filter { it.defaultSelected(currentInstallerType, currentApkArchitecture) }
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
                val currentBundleUids = availableBundles.map { it.uid }.toSet()

                val savedSelections = withContext(Dispatchers.IO) {
                    patchSelectionRepository.getAllSelectionsForPackage(configurationKey)
                        .filterKeys { it in currentBundleUids }
                }
                val savedOptions = withContext(Dispatchers.IO) {
                    optionsRepository.getAllOptionsForPackage(configurationKey, bundlesMap)
                        .filterKeys { it in currentBundleUids }
                }

                expertModeSelectedApp = selectedApp
                expertModeBundles = allBundles
                expertModeAllowIncompatible = allowIncompatible
                savedSelections.applyInstallerRules().toMutableMap()
                    .also { expertModePatches = it; expertModeInitialPatches = it }
                expertModeOptions = savedOptions.toMutableMap()
                showExpertModeDialog = true
                return
            }

            // If the user pre-selected a bundle via SimpleBundleSelectDialog, use it directly.
            // Use allowIncompatible=true unconditionally: the user explicitly picked this bundle
            // AND explicitly picked the APK version, so we must respect both choices regardless
            // of whether the version is in the bundle's supported list
            val preSelectedUid = pendingSelectedBundleUid
            if (preSelectedUid != null) {
                pendingSelectedBundleUid = null
                val bundle = allBundles.find { it.uid == preSelectedUid }
                if (bundle != null) {
                    val patchNames = bundle.patchSequence(allowIncompatible = true)
                        .filter { it.defaultSelected(currentInstallerType, currentApkArchitecture) }
                        .mapTo(mutableSetOf()) { it.name }
                    if (patchNames.isNotEmpty()) {
                        val patches = mapOf(bundle.uid to patchNames).applyInstallerRules()
                        proceedWithPatching(selectedApp, patches, emptyMap())
                        return
                    }
                }
                // Pre-selected bundle has no patches at all - fall through
            }

            // Simple mode: if more than one bundle has applicable patches, ask the user which single bundle to use
            if (bundleWithPatches.size > 1) {
                simpleBundleSelectApp = selectedApp
                simpleBundleSelectCandidates = bundleWithPatches
                showSimpleBundleSelectDialog = true
                return
            }

            // Only one bundle has patches - use it directly (no prompt needed)
            val patches = bundleWithPatches
                .associate { (bundle, patches) -> bundle.uid to patches }
                .applyInstallerRules()

            proceedWithPatching(selectedApp, patches, emptyMap())
        }
    }

    /**
     * The package the pending run reads its saved patches and options from.
     *
     * Patches belong to the install they were applied to, so rebuilding one reads what that
     * install was built with. Anything else reads the app itself: a run producing a separate
     * install starts from what the app was last patched with, and so does an install that
     * predates configurations of their own.
     */
    private suspend fun configurationKeyFor(originalPackageName: String): String {
        val repatched = pendingRepatchPackageName
        val hasOwnConfiguration = repatched != null &&
                repatched != originalPackageName &&
                withContext(Dispatchers.IO) {
                    patchSelectionRepository.getAllSelectionsForPackage(repatched).isNotEmpty()
                }

        return configurationKey(
            originalPackageName = originalPackageName,
            repatchedPackageName = repatched,
            repatchedHasOwnConfiguration = hasOwnConfiguration
        )
    }

    /**
     * Save options to repository.
     */
    fun saveOptions(packageName: String, options: Options) {
        viewModelScope.launch(Dispatchers.IO) {
            optionsRepository.saveOptions(packageName , options)
        }
    }

    /**
     * Proceed with patching.
     */
    fun proceedWithPatching(
        selectedApp: SelectedApp,
        patches: PatchSelection,
        options: Options
    ) {
        // Dismiss InstalledAppInfoDialog here, right before navigating to PatcherScreen.
        // This ensures there is never a gap between the info dialog closing and the next screen appearing
        dismissInstalledAppInfo()

        onStartQuickPatch?.invoke(
            QuickPatchParams(
                selectedApp = selectedApp,
                patches = patches,
                options = options,
                // Handed over before the state below is cleared, since the run has no other way
                // to learn which install it was started for
                targetPackageName = pendingRepatchPackageName
            )
        )

        // Clean only UI state
        pendingPackageName = null
        pendingRepatchPackageName = null
        pendingAppName = null
        pendingRecommendedVersion = null
        pendingCompatibleVersions = emptyList()
        pendingSelectedDownloadVersion = null
        pendingSelectedBundleUid = null
        resolvedDownloadUrl = null
        showDownloadInstructionsDialog = false
        showFilePickerPromptDialog = false
    }

    /**
     * All patches per bundle with their enabled state, sorted for display.
     * Recomputed whenever [expertModeBundles] or [expertModePatches] change.
     * Each bundle entry contains (PatchInfo, isEnabled) pairs sorted alphabetically -
     * the final per-section ordering (new patches first) is applied in the UI layer.
     */
    val expertModeAllPatchesInfo: List<Pair<PatchBundleInfo.Scoped, List<Pair<PatchInfo, Boolean>>>>
        get() = expertModeBundles.map { bundle ->
            val selected = expertModePatches[bundle.uid] ?: emptySet()
            val patches = bundle.patchSequence(true)
                .map { patch -> patch to (patch.name in selected) }
                .sortedBy { (patch, _) -> patch.name }
                .toList()
            bundle to patches
        }.filter { it.second.isNotEmpty() }
            .sortedByDescending { (bundle, _) -> bundle.compatible.size }

    /**
     * The selection as the run would use it: what the dialog shows, minus what it holds back.
     *
     * A hidden source keeps its patches in [expertModePatches] so that offering it back returns
     * the selection made from it rather than an empty tab. Everything that speaks for the run -
     * the counter, the multiple-sources warning, the patches handed to the patcher - reads this
     * instead, or it would answer for a source the run never reaches.
     */
    private val expertModeRunSelection: PatchSelection
        get() {
            if (expertModeMutedSources.isEmpty()) return expertModePatches
            val shown = expertModeBundles.mapTo(mutableSetOf()) { it.uid }
            return expertModePatches.filterKeys { it in shown }
        }

    /** Total number of currently selected patches across all bundles. */
    val expertModeTotalSelectedCount: Int
        get() = expertModeRunSelection.values.sumOf { it.size }

    /** True when patches from more than one bundle are selected (triggers warning on proceed). */
    val expertModeHasMultipleBundles: Boolean
        get() = expertModeRunSelection.spansMultipleBundles()

    /**
     * Toggle patch in expert mode.
     * Supports adding patches from bundles not yet in the selection.
     */
    fun togglePatchInExpertMode(bundleUid: Int, patchName: String) {
        // Locked patches are toggled only through availability rules; no-op here
        val patch = expertModeBundles
            .firstOrNull { it.uid == bundleUid }
            ?.patches
            ?.firstOrNull { it.name == patchName }
        val selected = patchName in expertModePatches[bundleUid].orEmpty()
        if (patch != null && expertModeLockState(patch).blocksToggle(selected)) return

        expertModePatches = expertModePatches.togglePatch(bundleUid, patchName)
            .applyExpertModeAvailability()
    }

    /**
     * Select all given patches for a bundle.
     * Only adds patches that are not already selected. LOCKED_OFF patches are skipped.
     *
     * Universal patches are staged behind the regular ones and need a second call, see
     * [PatchSelectionUtils.bulkEnablePatches]. [patches] is the list the dialog currently
     * shows, so an active search narrows the scope of both stages.
     */
    fun expertModeSelectAll(bundleUid: Int, patches: List<Pair<PatchInfo, Boolean>>) {
        val selected = expertModePatches[bundleUid].orEmpty()
        val updated = bulkEnablePatches(
            patches,
            selected,
            expertModeUniversalArmed(bundleUid, selected),
            ::expertModeLockState
        )

        expertModePatches = expertModePatches.withBundle(bundleUid, updated)
            .applyExpertModeAvailability()
        // Armed against what the availability rules left behind, so the next tap sees the
        // selection it is compared to
        expertModeUniversalArmedFor = bundleUid to expertModePatches[bundleUid].orEmpty()
    }

    /** True when the next [expertModeSelectAll] holds universal patches back for another tap. */
    fun expertModeSelectAllHoldsUniversal(bundleUid: Int, patches: List<Pair<PatchInfo, Boolean>>): Boolean {
        val selected = expertModePatches[bundleUid].orEmpty()
        return bulkEnableHoldsUniversal(
            patches,
            expertModeUniversalArmed(bundleUid, selected),
            ::expertModeLockState
        )
    }

    /**
     * Lock state of [patch] for the install target the dialog is configuring.
     *
     * A REQUIRED patch only locks while the selection stays within one bundle, see
     * [PatchSelectionUtils.applyAvailability].
     */
    fun expertModeLockState(patch: PatchInfo) =
        patch.lockState(currentInstallerType, currentApkArchitecture, !expertModeHasMultipleBundles)

    private fun expertModeUniversalArmed(bundleUid: Int, selected: Set<String>) =
        expertModeUniversalArmedFor == (bundleUid to selected)

    /**
     * Deselect all given patches for a bundle.
     * Removes the bundle entry entirely if nothing remains selected. LOCKED_ON patches are kept.
     */
    fun expertModeDeselectAll(bundleUid: Int, patches: List<Pair<PatchInfo, Boolean>>) {
        val kept = expertModePatches[bundleUid]?.toMutableSet() ?: mutableSetOf()
        patches.forEach { (patch, enabled) ->
            if (expertModeLockState(patch) == PatchLockState.LOCKED_ON) return@forEach
            if (enabled) kept.remove(patch.name)
        }
        expertModePatches = expertModePatches.withBundle(bundleUid, kept)
            .applyExpertModeAvailability()
    }

    /**
     * Reset a bundle's selection to the default patches for the current install target.
     *
     * Defaults are read from the bundle itself rather than the dialog's list, so they cover the
     * complete set instead of just search results and reach only the patches the run would have
     * started with: the dialog lists patches declaring other app versions so they can be enabled
     * by hand, but they are no more part of the defaults here than they were on first open.
     */
    fun expertModeResetToDefault(bundleUid: Int) {
        val bundle = expertModeBundles.firstOrNull { it.uid == bundleUid } ?: return
        val defaults = bundle.patchSequence(expertModeAllowIncompatible)
            .filter { it.defaultSelected(currentInstallerType, currentApkArchitecture) }
            .mapTo(mutableSetOf()) { it.name }
        expertModePatches = expertModePatches.withBundle(bundleUid, defaults)
            .applyExpertModeAvailability()
    }

    /**
     * Restores the DB-persisted patch selection for a bundle in expert mode.
     * No-op if there is no saved selection for the given bundle.
     */
    fun expertModeRestoreSaved(bundleUid: Int) {
        val savedForBundle = expertModeInitialPatches[bundleUid] ?: return
        expertModePatches = expertModePatches.withBundle(bundleUid, savedForBundle)
            .applyExpertModeAvailability()
    }

    /**
     * Update option in expert mode.
     */
    fun updateOptionInExpertMode(
        bundleUid: Int,
        patchName: String,
        optionKey: String,
        value: Any?
    ) {
        expertModeOptions = expertModeOptions.updateOption(bundleUid, patchName, optionKey, value)
    }

    /**
     * Reset options for a patch in expert mode.
     */
    fun resetOptionsInExpertMode(bundleUid: Int, patchName: String) {
        expertModeOptions = expertModeOptions.resetOptionsForPatch(bundleUid, patchName)
    }

    /**
     * Brings the sources this app is kept from back into the open dialog, for this run only.
     *
     * Nothing is written: the app stays kept from them, and the next run hides them again. Which
     * sources an app is patched from is a standing decision made where the apps are, and a look at
     * what was left out is not the place to overturn it by accident. The patches revealed here do
     * take part in this run, since the dialog counts them and the user can edit them.
     */
    fun revealHiddenExpertModeSources() {
        expertModeSelectedApp ?: return

        expertModeMutedSources = emptySet()
        applyExpertModeMutes()
    }

    private fun applyExpertModeMutes() {
        expertModeBundles = expertModeAllBundles.withoutMutedSources(expertModeMutedSources) { it.uid }
    }

    /**
     * Clean up expert mode data.
     */
    fun cleanupExpertModeData() {
        showExpertModeDialog = false
        expertModeSelectedApp = null
        expertModeBundles = emptyList()
        expertModeAllBundles = emptyList()
        expertModeMutedSources = emptySet()
        expertModePatches = emptyMap()
        expertModeInitialPatches = emptyMap()
        expertModeOptions = emptyMap()
        expertModeNewPatches = emptyMap()
        expertModeUniversalArmedFor = null
        expertModeAllowIncompatible = false
        expertModeUnreadablePaths = emptyList()
        expertModeCopy.close()
    }

    /** Opens the copy-from-another-bundle picker for [targetBundleUid]. */
    fun openExpertModeCopyDialog(targetBundleUid: Int) {
        val selectedApp = expertModeSelectedApp ?: return
        expertModeCopy.open(
            scope = viewModelScope,
            targetPackageName = selectedApp.packageName,
            targetBundleUid = targetBundleUid,
            targetPatchNames = targetBundlePatchNames(targetBundleUid)
        )
    }

    /**
     * Applies a picked [candidate] to the in-memory expert-mode selection. Changes reach the
     * database only when the user proceeds to patching.
     */
    fun applyExpertModeCopy(candidate: CopySelectionCandidate) {
        expertModeSelectedApp ?: return
        val targetBundleUid = expertModeCopy.targetBundleUid ?: return

        viewModelScope.launch {
            val copied = expertModeCopy.resolve(
                candidate = candidate,
                targetPatches = targetBundlePatchInfos(targetBundleUid)
            ) ?: return@launch

            // The copy comes from a run that may have targeted another installer
            expertModePatches = expertModePatches.withBundle(targetBundleUid, copied.patches)
                .applyExpertModeAvailability()
            expertModeOptions = expertModeOptions.mergeBundleOptions(targetBundleUid, copied.options)

            expertModeCopy.finish(copied.patches.size)
        }
    }

    /**
     * Availability rules of the current install target, scoped to the bundles the dialog shows.
     *
     * A source being held back takes no part in the rules and is put back untouched afterward.
     * Whether a REQUIRED patch locks turns on the run drawing from one source or several, and a
     * source the run cannot reach is not one of them.
     */
    private fun PatchSelection.applyExpertModeAvailability(): PatchSelection {
        val shown = expertModeBundles.mapTo(mutableSetOf()) { it.uid }
        val heldBack = filterKeys { it !in shown }

        return filterKeys { it in shown }
            .applyAvailability(
                currentInstallerType,
                currentApkArchitecture,
                expertModeBundles.associate { it.uid to it.patches.associateBy { patch -> patch.name } }
            ) + heldBack
    }

    private fun targetBundlePatchNames(bundleUid: Int): Set<String> =
        expertModeBundles.firstOrNull { it.uid == bundleUid }
            ?.patches
            ?.mapTo(mutableSetOf()) { it.name }
            ?: emptySet()

    private fun targetBundlePatchInfos(bundleUid: Int): Map<String, PatchInfo> =
        expertModeBundles.firstOrNull { it.uid == bundleUid }
            ?.patches
            ?.associateBy { it.name }
            ?: emptyMap()

    private suspend fun saveSeenPatchesForBundles(packageName: String) {
        expertModeBundles.forEach { bundle ->
            patchSelectionRepository.saveSeenPatches(
                packageName = packageName,
                bundleUid = bundle.uid,
                patchNames = bundle.patches.map { it.name }.toSet()
            )
        }
    }

    /**
     * Called when the user confirms the ExpertModeDialog. Persists the final selection
     * and options, then navigates to the patcher.
     */
    fun proceedExpertMode() {
        val selectedApp = expertModeSelectedApp ?: return
        // Only the bundles offered by the dialog are covered by this selection, so bundles that
        // were disabled at patch time keep whatever the user saved for them earlier.
        val bundleScope = expertModeBundles.mapTo(mutableSetOf()) { it.uid }
        val finalPatches = expertModeRunSelection
        val finalOptions = expertModeOptions
        // Strip UI-only empty strings (fields cleared via ✕) so the patcher engine
        // receives null / no key for those options and falls back to its own default,
        // rather than receiving a literal empty string.
        val patcherOptions = finalOptions.sanitizeForPatcher()

        viewModelScope.launch {
            // A path that leads nowhere fails the run, and by then this dialog is gone and the
            // option it belongs to is out of reach. So it is asked about while the selection is
            // still open and the value can be fixed or dropped on the spot
            val failures = withContext(Dispatchers.IO) {
                validateOptionPaths(patcherOptions.restrictTo(finalPatches))
            }

            if (failures.isNotEmpty()) {
                expertModeUnreadablePaths = failures
                return@launch
            }

            showExpertModeDialog = false

            withContext(Dispatchers.IO) {
                // Saved before patching runs so a long selection survives a failed run. It lands
                // on the install being rebuilt, and follows it from there if patching renames it
                val configurationKey = configurationKeyFor(selectedApp.packageName)
                patchSelectionRepository.updateSelection(
                    packageName = configurationKey,
                    selection = finalPatches,
                    scope = bundleScope
                )
                saveOptions(configurationKey, finalOptions)
                // Snapshot all bundle patch names so next open can detect genuinely new patches.
                saveSeenPatchesForBundles(configurationKey)
            }

            proceedWithPatching(selectedApp, finalPatches, patcherOptions)
            cleanupExpertModeData()
        }
    }

    /**
     * Option paths of the expert selection that cannot be read, raised over the dialog rather
     * than left for the run to fail on. Empty while there is nothing to answer.
     */
    var expertModeUnreadablePaths by mutableStateOf<List<PathValidationResult>>(emptyList())
        private set

    /** Leaves the values alone and puts the user back in the selection to deal with them. */
    fun dismissExpertModeUnreadablePaths() {
        expertModeUnreadablePaths = emptyList()
    }

    /**
     * Drops the options behind the unreadable paths and goes on with the run, which then uses
     * the defaults the patches declare. The selection is saved without them as well.
     */
    fun clearExpertModeUnreadablePaths() {
        val failures = expertModeUnreadablePaths
        expertModeUnreadablePaths = emptyList()
        expertModeOptions = expertModeOptions.withoutFailingPaths(failures)

        proceedExpertMode()
    }

    /**
     * Resolve download redirect.
     */
    fun resolveDownloadRedirect() {
        val packageName = pendingPackageName ?: return
        // Use the version selected by the user in Dialog 1; fall back to recommended
        val version = (pendingSelectedDownloadVersion ?: pendingRecommendedVersion)?.version

        // Marks the destination as unknown, which is what the dialog waits on before it can
        // say anything about the download or let the user leave for it
        resolvedDownloadUrl = downloadUrlResolver.apiSearchUrl(packageName, version)

        viewModelScope.launch {
            val resolved = withContext(Dispatchers.IO) {
                downloadUrlResolver.resolve(packageName, version)
            }
            resolvedDownloadUrl = resolved
        }
    }

    override val helperSignatureCheckAvailable: Boolean
        get() = pendingPackageName?.let { helperSignatureCheckAvailable(bundleAppMetadataFlow.value[it]) } == true

    /**
     * Build the request for an APK download helper, describing the original APK of the pending app.
     */
    override fun createApkDownloadHelperIntent(component: ComponentName): Intent? {
        val packageName = pendingPackageName ?: return null
        // Use the version selected by the user in Dialog 1; fall back to recommended
        val requestedVersion = (pendingSelectedDownloadVersion ?: pendingRecommendedVersion)?.version

        return createApkDownloadHelperRequest(
            component = component,
            callerPackage = app.packageName,
            packageName = packageName,
            appName = pendingAppName ?: KnownApps.getAppName(packageName),
            versionName = requestedVersion,
            compatible = pendingCompatibleVersions,
            metadata = bundleAppMetadataFlow.value[packageName],
            stockInstallRequired = usingMountInstall && pendingStockAppInstalled != true,
            fallbackWebUrl = downloadUrlResolver.webSearchUrl(packageName, requestedVersion)
        )
    }

    /**
     * The download dialogs are closed before the APK is taken on, because processing it puts up
     * an overlay of its own and two of them on screen at once reads as a stuck flow.
     */
    override fun onHelperApkReceived(uri: Uri) {
        showDownloadInstructionsDialog = false
        showFilePickerPromptDialog = false
        handleApkSelection(uri)
    }

    override fun onHelperInstalledAppChosen(packageName: String) {
        showDownloadInstructionsDialog = false
        showFilePickerPromptDialog = false
        handleHelperInstalledAppSelection(packageName)
    }

    /**
     * Handle download instructions continue.
     */
    fun handleDownloadInstructionsContinue(handOff: (String) -> Boolean) {
        val urlToOpen = resolvedDownloadUrl!!

        if (handOff(urlToOpen)) {
            showDownloadInstructionsDialog = false
            showFilePickerPromptDialog = true
        } else {
            Log.w(tag, "Failed to open URL")
            app.toast(app.getString(R.string.sources_management_failed_to_open_url))
            showDownloadInstructionsDialog = false
            cleanupPendingData()
        }
    }

    /**
     * Clean up pending data.
     */
    fun cleanupPendingData(keepSelectedApp: Boolean = false, keepBundleUid: Boolean = false) {
        pendingPackageName = null
        pendingAppName = null
        pendingRecommendedVersion = null
        pendingCompatibleVersions = emptyList()
        pendingSelectedDownloadVersion = null
        if (!keepBundleUid) pendingSelectedBundleUid = null
        resolvedDownloadUrl = null
        pendingSavedApkInfo = null
        pendingInstalledApkInfo = null
        pendingInstalledAppVersion = null
        pendingStockAppInstalled = null
        if (!keepSelectedApp) {
            pendingSelectedApp?.let { app ->
                if (app is SelectedApp.Local && app.temporary) {
                    app.file.delete()
                }
            }
            pendingSelectedApp = null
            // Kept while the app is, because the dialogs that pause the flow resume into the
            // same run, and it must still know which install it was aimed at
            pendingRepatchPackageName = null
        }
        showApkAvailabilityDialog = false
        showDownloadInstructionsDialog = false
        showFilePickerPromptDialog = false
        showInstalledAppPickerDialog = false
    }

    /**
     * Clean up any pending temporary APK when the ViewModel is destroyed.
     * This handles the edge case where the user navigates away or the system destroys
     * the ViewModel while a temporary APK file is still held in pendingSelectedApp.
     */
    override fun onCleared() {
        apps.close()
        val pending = pendingSelectedApp
        if (pending is SelectedApp.Local && pending.temporary) {
            pending.file.delete()
        }
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
    private suspend fun loadLocalApk(
        context: Context,
        uri: Uri
    ): ApkLoadResult = withContext(Dispatchers.IO) {
        try {
            // Copy file to uiTempDir with original extension detection
            val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) cursor.getString(nameIndex) else null
            } ?: "temp_${System.currentTimeMillis()}"

            val extension = fileName.substringAfterLast('.', "apk").lowercase()
            val tempFile = filesystem.uiTempDir.resolve("temp_apk_${System.currentTimeMillis()}.$extension")

            // openInputStream can return null when the provider is unavailable
            // e.g. Samsung External Storage restricted by Battery Optimization
            val bytesCopied = context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            if (bytesCopied == null || bytesCopied == 0L) {
                tempFile.delete()
                return@withContext ApkLoadResult.Unreadable
            }

            // A split archive is read through its base module, deleted after the call, so the icon is read in it
            val (packageInfo, icon, patched) = SplitApkInspector.withRepresentativeApk(
                source = tempFile,
                workspace = filesystem.uiTempDir
            ) { apk ->
                val info = pm.getPackageInfo(apk)
                Triple(
                    info,
                    info?.let(appDataResolver::detachedArchiveIcon),
                    info != null && localApkSources.isPatchedApk(apk, info.packageName)
                )
            }

            if (packageInfo == null) {
                Log.w(tag, "Picked file $fileName could not be parsed as an APK")
                tempFile.delete()
                return@withContext ApkLoadResult.NotAnApk
            }

            if (patched) {
                tempFile.delete()
                return@withContext ApkLoadResult.AlreadyPatched
            }

            ApkLoadResult.Success(
                SelectedApp.Local(
                    packageName = packageInfo.packageName,
                    version = packageInfo.versionName ?: "unknown",
                    versionCode = pm.getVersionCode(packageInfo),
                    file = tempFile,
                    temporary = true
                ),
                icon
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to load APK", e)
            ApkLoadResult.IoError
        }
    }
}

/** Result of attempting to load a local APK file. */
private sealed interface ApkLoadResult {
    /** File was read and parsed successfully, with the app's icon where it carries one. */
    data class Success(val app: SelectedApp.Local, val icon: Drawable?) : ApkLoadResult
    /** File could not be read - provider returned null stream or zero bytes. */
    data object Unreadable : ApkLoadResult
    /** File was read but is not a valid APK/split archive. */
    data object NotAnApk : ApkLoadResult
    /** File is a patched build, which is never patched again. */
    data object AlreadyPatched : ApkLoadResult
    /** An unexpected IO or system exception occurred while copying or parsing. */
    data object IoError : ApkLoadResult
}
