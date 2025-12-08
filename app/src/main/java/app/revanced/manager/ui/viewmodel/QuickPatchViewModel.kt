package app.revanced.manager.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.R
import app.revanced.manager.data.platform.Filesystem
import app.revanced.manager.data.room.apps.downloaded.DownloadedApp
import app.revanced.manager.data.room.apps.installed.InstalledApp
import app.revanced.manager.domain.installer.RootInstaller
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.domain.repository.DownloadedAppRepository
import app.revanced.manager.domain.repository.DownloaderPluginRepository
import app.revanced.manager.domain.repository.InstalledAppRepository
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.domain.repository.PatchOptionsRepository
import app.revanced.manager.network.downloader.LoadedDownloaderPlugin
import app.revanced.manager.network.downloader.ParceledDownloaderData
import app.revanced.manager.patcher.patch.PatchBundleInfo.Extensions.toPatchSelection
import app.revanced.manager.plugin.downloader.GetScope
import app.revanced.manager.plugin.downloader.PluginHostApi
import app.revanced.manager.plugin.downloader.UserInteractionException
import app.revanced.manager.ui.model.SelectedApp
import app.revanced.manager.util.Options
import app.revanced.manager.util.PM
import app.revanced.manager.util.PatchSelection
import app.revanced.manager.util.simpleMessage
import app.revanced.manager.util.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@OptIn(PluginHostApi::class)
class QuickPatchViewModel(
    private val packageName: String
) : ViewModel(), KoinComponent {
    private val app: Application = get()
    val bundleRepository: PatchBundleRepository = get()
    private val optionsRepository: PatchOptionsRepository = get()
    private val pluginsRepository: DownloaderPluginRepository = get()
    private val downloadedAppRepository: DownloadedAppRepository = get()
    private val installedAppRepository: InstalledAppRepository = get()
    private val rootInstaller: RootInstaller = get()
    private val pm: PM = get()
    private val filesystem: Filesystem = get()
    val prefs: PreferencesManager = get()

    val plugins = pluginsRepository.loadedPluginsFlow
    val hasRoot = rootInstaller.hasRootAccess()

    private val storageInputFile = File(filesystem.uiTempDir, "quick_patch_input.apk").apply { delete() }
    private val storageSelectionChannel = Channel<Unit>(Channel.CONFLATED)
    val requestStorageSelection = storageSelectionChannel.receiveAsFlow()

    var installedAppData: Pair<SelectedApp.Installed, InstalledApp?>? by mutableStateOf(null)
        private set

    var downloadedApps: List<DownloadedApp> by mutableStateOf(emptyList())
        private set

    var suggestedVersion: String? by mutableStateOf(null)
        private set

    var showUnsupportedVersionDialog by mutableStateOf<UnsupportedVersionDialogState?>(null)
        private set

    data class QuickPatchParams(
        val selectedApp: SelectedApp,
        val patches: PatchSelection,
        val options: Options
    )

    data class UnsupportedVersionDialogState(
        val packageName: String,
        val version: String,
        val selectedApp: SelectedApp
    )

    data class WrongPackageDialogState(
        val expectedPackage: String,
        val actualPackage: String
    )

    var showWrongPackageDialog by mutableStateOf<WrongPackageDialogState?>(null)
        private set

    fun dismissWrongPackageDialog() {
        showWrongPackageDialog = null
    }

    fun dismissUnsupportedVersionDialog() {
        showUnsupportedVersionDialog = null
    }

    fun proceedWithUnsupportedVersion() {
        val state = showUnsupportedVersionDialog ?: return
        showUnsupportedVersionDialog = null
        viewModelScope.launch {
            forceStartQuickPatch(state.selectedApp, true)
        }
    }

    init {
        // Asynchronous initialization of all data
        viewModelScope.launch {
            downloadedApps = withContext(Dispatchers.IO) {
                downloadedAppRepository.getAll()
                    .first()
                    .filter { it.packageName == packageName }
                    .sortedByDescending { it.lastUsed }
            }

            suggestedVersion = withContext(Dispatchers.IO) {
                bundleRepository.suggestedVersions.first()[packageName]
            }

            val packageInfo = withContext(Dispatchers.IO) { pm.getPackageInfo(packageName) }
            val installedAppDeferred = withContext(Dispatchers.IO) { installedAppRepository.get(packageName) }

            installedAppData = packageInfo?.let {
                SelectedApp.Installed(packageName, it.versionName!!) to installedAppDeferred
            }
        }
    }

    private var pluginAction: Pair<LoadedDownloaderPlugin, kotlinx.coroutines.Job>? by mutableStateOf(null)
    val activePluginAction get() = pluginAction?.first?.packageName

    private var launchedActivity by mutableStateOf<CompletableDeferred<ActivityResult>?>(null)
    private val launchActivityChannel = Channel<Intent>()
    val launchActivityFlow = launchActivityChannel.receiveAsFlow()

    private val startPatchingChannel = Channel<QuickPatchParams>()
    val startPatchingFlow = startPatchingChannel.receiveAsFlow()

    fun requestLocalSelection() {
        storageSelectionChannel.trySend(Unit)
    }

    private fun cancelPluginAction() {
        pluginAction?.second?.cancel()
        pluginAction = null
    }

    fun handleStorageResult(uri: Uri?) {
        if (uri == null) return

        viewModelScope.launch {
            val local = withContext(Dispatchers.IO) { loadLocalApk(uri) }
            if (local == null) {
                app.toast(app.getString(R.string.failed_to_load_apk))
                return@launch
            }
            startQuickPatch(local)
        }
    }

    private fun loadLocalApk(uri: Uri): SelectedApp.Local? =
        app.contentResolver.openInputStream(uri)?.use { stream ->
            storageInputFile.delete()
            Files.copy(stream, storageInputFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            pm.getPackageInfo(storageInputFile)?.let { packageInfo ->
                SelectedApp.Local(
                    packageName = packageInfo.packageName,
                    version = packageInfo.versionName ?: "",
                    file = storageInputFile,
                    temporary = true
                )
            }
        }

    fun selectDownloadedApp(downloadedApp: DownloadedApp) {
        cancelPluginAction()
        viewModelScope.launch {
            val result = runCatching {
                val apkFile = withContext(Dispatchers.IO) {
                    downloadedAppRepository.getApkFileForApp(downloadedApp)
                }
                withContext(Dispatchers.IO) {
                    downloadedAppRepository.get(
                        downloadedApp.packageName,
                        downloadedApp.version,
                        markUsed = true
                    )
                }
                SelectedApp.Local(
                    packageName = downloadedApp.packageName,
                    version = downloadedApp.version,
                    file = apkFile,
                    temporary = false
                )
            }

            result.onSuccess { local ->
                startQuickPatch(local)
            }.onFailure { throwable ->
                Log.e(TAG, "Failed to select downloaded app", throwable)
                app.toast(app.getString(R.string.failed_to_load_apk))
            }
        }
    }

    fun selectInstalledApp(app: SelectedApp.Installed) {
        viewModelScope.launch {
            startQuickPatch(app)
        }
    }

    fun selectAuto() {
        viewModelScope.launch {
            val version = suggestedVersion ?: ""
            startQuickPatch(SelectedApp.Search(packageName, version))
        }
    }

    fun searchUsingPlugin(plugin: LoadedDownloaderPlugin) {
        cancelPluginAction()
        pluginAction = plugin to viewModelScope.launch {
            try {
                val scope = object : GetScope {
                    override val hostPackageName = app.packageName
                    override val pluginPackageName = plugin.packageName
                    override suspend fun requestStartActivity(intent: Intent) =
                        withContext(Dispatchers.Main) {
                            if (launchedActivity != null) error("Previous activity has not finished")
                            try {
                                val result = with(CompletableDeferred<ActivityResult>()) {
                                    launchedActivity = this
                                    launchActivityChannel.send(intent)
                                    await()
                                }
                                when (result.resultCode) {
                                    Activity.RESULT_OK -> result.data
                                    Activity.RESULT_CANCELED -> throw UserInteractionException.Activity.Cancelled()
                                    else -> throw UserInteractionException.Activity.NotCompleted(
                                        result.resultCode,
                                        result.data
                                    )
                                }
                            } finally {
                                launchedActivity = null
                            }
                        }
                }

                val targetVersion = suggestedVersion
                withContext(Dispatchers.IO) {
                    plugin.get(scope, packageName, targetVersion)
                }?.let { (data, version) ->
                    if (targetVersion != null && version != targetVersion) {
                        app.toast(app.getString(R.string.downloader_invalid_version))
                        return@launch
                    }
                    startQuickPatch(
                        SelectedApp.Download(
                            packageName,
                            version,
                            ParceledDownloaderData(plugin, data)
                        )
                    )
                } ?: app.toast(app.getString(R.string.downloader_app_not_found))
            } catch (e: UserInteractionException.Activity) {
                app.toast(e.message!!)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                app.toast(app.getString(R.string.downloader_error, e.simpleMessage()))
                Log.e(TAG, "Downloader.get threw an exception", e)
            } finally {
                pluginAction = null
            }
        }
    }

    fun handlePluginActivityResult(result: ActivityResult) {
        launchedActivity?.complete(result)
    }

    private suspend fun startQuickPatch(selectedApp: SelectedApp) {
        // Validate package name matches the expected one
        if (selectedApp.packageName != packageName) {
            withContext(Dispatchers.Main) {
                showWrongPackageDialog = WrongPackageDialogState(
                    expectedPackage = packageName,
                    actualPackage = selectedApp.packageName
                )
            }
            return
        }

        val allowIncompatible = prefs.disablePatchVersionCompatCheck.get()

        val bundles = bundleRepository
            .scopedBundleInfoFlow(selectedApp.packageName, selectedApp.version)
            .first()

        val patches = bundles.toPatchSelection(allowIncompatible) { _, patch -> patch.include }

        // Check if there are any patches available
        val totalPatches = patches.values.sumOf { it.size }

        if (totalPatches == 0) {
            // No patches available, show warning dialog
            withContext(Dispatchers.Main) {
                showUnsupportedVersionDialog = UnsupportedVersionDialogState(
                    packageName = selectedApp.packageName,
                    version = selectedApp.version ?: "Unknown",
                    selectedApp = selectedApp
                )
            }
            return
        }

        forceStartQuickPatch(selectedApp, allowIncompatible)
    }

    // Proceed without patch check
    private suspend fun forceStartQuickPatch(selectedApp: SelectedApp, allowIncompatible: Boolean) {
        val bundles = bundleRepository
            .scopedBundleInfoFlow(selectedApp.packageName, selectedApp.version)
            .first()

        val patches = bundles.toPatchSelection(allowIncompatible) { _, patch -> patch.include }

        val bundlePatches = bundles.associate { scoped ->
            scoped.uid to scoped.patches.associateBy { it.name }
        }
        val options = optionsRepository.getOptions(selectedApp.packageName, bundlePatches)

        startPatchingChannel.send(
            QuickPatchParams(
                selectedApp = selectedApp,
                patches = patches,
                options = options
            )
        )
    }

    companion object {
        private const val TAG = "QuickPatchViewModel"
    }
}
