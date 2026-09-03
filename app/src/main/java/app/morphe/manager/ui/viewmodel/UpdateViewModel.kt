package app.morphe.manager.ui.viewmodel

import android.app.Application
import android.content.*
import androidx.annotation.StringRes
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.BuildConfig
import app.morphe.manager.R
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.data.platform.NetworkInfo
import app.morphe.manager.domain.installer.InstallCancelledException
import app.morphe.manager.domain.installer.InstallResult
import app.morphe.manager.domain.installer.InstallerManager
import app.morphe.manager.domain.installer.SessionInstaller
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.ManagerUpdateRepository
import app.morphe.manager.network.api.MorpheAPI
import app.morphe.manager.network.dto.MorpheAsset
import app.morphe.manager.network.service.AssetDownloader
import app.morphe.manager.util.*
import kotlinx.coroutines.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

/**
 * Drives the manager self-update, from the release lookup down to handing the APK to an
 * installer. The download is staged at a fixed path, so this must live as a single instance
 * shared by every screen that shows update or changelog UI.
 */
class UpdateViewModel : ViewModel(), KoinComponent {
    private val app: Application by inject()
    private val morpheAPI: MorpheAPI by inject()
    private val managerUpdateRepository: ManagerUpdateRepository by inject()
    private val assetDownloader: AssetDownloader by inject()
    private val sessionInstaller: SessionInstaller by inject()
    private val networkInfo: NetworkInfo by inject()
    private val fs: Filesystem by inject()
    private val prefs: PreferencesManager by inject()
    private val installerManager: InstallerManager by inject()

    private var pendingExternalInstall: InstallerManager.InstallPlan.External? = null
    private var externalInstallTimeoutJob: Job? = null

    // Only an install handed to the system installer has to be inferred from the app returning
    // to the foreground. Shizuku reports its own outcome, an external one has a pending plan
    private var installHandedOff = false

    var downloadedSize by mutableLongStateOf(0L)
        private set
    var totalSize by mutableLongStateOf(0L)
        private set
    val downloadProgress by derivedStateOf {
        if (totalSize <= 0L) return@derivedStateOf 0f

        (downloadedSize.toFloat() / totalSize).coerceIn(0f, 1f)
    }
    var showInternetCheckDialog by mutableStateOf(false)
    var state by mutableStateOf(State.CAN_DOWNLOAD)

    var installError by mutableStateOf("")

    // Release info for update dialog
    var releaseInfo: MorpheAsset? by mutableStateOf(null)
        private set

    // True while an update check is in flight, so the dialog can tell a check that is still
    // resolving apart from one that resolved to nothing
    var isCheckingForUpdate by mutableStateOf(true)
        private set

    // Changelog entries for the current channel (shown in Settings → Changelog).
    // Stable channel: single entry for the installed version.
    // Prerelease channel: the installed dev version and every preceding dev entry down to
    // (but not including) the last stable release; the "Show older" expander then continues
    // from the stable baseline.
    var currentChannelChangelogEntries: List<ChangelogEntry>? by mutableStateOf(null)
        private set

    // All changelog entries newer than the currently installed version (shown in update dialog)
    var missedChangelogEntries: List<ChangelogEntry>? by mutableStateOf(null)
        private set

    // Older changelog entries loaded on-demand by the "Show older releases" expander.
    // Reset on dialog dismiss so the expander reopens in a collapsed state next time
    var olderManagerEntries: List<ChangelogEntry>? by mutableStateOf(null)
        private set
    var isLoadingOlderEntries by mutableStateOf(false)
        private set

    // Parsed CHANGELOG.md per branch (false = main, true = dev). Shared across all loaders
    // and the older-entries expander to avoid duplicate fetches inside one VM lifetime
    private val managerEntriesCache = mutableMapOf<Boolean, List<ChangelogEntry>>()

    private val location = fs.tempDir.resolve("updater.apk")
    private var job = resolveUpdate()

    /**
     * Resolves the available update through [ManagerUpdateRepository] so the dialog shows the
     * same release the home banner announced, then loads its changelog.
     */
    private fun resolveUpdate() = viewModelScope.launch {
        isCheckingForUpdate = true
        try {
            uiSafe(app, R.string.download_manager_failed, "Failed to download Morphe Manager") {
                releaseInfo = managerUpdateRepository.getOrRefresh()
            }
        } finally {
            isCheckingForUpdate = false
        }

        if (releaseInfo == null) {
            state = State.CAN_DOWNLOAD
            return@launch
        }

        loadMissedChangelog()

        state = State.CAN_DOWNLOAD
    }

    /**
     * Re-runs the update check. Offered when the check resolved nothing, which happens while
     * a release is announced but its APK is still uploading.
     */
    fun retryUpdateCheck() {
        if (isCheckingForUpdate) return
        job = resolveUpdate()
    }

    fun downloadUpdate(ignoreInternetCheck: Boolean = false) = viewModelScope.launch {
        uiSafe(app, R.string.failed_to_download_update, "Failed to download update") {
            val release = releaseInfo ?: return@uiSafe
            val allowMeteredUpdates = prefs.allowMeteredUpdates.get()

            if (!allowMeteredUpdates && networkInfo.isMetered() && !ignoreInternetCheck) {
                showInternetCheckDialog = true
                return@uiSafe
            }

            downloadedSize = 0L
            // Left at 0 until the first progress callback reports the release size, so the dialog
            // shows an indeterminate bar rather than one pinned at zero while bytes are arriving
            totalSize = 0L
            state = State.DOWNLOADING

            try {
                withContext(Dispatchers.IO) {
                    // Routed through AssetDownloader so the manager update survives a blocked
                    // GitHub the same way patch bundles do
                    assetDownloader.downloadToFile(
                        downloadUrl = release.downloadUrl,
                        saveLocation = location,
                        onProgress = { bytesRead, contentLength ->
                            downloadedSize = bytesRead
                            totalSize = contentLength ?: totalSize
                        }
                    )
                }
                requireApkArchive(location)
                installUpdate().join()
            } catch (error: Exception) {
                resetToDownload()
                throw error
            }
        }
    }

    /**
     * Rejects a download that transferred cleanly but is not an APK, so the installer is never
     * handed an error page or an API response that arrived in the file's place. The file is
     * dropped as well, so nothing is left staged that a later install could pick up.
     */
    private suspend fun requireApkArchive(location: File) = withContext(Dispatchers.IO) {
        if (location.hasZipHeader()) return@withContext

        val size = runCatching { location.length() }.getOrDefault(0L)
        runCatching { location.delete() }
        throw IOException("The downloaded update is not an APK (size=$size)")
    }

    fun installUpdate() = viewModelScope.launch {
        pendingExternalInstall?.let(installerManager::cleanup)
        pendingExternalInstall = null
        externalInstallTimeoutJob?.cancel()
        externalInstallTimeoutJob = null
        installHandedOff = false
        installError = ""

        // The download is staged in a directory that is wiped on every process start, so an
        // install started from a dialog that outlived it has nothing left to hand over
        if (!hasDownloadedApk()) {
            resetToDownload()
            app.toast(app.getString(R.string.update_download_missing))
            return@launch
        }

        val plan = installerManager.resolvePlan(
            InstallerManager.InstallTarget.MANAGER_UPDATE,
            location,
            app.packageName,
            app.getString(R.string.app_name)
        )

        when (plan) {
            // Completion is handled by installBroadcastReceiver;
            // cancellation by resetIfInstallCancelled() in the dialog
            is InstallerManager.InstallPlan.Internal ->
                launchSystemInstall { sessionInstaller.launchIntentInstall(location) }

            is InstallerManager.InstallPlan.PlayStore ->
                launchSystemInstall { sessionInstaller.launchPlayStoreInstall(location) }

            is InstallerManager.InstallPlan.RootPlayStore,
            is InstallerManager.InstallPlan.ShizukuPlayStore,
            is InstallerManager.InstallPlan.Mount ->
                failInstall(app.getString(R.string.installer_status_not_supported))

            is InstallerManager.InstallPlan.Shizuku -> {
                state = State.INSTALLING
                try {
                    handleInstallResult(sessionInstaller.installShizuku(location, app.packageName))
                } catch (_: InstallCancelledException) {
                    state = State.CAN_INSTALL
                } catch (error: Exception) {
                    failInstall(error.simpleMessage().orEmpty())
                }
            }

            is InstallerManager.InstallPlan.External -> launchExternalInstaller(plan)
        }
    }

    /**
     * Hands the APK to an installer activity. Launching can still fail on devices where no
     * activity claims the install intent, which must not take the app down with it.
     */
    private fun launchSystemInstall(startInstaller: () -> Unit) {
        state = State.INSTALLING
        try {
            startInstaller()
            installHandedOff = true
        } catch (error: Exception) {
            failInstall(error.simpleMessage().orEmpty())
        }
    }

    /**
     * Ends the attempt in [State.FAILED], showing [message] in the dialog and [toastMessage] as a toast.
     */
    private fun failInstall(
        message: String,
        toastMessage: String = app.getString(R.string.install_app_fail, message)
    ) {
        installError = message
        app.toast(toastMessage)
        state = State.FAILED
    }

    /** Clears the progress of a download that produced nothing and offers to start it over. */
    private fun resetToDownload() {
        downloadedSize = 0L
        totalSize = 0L
        state = State.CAN_DOWNLOAD
    }

    /** Whether the staged update is still on disk and holds anything worth installing. */
    private fun hasDownloadedApk() = location.exists() && location.length() > 0

    private fun handleInstallResult(result: InstallResult) {
        when (result) {
            InstallResult.Success -> {
                installError = ""
                state = State.SUCCESS
                app.toast(app.getString(R.string.install_app_success))
            }
            is InstallResult.Conflict -> {
                val hint = app.getString(R.string.installer_hint_conflict)
                failInstall(hint, toastMessage = hint)
            }
            is InstallResult.Failure -> failInstall(result.message ?: "Unknown error")
        }
    }

    private fun launchExternalInstaller(plan: InstallerManager.InstallPlan.External) {
        pendingExternalInstall?.let(installerManager::cleanup)
        externalInstallTimeoutJob?.cancel()

        pendingExternalInstall = plan
        installError = ""
        try {
            // Add FLAG_ACTIVITY_NEW_TASK since we're starting from Application context
            plan.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(plan.intent)
            app.toast(app.getString(R.string.installer_external_launched, plan.installerLabel))
        } catch (error: ActivityNotFoundException) {
            installerManager.cleanup(plan)
            pendingExternalInstall = null
            failInstall(error.simpleMessage().orEmpty())
            return
        }

        state = State.INSTALLING

        externalInstallTimeoutJob = viewModelScope.launch {
            delay(EXTERNAL_INSTALL_TIMEOUT)
            if (pendingExternalInstall == plan) {
                installerManager.cleanup(plan)
                pendingExternalInstall = null
                val timedOut = app.getString(R.string.installer_external_timeout, plan.installerLabel)
                failInstall(timedOut, toastMessage = timedOut)
                externalInstallTimeoutJob = null
            }
        }
    }

    private fun handleExternalInstallSuccess(packageName: String) {
        val plan = pendingExternalInstall
        if (plan != null) {
            if (plan.expectedPackage != packageName) return
            pendingExternalInstall = null
            externalInstallTimeoutJob?.cancel()
            externalInstallTimeoutJob = null
            installerManager.cleanup(plan)
            app.toast(app.getString(R.string.installer_external_success, plan.installerLabel))
        } else {
            // Intent-based fallback - only care about our own package
            if (packageName != app.packageName) return
        }
        installError = ""
        state = State.SUCCESS
    }

    private val installBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REPLACED -> {
                    val pkg = intent.data?.schemeSpecificPart ?: return
                    handleExternalInstallSuccess(pkg)
                }
            }
        }
    }

    init {
        ContextCompat.registerReceiver(app, installBroadcastReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onCleared() {
        app.unregisterReceiver(installBroadcastReceiver)

        pendingExternalInstall?.let(installerManager::cleanup)
        pendingExternalInstall = null
        externalInstallTimeoutJob?.cancel()
        externalInstallTimeoutJob = null

        // The staged APK is deliberately left behind: an installer launched from here may still
        // be reading it, and Filesystem clears the directory on the next process start anyway
        job.cancel()
    }

    /**
     * Reset state if an external installation timed out or was abandoned.
     */
    fun resetIfInstallCancelled() {
        // If we're in INSTALLING state but the pending installation was canceled,
        // reset to CAN_INSTALL so user can try again
        if (state == State.INSTALLING && installHandedOff && pendingExternalInstall == null) {
            installHandedOff = false
            if (hasDownloadedApk()) state = State.CAN_INSTALL else resetToDownload()
        }
    }

    /**
     * Load all changelog entries newer than the currently installed version.
     * Called automatically after a successful update check.
     */
    private fun loadMissedChangelog() = viewModelScope.launch {
        uiSafe(app, R.string.download_manager_failed, "Failed to load changelog") {
            val installedVersion = BuildConfig.VERSION_NAME.normalizeVersion()

            // Use the dev branch if EITHER the installed version is a dev build OR the available
            // update is a pre-release. Without this, a stable user who has "Use pre-releases"
            // enabled would fetch CHANGELOG.md from main, which doesn't contain dev entries,
            // causing entriesNewerThan() to return an empty list even though a newer dev version
            // is available and its changelog lives on the dev branch
            val targetIsPrerelease = releaseInfo?.version?.contains('-') == true
            val forDevBranch = morpheAPI.isDevBuild || targetIsPrerelease
            val entries = managerEntriesCache.getOrPut(forDevBranch) {
                morpheAPI.fetchManagerChangelog(forDevBranch = forDevBranch)
            }
            val newer = ChangelogParser.entriesNewerThan(entries, installedVersion)
            // Strip pre-release entries when on stable channel - main CHANGELOG.md
            // contains merged pre-release entries that stable users should not see
            val filtered = if (forDevBranch) newer else newer.filter { !it.isPrerelease }
            // Right after a release the raw CDN can still serve a CHANGELOG.md that predates
            // it, so fall back to the notes the release itself carries rather than show nothing
            missedChangelogEntries = filtered.ifEmpty { listOfNotNull(releaseNotesEntry()) }
        }
    }

    /**
     * Builds a changelog entry from the release notes attached to the update.
     * The leading version heading is dropped because the entry header already shows it.
     */
    private fun releaseNotesEntry(): ChangelogEntry? {
        val release = releaseInfo ?: return null
        val lines = release.description.trim().lines()
        val body = if (lines.firstOrNull()?.trimStart()?.startsWith("# ") == true) lines.drop(1) else lines
        val notes = body.joinToString("\n").trim()

        return if (notes.isBlank()) null else ChangelogEntry(
            version = release.version.normalizeVersion(),
            date = release.createdAt.date.toString(),
            content = notes
        )
    }

    /**
     * Load changelog entries for the current channel from CHANGELOG.md.
     *
     * Stable channel: the installed version's entry only.
     * Prerelease channel: the installed dev version and every preceding dev entry down to
     * (but not including) the last stable release.
     */
    fun loadCurrentVersionChangelog() = viewModelScope.launch {
        uiSafe(app, R.string.download_manager_failed, "Failed to load changelog") {
            val currentVersion = BuildConfig.VERSION_NAME.normalizeVersion()
            val forDevBranch = morpheAPI.isDevBuild
            val entries = managerEntriesCache.getOrPut(forDevBranch) {
                morpheAPI.fetchManagerChangelog(forDevBranch = forDevBranch)
            }
            currentChannelChangelogEntries = if (forDevBranch) {
                val installedIdx = entries.indexOfFirst { it.version.normalizeVersion() == currentVersion }
                // Fall back to the newest dev entry when the installed version is absent
                val start = if (installedIdx >= 0) installedIdx else 0
                entries.drop(start).takeWhile { it.isPrerelease }
            } else {
                listOfNotNull(ChangelogParser.findVersion(entries, currentVersion))
            }
        }
    }

    /**
     * Loads older stable changelog entries on demand. Always reads from main; older history
     * is the stable release timeline by definition, regardless of which channel the user is
     * currently on. Versions already shown above (via [currentChannelChangelogEntries] or
     * [missedChangelogEntries]) are filtered out so the expander lists new content only.
     * Idempotent: repeat calls while loading or after a successful load are a no-op.
     */
    fun loadOlderManagerEntries() {
        if (isLoadingOlderEntries || olderManagerEntries != null) return
        isLoadingOlderEntries = true
        val exclude = (currentChannelChangelogEntries.orEmpty() + missedChangelogEntries.orEmpty())
            .map { it.version.normalizeVersion() }
            .toSet()
        viewModelScope.launch(Dispatchers.Default) {
            uiSafe(app, R.string.download_manager_failed, "Failed to load older releases") {
                val entries = managerEntriesCache.getOrPut(false) {
                    morpheAPI.fetchManagerChangelog(forDevBranch = false)
                }
                olderManagerEntries = entries.filter {
                    it.version.normalizeVersion() !in exclude && !it.isPrerelease
                }
            }
            isLoadingOlderEntries = false
        }
    }

    fun resetOlderManagerEntries() {
        olderManagerEntries = null
        isLoadingOlderEntries = false
    }

    companion object {
        private val EXTERNAL_INSTALL_TIMEOUT = 60.seconds
    }

    enum class State(@param:StringRes val title: Int) {
        CAN_DOWNLOAD(R.string.update_available),
        DOWNLOADING(R.string.downloading_manager_update),
        CAN_INSTALL(R.string.ready_to_install_update),
        INSTALLING(R.string.installing_manager_update),
        FAILED(R.string.install_update_manager_failed),
        SUCCESS(R.string.update_completed)
    }
}
