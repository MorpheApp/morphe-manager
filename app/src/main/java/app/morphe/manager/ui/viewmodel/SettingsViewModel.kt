package app.morphe.manager.ui.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.domain.installer.InstallerManager
import app.morphe.manager.domain.installer.RootInstaller
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.*
import app.morphe.manager.domain.repository.PatchBundleRepository.Companion.DEFAULT_SOURCE_UID
import app.morphe.manager.util.AppDataResolver
import app.morphe.manager.util.AppDataSource
import app.morphe.manager.util.syncFcmTopics
import app.morphe.manager.worker.UpdateCheckInterval
import app.morphe.manager.worker.UpdateCheckWorker
import app.morphe.patcher.dex.BytecodeMode
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

class SettingsViewModel(
    val prefs: PreferencesManager,
    private val installerManager: InstallerManager,
    private val rootInstaller: RootInstaller,
    private val selectionRepository: PatchSelectionRepository,
    private val optionsRepository: PatchOptionsRepository,
    patchBundleRepository: PatchBundleRepository,
    private val appDataResolver: AppDataResolver,
    originalApkRepository: OriginalApkRepository,
    installedAppRepository: InstalledAppRepository,
    private val appContext: Context,
) : ViewModel() {
    /** True when Google Play Services is available; FCM handles notifications on these devices. */
    val hasGms: Boolean = GoogleApiAvailability.getInstance()
        .isGooglePlayServicesAvailable(appContext) == ConnectionResult.SUCCESS

    /** True when POST_NOTIFICATIONS is granted (always true below Android 13). */
    fun hasNotificationPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

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
    val originalApkCount: StateFlow<Int> = originalApkRepository.getAll()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val patchedApkCount: StateFlow<Int> = installedAppRepository.getAll()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val patchedPackagesCount: StateFlow<Int> =
        selectionRepository.getPackagesWithSavedSelection()
            .map { it.size }
            .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /**
     * True for the duration of the current settings session after the user enables expert mode for the first time.
     * Reset to false when the user disables expert mode again.
     */
    var showExpertModeNotice by mutableStateOf(false)
        private set

    private var previousExpertMode: Boolean? = null

    /**
     * Must be called from the UI whenever useExpertMode changes so the VM can
     * derive [showExpertModeNotice] without holding its own coroutine-based observer here
     * (the pref is already observed as Compose state in the composable).
     */
    fun onExpertModeChanged(newValue: Boolean) {
        val prev = previousExpertMode
        if (prev != null && newValue && !prev) {
            showExpertModeNotice = true
        }
        if (!newValue) {
            showExpertModeNotice = false
        }
        previousExpertMode = newValue
    }

    fun setExpertMode(enabled: Boolean) = viewModelScope.launch {
        prefs.useExpertMode.update(enabled)
    }

    fun setProcessRuntime(enabled: Boolean) = viewModelScope.launch {
        prefs.useProcessRuntime.update(enabled)
    }

    fun setMemoryLimit(limit: Int) = viewModelScope.launch {
        prefs.patcherProcessMemoryLimit.update(limit)
    }

    fun setStripUnusedNativeLibs(enabled: Boolean) = viewModelScope.launch {
        prefs.stripUnusedNativeLibs.update(enabled)
    }

    fun setBytecodeMode(mode: BytecodeMode) = viewModelScope.launch {
        prefs.bytecodeModePreference.update(mode)
    }

    fun setGitHubPat(pat: String, includeInExport: Boolean) = viewModelScope.launch {
        prefs.gitHubPat.update(pat)
        prefs.includeGitHubPatInExports.update(includeInExport)
    }

    fun setPromptInstallerOnInstall(enabled: Boolean) = viewModelScope.launch {
        prefs.promptInstallerOnInstall.update(enabled)
    }

    /**
     * Requests root access when the AutoSaved (root-mount) installer is chosen,
     * then persists the selection.
     */
    fun confirmInstallerSelection(token: InstallerManager.Token) =
        viewModelScope.launch(Dispatchers.IO) {
            if (token == InstallerManager.Token.AutoSaved) {
                runCatching { rootInstaller.hasRootAccess() }
            }
            installerManager.updatePrimaryToken(token)
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
        return ensureValidEntries(raw, token, installerManager, installTarget)
    }

    fun parseInstallerToken(preference: String): InstallerManager.Token =
        installerManager.parseToken(preference)

    fun describeInstallerEntry(
        token: InstallerManager.Token,
        installTarget: InstallerManager.InstallTarget,
    ): InstallerManager.Entry? = installerManager.describeEntry(token, installTarget)

    fun openShizukuApp(): Boolean = installerManager.openShizukuApp()

    /** Summary flow: packageName → (bundleUid → patchCount) */
    val selectionsSummary: StateFlow<Map<String, Map<Int, Int>>> =
        selectionRepository.getSelectionsSummaryFlow()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Bundle uid → bundle name */
    val bundleNames: StateFlow<Map<Int, String>> =
        patchBundleRepository.sources
            .map { bundles -> bundles.associate { it.uid to it.name } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun resetAllSelections() = viewModelScope.launch(Dispatchers.IO) {
        selectionRepository.reset()
        optionsRepository.reset()
    }

    fun resetSelectionsForPackage(packageName: String) = viewModelScope.launch(Dispatchers.IO) {
        selectionRepository.resetSelectionForPackage(packageName)
        optionsRepository.resetOptionsForPackage(packageName)
    }

    fun resetSelectionsForPackageBundle(packageName: String, bundleUid: Int) =
        viewModelScope.launch(Dispatchers.IO) {
            selectionRepository.resetSelectionForPackageAndBundle(packageName, bundleUid)
            optionsRepository.resetOptionsForPackageAndBundle(packageName, bundleUid)
        }

    /** Counts total options across all packages (used by the "reset all" confirmation dialog). */
    suspend fun loadTotalOptionsCount(): Int = withContext(Dispatchers.IO) {
        val packages = optionsRepository.getPackagesWithSavedOptions().first()
        packages.sumOf { optionsRepository.getOptionsCountForPackage(it) }
    }

    /** Loads the options count for a single package (used by the per-package confirmation dialog). */
    suspend fun loadOptionsCountForPackage(packageName: String): Int =
        withContext(Dispatchers.IO) {
            optionsRepository.getOptionsCountForPackage(packageName)
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
        val displayName: String,
        val patchList: List<String>,
        val optionsMap: Map<String, Map<String, Any?>>,
    )

    /** Loads all display data for the patch-details dialog. */
    suspend fun loadPatchDetails(packageName: String, bundleUid: Int): PatchDetails =
        withContext(Dispatchers.IO) {
            val displayName = appDataResolver.resolveAppData(packageName).displayName
            val patchList = selectionRepository.exportForPackageAndBundle(packageName, bundleUid)
            val rawOptions = optionsRepository.exportOptionsForBundle(
                packageName = packageName,
                bundleUid = bundleUid
            )
            val optionsMap = rawOptions.mapValues { (_, patchOptions) ->
                patchOptions.mapValues { (_, jsonString) -> parseJsonValue(jsonString) }
            }
            PatchDetails(displayName, patchList, optionsMap)
        }

    companion object {
        /**
         * Builds a deduplicated list of [InstallerManager.Entry] objects, guaranteeing
         * that the currently-preferred [token] is always included even if the live
         * [installerManager] does not enumerate it.
         */
        fun ensureValidEntries(
            entries: List<InstallerManager.Entry>,
            token: InstallerManager.Token,
            installerManager: InstallerManager,
            installTarget: InstallerManager.InstallTarget,
        ): List<InstallerManager.Entry> {
            // Remove duplicates based on component name for Component tokens
            val normalized = buildList {
                val seen = mutableSetOf<Any>()
                entries.forEach { entry ->
                    val key = when (val entryToken = entry.token) {
                        is InstallerManager.Token.Component -> entryToken.componentName
                        else -> entryToken
                    }
                    if (seen.add(key)) add(entry)
                }
            }

            val tokenExists = token == InstallerManager.Token.Internal ||
                    token == InstallerManager.Token.AutoSaved ||
                    normalized.any { tokensEqual(it.token, token) }

            return if (tokenExists) normalized
            else installerManager.describeEntry(token, installTarget)
                ?.let { normalized + it } ?: normalized
        }

        fun tokensEqual(a: InstallerManager.Token?, b: InstallerManager.Token?): Boolean = when {
            a === b -> true
            a == null || b == null -> false
            a is InstallerManager.Token.Component && b is InstallerManager.Token.Component ->
                a.componentName == b.componentName
            else -> false
        }

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

        fun formatOptionValue(value: Any?): String = when (value) {
            null -> "null"
            is String -> value
            is Boolean -> value.toString()
            is Number -> value.toString()
            is List<*> -> if (value.isEmpty()) "[]" else value.joinToString(", ")
            else -> value.toString()
        }
    }
}
