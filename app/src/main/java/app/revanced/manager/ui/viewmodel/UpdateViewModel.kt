package app.revanced.manager.ui.viewmodel

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import androidx.annotation.StringRes
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.BuildConfig
import app.morphe.manager.R
import app.revanced.manager.data.platform.Filesystem
import app.revanced.manager.data.platform.NetworkInfo
import app.revanced.manager.network.api.ReVancedAPI
import app.revanced.manager.network.dto.ReVancedAsset
import app.revanced.manager.network.service.HttpService
import app.revanced.manager.domain.installer.InstallerManager
import app.revanced.manager.domain.installer.ShizukuInstaller
import app.revanced.manager.service.InstallService
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.network.utils.getOrNull
import app.revanced.manager.util.PM
import app.revanced.manager.util.toast
import app.revanced.manager.util.uiSafe
import app.revanced.manager.util.simpleMessage
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class UpdateViewModel(
    private val downloadOnScreenEntry: Boolean,
    private val network: NetworkInfo,
) : ViewModel(), KoinComponent {
    private val app: Application by inject()
    private val reVancedAPI: ReVancedAPI by inject()
    private val http: HttpService by inject()
    private val pm: PM by inject()
    private val shizukuInstaller: ShizukuInstaller by inject()
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
    var releaseInfo: ReVancedAsset? by mutableStateOf(null)
        private set

    // Release info for changelog dialog
    var currentVersionReleaseInfo: ReVancedAsset? by mutableStateOf(null)
        private set

    var canResumeDownload by mutableStateOf(false)
        private set

    private val location = fs.tempDir.resolve("updater.apk")
    private val job = viewModelScope.launch {
        uiSafe(app, R.string.download_manager_failed, "Failed to download Morphe Manager") {
            releaseInfo = reVancedAPI.getLatestAppInfo().getOrNull()

            if (downloadOnScreenEntry) {
                val isUpdate = releaseInfo?.version?.removePrefix("v") != BuildConfig.VERSION_NAME
                if (isUpdate) {
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
            withContext(Dispatchers.IO) {
                if (!allowMeteredUpdates && !networkInfo.isSafe() && !ignoreInternetCheck) {
                    showInternetCheckDialog = true
                } else {
                    if (currentDownloadVersion != release.version) {
                        currentDownloadVersion = release.version
                        if (location.exists()) {
                            location.delete()
                        }
                        downloadedSize = 0L
                        totalSize = 0L
                        canResumeDownload = false
                    }

                    val resumeOffset = if (location.exists()) location.length() else 0L
                    downloadedSize = resumeOffset
                    totalSize = resumeOffset
                    canResumeDownload = resumeOffset > 0L

                    state = State.DOWNLOADING

                    try {
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
                                    totalSize = resumeOffset + contentLength
                                }
                            }
                        }
                        canResumeDownload = false
                        installUpdate()
                    } catch (error: Exception) {
                        downloadedSize = location.takeIf { it.exists() }?.length() ?: 0L
                        if (totalSize < downloadedSize) {
                            totalSize = downloadedSize
                        }
                        canResumeDownload = downloadedSize > 0L
                        state = State.CAN_DOWNLOAD
                        throw error
                    }
                }
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
                pm.installApp(listOf(location))
            }

            is InstallerManager.InstallPlan.Mount -> {
                val hint = app.getString(R.string.installer_status_not_supported)
                app.toast(app.getString(R.string.install_app_fail, hint))
                installError = hint
                state = State.FAILED
            }

            is InstallerManager.InstallPlan.Shizuku -> {
                state = State.INSTALLING
                try {
                    shizukuInstaller.install(location, app.packageName)
                    installError = ""
                    state = State.SUCCESS
                    app.toast(app.getString(R.string.update_completed))
                } catch (error: ShizukuInstaller.InstallerOperationException) {
                    val message = error.message ?: app.getString(R.string.installer_hint_generic)
                    installError = message
                    app.toast(app.getString(R.string.install_app_fail, message))
                    state = State.FAILED
                } catch (error: Exception) {
                    val message = error.simpleMessage().orEmpty()
                    installError = message
                    app.toast(app.getString(R.string.install_app_fail, message))
                    state = State.FAILED
                }
            }

            is InstallerManager.InstallPlan.External -> launchExternalInstaller(plan)
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
            delay(EXTERNAL_INSTALL_TIMEOUT_MS)
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
        val plan = pendingExternalInstall ?: return
        if (plan.expectedPackage != packageName) return

        pendingExternalInstall = null
        externalInstallTimeoutJob?.cancel()
        externalInstallTimeoutJob = null
        installerManager.cleanup(plan)

        installError = ""
        app.toast(app.getString(R.string.installer_external_success, plan.installerLabel))
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

                InstallService.APP_INSTALL_ACTION -> {
                    val pmStatus = intent.getIntExtra(InstallService.EXTRA_INSTALL_STATUS, -999)
                    val extra =
                        intent.getStringExtra(InstallService.EXTRA_INSTALL_STATUS_MESSAGE) ?: ""

                    when (pmStatus) {
                        PackageInstaller.STATUS_SUCCESS -> {
                            pendingExternalInstall?.let(installerManager::cleanup)
                            pendingExternalInstall = null
                            externalInstallTimeoutJob?.cancel()
                            externalInstallTimeoutJob = null
                            installError = ""
                            app.toast(app.getString(R.string.install_app_success))
                            state = State.SUCCESS
                        }
                        PackageInstaller.STATUS_FAILURE_ABORTED -> {
                            pendingExternalInstall?.let(installerManager::cleanup)
                            pendingExternalInstall = null
                            externalInstallTimeoutJob?.cancel()
                            externalInstallTimeoutJob = null
                            state = State.CAN_INSTALL
                        }
                        else -> {
                            val hint = installerManager.formatFailureHint(pmStatus, extra)
                            val message = app.getString(
                                R.string.install_app_fail,
                                hint ?: extra.ifBlank { pmStatus.toString() }
                            )
                            pendingExternalInstall?.let(installerManager::cleanup)
                            pendingExternalInstall = null
                            externalInstallTimeoutJob?.cancel()
                            externalInstallTimeoutJob = null
                            app.toast(message)
                            installError = hint ?: extra
                            state = State.FAILED
                        }
                    }
                }
            }
        }
    }

    init {
        ContextCompat.registerReceiver(app, installBroadcastReceiver, IntentFilter().apply {
            addAction(InstallService.APP_INSTALL_ACTION)
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onCleared() {
        super.onCleared()
        app.unregisterReceiver(installBroadcastReceiver)

        pendingExternalInstall?.let(installerManager::cleanup)
        pendingExternalInstall = null
        externalInstallTimeoutJob?.cancel()
        externalInstallTimeoutJob = null

        job.cancel()
        location.delete()
    }

    /**
     * Reset state if installation was cancelled by user (dismissed system dialog)
     * Called when dialog reopens to check if we need to reset
     */
    fun resetIfInstallCancelled() {
        // If we're in INSTALLING state but the pending install was cancelled,
        // reset to CAN_INSTALL so user can try again
        if (state == State.INSTALLING && pendingExternalInstall == null) {
            // Check if the APK file still exists
            if (location.exists() && location.length() > 0) {
                state = State.CAN_INSTALL
            } else {
                // File was deleted somehow, need to download again
                state = State.CAN_DOWNLOAD
                canResumeDownload = false
            }
        }
    }

    /**
     * Load changelog for currently installed version
     */
    fun loadCurrentVersionChangelog() = viewModelScope.launch {
        uiSafe(app, R.string.download_manager_failed, "Failed to load changelog") {
            val currentVersion = "v${BuildConfig.VERSION_NAME}"
            currentVersionReleaseInfo = reVancedAPI.getManagerReleaseByVersion(currentVersion).getOrNull()
        }
    }

    companion object {
        private const val EXTERNAL_INSTALL_TIMEOUT_MS = 60_000L
    }

    enum class State(@StringRes val title: Int) {
        CAN_DOWNLOAD(R.string.update_available),
        DOWNLOADING(R.string.downloading_manager_update),
        CAN_INSTALL(R.string.ready_to_install_update),
        INSTALLING(R.string.installing_manager_update),
        FAILED(R.string.install_update_manager_failed),
        SUCCESS(R.string.update_completed)
    }
}
