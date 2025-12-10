package app.revanced.manager.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Parcelable
import android.util.Log
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
import app.revanced.manager.network.downloader.LoadedDownloaderPlugin
import app.revanced.manager.network.downloader.ParceledDownloaderData
import app.revanced.manager.patcher.patch.PatchBundleInfo.Extensions.requiredOptionsSet
import app.revanced.manager.plugin.downloader.GetScope
import app.revanced.manager.plugin.downloader.PluginHostApi
import app.revanced.manager.plugin.downloader.UserInteractionException
import app.revanced.manager.ui.model.SelectedApp
import app.revanced.manager.ui.model.navigation.Patcher
import app.revanced.manager.ui.model.navigation.SelectedApplicationInfo
import app.revanced.manager.util.Options
import app.revanced.manager.util.PM
import app.revanced.manager.util.PatchSelection
import app.revanced.manager.util.simpleMessage
import app.revanced.manager.util.toast
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
    private val storageInputFile = File(filesystem.uiTempDir, "profile_input.apk").apply { delete() }
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
    var options: Options by savedStateHandle.saveable {
        val state = mutableStateOf<Options>(emptyMap())

        optionsLoadJob = viewModelScope.launch {
            if (!persistConfiguration) return@launch // TODO: save options for patched apps.
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

    suspend fun awaitOptions(): Options {
        optionsLoadJob?.join()
        return options
    }

    private var selectionState: SelectionState by savedStateHandle.saveable {
        if (input.patches != null)
            return@saveable mutableStateOf(SelectionState.Customized(input.patches))

        val selection: MutableState<SelectionState> = mutableStateOf(SelectionState.Default)

        // Try to get the previous selection if customization is enabled.
        selectionLoadJob = viewModelScope.launch {
            val previous = selectionRepository.getSelection(packageName)
            if (previous.values.sumOf { it.size } == 0) return@launch
            selection.value = SelectionState.Customized(previous)
        }

        selection
    }

    var showSourceSelector by mutableStateOf(requiresSourceSelection || _selectedApp is SelectedApp.Search)
        private set
    private var pluginAction: Pair<LoadedDownloaderPlugin, Job>? by mutableStateOf(null)
    val activePluginAction get() = pluginAction?.first?.packageName
    private var launchedActivity by mutableStateOf<CompletableDeferred<ActivityResult>?>(null)
    private val launchActivityChannel = Channel<Intent>()
    val launchActivityFlow = launchActivityChannel.receiveAsFlow()

    val errorFlow = combine(plugins, snapshotFlow { selectedApp }) { pluginsList, app ->
        when {
            app is SelectedApp.Search && pluginsList.isEmpty() -> Error.NoPlugins
            else -> null
        }
    }

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
                withContext(Dispatchers.IO) {
                    plugin.get(scope, packageName, targetVersion)
                }?.let { (data, version) ->
                    if (targetVersion != null && version != targetVersion) {
                        app.toast(app.getString(R.string.downloader_invalid_version))
                        return@launch
                    }
                    selectedApp = SelectedApp.Download(
                        packageName,
                        version,
                        ParceledDownloaderData(plugin, data)
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
                dismissSourceSelector()
            }
        }
    }

    fun handlePluginActivityResult(result: ActivityResult) {
        launchedActivity?.complete(result)
    }

    private fun invalidateSelectedAppInfo() = viewModelScope.launch {
        val info = when (val app = selectedApp) {
            is SelectedApp.Local -> withContext(Dispatchers.IO) { pm.getPackageInfo(app.file) }
            is SelectedApp.Installed -> withContext(Dispatchers.IO) { pm.getPackageInfo(app.packageName) }
            else -> null
        }

        selectedAppInfo = info
    }

    fun getOptionsFiltered(bundles: List<PatchBundleInfo.Scoped>) = options.filtered(bundles)
    suspend fun hasSetRequiredOptions(patchSelection: PatchSelection) = bundleInfoFlow
        .first()
        .requiredOptionsSet(
            allowIncompatible = prefs.disablePatchVersionCompatCheck.get(),
            isSelected = { bundle, patch -> patch.name in patchSelection[bundle.uid]!! },
            optionsForPatch = { bundle, patch -> options[bundle.uid]?.get(patch.name) },
        )

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


