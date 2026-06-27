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
import app.morphe.manager.network.api.MorpheAPI
import app.morphe.manager.network.dto.MorpheAsset
import app.morphe.manager.network.service.HttpService
import app.morphe.manager.util.*
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.url
import kotlinx.coroutines.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.seconds

class UpdateViewModel(
    private val downloadOnScreenEntry: Boolean,
    private val network: NetworkInfo,
) : ViewModel(), KoinComponent {
    private val app: Application by inject()
    private val morpheAPI: MorpheAPI by inject()
    private val http: HttpService by inject()
    private val sessionInstaller: SessionInstaller by inject()
    private val networkInfo: NetworkInfo by inject()
    private val fs: Filesystem by inject()
    private val prefs: PreferencesManager by inject()
    private val installerManager: InstallerManager by inject()

    private var pendingExternalInstall: InstallerManager.InstallPlan.External? = null
    private var externalInstallTimeoutJob: Job? = null
    private var currentDownloadVersion: String? = null

    var downloadedSize by mutableLongStateOf(0L)
        private set
    var totalSize by mutableLongStateOf(0L)
        private set
    val downloadProgress by derivedStateOf {
        if (downloadedSize == 0L || totalSize == 0L) return@derivedStateOf 0f

        downloadedSize.toFloat() / totalSize.toFloat()
    }
    var showInternetCheckDialog by mutableStateOf(false)
    var state by mutableStateOf(State.CAN_DOWNLOAD)

    var installError by mutableStateOf("")

    // Release info for update dialog
    var releaseInfo: MorpheAsset? by mutableStateOf(null)
        private set

    // Changelog entry for the currently installed manager version (shown in Settings → Changelog)
    var currentVersionChangelogEntry: ChangelogEntry? by mutableStateOf(null)
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

    var canResumeDownload by mutableStateOf(false)
        private set

    private val location = fs.tempDir.resolve("updater.apk")
    private val job = viewModelScope.launch {
        uiSafe(app, R.string.download_manager_failed, "Failed to download Morphe Manager") {
            releaseInfo = morpheAPI.getAppUpdate()

            if (releaseInfo != null) {
                loadMissedChangelog()
            }

            if (downloadOnScreenEntry) {
                if (releaseInfo != null) {
                    downloadUpdate()
                } else {
                    state = State.CAN_DOWNLOAD
                }
            } else {
                state = State.CAN_DOWNLOAD
            }
        }
    }

    val isConnected: Boolean
        get() = network.isConnected()

    fun downloadUpdate(ignoreInternetCheck: Boolean = false) = viewModelScope.launch {
        uiSafe(app, R.string.failed_to_download_update, "Failed to download update") {
            val release = releaseInfo ?: return@uiSafe
            val allowMeteredUpdates = prefs.allowMeteredUpdates.get()

            if (!allowMeteredUpdates && networkInfo.isMetered() && !ignoreInternetCheck) {
                showInternetCheckDialog = true
                return@uiSafe
            }

            if (currentDownloadVersion != release.version) {
                currentDownloadVersion = release.version
                withContext(Dispatchers.IO) { location.delete() }
                downloadedSize = 0L
                totalSize = 0L
                canResumeDownload = false
            }

            val resumeOffset = withContext(Dispatchers.IO) {
                if (location.exists()) location.length() else 0L
            }
            downloadedSize = resumeOffset
            // totalSize stays 0 until first progress callback - avoids false 100% on resume
            totalSize = 0L
            canResumeDownload = resumeOffset > 0L

            state = State.DOWNLOADING

            try {
                withContext(Dispatchers.IO) {
                    if (resumeOffset == 0L) {
                        http.downloadToFile(
                            saveLocation = location,
                            builder = { url(release.downloadUrl) },
                            onProgress = { bytesRead, contentLength ->
                                downloadedSize = bytesRead
                                totalSize = contentLength ?: totalSize
                            }
                        )
                    } else {
                        http.download(location, resumeOffset) {
                            url(release.downloadUrl)
                            onDownload { bytesSentTotal, contentLength ->
                                downloadedSize = resumeOffset + bytesSentTotal
                                totalSize = resumeOffset + (contentLength ?: totalSize)
                            }
                        }
                    }
                }
                canResumeDownload = false
                installUpdate().join()
            } catch (error: Exception) {
                val downloaded = withContext(Dispatchers.IO) {
                    location.takeIf { it.exists() }?.length() ?: 0L
                }
                downloadedSize = downloaded
                if (totalSize < downloadedSize) totalSize = downloadedSize
                canResumeDownload = downloadedSize > 0L
                state = State.CAN_DOWNLOAD
                throw error
            }
        }
    }

    fun installUpdate() = viewModelScope.launch {
        pendingExternalInstall?.let(installerManager::cleanup)
        pendingExternalInstall = null
        externalInstallTimeoutJob?.cancel()
        externalInstallTimeoutJob = null
        installError = ""

        val plan = installerManager.resolvePlan(
            InstallerManager.InstallTarget.MANAGER_UPDATE,
            location,
            app.packageName,
            app.getString(R.string.app_name)
        )

        when (plan) {
            is InstallerManager.InstallPlan.Internal -> {
                state = State.INSTALLING
                sessionInstaller.launchIntentInstall(location)
                // Completion handled by installBroadcastReceiver;
                // cancellation handled by resetIfInstallCancelled() in the dialog
            }

            is InstallerManager.InstallPlan.Mount -> {
                val hint = app.getString(R.string.installer_status_not_supported)
                app.toast(app.getString(R.string.install_app_fail, hint))
                installError = hint
                canResumeDownload = false
                state = State.FAILED
            }

            is InstallerManager.InstallPlan.Shizuku -> {
                state = State.INSTALLING
                try {
                    handleInstallResult(sessionInstaller.installShizuku(location, app.packageName))
                } catch (_: InstallCancelledException) {
                    state = State.CAN_INSTALL
                } catch (e: Exception) {
                    val message = e.simpleMessage().orEmpty()
                    installError = message
                    canResumeDownload = false
                    app.toast(app.getString(R.string.install_app_fail, message))
                    state = State.FAILED
                }
            }

            is InstallerManager.InstallPlan.External -> launchExternalInstaller(plan)
        }
    }

    private fun handleInstallResult(result: InstallResult) {
        when (result) {
            InstallResult.Success -> {
                installError = ""
                state = State.SUCCESS
                app.toast(app.getString(R.string.install_app_success))
            }
            is InstallResult.Conflict -> {
                installError = app.getString(R.string.installer_hint_conflict)
                canResumeDownload = false
                app.toast(installError)
                state = State.FAILED
            }
            is InstallResult.Failure -> {
                val message = result.message ?: "Unknown error"
                installError = message
                canResumeDownload = false
                app.toast(app.getString(R.string.install_app_fail, message))
                state = State.FAILED
            }
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
            installError = error.simpleMessage().orEmpty()
            app.toast(app.getString(R.string.install_app_fail, error.simpleMessage()))
            state = State.FAILED
            return
        }

        state = State.INSTALLING

        externalInstallTimeoutJob = viewModelScope.launch {
            delay(EXTERNAL_INSTALL_TIMEOUT)
            if (pendingExternalInstall == plan) {
                installerManager.cleanup(plan)
                pendingExternalInstall = null
                installError = app.getString(R.string.installer_external_timeout, plan.installerLabel)
                app.toast(installError)
                state = State.FAILED
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

        job.cancel()
        location.delete()
    }

    /**
     * Reset state if an external installation timed out or was abandoned.
     */
    fun resetIfInstallCancelled() {
        // If we're in INSTALLING state but the pending installation was canceled,
        // reset to CAN_INSTALL so user can try again
        if (state == State.INSTALLING && pendingExternalInstall == null) {
            state = if (location.exists() && location.length() > 0) {
                State.CAN_INSTALL
            } else {
                canResumeDownload = false
                State.CAN_DOWNLOAD
            }
        }
    }

    /**
     * Load all changelog entries newer than the currently installed version.
     * Called automatically after a successful update check.
     */
    private fun loadMissedChangelog() = viewModelScope.launch {
        uiSafe(app, R.string.download_manager_failed, "Failed to load changelog") {
            val installedVersion = BuildConfig.VERSION_NAME.removePrefix("v")

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
            missedChangelogEntries = if (forDevBranch) newer
                else newer.filter { !it.version.contains('-') }
        }
    }

    /**
     * Load changelog entry for the currently installed manager version from CHANGELOG.md.
     * Reads the static CHANGELOG.md file.
     */
    fun loadCurrentVersionChangelog() = viewModelScope.launch {
        uiSafe(app, R.string.download_manager_failed, "Failed to load changelog") {
            val currentVersion = BuildConfig.VERSION_NAME.removePrefix("v")
            val entries = managerEntriesCache.getOrPut(morpheAPI.isDevBuild) {
                morpheAPI.fetchManagerChangelog()
            }
            currentVersionChangelogEntry = ChangelogParser.findVersion(entries, currentVersion)
        }
    }

    /**
     * Loads older stable changelog entries on demand, dropping versions in [exclude].
     * Always reads from main; older history is the stable release timeline by definition,
     * regardless of which channel the user is currently on.
     * Idempotent: repeat calls while loading or after a successful load are a no-op.
     */
    fun loadOlderManagerEntries(exclude: Set<String>) {
        if (isLoadingOlderEntries || olderManagerEntries != null) return
        isLoadingOlderEntries = true
        viewModelScope.launch(Dispatchers.Default) {
            uiSafe(app, R.string.download_manager_failed, "Failed to load older releases") {
                val entries = managerEntriesCache.getOrPut(false) {
                    morpheAPI.fetchManagerChangelog(forDevBranch = false)
                }
                val normalize = { v: String -> v.removePrefix("v").trim() }
                val excludeNorm = exclude.map(normalize).toSet()
                olderManagerEntries = entries.filter {
                    normalize(it.version) !in excludeNorm && !it.version.contains('-')
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
