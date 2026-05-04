package app.morphe.manager.ui.viewmodel

import android.app.Application
import android.content.*
import android.content.pm.PackageInfo
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.R
import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.domain.installer.*
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.util.AppDataResolver
import app.morphe.manager.util.PM
import app.morphe.manager.util.simpleMessage
import app.morphe.manager.util.toast
import kotlinx.coroutines.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Centralized view model for all installation operations, mounting/unmounting and exporting.
 * Handles installation with support for multiple installers (Standard, Shizuku, Root, External).
 */
class InstallViewModel : ViewModel(), KoinComponent {
    private val app: Application by inject()
    private val pm: PM by inject()
    private val rootInstaller: RootInstaller by inject()
    private val sessionInstaller: SessionInstaller by inject()
    private val installerManager: InstallerManager by inject()
    private val prefs: PreferencesManager by inject()
    private val appDataResolver: AppDataResolver by inject()

    /**
     * Current installation state.
     */
    sealed class InstallState {
        /** Ready to install - shows Install button. */
        data object Ready : InstallState()

        /** Currently installing - shows progress indicator. */
        data object Installing : InstallState()

        /** Successfully installed - shows Open button. */
        data class Installed(val packageName: String) : InstallState()

        /** Signature conflict detected - shows Uninstall button. */
        data class Conflict(val packageName: String) : InstallState()

        /** Installation error - shows error message and retry. */
        data class Error(val message: String) : InstallState()
    }

    /**
     * State for installer unavailability dialog.
     */
    data class InstallerUnavailableState(
        val installerToken: InstallerManager.Token,
        val reason: Int?,
        val canOpenApp: Boolean
    )

    /**
     * Mount operation state.
     */
    enum class MountOperation { UNMOUNTING, MOUNTING }

    var installState by mutableStateOf<InstallState>(InstallState.Ready)
        private set

    var installedPackageName by mutableStateOf<String?>(null)
        private set

    var installerUnavailableDialog by mutableStateOf<InstallerUnavailableState?>(null)
        private set

    var showInstallerSelectionDialog by mutableStateOf(false)
        private set

    private var oneTimeInstallerToken: InstallerManager.Token? = null
    private var selectedInstallerToken: InstallerManager.Token? = null

    var mountOperation: MountOperation? by mutableStateOf(null)
        private set

    // For external installer monitoring
    private var pendingExternalInstall: InstallerManager.InstallPlan.External? = null
    private var externalInstallTimeoutJob: Job? = null
    private var externalInstallBaseline: Pair<Long?, Long?>? = null
    private var externalInstallStartTime: Long? = null
    private var externalPackageWasPresentAtStart: Boolean = false

    // For intent-based fallback monitoring (when session dies on OEM devices)
    private var pendingIntentFallbackPackage: String? = null

    // Store pending install params for retry
    private var pendingInstallFile: File? = null
    private var pendingOriginalPackageName: String? = null
    private var pendingPersistCallback: (suspend (String, InstallType) -> Boolean)? = null

    // Track current installation type for proper persistence
    var currentInstallType: InstallType = InstallType.DEFAULT
        private set

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REPLACED -> {
                    val pkg = intent.data?.schemeSpecificPart ?: return
                    // Intent-based fallback monitor
                    if (pkg == pendingIntentFallbackPackage) {
                        val info = pm.getPackageInfo(pkg) ?: return
                        if (isUpdatedSinceBaseline(info)) {
                            handleIntentFallbackSuccess(pkg)
                            return
                        }
                    }
                    // External installer monitor
                    handleExternalInstallSuccess(pkg)
                }
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            app,
            packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onCleared() {
        super.onCleared()
        try {
            app.unregisterReceiver(packageReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister receiver", e)
        }
        externalInstallTimeoutJob?.cancel()
        pendingExternalInstall?.let(installerManager::cleanup)
    }

    /**
     * Start installation process using user's preferred installer or prompt for selection.
     */
    fun install(
        outputFile: File,
        originalPackageName: String,
        onPersistApp: suspend (String, InstallType) -> Boolean
    ) {
        if (installState is InstallState.Installing) return

        // Store for potential retry
        pendingInstallFile = outputFile
        pendingOriginalPackageName = originalPackageName
        pendingPersistCallback = onPersistApp

        viewModelScope.launch {
            // Check if we should prompt for installer selection
            val shouldPrompt = prefs.promptInstallerOnInstall.get()

            if (shouldPrompt && oneTimeInstallerToken == null) {
                // Show installer selection dialog
                showInstallerSelectionDialog = true
                return@launch
            }

            installState = InstallState.Installing

            try {
                val packageInfo = pm.getPackageInfo(outputFile)
                    ?: throw Exception("Failed to load application info")

                val targetPackageName = packageInfo.packageName

                // Check if app is already installed
                val existingInfo = pm.getPackageInfo(targetPackageName)

                if (existingInfo != null) {
                    // Check version - can't downgrade
                    if (pm.getVersionCode(packageInfo) < pm.getVersionCode(existingInfo)) {
                        Log.i(TAG, "Version downgrade detected - showing conflict")
                        installState = InstallState.Conflict(targetPackageName)
                        return@launch
                    }
                    // Check signature mismatch before launching the installer - avoids
                    // INSTALL_FAILED_UPDATE_INCOMPATIBLE from the system PackageInstaller
                    if (pm.hasSignatureMismatch(targetPackageName, outputFile)) {
                        Log.i(TAG, "Signature mismatch detected for $targetPackageName - showing conflict")
                        installState = InstallState.Conflict(targetPackageName)
                        return@launch
                    }
                }

                // Resolve installation plan - use one-time token if available
                val resolved = if (oneTimeInstallerToken != null) {
                    // Use one-time installer token for this install
                    val token = oneTimeInstallerToken!!
                    selectedInstallerToken = token
                    oneTimeInstallerToken = null

                    // Check if selected installer is available
                    val entry = installerManager.describeEntry(token, InstallerManager.InstallTarget.PATCHER)

                    if (entry != null && entry.availability.available) {
                        // Selected installer is available - temporarily change primary
                        val originalPrimary = installerManager.getPrimaryToken()

                        // Set selected token as primary
                        installerManager.updatePrimaryToken(token)

                        // Resolve with selected token
                        val result = installerManager.resolvePlanWithStatus(
                            InstallerManager.InstallTarget.PATCHER,
                            outputFile,
                            targetPackageName,
                            null
                        )

                        // Restore original primary
                        installerManager.updatePrimaryToken(originalPrimary)

                        result
                    } else {
                        // Even if the installer is unavailable, try resolve with it
                        // to get the correct primaryToken and unavailabilityReason
                        val originalPrimary = installerManager.getPrimaryToken()
                        installerManager.updatePrimaryToken(token)
                        val result = installerManager.resolvePlanWithStatus(
                            InstallerManager.InstallTarget.PATCHER,
                            outputFile,
                            targetPackageName,
                            null
                        )
                        installerManager.updatePrimaryToken(originalPrimary)
                        result
                    }
                } else {
                    selectedInstallerToken = null
                    installerManager.resolvePlanWithStatus(
                        InstallerManager.InstallTarget.PATCHER,
                        outputFile,
                        targetPackageName,
                        null
                    )
                }

                Log.d(TAG, "Resolved plan: ${resolved.plan::class.java.simpleName}")

                // Check if installer is unavailable
                if (resolved.primaryUnavailable) {
                    val actualToken = selectedInstallerToken ?: resolved.primaryToken
                    when (actualToken) {
                        InstallerManager.Token.Shizuku -> {
                            Log.d(TAG, "Shizuku unavailable, showing dialog")
                            installerUnavailableDialog = InstallerUnavailableState(
                                installerToken = actualToken,
                                reason = resolved.unavailabilityReason,
                                canOpenApp = true
                            )
                            installState = InstallState.Ready
                            selectedInstallerToken = null
                            return@launch
                        }
                        InstallerManager.Token.AutoSaved -> {
                            Log.d(TAG, "Root unavailable, showing dialog")
                            installerUnavailableDialog = InstallerUnavailableState(
                                installerToken = actualToken,
                                reason = resolved.unavailabilityReason,
                                canOpenApp = false
                            )
                            installState = InstallState.Ready
                            selectedInstallerToken = null
                            return@launch
                        }
                        else -> {
                            // For other installers, proceed with fallback
                            selectedInstallerToken = null
                        }
                    }
                }

                // Execute the installation plan
                executeInstallPlan(resolved.plan, outputFile, originalPackageName, onPersistApp)

            } catch (e: Exception) {
                Log.e(TAG, "Install failed", e)
                handleInstallError(
                    app.getString(
                        R.string.install_app_fail,
                        e.simpleMessage() ?: e.javaClass.simpleName
                    )
                )
            }
        }
    }

    /**
     * Execute the resolved installation plan.
     */
    private suspend fun executeInstallPlan(
        plan: InstallerManager.InstallPlan,
        outputFile: File,
        originalPackageName: String,
        onPersistApp: suspend (String, InstallType) -> Boolean
    ) {
        when (plan) {
            is InstallerManager.InstallPlan.Internal -> {
                Log.d(TAG, "Using internal (standard) installer")
                currentInstallType = InstallType.DEFAULT
                performStandardInstall(outputFile, originalPackageName, onPersistApp)
            }

            is InstallerManager.InstallPlan.Shizuku -> {
                Log.d(TAG, "Using Shizuku installer")
                currentInstallType = InstallType.SHIZUKU
                performShizukuInstall(outputFile, onPersistApp)
            }

            is InstallerManager.InstallPlan.Mount -> {
                Log.d(TAG, "Using root/mount installer")
                currentInstallType = InstallType.MOUNT
                // Mount install requires additional parameters, handled separately
                handleInstallError(app.getString(R.string.installer_status_not_supported))
            }

            is InstallerManager.InstallPlan.External -> {
                Log.d(TAG, "Using external installer: ${plan.installerLabel}")
                currentInstallType = if (plan.token is InstallerManager.Token.Component) {
                    InstallType.CUSTOM
                } else {
                    InstallType.DEFAULT
                }
                launchExternalInstaller(plan)
            }
        }
    }

    /**
     * Internal (native PackageInstaller session) installation.
     * Suspends until the user confirms or cancels.
     * If the OEM kills the session, falls back to [Intent.ACTION_INSTALL_PACKAGE] via
     * the existing external installation monitor.
     */
    private suspend fun performStandardInstall(
        outputFile: File,
        originalPackageName: String,
        onPersistApp: suspend (String, InstallType) -> Boolean
    ) {
        val packageInfo = pm.getPackageInfo(outputFile)
            ?: throw Exception("Failed to load application info")
        val targetPackageName = packageInfo.packageName

        // Unmount if mounted as root
        if (rootInstaller.hasRootAccess() && rootInstaller.isAppMounted(originalPackageName)) {
            rootInstaller.unmount(originalPackageName)
        }

        val result = try {
            sessionInstaller.installInternal(outputFile)
        } catch (_: InstallCancelledException) {
            // User dismissed the dialog - go back to Ready immediately, no error shown
            installState = InstallState.Ready
            return
        } catch (_: SessionDeadException) {
            Log.w(TAG, "Session dead, falling back to intent-based install")
            launchIntentBasedFallback(outputFile, targetPackageName, onPersistApp)
            return
        }

        when (result) {
            InstallResult.Success -> {
                onPersistApp(targetPackageName, InstallType.DEFAULT)
                handleInstallSuccess(targetPackageName)
            }
            is InstallResult.Conflict -> {
                Log.i(TAG, "Signature conflict for $targetPackageName")
                installState = InstallState.Conflict(targetPackageName)
            }
            is InstallResult.Failure -> handleInstallError(
                app.getString(R.string.install_app_fail, result.message ?: "Unknown error")
            )
        }
    }

    /**
     * Silent install via Shizuku/Sui.
     */
    private suspend fun performShizukuInstall(
        outputFile: File,
        onPersistApp: suspend (String, InstallType) -> Boolean
    ) {
        val packageInfo = pm.getPackageInfo(outputFile)
            ?: throw Exception("Failed to load application info")
        val targetPackageName = packageInfo.packageName

        if (rootInstaller.hasRootAccess() && rootInstaller.isAppMounted(targetPackageName)) {
            rootInstaller.unmount(targetPackageName)
        }

        Log.d(TAG, "Starting Shizuku install for $targetPackageName")

        val result = try {
            sessionInstaller.installShizuku(outputFile, targetPackageName)
        } catch (_: InstallCancelledException) {
            installState = InstallState.Ready
            return
        }

        when (result) {
            InstallResult.Success -> {
                Log.d(TAG, "Shizuku install successful")
                onPersistApp(targetPackageName, InstallType.SHIZUKU)
                installedPackageName = targetPackageName
                installState = InstallState.Installed(targetPackageName)
                app.toast(app.getString(R.string.install_app_success))
            }
            is InstallResult.Conflict -> {
                Log.i(TAG, "Signature conflict for $targetPackageName")
                installState = InstallState.Conflict(targetPackageName)
            }
            is InstallResult.Failure -> handleInstallError(
                app.getString(R.string.install_app_fail, result.message ?: "Unknown error")
            )
        }
    }

    /**
     * Launch external installer app.
     */
    private fun launchExternalInstaller(plan: InstallerManager.InstallPlan.External) {
        pendingExternalInstall?.let(installerManager::cleanup)
        externalInstallTimeoutJob?.cancel()

        pendingExternalInstall = plan
        externalInstallStartTime = System.currentTimeMillis()

        val baselineInfo = pm.getPackageInfo(plan.expectedPackage)
        externalPackageWasPresentAtStart = baselineInfo != null
        externalInstallBaseline = baselineInfo?.let { info ->
            pm.getVersionCode(info) to info.lastUpdateTime
        }

        try {
            // Add FLAG_ACTIVITY_NEW_TASK since we're starting from Application context
            plan.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(plan.intent)
            app.toast(app.getString(R.string.installer_external_launched, plan.installerLabel))
        } catch (e: ActivityNotFoundException) {
            installerManager.cleanup(plan)
            pendingExternalInstall = null
            handleInstallError(app.getString(R.string.install_app_fail, e.simpleMessage()))
            return
        }

        // Monitor for install completion
        externalInstallTimeoutJob = viewModelScope.launch {
            val timeoutAt = System.currentTimeMillis() + EXTERNAL_INSTALL_TIMEOUT_MS
            while (true) {
                if (pendingExternalInstall != plan) return@launch

                val info = pm.getPackageInfo(plan.expectedPackage)
                if (info != null && isUpdatedSinceBaseline(info)) {
                    handleExternalInstallSuccess(plan.expectedPackage)
                    return@launch
                }

                if (System.currentTimeMillis() >= timeoutAt) break
                delay(INSTALL_MONITOR_POLL_MS)
            }

            if (pendingExternalInstall == plan) {
                installerManager.cleanup(plan)
                pendingExternalInstall = null
                handleInstallError(
                    app.getString(R.string.installer_external_timeout, plan.installerLabel)
                )
            }
        }
    }

    /**
     * Fallback when [SessionDeadException] is caught from [performStandardInstall].
     * Launches [Intent.ACTION_INSTALL_PACKAGE] and monitors for completion via the
     * existing package broadcast receiver + timeout mechanism.
     */
    private fun launchIntentBasedFallback(
        outputFile: File,
        targetPackageName: String,
        onPersistApp: suspend (String, InstallType) -> Boolean
    ) {
        pendingPersistCallback = onPersistApp
        currentInstallType = InstallType.DEFAULT

        val baselineInfo = pm.getPackageInfo(targetPackageName)
        externalPackageWasPresentAtStart = baselineInfo != null
        externalInstallBaseline = baselineInfo?.let { pm.getVersionCode(it) to it.lastUpdateTime }
        externalInstallStartTime = System.currentTimeMillis()

        // Use a lightweight sentinel so handleExternalInstallSuccess can match the package
        // without a full External plan — we repurpose pendingExternalInstall's expectedPackage
        // by creating a minimal sentinel object just for the package name check.
        pendingIntentFallbackPackage = targetPackageName

        sessionInstaller.launchIntentInstall(outputFile)

        externalInstallTimeoutJob = viewModelScope.launch {
            val timeoutAt = System.currentTimeMillis() + EXTERNAL_INSTALL_TIMEOUT_MS
            while (true) {
                if (pendingIntentFallbackPackage == null) return@launch
                val info = pm.getPackageInfo(targetPackageName)
                if (info != null && isUpdatedSinceBaseline(info)) {
                    handleIntentFallbackSuccess(targetPackageName)
                    return@launch
                }
                if (System.currentTimeMillis() >= timeoutAt) break
                delay(INSTALL_MONITOR_POLL_MS)
            }
            if (pendingIntentFallbackPackage != null) {
                pendingIntentFallbackPackage = null
                handleInstallError(app.getString(R.string.installer_external_timeout, app.getString(R.string.installer_internal_name)))
            }
        }
    }

    private fun handleIntentFallbackSuccess(packageName: String) {
        pendingIntentFallbackPackage = null
        externalInstallTimeoutJob?.cancel()
        externalInstallBaseline = null
        externalInstallStartTime = null
        externalPackageWasPresentAtStart = false
        pendingPersistCallback?.let { callback ->
            viewModelScope.launch {
                runCatching { callback(packageName, InstallType.DEFAULT) }
            }
        }
        handleInstallSuccess(packageName)
    }

    private fun isUpdatedSinceBaseline(info: PackageInfo): Boolean {
        val baseline = externalInstallBaseline
        val startTime = externalInstallStartTime ?: 0L

        val vc = pm.getVersionCode(info)
        val updated = info.lastUpdateTime

        val baseVc = baseline?.first
        val baseUpdated = baseline?.second

        val versionChanged = baseVc != null && vc != baseVc
        val timestampChanged = baseUpdated != null && updated > baseUpdated
        val updatedSinceStart = startTime in 1..updated

        return versionChanged || timestampChanged || updatedSinceStart
    }

    private fun handleExternalInstallSuccess(packageName: String): Boolean {
        val plan = pendingExternalInstall ?: return false
        if (plan.expectedPackage != packageName) return false

        pendingExternalInstall = null
        externalInstallTimeoutJob?.cancel()
        externalInstallBaseline = null
        externalInstallStartTime = null
        externalPackageWasPresentAtStart = false
        installerManager.cleanup(plan)

        val installType = if (plan.token is InstallerManager.Token.Component) {
            InstallType.CUSTOM
        } else {
            InstallType.DEFAULT
        }

        // Persist app data
        pendingPersistCallback?.let { callback ->
            viewModelScope.launch {
                try {
                    callback(packageName, installType)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to persist app data", e)
                }
            }
        }

        installedPackageName = packageName
        installState = InstallState.Installed(packageName)
        app.toast(app.getString(R.string.installer_external_success, plan.installerLabel))
        return true
    }

    /**
     * Install with root/mount.
     */
    fun installMount(
        outputFile: File,
        inputFile: File?,
        packageName: String,
        inputVersion: String,
        onPersistApp: suspend (String, InstallType) -> Boolean
    ) {
        if (installState is InstallState.Installing) return

        viewModelScope.launch {
            installState = InstallState.Installing

            try {
                val packageInfo = pm.getPackageInfo(outputFile)
                    ?: throw Exception("Failed to load application info")

                val label = with(pm) { packageInfo.label() }
                val patchedVersion = packageInfo.versionName ?: ""

                // Check version mismatch for mount
                val stockInfo = pm.getPackageInfo(packageName)
                val stockVersion = stockInfo?.versionName
                if (stockVersion != null && stockVersion != patchedVersion) {
                    handleInstallError(
                        app.getString(
                            R.string.mount_version_mismatch_message,
                            patchedVersion,
                            stockVersion
                        )
                    )
                    return@launch
                }

                // Check for base APK - app must be installed for mount
                if (stockInfo == null) {
                    if (packageInfo.splitNames.isNotEmpty()) {
                        handleInstallError(app.getString(R.string.installer_hint_generic))
                        return@launch
                    }
                }

                // Install as root
                rootInstaller.install(
                    outputFile,
                    inputFile,
                    packageName,
                    inputVersion,
                    label
                )

                // Persist app data
                onPersistApp(packageInfo.packageName, InstallType.MOUNT)

                // Mount
                rootInstaller.mount(packageName)

                // Success
                handleInstallSuccess(packageName)

            } catch (e: Exception) {
                Log.e(TAG, "Mount install failed", e)
                handleInstallError(
                    app.getString(
                        R.string.install_app_fail,
                        e.simpleMessage() ?: e.javaClass.simpleName
                    )
                )

                // Cleanup on failure
                try {
                    rootInstaller.uninstall(packageName)
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Mount app (for root installer).
     */
    fun mount(packageName: String, version: String) = viewModelScope.launch {
        val stockVersion = pm.getPackageInfo(packageName)?.versionName
        if (stockVersion != null && stockVersion != version) {
            handleInstallError(
                app.getString(
                    R.string.mount_version_mismatch_message,
                    version,
                    stockVersion
                )
            )
            return@launch
        }

        try {
            mountOperation = MountOperation.MOUNTING
            app.toast(app.getString(R.string.mounting_ellipsis))
            rootInstaller.mount(packageName)
            app.toast(app.getString(R.string.mounted))
        } catch (e: Exception) {
            app.toast(app.getString(R.string.failed_to_mount, e.simpleMessage()))
            Log.e(TAG, "Failed to mount", e)
        } finally {
            mountOperation = null
        }
    }

    /**
     * Unmount app (for root installer).
     */
    fun unmount(packageName: String) = viewModelScope.launch {
        try {
            mountOperation = MountOperation.UNMOUNTING
            app.toast(app.getString(R.string.unmounting_ellipsis))
            rootInstaller.unmount(packageName)
            app.toast(app.getString(R.string.unmounted))
        } catch (e: Exception) {
            app.toast(app.getString(R.string.failed_to_unmount, e.simpleMessage()))
            Log.e(TAG, "Failed to unmount", e)
        } finally {
            mountOperation = null
        }
    }

    /**
     * Remount app (unmount then mount).
     */
    fun remount(packageName: String, version: String) = viewModelScope.launch {
        val stockVersion = pm.getPackageInfo(packageName)?.versionName
        if (stockVersion != null && stockVersion != version) {
            handleInstallError(
                app.getString(
                    R.string.mount_version_mismatch_message,
                    version,
                    stockVersion
                )
            )
            return@launch
        }

        try {
            mountOperation = MountOperation.UNMOUNTING
            app.toast(app.getString(R.string.unmounting_ellipsis))
            rootInstaller.unmount(packageName)
            app.toast(app.getString(R.string.unmounted))

            mountOperation = MountOperation.MOUNTING
            app.toast(app.getString(R.string.mounting_ellipsis))
            rootInstaller.mount(packageName)
            app.toast(app.getString(R.string.mounted))
        } catch (e: Exception) {
            app.toast(app.getString(R.string.failed_to_mount, e.simpleMessage()))
            Log.e(TAG, "Failed to remount", e)
        } finally {
            mountOperation = null
        }
    }

    /**
     * Export patched app to URI.
     */
    fun export(outputFile: File, uri: Uri?, onComplete: (Boolean) -> Unit = {}) = viewModelScope.launch {
        if (uri == null) {
            onComplete(false)
            return@launch
        }

        val exportSucceeded = runCatching {
            withContext(Dispatchers.IO) {
                app.contentResolver.openOutputStream(uri)
                    ?.use { stream -> Files.copy(outputFile.toPath(), stream) }
                    ?: throw IOException("Could not open output stream for export")
            }
        }.isSuccess

        onComplete(exportSucceeded)
    }

    /**
     * Dismiss the installer unavailable dialog.
     */
    fun dismissInstallerUnavailableDialog() {
        installerUnavailableDialog = null
    }

    /**
     * Open the installer app (Shizuku).
     */
    fun openInstallerApp() {
        val dialog = installerUnavailableDialog ?: return
        when (dialog.installerToken) {
            InstallerManager.Token.Shizuku -> {
                val opened = installerManager.openShizukuApp()
                if (!opened) {
                    app.toast(app.getString(R.string.installer_status_shizuku_not_installed))
                }
            }
            else -> {}
        }
    }

    /**
     * Retry installation with preferred installer.
     */
    fun retryWithPreferredInstaller() {
        installerUnavailableDialog = null

        val file = pendingInstallFile ?: return
        val originalPkg = pendingOriginalPackageName ?: return
        val callback = pendingPersistCallback ?: return

        install(file, originalPkg, callback)
    }

    /**
     * Proceed with standard installer instead.
     */
    fun proceedWithFallbackInstaller() {
        installerUnavailableDialog = null

        val file = pendingInstallFile ?: return
        val originalPkg = pendingOriginalPackageName ?: return
        val callback = pendingPersistCallback ?: return

        viewModelScope.launch {
            installState = InstallState.Installing
            currentInstallType = InstallType.DEFAULT
            try {
                performStandardInstall(file, originalPkg, callback)
            } catch (e: Exception) {
                Log.e(TAG, "Fallback install failed", e)
                handleInstallError(
                    app.getString(
                        R.string.install_app_fail,
                        e.simpleMessage() ?: e.javaClass.simpleName
                    )
                )
            }
        }
    }

    /**
     * Dismiss installer selection dialog.
     */
    fun dismissInstallerSelectionDialog() {
        showInstallerSelectionDialog = false
        oneTimeInstallerToken = null
        selectedInstallerToken = null
    }

    /**
     * Proceed with selected installer from dialog.
     */
    fun proceedWithSelectedInstaller(token: InstallerManager.Token) {
        oneTimeInstallerToken = token
        showInstallerSelectionDialog = false

        val file = pendingInstallFile ?: return
        val originalPkg = pendingOriginalPackageName ?: return
        val callback = pendingPersistCallback ?: return

        install(file, originalPkg, callback)
    }

    /**
     * Launches system uninstall UI for [packageName] and suspends until the user confirms.
     * Resets [installState] to [InstallState.Ready] after successful uninstall so the user
     * can immediately retry installation.
     */
    fun requestUninstall(packageName: String) {
        viewModelScope.launch {
            try {
                sessionInstaller.uninstall(packageName)
                installState = InstallState.Ready
            } catch (_: UninstallCancelledException) {
                // User dismissed the dialog - keep current state
            }
        }
    }

    /**
     * Resets install state back to [InstallState.Ready].
     * Used when the user dismisses a conflict or error dialog without taking action.
     */
    fun resetInstallState() {
        installState = InstallState.Ready
    }

    fun openApp() {
        installedPackageName?.let { pm.launch(it) }
    }

    /**
     * Returns installer entries for the one-time selection dialog shown during install.
     * Mirrors the logic in SettingsViewModel but scoped to PATCHER target only.
     */
    fun getInstallerOptions(): List<InstallerManager.Entry> {
        val token = installerManager.getPrimaryToken()
        val raw = installerManager.listEntries(InstallerManager.InstallTarget.PATCHER, includeNone = false)
        return installerManager.ensureValidEntries(raw, token, InstallerManager.InstallTarget.PATCHER)
    }

    fun getPrimaryInstallerToken(): InstallerManager.Token =
        installerManager.getPrimaryToken()

    fun openShizukuApp(): Boolean = installerManager.openShizukuApp()

    private fun handleInstallSuccess(packageName: String) {
        externalInstallTimeoutJob?.cancel()
        selectedInstallerToken = null
        installedPackageName = packageName
        appDataResolver.invalidate(packageName)
        installState = InstallState.Installed(packageName)
    }

    private fun handleInstallError(message: String) {
        externalInstallTimeoutJob?.cancel()
        selectedInstallerToken = null
        installState = InstallState.Error(message)
    }

    companion object {
        private const val TAG = "Morphe Install"
        private const val EXTERNAL_INSTALL_TIMEOUT_MS = 60_000L
        private const val INSTALL_MONITOR_POLL_MS = 1000L
    }
}
