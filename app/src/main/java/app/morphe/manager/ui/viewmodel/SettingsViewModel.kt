/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.domain.installer.InstallerManager
import app.morphe.manager.domain.installer.RootInstaller
import app.morphe.manager.domain.installer.SessionInstaller
import app.morphe.manager.domain.manager.AfterPatching
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.manager.RootPatchMode
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.domain.repository.PatchBundleRepository.Companion.DEFAULT_SOURCE_UID
import app.morphe.manager.domain.repository.PatchOptionsRepository
import app.morphe.manager.domain.repository.PatchSelectionRepository
import app.morphe.manager.domain.repository.SelectionSetRepository
import app.morphe.manager.domain.repository.SelectionSetSummary
import app.morphe.manager.domain.repository.SourceMuteRepository
import app.morphe.manager.patcher.runtime.jvm.JvmInstallation
import app.morphe.manager.network.api.ReleaseAPI
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.ui.screen.settings.system.CopyTarget
import app.morphe.manager.ui.screen.shared.CopySelectionCandidate
import app.morphe.manager.util.ApkDownloadHelperContract
import app.morphe.manager.util.AppDataResolver
import app.morphe.manager.util.AppDataSource
import app.morphe.manager.util.hasGooglePlayServices
import app.morphe.manager.util.syncFcmTopics
import app.morphe.manager.worker.UpdateCheckInterval
import app.morphe.manager.worker.UpdateCheckWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.util.Locale

/**
 * An app with saved patch selections or sets: its name as resolved for display, where that came
 * from, how many patches each source it has a selection from holds, by source uid, and its sets.
 */
@Immutable
data class SelectionApp(
    val packageName: String,
    val displayName: String,
    val dataSource: AppDataSource,
    val bundles: Map<Int, Int>,
    val sets: List<SelectionSetSummary>
)

/** An installed APK download helper as the trust list shows it. Trust is per app, not per activity. */
@Immutable
data class ApkDownloadHelperApp(
    val packageName: String,
    val label: String
)

class SettingsViewModel(
    val prefs: PreferencesManager,
    private val installerManager: InstallerManager,
    private val rootInstaller: RootInstaller,
    private val selectionRepository: PatchSelectionRepository,
    private val setRepository: SelectionSetRepository,
    private val sourceMuteRepository: SourceMuteRepository,
    private val optionsRepository: PatchOptionsRepository,
    private val patchBundleRepository: PatchBundleRepository,
    private val appDataResolver: AppDataResolver,
    private val api: ReleaseAPI,
    private val appContext: Context,
    jvmInstallation: JvmInstallation,
    /** State behind the copy panel, see [openCopy], and behind renaming and deleting sets. */
    val selectionPanel: SelectionPanelController
) : ViewModel() {
    /** Whether this device can patch in the embedded JVM at all. */
    val jvmRuntimeAvailable = jvmInstallation.isAvailable

    /** True when Google Play Services is available; FCM handles notifications on these devices. */
    val hasGms: Boolean = appContext.hasGooglePlayServices()

    /** True when POST_NOTIFICATIONS is granted (always true below Android 13). */
    fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    /**
     * Called when the user flips the manager prereleases switch.
     * Syncs FCM topics and triggers the update check via [onCheckUpdate].
     */
    fun toggleManagerPrereleases(
        currentValue: Boolean,
        backgroundNotificationsEnabled: Boolean,
        patchesPrereleaseIds: Set<String>,
        onCheckUpdate: () -> Unit
    ) = viewModelScope.launch {
        val newValue = !currentValue
        prefs.useManagerPrereleases.update(newValue)
        syncFcmTopics(
            notificationsEnabled = backgroundNotificationsEnabled,
            useManagerPrereleases = newValue,
            usePatchesPrereleases = patchesPrereleaseIds.contains(DEFAULT_SOURCE_UID.toString())
        )
        onCheckUpdate()
    }

    /**
     * Called when the user flips the background notifications switch.
     * Optimistically enables and shows the permission dialog if needed; onPermissionResult reverts if denied.
     */
    fun toggleBackgroundNotifications(
        currentValue: Boolean,
        useManagerPrereleases: Boolean,
        patchesPrereleaseIds: Set<String>,
        updateCheckInterval: UpdateCheckInterval,
        onShowPermissionDialog: () -> Unit
    ) = viewModelScope.launch {
        val newValue = !currentValue
        if (newValue && !hasNotificationPermission()) {
            prefs.backgroundUpdateNotifications.update(true)
            onShowPermissionDialog()
        } else {
            prefs.backgroundUpdateNotifications.update(newValue)
            syncFcmTopics(
                notificationsEnabled = newValue,
                useManagerPrereleases = useManagerPrereleases,
                usePatchesPrereleases = patchesPrereleaseIds.contains(DEFAULT_SOURCE_UID.toString())
            )
            if (newValue && !hasGms) UpdateCheckWorker.schedule(appContext, updateCheckInterval)
            else UpdateCheckWorker.cancel(appContext)
        }
    }

    /**
     * Handles the Android runtime permission result after the rationale dialog.
     * Reverts the optimistic pref update if the user denied the permission.
     */
    fun onNotificationPermissionResult(
        granted: Boolean,
        useManagerPrereleases: Boolean,
        patchesPrereleaseIds: Set<String>,
        updateCheckInterval: UpdateCheckInterval
    ) = viewModelScope.launch {
        if (granted) {
            syncFcmTopics(
                notificationsEnabled = true,
                useManagerPrereleases = useManagerPrereleases,
                usePatchesPrereleases = patchesPrereleaseIds.contains(DEFAULT_SOURCE_UID.toString())
            )
            if (!hasGms) UpdateCheckWorker.schedule(appContext, updateCheckInterval)
        } else {
            prefs.backgroundUpdateNotifications.update(false)
        }
    }

    /** User canceled the permission rationale dialog - revert the optimistic pref. */
    fun onNotificationPermissionDismissed() = viewModelScope.launch {
        prefs.backgroundUpdateNotifications.update(false)
    }

    /** Persists the selected update check interval and reschedules the worker on non-GMS devices. */
    fun selectUpdateInterval(interval: UpdateCheckInterval) = viewModelScope.launch {
        prefs.updateCheckInterval.update(interval)
        if (!hasGms) UpdateCheckWorker.schedule(appContext, interval)
    }

    /** Persists the allow-metered-updates preference. */
    fun toggleAllowMeteredUpdates(current: Boolean) = viewModelScope.launch {
        prefs.allowMeteredUpdates.update(!current)
    }

    /**
     * Turns the external batch patch entry point on or off. Disabling it also forgets every
     * app that was trusted before, so re-enabling starts from a clean allowlist.
     */
    fun toggleExternalBatchPatch(current: Boolean) = viewModelScope.launch {
        val enabled = !current
        prefs.externalBatchPatchEnabled.update(enabled)
        if (!enabled) prefs.externalBatchPatchAllowlist.update(emptySet())
    }

    /**
     * True for the duration of the current settings session after the user enables expert mode for the first time.
     * Reset to false when the user disables expert mode again.
     */
    fun setExpertMode(enabled: Boolean) = viewModelScope.launch {
        prefs.useExpertMode.update(enabled)
        if (enabled && !prefs.customFilePickerUserConfigured.get()) {
            prefs.useCustomFilePicker.update(true)
        }
    }

    fun setJvmRuntime(enabled: Boolean) = viewModelScope.launch {
        prefs.useJvmRuntime.update(enabled)
    }

    /** Enables/disables native library stripping for plain APKs, and simultaneously split APK filtering for split bundles. */
    fun setStripUnusedNativeLibs(enabled: Boolean) = viewModelScope.launch {
        prefs.stripUnusedNativeLibs.update(enabled)
    }

    /** Whether GitHub has turned down the token in use, which leaves requests going out without it. */
    val gitHubPatRejected = combine(prefs.gitHubPat.flow, api.rejectedPat) { pat, rejected ->
        pat.isNotBlank() && pat == rejected
    }

    suspend fun checkGitHubPat(pat: String) = api.checkGitHubPat(pat)

    fun setGitHubPat(pat: String, includeInExport: Boolean) = viewModelScope.launch {
        prefs.gitHubPat.update(pat)
        prefs.includeGitHubPatInExports.update(includeInExport)
    }

    fun setAfterPatching(action: AfterPatching) = viewModelScope.launch {
        prefs.afterPatching.update(action)
    }

    fun setNoMountOnly(enabled: Boolean) = viewModelScope.launch {
        prefs.noMountOnly.update(enabled)
    }

    fun setAutoUninstallWithShizuku(enabled: Boolean) = viewModelScope.launch {
        prefs.autoUninstallWithShizuku.update(enabled)
    }

    fun setIgnoreInstallTargetRules(enabled: Boolean) = viewModelScope.launch {
        prefs.ignoreInstallTargetRules.update(enabled)
    }

    /** Whether the device has root, which the patch mode and the rules tied to it depend on. */
    fun isDeviceRooted() = rootInstaller.isDeviceRooted()

    suspend fun isMagisk() = rootInstaller.isMagisk()

    fun setUseCustomFilePicker(enabled: Boolean) = viewModelScope.launch {
        prefs.useCustomFilePicker.update(enabled)
        prefs.customFilePickerUserConfigured.update(true)
    }

    private val _apkDownloadHelpers = MutableStateFlow(emptyList<ApkDownloadHelperApp>())

    /**
     * The APK download helpers installed on the device, one per app. Kept here rather than looked
     * up by each page that lists them, so a page coming back shows them at once instead of growing
     * a row under a scroll it restored, see [refreshApkDownloadHelpers].
     */
    val apkDownloadHelpers: StateFlow<List<ApkDownloadHelperApp>> = _apkDownloadHelpers.asStateFlow()

    /** Looks the installed helpers up again, as one may have come or gone since the last look. */
    fun refreshApkDownloadHelpers() = viewModelScope.launch {
        _apkDownloadHelpers.value = withContext(Dispatchers.IO) {
            ApkDownloadHelperContract.findHelpers(appContext)
                .distinctBy { it.componentName.packageName }
                .map { ApkDownloadHelperApp(packageName = it.componentName.packageName, label = it.label) }
        }
    }

    fun setApkDownloadHelperTrusted(packageName: String, trusted: Boolean) = viewModelScope.launch {
        val current = prefs.trustedApkDownloadHelpers.get()
        prefs.trustedApkDownloadHelpers.update(if (trusted) current + packageName else current - packageName)
    }

    fun setOpenApkDownloadHelperDirectly(enabled: Boolean) = viewModelScope.launch {
        prefs.openApkDownloadHelperDirectly.update(enabled)
    }

    fun setAutoDownloadApkHelpersInBatch(enabled: Boolean) = viewModelScope.launch {
        prefs.autoDownloadApkHelpersInBatch.update(enabled)
    }

    fun setPatcherCompletionSound(enabled: Boolean) = viewModelScope.launch {
        prefs.patcherCompletionSound.update(enabled)
    }

    fun setPatcherSuccessSoundUri(uri: String) = viewModelScope.launch {
        prefs.patcherSuccessSoundUri.update(uri)
    }

    fun setPatcherErrorSoundUri(uri: String) = viewModelScope.launch {
        prefs.patcherErrorSoundUri.update(uri)
    }

    /** Asks for root access when [token] installs with root, then saves it. */
    fun confirmInstallerSelection(token: InstallerManager.Token) =
        viewModelScope.launch(Dispatchers.IO) {
            if (token.usesRoot) runCatching { rootInstaller.hasRootAccess() }
            installerManager.updatePrimaryToken(token)
        }

    /** Asks for root access when [mode] can mount, then saves it. */
    fun setRootPatchMode(mode: RootPatchMode) = viewModelScope.launch(Dispatchers.IO) {
        if (mode != RootPatchMode.STANDARD) runCatching { rootInstaller.hasRootAccess() }
        prefs.rootPatchMode.update(mode)
    }

    /**
     * Returns a deduplicated, validated list of installer entries for [installTarget],
     * ensuring the currently-preferred [token] is always present in the list.
     */
    fun getInstallerEntries(
        installTarget: InstallerManager.InstallTarget,
        token: InstallerManager.Token,
    ): List<InstallerManager.Entry> {
        val raw = installerManager.listEntries(installTarget, includeNone = false)
        return installerManager.ensureValidEntries(raw, token, installTarget)
    }

    fun parseInstallerToken(preference: String): InstallerManager.Token =
        installerManager.parseToken(preference)

    fun describeInstallerEntry(
        token: InstallerManager.Token,
        installTarget: InstallerManager.InstallTarget,
    ): InstallerManager.Entry? = installerManager.describeEntry(token, installTarget)

    fun openShizukuApp(): Boolean = installerManager.openShizukuApp()

    fun getShizukuStatus(): SessionInstaller.ShizukuStatus = installerManager.shizukuStatus()

    fun requestShizukuPermission(): Boolean = installerManager.requestShizukuPermission()

    /** Summary flow: packageName → (bundleUid → patchCount) */
    val selectionsSummary: StateFlow<Map<String, Map<Int, Int>>> =
        selectionRepository.getSelectionsSummaryFlow()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Every set kept for any app. */
    val selectionSets: StateFlow<List<SelectionSetSummary>> =
        setRepository.summaries.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * The apps with saved selections or sets, each with its name resolved and in name order, emitted
     * once every name of a summary is in, so a list of them opens already in its order. Cold, so the
     * names are resolved for whoever collects it now rather than kept from an earlier look.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun selectionApps(): Flow<List<SelectionApp>> =
        combine(selectionRepository.getSelectionsSummaryFlow(), setRepository.summaries, ::Pair)
            .mapLatest { (summary, sets) ->
                val setsByPackage = sets.groupBy { it.packageName }
                coroutineScope {
                    (summary.keys + setsByPackage.keys).map { packageName ->
                        async {
                            val data = appDataResolver.resolveAppData(packageName)
                            SelectionApp(
                                packageName = packageName,
                                displayName = data.displayName,
                                dataSource = data.source,
                                bundles = summary[packageName].orEmpty(),
                                sets = setsByPackage[packageName].orEmpty()
                            )
                        }
                    }.awaitAll()
                }.sortedBy { it.displayName.lowercase(Locale.ROOT) }
            }
            .flowOn(Dispatchers.IO)

    fun deleteSet(set: SelectionSetSummary) {
        selectionPanel.deleteSet(viewModelScope, set)
    }

    fun renameSet(set: SelectionSetSummary, name: String) {
        selectionPanel.renameSet(viewModelScope, set, name)
    }

    /** Bundle uid → bundle name */
    val bundleNames: StateFlow<Map<Int, String>> =
        patchBundleRepository.sources
            .map { bundles -> bundles.associate { it.uid to it.name } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // Which sources an app is kept from is part of how it is configured, so a reset that clears
    // its selection and options clears that too. Otherwise, an app reset back to the defaults keeps
    // a narrowing nothing on screen still explains
    fun resetAllSelections() = viewModelScope.launch(Dispatchers.IO) {
        selectionRepository.reset()
        optionsRepository.reset()
        sourceMuteRepository.reset()
    }

    fun resetSelectionsForPackage(packageName: String) = viewModelScope.launch(Dispatchers.IO) {
        selectionRepository.resetSelectionForPackage(packageName)
        optionsRepository.resetOptionsForPackage(packageName)
        sourceMuteRepository.unmuteAll(packageName)
    }

    fun resetSelectionsForPackageBundle(packageName: String, bundleUid: Int) =
        viewModelScope.launch(Dispatchers.IO) {
            selectionRepository.resetSelectionForPackageAndBundle(packageName, bundleUid)
            optionsRepository.resetOptionsForPackageAndBundle(packageName, bundleUid)
            sourceMuteRepository.unmute(packageName, bundleUid)
        }

    /** Counts total options across all packages (used by the "reset all" confirmation dialog). */
    suspend fun loadTotalOptionsCount(): Int = withContext(Dispatchers.IO) {
        val packages = optionsRepository.getPackagesWithSavedOptions().first()
        packages.sumOf { optionsRepository.getOptionsCountForPackage(it) }
    }

    /** Loads the options count for a specific package+bundle (used by bundle confirmation dialog). */
    suspend fun loadOptionsCountForBundle(packageName: String, bundleUid: Int): Int =
        withContext(Dispatchers.IO) {
            optionsRepository.getOptionsCountForBundle(packageName, bundleUid)
        }

    /** Resolves display name and source for [packageName]. */
    suspend fun resolveAppDisplayName(packageName: String): Pair<String, AppDataSource> =
        withContext(Dispatchers.IO) {
            val data = appDataResolver.resolveAppData(packageName)
            data.displayName to data.source
        }

    data class PatchDetails(
        val patchList: List<String>,
        val optionsMap: Map<String, Map<String, Any?>>,
        /** Stored key to the name its bundle declares, for keys the bundle still knows. */
        val displayNames: Map<String, String>,
    )

    /** Loads patch selections and options for one package+bundle. */
    suspend fun loadPatchDetails(packageName: String, bundleUid: Int): PatchDetails =
        withContext(Dispatchers.IO) {
            patchDetails(
                packageName = packageName,
                bundleUid = bundleUid,
                patchList = selectionRepository.exportForPackageAndBundle(packageName, bundleUid),
                rawOptions = optionsRepository.exportOptionsForBundle(packageName, bundleUid)
            )
        }

    /** Loads what [set] holds, keyed by the source it holds it from. */
    suspend fun loadSetDetails(set: SelectionSetSummary): Map<Int, PatchDetails> =
        withContext(Dispatchers.IO) {
            val stored = setRepository.stored(set.uid) ?: return@withContext emptyMap()
            (stored.patches.keys + stored.options.keys).associateWith { bundleUid ->
                patchDetails(
                    packageName = stored.packageName,
                    bundleUid = bundleUid,
                    patchList = stored.patches[bundleUid].orEmpty(),
                    rawOptions = stored.options[bundleUid].orEmpty()
                )
            }
        }

    /** [patchList] and [rawOptions] saved from [bundleUid], named as the bundle names them today. */
    private suspend fun patchDetails(
        packageName: String,
        bundleUid: Int,
        patchList: List<String>,
        rawOptions: Map<String, Map<String, String>>
    ): PatchDetails {
        val optionsMap = rawOptions.mapValues { (_, patchOptions) ->
            patchOptions.mapValues { (_, jsonString) -> parseJsonValue(jsonString) }
        }
        val displayNames = targetBundlePatchInfos(packageName, bundleUid)
            .mapValues { (_, info) -> info.displayName }
        return PatchDetails(patchList, optionsMap, displayNames)
    }

    /** Opens the panel [target] is copied into from, with what it can copy loading. */
    fun openCopy(target: CopyTarget) = viewModelScope.launch {
        val targetPatchNames = withContext(Dispatchers.IO) {
            targetBundlePatchNames(target.packageName, target.bundleUid)
        }
        selectionPanel.open()
        selectionPanel.loadCandidates(viewModelScope, target.packageName, target.bundleUid, targetPatchNames)
    }

    /**
     * Replace the target's saved selection (and matching options) with what [candidate] holds of
     * it. Entries the target bundle no longer defines are dropped.
     */
    fun copySelectionFromBundle(
        target: CopyTarget,
        candidate: CopySelectionCandidate
    ) = viewModelScope.launch {
        val targetPatches = withContext(Dispatchers.IO) {
            targetBundlePatchInfos(target.packageName, target.bundleUid)
        }
        val copied = selectionPanel.resolveCopy(candidate, targetPatches) ?: return@launch
        if (copied.patches.isEmpty()) return@launch

        withContext(Dispatchers.IO) {
            selectionRepository.importForPackageAndBundle(
                packageName = target.packageName,
                bundleUid = target.bundleUid,
                patches = copied.patches.toList()
            )
            // Written even when empty, which clears the target's old options along with its selection
            optionsRepository.saveOptions(target.packageName, mapOf(target.bundleUid to copied.options))
        }
        selectionPanel.copied(copied.patches.size)
        selectionPanel.close()
    }

    private suspend fun targetBundlePatchNames(packageName: String, bundleUid: Int): Set<String> =
        targetBundlePatchInfos(packageName, bundleUid).keys

    /**
     * Patches the bundle offers for [packageName], keyed the way selections are stored.
     * Scoped to the app because that is where duplicate names get their suffix.
     */
    private suspend fun targetBundlePatchInfos(packageName: String, bundleUid: Int): Map<String, PatchInfo> {
        val info = patchBundleRepository.allBundlesInfoFlow.first()[bundleUid] ?: return emptyMap()
        return info.forPackage(packageName, null).patches.associateBy { it.name }
    }

    companion object {
        fun parseJsonValue(jsonString: String): Any? = try {
            val json = Json { ignoreUnknownKeys = true }
            when (val element = json.parseToJsonElement(jsonString)) {
                is JsonNull -> null
                is JsonPrimitive -> when {
                    element.isString -> element.content
                    element.booleanOrNull != null -> element.boolean
                    element.intOrNull != null -> element.int
                    element.longOrNull != null -> element.long
                    element.floatOrNull != null -> element.float
                    element.doubleOrNull != null -> element.double
                    else -> element.content
                }
                is JsonArray -> element.map { item ->
                    when (item) {
                        is JsonPrimitive -> when {
                            item.isString -> item.content
                            item.booleanOrNull != null -> item.boolean
                            item.intOrNull != null -> item.int
                            item.longOrNull != null -> item.long
                            item.floatOrNull != null -> item.float
                            else -> item.content
                        }
                        else -> item.toString()
                    }
                }
                else -> jsonString
            }
        } catch (_: Exception) {
            jsonString
        }
    }
}
