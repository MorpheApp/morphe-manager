package app.revanced.manager.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Parcelable
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.activity.result.ActivityResult
import androidx.annotation.StringRes
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
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
import app.revanced.manager.domain.repository.PatchProfile
import app.revanced.manager.domain.repository.PatchProfileRepository
import app.revanced.manager.domain.repository.PatchSelectionRepository
import app.revanced.manager.domain.repository.PatchOptionsRepository.ResetEvent as OptionsResetEvent
import app.revanced.manager.domain.repository.PatchSelectionRepository.ResetEvent as SelectionResetEvent
import app.revanced.manager.domain.repository.remapLocalBundles
import app.revanced.manager.domain.repository.toConfiguration
import app.revanced.manager.patcher.patch.PatchBundleInfo
import app.revanced.manager.patcher.patch.PatchBundleInfo.Extensions.toPatchSelection
import app.revanced.manager.patcher.split.SplitApkInspector
import app.revanced.manager.patcher.split.SplitApkPreparer
import app.revanced.manager.network.downloader.LoadedDownloaderPlugin
import app.revanced.manager.network.downloader.ParceledDownloaderData
import app.revanced.manager.patcher.patch.PatchBundleInfo.Extensions.requiredOptionsSet
import app.revanced.manager.plugin.downloader.DownloadUrl
import app.revanced.manager.plugin.downloader.GetScope
import app.revanced.manager.plugin.downloader.PluginHostApi
import app.revanced.manager.plugin.downloader.Package as DownloaderPackage
import app.revanced.manager.plugin.downloader.UserInteractionException
import app.revanced.manager.ui.model.SelectedApp
import app.revanced.manager.ui.model.navigation.Patcher
import app.revanced.manager.ui.model.navigation.SelectedApplicationInfo
import app.revanced.manager.util.Options
import app.revanced.manager.util.APK_FILE_EXTENSIONS
import app.revanced.manager.util.PM
import app.revanced.manager.util.PatchSelection
import app.revanced.manager.util.simpleMessage
import app.revanced.manager.util.toast
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

@OptIn(SavedStateHandleSaveableApi::class, PluginHostApi::class, ExperimentalCoroutinesApi::class)
class SelectedAppInfoViewModel(
    input: SelectedApplicationInfo.ViewModelParams
) : ViewModel(), KoinComponent {
    private val app: Application = get()
    private val bundleRepository: PatchBundleRepository = get()
    private val selectionRepository: PatchSelectionRepository = get()
    private val optionsRepository: PatchOptionsRepository = get()
    private val pluginsRepository: DownloaderPluginRepository = get()
    private val downloadedAppRepository: DownloadedAppRepository = get()
    private val patchProfileRepository: PatchProfileRepository = get()
    private val installedAppRepository: InstalledAppRepository = get()
    private val rootInstaller: RootInstaller = get()
    private val pm: PM = get()
    private val filesystem: Filesystem = get()
    private val savedStateHandle: SavedStateHandle = get()
    val prefs: PreferencesManager = get()
    private var selectionLoadJob: Job? = null
    private var optionsLoadJob: Job? = null
    val plugins = pluginsRepository.loadedPluginsFlow
    val desiredVersion = input.app.version
    val packageName = input.app.packageName
    private val profileId = input.profileId
    private val requiresSourceSelection = input.requiresSourceSelection
    val sourceSelectionRequired get() = requiresSourceSelection
    private val storageInputDir = filesystem.uiTempDir
    private val splitWorkspace = filesystem.tempDir
    private var preparedApkCleanup: (() -> Unit)? = null
    private val storageSelectionChannel = Channel<Unit>(Channel.CONFLATED)
    val requestStorageSelection = storageSelectionChannel.receiveAsFlow()
    private val _profileLaunchState = MutableStateFlow<ProfileLaunchState?>(null)
    val profileLaunchState = _profileLaunchState.asStateFlow()
    private var autoLaunchProfilePatcher = profileId != null
    private val allowUniversalFlow = prefs.disableUniversalPatchCheck.flow
    private var allowUniversalPatches = true
    private var _selectedApp by savedStateHandle.saveable {
        mutableStateOf(input.app)
    }
    private val selectedAppState = MutableStateFlow(_selectedApp)
    private val bundleInfoFlowInternal =
        MutableStateFlow<List<PatchBundleInfo.Scoped>>(emptyList())
    val bundleInfoFlow = bundleInfoFlowInternal.asStateFlow()
    private val preferredBundleOverrideFlow =
        savedStateHandle.getStateFlow("preferred_bundle_override", null as String?)
    val suggestedVersionsByBundle = selectedAppState
        .flatMapLatest { selected ->
            combine(
                bundleRepository.suggestedVersionsByBundle,
                bundleInfoFlow
            ) { bundleVersions, bundles ->
                val relevantBundles = bundles.map { it.uid }.toSet()
                buildMap {
                    bundleVersions.forEach { (bundleUid, versions) ->
                        if (bundleUid in relevantBundles) {
                            put(bundleUid, versions[selected.packageName])
                        }
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    private val preferredBundleUidFlow =
        savedStateHandle.getStateFlow("preferred_bundle_uid", null as Int?)
    private val preferredBundleAllVersionsFlow =
        savedStateHandle.getStateFlow("preferred_bundle_all_versions", false)
    val selectedBundleUidFlow = preferredBundleUidFlow
    val selectedBundleVersionOverrideFlow = preferredBundleOverrideFlow
    val preferredBundleTargetsAllVersionsFlow = preferredBundleAllVersionsFlow
    val preferredBundleVersionFlow = combine(
        preferredBundleUidFlow,
        preferredBundleOverrideFlow,
        suggestedVersionsByBundle
    ) { uid, override, versions ->
        if (uid == null) null else override ?: versions[uid]
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val preferredBundleVersion get() = preferredBundleVersionFlow.value
    val effectiveDesiredVersion get() = preferredBundleVersion ?: desiredVersion
    val bundleRecommendationDetailsFlow = combine(
        bundleInfoFlow,
        suggestedVersionsByBundle
    ) { scopedBundles, versions ->
        scopedBundles.mapNotNull { scoped ->
            val support = scoped.collectBundleSupport(packageName)
            if (!support.hasSupport) return@mapNotNull null
            val recommended = versions[scoped.uid]
            if (
                recommended == null &&
                support.versions.isEmpty() &&
                !support.supportsAllVersions
            ) return@mapNotNull null

            val otherVersions = support.versions
                .filterNot { recommended.equals(it, ignoreCase = true) }
                .sorted()

            BundleRecommendationDetail(
                bundleUid = scoped.uid,
                name = scoped.name,
                recommendedVersion = recommended,
                otherSupportedVersions = otherVersions,
                supportsAllVersions = support.supportsAllVersions
            )
        }.sortedBy { it.name.lowercase(Locale.ROOT) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val persistConfiguration = input.patches == null

    val hasRoot = rootInstaller.hasRootAccess()
    var installedAppData: Pair<SelectedApp.Installed, InstalledApp?>? by mutableStateOf(null)
        private set
    val downloadedApps =
        downloadedAppRepository.getAll().map { apps ->
            apps.filter { it.packageName == packageName }
                .sortedByDescending { it.lastUsed }
        }

    var selectedAppInfo: PackageInfo? by mutableStateOf(null)
        private set

    var selectedApp
        get() = _selectedApp
        set(value) {
            _selectedApp = value
            selectedAppState.value = value
            invalidateSelectedAppInfo()
        }

    var options: Options by savedStateHandle.saveable {
        val state = mutableStateOf<Options>(emptyMap())

        optionsLoadJob = viewModelScope.launch {
            val bundlePatches = withContext(Dispatchers.Default) {
                bundleRepository
                    .scopedBundleInfoFlow(packageName, input.app.version)
                    .first()
                    .associate { scoped -> scoped.uid to scoped.patches.associateBy { it.name } }
            }

            state.value = withContext(Dispatchers.Default) {
                optionsRepository.getOptions(packageName, bundlePatches)
            }
        }

        state
    }
        private set

    private var selectionState: SelectionState by mutableStateOf(
        if (input.patches != null) SelectionState.Customized(input.patches) else SelectionState.Default
    )

    init {
        if (input.patches == null) {
            selectionLoadJob = viewModelScope.launch {
                val previous = selectionRepository.getSelection(packageName)
                if (previous.values.sumOf { it.size } == 0) return@launch
                selectionState = SelectionState.Customized(previous)
            }
        }
    }

    init {
        invalidateSelectedAppInfo()
        profileId?.let(::loadProfileConfiguration)
        viewModelScope.launch(Dispatchers.Main) {
            val packageInfo = async(Dispatchers.IO) { pm.getPackageInfo(packageName) }
            val installedAppDeferred =
                async(Dispatchers.IO) { installedAppRepository.get(packageName) }

            installedAppData =
                packageInfo.await()?.let {
                    SelectedApp.Installed(
                        packageName,
                        it.versionName!!
                    ) to installedAppDeferred.await()
                }
        }

        allowUniversalPatches = prefs.disableUniversalPatchCheck.getBlocking()

        viewModelScope.launch {
            selectedAppState
                .flatMapLatest { app ->
                    bundleRepository.scopedBundleInfoFlow(app.packageName, app.version)
                }
                .combine(allowUniversalFlow.distinctUntilChanged()) { bundles, allowUniversal ->
                    allowUniversal to if (allowUniversal) {
                        bundles
                    } else {
                        bundles.map(PatchBundleInfo.Scoped::withoutUniversalPatches)
                    }
                }
                .collect { (allowUniversal, bundles) ->
                    allowUniversalPatches = allowUniversal
                    bundleInfoFlowInternal.value = bundles
                    if (!allowUniversal) pruneSelectionForUniversal(bundles)
                }
        }

        viewModelScope.launch {
            preferredBundleVersionFlow.collect { version ->
                val target = version ?: desiredVersion
                val current = selectedApp
                if (current is SelectedApp.Search && current.version != target) {
                    selectedApp = SelectedApp.Search(packageName, target)
                }
            }
        }

        viewModelScope.launch {
            combine(
                prefs.disablePatchVersionCompatCheck.flow,
                prefs.suggestedVersionSafeguard.flow,
                selectedBundleUidFlow,
                selectedBundleVersionOverrideFlow
            ) { allowIncompatible, requireSuggested, selectedUid, override ->
                val overridesAllowed = allowIncompatible && !requireSuggested
                val hasSelection = selectedUid != null || override != null
                overridesAllowed to hasSelection
            }.collect { (overridesAllowed, hasSelection) ->
                if (!overridesAllowed && hasSelection) {
                    selectBundleRecommendation(null, null)
                }
            }
        }

        if (persistConfiguration) {
            viewModelScope.launch {
                selectionRepository.resetEvents.collect(::handleSelectionResetEvent)
            }
            viewModelScope.launch {
                optionsRepository.resetEvents.collect(::handleOptionsResetEvent)
            }
        }
    }

    private fun handleSelectionResetEvent(event: SelectionResetEvent) {
        if (!persistConfiguration) return
        when (event) {
            SelectionResetEvent.All -> clearSelectionState()
            is SelectionResetEvent.Package -> if (event.packageName == packageName) {
                clearSelectionState()
            }

            is SelectionResetEvent.Bundle -> clearSelectionForBundle(event.bundleUid)
        }
    }

    private fun clearSelectionState() {
        if (selectionState is SelectionState.Customized) {
            selectionState = SelectionState.Default
        }
    }

    private fun clearSelectionForBundle(bundleUid: Int) {
        val current = selectionState
        if (current !is SelectionState.Customized) return
        if (bundleUid !in current.patchSelection) return
        val updated = current.patchSelection
            .filterKeys { it != bundleUid }
        selectionState = if (updated.isEmpty()) SelectionState.Default else SelectionState.Customized(updated)
    }

    private fun handleOptionsResetEvent(event: OptionsResetEvent) {
        if (!persistConfiguration) return
        when (event) {
            OptionsResetEvent.All -> if (options.isNotEmpty()) {
                options = emptyMap()
            }

            is OptionsResetEvent.Package -> if (event.packageName == packageName && options.isNotEmpty()) {
                options = emptyMap()
            }

            is OptionsResetEvent.Bundle -> {
                if (event.bundleUid in options) {
                    options = options.filterKeys { it != event.bundleUid }
                }
            }
        }
    }

    private fun loadProfileConfiguration(id: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            val profile = patchProfileRepository.getProfile(id) ?: run {
                withContext(Dispatchers.Main) {
                    app.toast(app.getString(R.string.patch_profile_launch_error))
                }
                autoLaunchProfilePatcher = false
                return@launch
            }

            val sourcesList = bundleRepository.sources.first()
            val bundleInfoSnapshot = bundleRepository.bundleInfoFlow.first()
            val signatureMap = bundleInfoSnapshot.mapValues { (_, info) ->
                info.patches.map { it.name.trim().lowercase() }.toSet()
            }
            val remappedPayload = profile.payload.remapLocalBundles(sourcesList, signatureMap)
            val workingProfile = if (remappedPayload === profile.payload) profile else profile.copy(payload = remappedPayload)

            val scopedBundles = bundleRepository
                .scopedBundleInfoFlow(profile.packageName, profile.appVersion)
                .first()
                .associateBy { it.uid }
            val allowUniversal = prefs.disableUniversalPatchCheck.get()
            if (!allowUniversal) {
                val universalPatchNames = bundleInfoSnapshot
                    .values
                    .flatMap { it.patches }
                    .filter { it.compatiblePackages == null }
                    .mapTo(mutableSetOf()) { it.name.lowercase() }
                val containsUniversal = workingProfile.payload.bundles.any { bundle ->
                    val info = scopedBundles[bundle.bundleUid]
                    bundle.patches.any { patchName ->
                        val normalized = patchName.lowercase()
                        val matchesScoped = info?.patches?.any {
                            it.name.equals(patchName, true) && it.compatiblePackages == null
                        } == true
                        matchesScoped || universalPatchNames.contains(normalized)
                    }
                }
                if (containsUniversal) {
                    autoLaunchProfilePatcher = false
                    withContext(Dispatchers.Main) {
                        app.toast(
                            app.getString(
                                R.string.universal_patches_profile_blocked_description,
                                app.getString(R.string.universal_patches_safeguard)
                            )
                        )
                    }
                    return@launch
                }
            }

            val sources = sourcesList.associateBy { it.uid }
            val configuration = workingProfile.toConfiguration(scopedBundles, sources)
            val selection = configuration.selection.takeUnless { it.isEmpty() }

            updateConfiguration(
                selection,
                configuration.options,
                persistState = false,
                filterOptions = false
            ).join()

            _profileLaunchState.value = ProfileLaunchState(
                profile = workingProfile,
                selection = selection,
                options = configuration.options,
                missingBundles = configuration.missingBundles,
                changedBundles = configuration.changedBundles
            )

            if (configuration.missingBundles.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    app.toast(app.getString(R.string.patch_profile_missing_bundles_toast))
                }
            }
            if (configuration.changedBundles.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    app.toast(app.getString(R.string.patch_profile_changed_patches_toast))
                }
            }
        }
    }
    private fun pruneSelectionForUniversal(bundles: List<PatchBundleInfo.Scoped>) {
        if (bundles.isEmpty()) return

        val currentState = selectionState
        if (currentState is SelectionState.Customized) {
            val available = bundles.associate { bundle ->
                bundle.uid to bundle.patches.map { it.name }.toSet()
            }
            val filteredSelection = buildMap<Int, Set<String>> {
                currentState.patchSelection.forEach { (bundleUid, patches) ->
                    val allowed = available[bundleUid] ?: return@forEach
                    val kept = patches.filter { it in allowed }.toSet()
                    if (kept.isNotEmpty()) put(bundleUid, kept)
                }
            }
            if (filteredSelection != currentState.patchSelection) {
                selectionState = SelectionState.Customized(filteredSelection)
            }
        }

        val filteredOptions = options.filtered(bundles)
        if (filteredOptions != options) {
            options = filteredOptions
        }
    }

    val requiredVersion = combine(
        prefs.suggestedVersionSafeguard.flow,
        bundleRepository.suggestedVersions,
        preferredBundleVersionFlow
    ) { suggestedVersionSafeguard, suggestedVersions, preferred ->
        if (!suggestedVersionSafeguard) return@combine null

        preferred ?: suggestedVersions[input.app.packageName]
    }

    suspend fun awaitOptions(): Options {
        optionsLoadJob?.join()
        return options
    }

    var showSourceSelector by mutableStateOf(requiresSourceSelection)
        private set
    private var pluginAction: Pair<LoadedDownloaderPlugin, Job>? by mutableStateOf(null)
    val activePluginAction get() = pluginAction?.first?.packageName
    private var launchedActivity by mutableStateOf<CompletableDeferred<ActivityResult>?>(null)
    private val launchActivityChannel = Channel<Intent>()
    val launchActivityFlow = launchActivityChannel.receiveAsFlow()

    // Morphe: Do not show the message "Downloaders installed but none are trusted"
//    val errorFlow = combine(plugins, snapshotFlow { selectedApp }) { pluginsList, app ->
//        when {
//            app is SelectedApp.Search && pluginsList.isEmpty() -> Error.NoPlugins
//            else -> null
//        }
//    }
    val errorFlow = MutableStateFlow<Error?>(null).asStateFlow()

    fun showSourceSelector() {
        dismissSourceSelector()
        showSourceSelector = true
    }

    fun requestLocalSelection() {
        storageSelectionChannel.trySend(Unit)
    }

    private fun cancelPluginAction() {
        pluginAction?.second?.cancel()
        pluginAction = null
    }

    fun dismissSourceSelector() {
        cancelPluginAction()
        showSourceSelector = false
    }

    fun handleStorageResult(uri: Uri?) {
        if (uri == null) {
            if (requiresSourceSelection && selectedApp is SelectedApp.Search) {
                showSourceSelector = true
            }
            return
        }

        viewModelScope.launch {
            val local = withContext(Dispatchers.IO) { loadLocalApk(uri) }
            if (local == null) {
                app.toast(app.getString(R.string.failed_to_load_apk))
                if (requiresSourceSelection && selectedApp is SelectedApp.Search) {
                    showSourceSelector = true
                }
                return@launch
            }
            selectedApp = local
            dismissSourceSelector()
        }
    }

    fun handleStorageFile(file: File?) {
        if (file == null) {
            if (requiresSourceSelection && selectedApp is SelectedApp.Search) {
                showSourceSelector = true
            }
            return
        }

        viewModelScope.launch {
            val local = withContext(Dispatchers.IO) { loadLocalApk(file) }
            if (local == null) {
                app.toast(app.getString(R.string.failed_to_load_apk))
                if (requiresSourceSelection && selectedApp is SelectedApp.Search) {
                    showSourceSelector = true
                }
                return@launch
            }
            selectedApp = local
            dismissSourceSelector()
        }
    }

    private suspend fun loadLocalApk(uri: Uri): SelectedApp.Local? =
        app.contentResolver.openInputStream(uri)?.use { stream ->
            storageInputDir.listFiles()
                ?.filter { it.name.startsWith("profile_input.") }
                ?.forEach(File::delete)
            val extension = resolveExtension(uri)
            if (extension !in APK_FILE_EXTENSIONS) return@use null
            val sanitized = extension.lowercase(Locale.ROOT).takeIf { it.matches(Regex("^[a-z0-9]{1,10}$")) }
                ?: "apk"
            val storageInputFile = File(storageInputDir, "profile_input.$sanitized").apply { delete() }
            Files.copy(stream, storageInputFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            resolvePackageInfo(storageInputFile)?.let { packageInfo ->
                SelectedApp.Local(
                    packageName = packageInfo.packageName,
                    version = packageInfo.versionName ?: "",
                    file = storageInputFile,
                    temporary = true
                )
            }
        }

    private suspend fun loadLocalApk(file: File): SelectedApp.Local? {
        if (!file.exists()) return null
        storageInputDir.listFiles()
            ?.filter { it.name.startsWith("profile_input.") }
            ?.forEach(File::delete)
        val extension = file.extension.lowercase(Locale.ROOT)
        if (extension !in APK_FILE_EXTENSIONS) return null
        val sanitized = extension.lowercase(Locale.ROOT).takeIf { it.matches(Regex("^[a-z0-9]{1,10}$")) }
            ?: "apk"
        val storageInputFile = File(storageInputDir, "profile_input.$sanitized").apply { delete() }
        Files.copy(file.toPath(), storageInputFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        return resolvePackageInfo(storageInputFile)?.let { packageInfo ->
            SelectedApp.Local(
                packageName = packageInfo.packageName,
                version = packageInfo.versionName ?: "",
                file = storageInputFile,
                temporary = true
            )
        }
    }

    private fun resolveExtension(uri: Uri): String {
        val document = DocumentFile.fromSingleUri(app, uri)
        val nameExt = document?.name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
        if (!nameExt.isNullOrBlank()) return nameExt

        val mime = app.contentResolver.getType(uri)
        val resolved = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        return resolved?.lowercase(Locale.ROOT).orEmpty()
    }

    private suspend fun resolvePackageInfo(file: File): PackageInfo? {
        if (!file.exists()) return null
        if (!SplitApkPreparer.isSplitArchive(file)) return pm.getPackageInfo(file)

        // For metadata/UI we only need the representative base split, not a full merge.
        val representative = SplitApkInspector.extractRepresentativeApk(file, splitWorkspace) ?: return null
        return pm.getPackageInfo(representative.file)?.also {
            // Retain cleanup until the next resolution to keep label/icon loading working.
            preparedApkCleanup = representative.cleanup
        }
    }

    fun selectDownloadedApp(downloadedApp: DownloadedApp) {
        cancelPluginAction()
        viewModelScope.launch {
            val result = runCatching {
                val apkFile = withContext(Dispatchers.IO) {
                    downloadedAppRepository.getPreparedApkFile(downloadedApp)
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
                selectedApp = local
                dismissSourceSelector()
            }.onFailure { throwable ->
                Log.e(TAG, "Failed to select downloaded app", throwable)
                app.toast(app.getString(R.string.failed_to_load_apk))
            }
        }
    }

    fun selectBundleRecommendation(
        bundleUid: Int?,
        versionOverride: String?,
        targetsAllVersions: Boolean = false
    ) {
        if (bundleUid == null) {
            savedStateHandle["preferred_bundle_uid"] = null
            savedStateHandle["preferred_bundle_override"] = null
            savedStateHandle["preferred_bundle_all_versions"] = false
        } else {
            savedStateHandle["preferred_bundle_uid"] = bundleUid
            savedStateHandle["preferred_bundle_override"] = versionOverride
            savedStateHandle["preferred_bundle_all_versions"] = targetsAllVersions
        }

        // Keep the selected app version in sync when no APK has been chosen yet (Search/download flows).
        val targetVersion = if (targetsAllVersions) null else versionOverride ?: preferredBundleVersion
        when (val current = selectedApp) {
            is SelectedApp.Search -> {
                if (current.version != targetVersion) {
                    selectedApp = current.copy(version = targetVersion ?: desiredVersion)
                }
            }

            is SelectedApp.Download -> {
                if (current.version.isNullOrBlank() || current.version != targetVersion) {
                    selectedApp = current.copy(version = targetVersion ?: desiredVersion)
                }
            }

            else -> Unit
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

                val targetsAllVersions = preferredBundleAllVersionsFlow.value
                val targetVersion =
                    if (targetsAllVersions) null else preferredBundleVersion ?: desiredVersion
                val result = withContext(Dispatchers.IO) {
                    plugin.get(scope, packageName, targetVersion)
                }
                if (result == null) {
                    app.toast(app.getString(R.string.downloader_app_not_found))
                    return@launch
                }

                val (data, reportedVersion) = result

                val derivedVersion = resolveVersionFromDownloaderData(plugin, data, targetVersion)
                val resolvedVersion = when {
                    derivedVersion != null && !looksLikeVersionCode(derivedVersion) -> derivedVersion
                    targetVersion != null && !looksLikeVersionCode(targetVersion) -> targetVersion
                    else -> preferredBundleVersion ?: desiredVersion
                }
                if (targetVersion != null && resolvedVersion != targetVersion) {
                    Log.d(TAG, "Downloader provided $targetVersion, resolved version=$resolvedVersion")
                }
                selectedApp = SelectedApp.Download(
                    packageName,
                    resolvedVersion,
                    ParceledDownloaderData(plugin, data)
                )
            } catch (e: UserInteractionException.Activity) {
                app.toast(e.message!!)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                app.toast(app.getString(R.string.downloader_error, e.simpleMessage()))
                Log.e(TAG, "Downloader.get threw an exception", e)
            } finally {
                pluginAction = null
                dismissSourceSelector()
            }
        }
    }

    fun handlePluginActivityResult(result: ActivityResult) {
        launchedActivity?.complete(result)
    }

    private fun invalidateSelectedAppInfo() = viewModelScope.launch {
        // Defer cleanup of the previous prepared APK until after new metadata is resolved,
        // so existing UI can keep reading the old label/icon without races.
        val previousCleanup = preparedApkCleanup
        preparedApkCleanup = null

        val info = when (val app = selectedApp) {
            is SelectedApp.Local -> withContext(Dispatchers.IO) { resolvePackageInfo(app.file) }
            is SelectedApp.Installed -> withContext(Dispatchers.IO) { pm.getPackageInfo(app.packageName) }
            is SelectedApp.Download, is SelectedApp.Search -> withContext(Dispatchers.IO) {
                val version = app.version
                val downloaded = when {
                    version != null -> downloadedAppRepository.get(app.packageName, version)
                        ?: downloadedAppRepository.getLatest(app.packageName)
                    else -> downloadedAppRepository.getLatest(app.packageName)
                }
                downloaded?.let { resolvePackageInfo(downloadedAppRepository.getPreparedApkFile(it)) }
            }
        }

        selectedAppInfo = info
        // Now that UI is updated to new info, we can safely clean up the old prepared APK.
        previousCleanup?.invoke()

        val current = selectedApp
        val resolvedVersion = info?.versionName?.takeUnless(String::isNullOrBlank)
        if (info != null) {
            when (current) {
                is SelectedApp.Local -> if (!current.resolved || current.packageName == current.file.nameWithoutExtension) {
                    selectedApp = current.copy(
                        packageName = info.packageName,
                        version = resolvedVersion ?: current.version,
                        resolved = true
                    )
                }

                is SelectedApp.Download -> if (current.version.isNullOrBlank() || current.packageName == current.version) {
                    selectedApp = current.copy(
                        packageName = info.packageName,
                        version = resolvedVersion ?: info.versionName
                    )
                }

                is SelectedApp.Search -> if (current.version.isNullOrBlank()) {
                    selectedApp = current.copy(
                        packageName = info.packageName,
                        version = resolvedVersion ?: info.versionName
                    )
                }

                else -> Unit
            }
        }
    }

    fun getOptionsFiltered(bundles: List<PatchBundleInfo.Scoped>) = options.filtered(bundles)
    suspend fun hasSetRequiredOptions(patchSelection: PatchSelection) = bundleInfoFlow
        .first()
        .requiredOptionsSet(
            allowIncompatible = prefs.disablePatchVersionCompatCheck.get(),
            isSelected = { bundle, patch -> patch.name in patchSelection[bundle.uid]!! },
            optionsForPatch = { bundle, patch -> options[bundle.uid]?.get(patch.name) },
        )

    override fun onCleared() {
        super.onCleared()
        preparedApkCleanup?.invoke()
        preparedApkCleanup = null
    }

    suspend fun getPatcherParams(): Patcher.ViewModelParams {
        selectionLoadJob?.join()
        optionsLoadJob?.join()
        val allowIncompatible = prefs.disablePatchVersionCompatCheck.get()
        val bundles = bundleInfoFlow.first()
        return Patcher.ViewModelParams(
            selectedApp,
            getPatches(bundles, allowIncompatible),
            getOptionsFiltered(bundles)
        )
    }

    fun getPatches(bundles: List<PatchBundleInfo.Scoped>, allowIncompatible: Boolean) =
        selectionState.patches(bundles, allowIncompatible)

    fun getCustomPatches(
        bundles: List<PatchBundleInfo.Scoped>,
        allowIncompatible: Boolean
    ): PatchSelection? =
        (selectionState as? SelectionState.Customized)?.patches(bundles, allowIncompatible)


    fun updateConfiguration(
        selection: PatchSelection?,
        options: Options,
        persistState: Boolean = persistConfiguration,
        filterOptions: Boolean = true
    ) = viewModelScope.launch {
        optionsLoadJob?.cancel()
        optionsLoadJob = null

        selectionState = selection?.let(SelectionState::Customized) ?: SelectionState.Default

        val filteredOptions = if (filterOptions) {
            options.filtered(bundleInfoFlow.first())
        } else {
            options
        }
        this@SelectedAppInfoViewModel.options = filteredOptions

        if (!persistConfiguration || !persistState) return@launch
        viewModelScope.launch(Dispatchers.Default) {
            selection?.let { selectionRepository.updateSelection(packageName, it) }
                ?: selectionRepository.resetSelectionForPackage(packageName)

            optionsRepository.saveOptions(packageName, filteredOptions)
        }
    }

    fun shouldAutoLaunchProfile() = autoLaunchProfilePatcher

    fun markProfileAutoLaunchConsumed() {
        autoLaunchProfilePatcher = false
    }

    data class ProfileLaunchState(
        val profile: PatchProfile,
        val selection: PatchSelection?,
        val options: Options,
        val missingBundles: Set<Int>,
        val changedBundles: Set<Int>
    )

    enum class Error(@StringRes val resourceId: Int) {
        NoPlugins(R.string.downloader_no_plugins_available)
    }

    private fun PatchBundleInfo.Scoped.collectBundleSupport(packageName: String): BundleSupport {
        var supportsAllVersions = false
        val versions = mutableSetOf<String>()
        var hasSupport = false

        patches.forEach { patch ->
            patch.compatiblePackages
                ?.filter { it.packageName.equals(packageName, ignoreCase = true) }
                ?.forEach { compatible ->
                    hasSupport = true
                    val supportedVersions = compatible.versions
                    if (supportedVersions.isNullOrEmpty()) {
                        supportsAllVersions = true
                    } else {
                        versions += supportedVersions
                    }
                }
        }

        return BundleSupport(
            hasSupport = hasSupport,
            supportsAllVersions = supportsAllVersions,
            versions = versions
        )
    }

    private data class BundleSupport(
        val hasSupport: Boolean,
        val supportsAllVersions: Boolean,
        val versions: Set<String>
    )

    private suspend fun resolveVersionFromDownloaderData(
        plugin: LoadedDownloaderPlugin,
        data: Parcelable,
        targetVersion: String?
    ): String? = when (data) {
        is DownloaderPackage -> sanitizeVersionString(data.version)
        is DownloadUrl -> resolveVersionFromDownloadUrl(data, sanitizeVersionString(targetVersion) ?: targetVersion)
        else -> {
            Log.d(TAG, "Unhandled downloader data type from ${plugin.packageName}: ${data::class.java.simpleName}")
            null
        }
    }

    private suspend fun resolveVersionFromDownloadUrl(
        downloadUrl: DownloadUrl,
        targetVersion: String?
    ): String? = withContext(Dispatchers.IO) {
        val probe = runCatching { probeDownloadUrl(downloadUrl) }.getOrNull()
        val candidates = buildList {
            probe?.filename?.let(::add)
            probe?.finalUrl?.let(::add)
            add(downloadUrl.url)
            sanitizeVersionString(targetVersion)?.let(::add)
        }

        candidates.firstNotNullOfOrNull { candidate ->
            candidate.let(::extractVersionCandidate)
        }
    }

    private data class ProbeResult(val finalUrl: String, val filename: String?)

    private fun probeDownloadUrl(downloadUrl: DownloadUrl): ProbeResult {
        var currentUrl = downloadUrl.url
        var filename: String? = null

        repeat(4) {
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "HEAD"
                connectTimeout = 10_000
                readTimeout = 10_000
                useCaches = false
                doInput = true
                downloadUrl.headers.forEach(::setRequestProperty)
            }

            connection.connect()
            connection.getHeaderField("Content-Disposition")?.let { disposition ->
                parseFilename(disposition)?.let { resolved -> filename = resolved }
            }

            val code = runCatching { connection.responseCode }.getOrDefault(-1)
            val location = connection.getHeaderField("Location")

            if (code in 300..399 && !location.isNullOrBlank()) {
                currentUrl = URL(URL(currentUrl), location).toString()
                return@repeat
            }

            if (filename.isNullOrBlank()) {
                filename = connection.url.path.substringAfterLast('/').takeIf { it.isNotBlank() }
            }

            return ProbeResult(currentUrl, filename)
        }

        return ProbeResult(currentUrl, filename)
    }

    private fun parseFilename(contentDisposition: String): String? {
        contentDisposition.split(';').forEach { part ->
            val trimmed = part.trim()
            if (trimmed.startsWith("filename", ignoreCase = true)) {
                val value = trimmed.substringAfter('=').trim().trim('"')
                if (value.isNotBlank()) return value
            }
        }
        return null
    }

    private fun extractVersionCandidate(raw: String): String? {
        val decoded = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        // Strip build metadata / query markers that occasionally leak into filenames or URLs.
        val cleaned = decoded
            .substringBefore('$')
            .trimEnd('-', '_', '.', '+')
        if (cleaned.isBlank()) return null
        val qualifiedPattern = Regex(
            """\d+(?:[._-]\d+){1,5}(?:[._-](?:release|beta|alpha|rc|build)[A-Za-z0-9.]*)?""",
            RegexOption.IGNORE_CASE
        )
        val numericPattern = Regex("""\d+(?:[._-]\d+){1,5}""")
        val matches = (qualifiedPattern.findAll(cleaned) + numericPattern.findAll(cleaned))
            .map { it.value }
            .distinct()
            .toList()
        if (matches.isEmpty()) return null

        val best = matches
            .map { it to scoreVersionCandidate(it) }
            .maxWithOrNull(
                compareBy<Pair<String, Int>> { it.second }
                    .thenBy { it.first.length } // prefer shorter strings
                    .thenBy { leadingNumber(it.first) } // prefer smaller leading number
            )?.first ?: return null
        var normalized = best
            .trim('.', '-', '_')
            .replace("_", ".")
            .replace(Regex("(?<=\\d)-(\\d)")) { ".$1" }
        normalized = trimBuildMetadata(normalized)
        if (normalized.isBlank()) return null
        if (!normalized.first().isDigit()) return null
        if (!normalized.contains('.')) return null
        val firstNumeric = leadingNumber(normalized)
        val hasQualifier = hasQualifier(normalized)
        val dotCount = normalized.count { it == '.' }
        if (firstNumeric > 200000 && !hasQualifier && dotCount <= 1) return null
        return normalized
    }

    private fun sanitizeVersionString(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        extractVersionCandidate(trimmed)?.let { return it }
        val withoutPrefix = trimmed.removePrefix("v").trim()
        return withoutPrefix.takeIf { it.isNotBlank() && withoutPrefix.first().isDigit() }
    }

    private fun scoreVersionCandidate(candidate: String): Int {
        var score = 0
        val hasQualifier = hasQualifier(candidate)
        val parts = candidate.split('.', '-', '_').filter { it.isNotBlank() }
        val firstPart = parts.firstOrNull() ?: ""
        val firstNumeric = firstPart.toIntOrNull() ?: 0

        if (hasQualifier) score += 10
        if (parts.size >= 3) score += 6
        if (parts.size >= 4) score += 2
        if (firstPart.length <= 4) score += 4
        if (firstNumeric in 1..9999) score += 2
        if (candidate.length <= 20) score += 2

        // Penalize likely version codes (very large leading number with few segments and no qualifier)
        if (firstPart.length >= 6 && !hasQualifier) score -= 8
        if (firstNumeric > 200000 && !hasQualifier) score -= 6
        if (parts.size <= 2 && !hasQualifier) score -= 3

        return score
    }

    private fun trimBuildMetadata(value: String): String {
        return value.substringBefore('$').trim('.', '-', '_')
    }

    private fun leadingNumber(candidate: String): Int {
        val first = candidate.split('.', '-', '_').firstOrNull()?.trim() ?: return Int.MAX_VALUE
        return first.toIntOrNull() ?: Int.MAX_VALUE
    }

    private fun hasQualifier(candidate: String): Boolean =
        candidate.contains(Regex("(release|beta|alpha|rc|build)", RegexOption.IGNORE_CASE))

    private fun looksLikeVersionCode(value: String): Boolean {
        val numericOnly = value.all { it.isDigit() || it == '.' }
        val first = value.split('.', '-', '_').firstOrNull()?.trim().orEmpty()
        val firstNum = first.toLongOrNull() ?: return false
        val dotCount = value.count { it == '.' }
        return numericOnly && firstNum > 200000 && dotCount <= 1
    }

    private companion object {
        private val TAG = SelectedAppInfoViewModel::class.java.simpleName ?: "SelectedAppInfoViewModel"
        /**
         * Returns a copy with all nonexistent options removed.
         */
        private fun Options.filtered(bundles: List<PatchBundleInfo.Scoped>): Options {
            if (isEmpty()) return this
            if (bundles.isEmpty()) return this

            return buildMap options@{
                bundles.forEach bundles@{ bundle ->
                    val bundleOptions = this@filtered[bundle.uid] ?: return@bundles

                    val patches = bundle.patches.associateBy { it.name }

                    this@options[bundle.uid] = buildMap bundleOptions@{
                        bundleOptions.forEach patch@{ (patchName, values) ->
                            // Get all valid option keys for the patch.
                            val validOptionKeys =
                                patches[patchName]?.options?.map { it.key }?.toSet() ?: return@patch

                            this@bundleOptions[patchName] = values.filterKeys { key ->
                                key in validOptionKeys
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed interface SelectionState : Parcelable {
    fun patches(bundles: List<PatchBundleInfo.Scoped>, allowIncompatible: Boolean): PatchSelection

    @Parcelize
    data class Customized(val patchSelection: PatchSelection) : SelectionState {
        override fun patches(bundles: List<PatchBundleInfo.Scoped>, allowIncompatible: Boolean) =
            bundles.toPatchSelection(
                allowIncompatible
            ) { uid, patch ->
                patchSelection[uid]?.contains(patch.name) ?: false
            }
    }

    @Parcelize
    data object Default : SelectionState {
        override fun patches(bundles: List<PatchBundleInfo.Scoped>, allowIncompatible: Boolean) =
            bundles.toPatchSelection(allowIncompatible) { _, patch -> patch.include }
    }
}

data class BundleRecommendationDetail(
    val bundleUid: Int,
    val name: String,
    val recommendedVersion: String?,
    val otherSupportedVersions: List<String>,
    val supportsAllVersions: Boolean
)

private fun PatchBundleInfo.Scoped.withoutUniversalPatches(): PatchBundleInfo.Scoped {
    if (universal.isEmpty()) return this

    val filteredPatches = patches.filter { it.compatiblePackages != null }
    return copy(
        patches = filteredPatches,
        universal = emptyList()
    )
}
