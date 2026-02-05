package app.revanced.manager.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.*
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.R
import app.revanced.manager.data.room.apps.installed.InstallType
import app.revanced.manager.domain.installer.InstallerManager
import app.revanced.manager.domain.installer.RootInstaller
import app.revanced.manager.domain.installer.ShizukuInstaller
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.service.InstallService
import app.revanced.manager.util.PM
import app.revanced.manager.util.simpleMessage
import app.revanced.manager.util.toast
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
    private val shizukuInstaller: ShizukuInstaller by inject()
    private val installerManager: InstallerManager by inject()
    private val prefs: PreferencesManager by inject()

    /**
     * Current install state
     */
    sealed class InstallState {
        /** Ready to install - shows Install button */
        data object Ready : InstallState()

        /** Currently installing - shows progress indicator */
        data object Installing : InstallState()

        /** Successfully installed - shows Open button */
        data class Installed(val packageName: String) : InstallState()

        /** Signature conflict detected - shows Uninstall button */
        data class Conflict(val packageName: String) : InstallState()

        /** Installation error - shows error message and retry */
        data class Error(val message: String) : InstallState()
    }

    /**
     * State for installer unavailability dialog
     */
    data class InstallerUnavailableState(
        val installerToken: InstallerManager.Token,
        val reason: Int?,
        val canOpenApp: Boolean
    )

    /**
     * Mount operation state
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

    private var awaitingPackageName: String? = null
    private var installTimeoutJob: Job? = null
    private var isWaitingForUninstall = false

    // For external installer monitoring
    private var pendingExternalInstall: InstallerManager.InstallPlan.External? = null
    private var externalInstallTimeoutJob: Job? = null
    private var externalInstallBaseline: Pair<Long?, Long?>? = null
    private var externalInstallStartTime: Long? = null
    private var externalPackageWasPresentAtStart: Boolean = false

    // Store pending install params for retry
    private var pendingInstallFile: File? = null
    private var pendingOriginalPackageName: String? = null
    private var pendingPersistCallback: (suspend (String, InstallType) -> Boolean)? = null

    // Track current install type for proper persistence
    var currentInstallType: InstallType = InstallType.DEFAULT
        private set

    // Broadcast receiver for install results
    private val installReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REPLACED -> {
                    val pkg = intent.data?.schemeSpecificPart ?: return

                    // Check external install first
                    if (handleExternalInstallSuccess(pkg)) return

                    if (pkg == awaitingPackageName) {
                        handleInstallSuccess(pkg)
                    }
                }

                Intent.ACTION_PACKAGE_REMOVED -> {
                    val pkg = intent.data?.schemeSpecificPart ?: return
                    if (isWaitingForUninstall && pkg == awaitingPackageName) {
                        handleUninstallComplete()
                    }
                }

                InstallService.APP_INSTALL_ACTION -> {
                    val pmStatus = intent.getIntExtra(
                        InstallService.EXTRA_INSTALL_STATUS,
                        PackageInstaller.STATUS_FAILURE
                    )

                    when (pmStatus) {
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            // User needs to confirm - keep installing state
                        }
                        PackageInstaller.STATUS_SUCCESS -> {
                            val packageName = intent.getStringExtra(InstallService.EXTRA_PACKAGE_NAME)
                            if (packageName != null) {
                                handleInstallSuccess(packageName)
                            } else {
                                awaitingPackageName?.let { handleInstallSuccess(it) }
                            }
                        }
                        else -> {
                            val message = intent.getStringExtra(InstallService.EXTRA_INSTALL_STATUS_MESSAGE)
                                ?.takeIf { it.isNotBlank() }

                            // Check for signature mismatch
                            if (installerManager.isSignatureMismatch(message)) {
                                awaitingPackageName?.let { pkg ->
                                    installState = InstallState.Conflict(pkg)
                                }
                                return
                            }

                            val formatted = installerManager.formatFailureHint(pmStatus, message)
                            handleInstallError(
                                formatted ?: message ?: app.getString(R.string.install_app_fail, pmStatus.toString())
                            )
                        }
                    }
                }
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            app,
            installReceiver,
            IntentFilter().apply {
                addAction(InstallService.APP_INSTALL_ACTION)
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onCleared() {
        super.onCleared()
        try {
            app.unregisterReceiver(installReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister receiver", e)
        }
        installTimeoutJob?.cancel()
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
                awaitingPackageName = targetPackageName

                // Check if app is already installed
                val existingInfo = pm.getPackageInfo(targetPackageName)

                if (existingInfo != null) {
                    // Check version - can't downgrade
                    if (pm.getVersionCode(packageInfo) < pm.getVersionCode(existingInfo)) {
                        Log.i(TAG, "Version downgrade detected - showing conflict")
                        installState = InstallState.Conflict(targetPackageName)
                        return@launch
                    }

                    // Check for signature conflict
                    val hasConflict = hasSignatureConflict(outputFile, targetPackageName)
                    if (hasConflict) {
                        Log.i(TAG, "Signature conflict detected for $targetPackageName")
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
     * Execute the resolved installation plan
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
                performStandardInstall(outputFile, originalPackageName)
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
     * Standard PackageInstaller installation
     */
    private suspend fun performStandardInstall(
        outputFile: File,
        originalPackageName: String
    ) {
        // Unmount if mounted as root
        if (rootInstaller.hasRootAccess() && rootInstaller.isAppMounted(originalPackageName)) {
            rootInstaller.unmount(originalPackageName)
        }

        // Start system installation
        pm.installApp(listOf(outputFile))

        // Set timeout
        installTimeoutJob?.cancel()
        installTimeoutJob = viewModelScope.launch {
            delay(INSTALL_TIMEOUT_MS)
            if (installState is InstallState.Installing) {
                handleInstallError(app.getString(R.string.install_timeout_message))
            }
        }
    }

    /**
     * Shizuku installation
     */
    private suspend fun performShizukuInstall(
        outputFile: File,
        onPersistApp: suspend (String, InstallType) -> Boolean
    ) {
        try {
            val packageInfo = pm.getPackageInfo(outputFile)
                ?: throw Exception("Failed to load application info")

            val targetPackageName = packageInfo.packageName

            // Unmount if mounted as root
            if (rootInstaller.hasRootAccess() && rootInstaller.isAppMounted(targetPackageName)) {
                rootInstaller.unmount(targetPackageName)
            }

            Log.d(TAG, "Starting Shizuku install for $targetPackageName")
            val result = shizukuInstaller.install(outputFile, targetPackageName)

            if (result.status == PackageInstaller.STATUS_SUCCESS) {
                Log.d(TAG, "Shizuku install successful")

                // Persist app data with SHIZUKU type
                try {
                    onPersistApp(targetPackageName, InstallType.SHIZUKU)
                    Log.d(TAG, "Persisted app with InstallType: SHIZUKU")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to persist app data", e)
                }

                installedPackageName = targetPackageName
                installState = InstallState.Installed(targetPackageName)
                app.toast(app.getString(R.string.install_app_success))
            } else {
                val message = result.message ?: app.getString(R.string.installer_hint_generic)
                Log.e(TAG, "Shizuku install failed: $message")
                handleInstallError(app.getString(R.string.install_app_fail, message))
            }
        } catch (e: ShizukuInstaller.InstallerOperationException) {
            val message = e.message ?: app.getString(R.string.installer_hint_generic)
            Log.e(TAG, "Shizuku install exception", e)
            handleInstallError(app.getString(R.string.install_app_fail, message))
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku install failed", e)
            handleInstallError(
                app.getString(
                    R.string.install_app_fail,
                    e.simpleMessage() ?: e.javaClass.simpleName
                )
            )
        }
    }

    /**
     * Launch external installer app
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

    private fun isUpdatedSinceBaseline(info: PackageInfo): Boolean {
        val baseline = externalInstallBaseline
        val startTime = externalInstallStartTime ?: 0L

        val vc = pm.getVersionCode(info)
        val updated = info.lastUpdateTime

        val baseVc = baseline?.first
        val baseUpdated = baseline?.second

        val versionChanged = baseVc != null && vc != baseVc
        val timestampChanged = baseUpdated != null && updated > baseUpdated
        val updatedSinceStart = updated >= startTime && startTime > 0L

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
     * Install with root/mount
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
     * Mount app (for root installer)
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
     * Unmount app (for root installer)
     */
    @Suppress("unused")
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
     * Remount app (unmount then mount)
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
     * Export patched app to URI
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

    // ==================== Dialog handlers ====================

    /**
     * Dismiss the installer unavailable dialog
     */
    fun dismissInstallerUnavailableDialog() {
        installerUnavailableDialog = null
    }

    /**
     * Open the installer app (Shizuku)
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
     * Retry installation with preferred installer
     */
    fun retryWithPreferredInstaller() {
        installerUnavailableDialog = null

        val file = pendingInstallFile ?: return
        val originalPkg = pendingOriginalPackageName ?: return
        val callback = pendingPersistCallback ?: return

        install(file, originalPkg, callback)
    }

    /**
     * Proceed with standard installer instead
     */
    fun proceedWithFallbackInstaller() {
        installerUnavailableDialog = null

        val file = pendingInstallFile ?: return
        val originalPkg = pendingOriginalPackageName ?: return

        viewModelScope.launch {
            installState = InstallState.Installing
            currentInstallType = InstallType.DEFAULT  // Standard installer
            try {
                val packageInfo = pm.getPackageInfo(file)
                    ?: throw Exception("Failed to load application info")

                awaitingPackageName = packageInfo.packageName
                performStandardInstall(file, originalPkg)
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
     * Dismiss installer selection dialog
     */
    fun dismissInstallerSelectionDialog() {
        showInstallerSelectionDialog = false
        oneTimeInstallerToken = null
        selectedInstallerToken = null
    }

    /**
     * Proceed with selected installer from dialog
     */
    fun proceedWithSelectedInstaller(token: InstallerManager.Token) {
        oneTimeInstallerToken = token
        showInstallerSelectionDialog = false

        val file = pendingInstallFile ?: return
        val originalPkg = pendingOriginalPackageName ?: return
        val callback = pendingPersistCallback ?: return

        install(file, originalPkg, callback)
    }

    // ==================== Signature checking ====================

    private suspend fun hasSignatureConflict(apkFile: File, packageName: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                if (pm.getPackageInfo(packageName) == null) {
                    return@withContext false
                }

                val installedSignatures = getInstalledPackageSignatures(packageName)
                val apkSignatures = getApkFileSignatures(apkFile)

                if (installedSignatures.isEmpty() || apkSignatures.isEmpty()) {
                    return@withContext false
                }

                val signaturesMatch = installedSignatures.any { installed ->
                    apkSignatures.any { apk -> installed.contentEquals(apk) }
                }

                !signaturesMatch
            } catch (e: Exception) {
                Log.e(TAG, "Error checking signature conflict", e)
                false
            }
        }

    @Suppress("DEPRECATION")
    private fun getInstalledPackageSignatures(packageName: String): List<ByteArray> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = app.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                val signingInfo = packageInfo.signingInfo
                if (signingInfo != null) {
                    if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners.map { it.toByteArray() }
                    } else {
                        signingInfo.signingCertificateHistory?.map { it.toByteArray() } ?: emptyList()
                    }
                } else emptyList()
            } else {
                val packageInfo = app.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNATURES
                )
                packageInfo.signatures?.map { it.toByteArray() } ?: emptyList()
            }
        } catch (_: PackageManager.NameNotFoundException) {
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting installed package signatures", e)
            emptyList()
        }
    }

    @Suppress("DEPRECATION")
    private fun getApkFileSignatures(apkFile: File): List<ByteArray> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = app.packageManager.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                if (packageInfo != null) {
                    val signingInfo = packageInfo.signingInfo
                    if (signingInfo != null) {
                        if (signingInfo.hasMultipleSigners()) {
                            signingInfo.apkContentsSigners.map { it.toByteArray() }
                        } else {
                            signingInfo.signingCertificateHistory?.map { it.toByteArray() } ?: emptyList()
                        }
                    } else emptyList()
                } else emptyList()
            } else {
                val packageInfo = app.packageManager.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.GET_SIGNATURES
                )
                packageInfo?.signatures?.map { it.toByteArray() } ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting APK signatures", e)
            emptyList()
        }
    }

    // ==================== Other actions ====================

    @SuppressLint("UseKtx")
    fun requestUninstall(packageName: String) {
        isWaitingForUninstall = true
        awaitingPackageName = packageName

        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = "package:$packageName".toUri()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        app.startActivity(intent)
    }

    fun openApp() {
        installedPackageName?.let { pm.launch(it) }
    }

    private fun handleInstallSuccess(packageName: String) {
        installTimeoutJob?.cancel()
        externalInstallTimeoutJob?.cancel()
        awaitingPackageName = null
        isWaitingForUninstall = false
        selectedInstallerToken = null
        installedPackageName = packageName
        installState = InstallState.Installed(packageName)

        pendingPersistCallback?.let { callback ->
            val installType = currentInstallType
            viewModelScope.launch {
                try {
                    callback(packageName, installType)
                    Log.d(TAG, "Persisted app with InstallType: $installType")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to persist app data", e)
                }
            }
        }
    }

    private fun handleInstallError(message: String) {
        installTimeoutJob?.cancel()
        externalInstallTimeoutJob?.cancel()
        awaitingPackageName = null
        selectedInstallerToken = null
        installState = InstallState.Error(message)
    }

    private fun handleUninstallComplete() {
        viewModelScope.launch {
            delay(500)
            isWaitingForUninstall = false
            installState = InstallState.Ready
        }
    }

    companion object {
        private const val TAG = "Morphe Install"
        private const val INSTALL_TIMEOUT_MS = 240_000L
        private const val EXTERNAL_INSTALL_TIMEOUT_MS = 60_000L
        private const val INSTALL_MONITOR_POLL_MS = 1000L
    }
}
