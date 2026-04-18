package app.morphe.manager.ui.viewmodel

import android.app.Application
import android.content.pm.PackageInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.R
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.domain.installer.AckpineInstaller
import app.morphe.manager.domain.installer.InstallerManager
import app.morphe.manager.domain.installer.RootInstaller
import app.morphe.manager.domain.installer.UninstallCancelledException
import app.morphe.manager.domain.repository.*
import app.morphe.manager.ui.screen.home.AppliedPatchBundleUi
import app.morphe.manager.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

class InstalledAppInfoViewModel(
    packageName: String
) : ViewModel(), KoinComponent {

    private val context: Application by inject()
    private val pm: PM by inject()
    private val installedAppRepository: InstalledAppRepository by inject()
    private val patchBundleRepository: PatchBundleRepository by inject()
    private val rootInstaller: RootInstaller by inject()
    private val ackpineInstaller: AckpineInstaller by inject()
    private val installerManager: InstallerManager by inject()
    private val originalApkRepository: OriginalApkRepository by inject()
    private val filesystem: Filesystem by inject()

    lateinit var onBackClick: () -> Unit

    var installedApp: InstalledApp? by mutableStateOf(null)
        private set
    var appInfo: PackageInfo? by mutableStateOf(null)
        private set

    private val _appliedPatches = MutableStateFlow<PatchSelection?>(null)
    var appliedPatches: PatchSelection?
        get() = _appliedPatches.value
        set(value) { _appliedPatches.value = value }
    var isMounted by mutableStateOf(false)
        private set
    var isInstalledOnDevice by mutableStateOf(false)
        private set
    var hasSavedCopy by mutableStateOf(false)
        private set
    var hasOriginalApk by mutableStateOf(false)
        private set
    var isAppDeleted by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(true)
        private set

    val primaryInstallerIsMount: Boolean
        get() = installerManager.getPrimaryToken() == InstallerManager.Token.AutoSaved

    init {
        viewModelScope.launch {
            // Use Flow to automatically update when app data changes in database
            installedAppRepository.getAsFlow(packageName).collect { app ->
                installedApp = app

                if (app != null) {
                    // Run all checks in parallel
                    val deferredMounted = async { rootInstaller.isDeviceRooted() && rootInstaller.isAppMounted(app.currentPackageName) }
                    val deferredOriginalApk = async { originalApkRepository.get(app.originalPackageName) != null }
                    val deferredAppState = async { refreshAppState(app) }
                    val deferredPatches = async { resolveAppliedSelection(app) }

                    // Wait for all to complete
                    isMounted = deferredMounted.await()
                    hasOriginalApk = deferredOriginalApk.await()
                    deferredAppState.await()
                    appliedPatches = deferredPatches.await()
                }

                // Mark as loaded
                isLoading = false
            }
        }
    }

    private suspend fun resolveAppliedSelection(app: InstalledApp) = withContext(Dispatchers.IO) {
        val selection = installedAppRepository.getAppliedPatches(app.currentPackageName)
        if (selection.isNotEmpty()) return@withContext selection
        val payload = app.selectionPayload ?: return@withContext emptyMap()
        val sources = patchBundleRepository.sources.first()
        val sourceIds = sources.map { it.uid }.toSet()
        val (remappedPayload, remappedSelection) = payload.remapAndExtractSelection(sources)
        val persistableSelection = remappedSelection.filterKeys { it in sourceIds }
        if (persistableSelection.isNotEmpty()) {
            installedAppRepository.addOrUpdate(
                app.currentPackageName,
                app.originalPackageName,
                app.version,
                app.installType,
                persistableSelection,
                remappedPayload,
                app.patchedAt
            )
        }
        if (remappedSelection.isNotEmpty()) return@withContext remappedSelection

        // Fallback: convert payload directly to selection
        payload.toPatchSelection()
    }

    fun launch() {
        val app = installedApp ?: return
        if (app.installType == InstallType.SAVED) {
            context.toast(context.getString(R.string.saved_app_launch_unavailable))
        } else {
            pm.launch(app.currentPackageName)
        }
    }

    fun uninstall() {
        val app = installedApp ?: return
        when (app.installType) {
            InstallType.DEFAULT, InstallType.CUSTOM, InstallType.SHIZUKU, InstallType.SAVED -> {
                viewModelScope.launch {
                    try {
                        ackpineInstaller.uninstall(app.currentPackageName)
                        // Ackpine suspends until confirmed — refresh state after success
                        refreshCurrentAppState()
                    } catch (_: UninstallCancelledException) {
                        // User dismissed dialog — do nothing
                    } catch (e: Exception) {
                        context.toast(context.getString(R.string.install_app_fail, e.simpleMessage()))
                    }
                }
            }

            InstallType.MOUNT -> viewModelScope.launch {
                rootInstaller.uninstall(app.currentPackageName)
                // Delete record and APK but preserve selection and options
                deleteRecordAndApk(app)
                onBackClick()
            }
        }
    }

    /**
     * Remove app completely: database record, patched APK and original APK.
     * Patch selection and options are preserved for future patching.
     */
    fun removeAppCompletely() = viewModelScope.launch {
        val app = installedApp ?: return@launch
        deleteRecordAndApk(app)

        // Also delete original APK if it exists
        withContext(Dispatchers.IO) {
            originalApkRepository.get(app.originalPackageName)?.let { originalApk ->
                originalApkRepository.delete(originalApk)
            }
        }

        installedApp = null
        appInfo = null
        appliedPatches = null
        isInstalledOnDevice = false
        context.toast(context.getString(R.string.saved_app_removed_toast))
        onBackClick()
    }

    /**
     * Delete database record and patched APK file.
     * Note: Patch selection and options are NOT deleted - they remain for future patching.
     */
    private suspend fun deleteRecordAndApk(app: InstalledApp) {
        // Delete database record
        installedAppRepository.delete(app)

        // Delete patched APK file
        withContext(Dispatchers.IO) {
            savedApkFile(app)?.delete()
        }
        hasSavedCopy = false
    }

    fun updateInstallType(packageName: String, newInstallType: InstallType) = viewModelScope.launch {
        val app = installedApp ?: return@launch
        // Update in database
        withContext(Dispatchers.IO) {
            installedAppRepository.addOrUpdate(
                packageName,
                app.originalPackageName,
                app.version,
                newInstallType,
                appliedPatches ?: emptyMap(),
                app.selectionPayload,
                app.patchedAt
            )
        }
        // Refresh app state to update UI
        refreshAppState(app.copy(installType = newInstallType, currentPackageName = packageName))
    }

    fun savedApkFile(app: InstalledApp? = this.installedApp): File? {
        val target = app ?: return null
        val candidates = listOf(
            filesystem.getPatchedAppFile(target.currentPackageName, target.version),
            filesystem.getPatchedAppFile(target.originalPackageName, target.version)
        ).distinct()
        return candidates.firstOrNull { it.exists() }
    }

    private suspend fun refreshAppState(app: InstalledApp) {
        val installedInfo = withContext(Dispatchers.IO) {
            pm.getPackageInfo(app.currentPackageName)
        }
        hasSavedCopy = withContext(Dispatchers.IO) { savedApkFile(app) != null }

        if (installedInfo != null) {
            isInstalledOnDevice = true
            isAppDeleted = false
            appInfo = installedInfo
        } else {
            isInstalledOnDevice = false
            // App is deleted if it was installed on device but now missing
            isAppDeleted = pm.isAppDeleted(
                packageName = app.currentPackageName,
                hasSavedCopy = hasSavedCopy,
                wasInstalledOnDevice = app.installType != InstallType.SAVED
            )
            appInfo = withContext(Dispatchers.IO) {
                savedApkFile(app)?.let(pm::getPackageInfo)
            }
        }

        // Update mounted state
        isMounted = rootInstaller.isDeviceRooted() && rootInstaller.isAppMounted(app.currentPackageName)
    }

    /** Manually refresh app state (e.g., after app installation/uninstallation) */
    fun refreshCurrentAppState() {
        val app = installedApp ?: return
        viewModelScope.launch {
            refreshAppState(app)
        }
    }

    /** Count of all patches across all enabled bundles. */
    val availablePatches: StateFlow<Int> = patchBundleRepository.bundleInfoFlow
        .map { it.values.sumOf { bundle -> bundle.patches.size } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /**
     * UI model for each bundle that was used to patch the current app.
     * Combines applied patches, bundle metadata and stored bundle versions from DB.
     * Recomputed reactively when any input changes.
     */
    val appliedBundles: StateFlow<List<AppliedPatchBundleUi>> =
        combine(
            installedAppRepository.getAsFlow(packageName).filterNotNull(),
            patchBundleRepository.allBundlesInfoFlow,
            patchBundleRepository.sources,
            _appliedPatches
        ) { app, bundleInfo, sources, patches ->
            if (patches.isNullOrEmpty()) return@combine emptyList()

            val storedVersions = withContext(Dispatchers.IO) {
                installedAppRepository.getBundleVersionsForApp(app.currentPackageName)
            }

            patches.entries.mapNotNull { (bundleUid, bundlePatches) ->
                if (bundlePatches.isEmpty()) return@mapNotNull null
                val info = bundleInfo[bundleUid]
                val source = sources.firstOrNull { it.uid == bundleUid }
                val fallbackName = if (bundleUid == 0) {
                    context.getString(R.string.home_app_info_patches_name_default)
                } else {
                    context.getString(R.string.home_app_info_patches_name_fallback)
                }
                val title = source?.displayTitle ?: info?.name ?: "$fallbackName (#$bundleUid)"
                val version = storedVersions[bundleUid] ?: info?.version
                val patchInfos = info?.patches
                    ?.filter { it.name in bundlePatches }
                    ?.distinctBy { it.name }
                    ?.sortedBy { it.name }
                    ?: emptyList()
                val missingNames = bundlePatches.toList().sorted()
                    .filterNot { name -> patchInfos.any { it.name == name } }
                    .distinct()
                AppliedPatchBundleUi(
                    uid = bundleUid,
                    title = title,
                    version = version,
                    patchInfos = patchInfos,
                    fallbackNames = missingNames,
                    bundleAvailable = info != null
                )
            }.sortedBy { it.title }
        }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Human-readable summary of applied bundles with versions for display in the dialog. */
    val bundlesUsedSummary: StateFlow<String> = appliedBundles
        .map { bundles ->
            bundles.joinToString("\n") { bundle ->
                val version = bundle.version?.takeIf { it.isNotBlank() }
                if (version != null) "${bundle.title} ($version)" else bundle.title
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, "")
}
