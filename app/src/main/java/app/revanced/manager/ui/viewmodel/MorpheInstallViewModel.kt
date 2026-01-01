package app.revanced.manager.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.R
import app.revanced.manager.data.room.apps.installed.InstallType
import app.revanced.manager.domain.installer.RootInstaller
import app.revanced.manager.service.InstallService
import app.revanced.manager.util.PM
import app.revanced.manager.util.simpleMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import androidx.core.net.toUri

/**
 * Simplified install view model for Morphe patcher screen.
 * Handles installation with pre-check for signature conflicts BEFORE system dialog.
 */
class MorpheInstallViewModel : ViewModel(), KoinComponent {

    private val app: Application by inject()
    private val pm: PM by inject()
    private val rootInstaller: RootInstaller by inject()

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

    var installState by mutableStateOf<InstallState>(InstallState.Ready)
        private set

    var installedPackageName by mutableStateOf<String?>(null)
        private set

    private var awaitingPackageName: String? = null
    private var installTimeoutJob: Job? = null
    private var isWaitingForUninstall = false

    // Broadcast receiver for install results
    private val installReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REPLACED -> {
                    val pkg = intent.data?.schemeSpecificPart ?: return
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
                            // Installation failed - but we already checked for conflicts
                            val message = intent.getStringExtra(InstallService.EXTRA_INSTALL_STATUS_MESSAGE)
                                ?.takeIf { it.isNotBlank() }
                                ?: app.getString(R.string.install_app_fail, pmStatus.toString())
                            handleInstallError(message)
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
    }

    /**
     * Check if there's a signature conflict between the APK and installed app.
     * Returns true if there's a conflict (different signatures).
     */
    private suspend fun hasSignatureConflict(apkFile: File, packageName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check if app is installed first
            if (pm.getPackageInfo(packageName) == null) {
                return@withContext false // Not installed, no conflict
            }

            // Get signatures
            val installedSignatures = getInstalledPackageSignatures(packageName)
            val apkSignatures = getApkFileSignatures(apkFile)

            if (installedSignatures.isEmpty() || apkSignatures.isEmpty()) {
                Log.w(TAG, "Could not get signatures for comparison")
                return@withContext false // Can't determine - let system handle it
            }

            // Compare signatures - check if ANY signature matches
            val signaturesMatch = installedSignatures.any { installed ->
                apkSignatures.any { apk ->
                    installed.contentEquals(apk)
                }
            }

            val hasConflict = !signaturesMatch
            if (hasConflict) {
                Log.i(TAG, "Signature conflict detected: installed and APK signatures don't match")
            }

            hasConflict
        } catch (e: Exception) {
            Log.e(TAG, "Error checking signature conflict", e)
            false // On error, let system handle it
        }
    }

    /**
     * Get signatures of installed package
     */
    @Suppress("DEPRECATION")
    private fun getInstalledPackageSignatures(packageName: String): List<ByteArray> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // API 28+ - use GET_SIGNING_CERTIFICATES
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
                } else {
                    emptyList()
                }
            } else {
                // API < 28 - use deprecated GET_SIGNATURES
                val packageInfo = app.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNATURES
                )
                packageInfo.signatures?.map { it.toByteArray() } ?: emptyList()
            }
        } catch (_: PackageManager.NameNotFoundException) {
            Log.d(TAG, "Package not found: $packageName")
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting installed package signatures", e)
            emptyList()
        }
    }

    /**
     * Get signatures from APK file
     */
    @Suppress("DEPRECATION")
    private fun getApkFileSignatures(apkFile: File): List<ByteArray> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // API 28+ - use GET_SIGNING_CERTIFICATES
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
                    } else {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            } else {
                // API < 28 - use deprecated GET_SIGNATURES
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

    // Callback to persist app after successful installation
    private var pendingPersistCallback: (suspend (String, InstallType) -> Boolean)? = null

    /**
     * Start installation process with pre-check for signature conflicts.
     */
    fun install(
        outputFile: File,
        originalPackageName: String,
        onPersistApp: suspend (String, InstallType) -> Boolean
    ) {
        if (installState is InstallState.Installing) return

        viewModelScope.launch {
            installState = InstallState.Installing

            try {
                // Get package info from APK
                val packageInfo = pm.getPackageInfo(outputFile)
                    ?: throw Exception("Failed to load application info")

                val targetPackageName = packageInfo.packageName
                awaitingPackageName = targetPackageName

                // Store callback for later use after successful install
                pendingPersistCallback = onPersistApp

                // Check if app is already installed
                val existingInfo = pm.getPackageInfo(targetPackageName)

                if (existingInfo != null) {
                    // Check version - can't downgrade
                    if (pm.getVersionCode(packageInfo) < pm.getVersionCode(existingInfo)) {
                        Log.i(TAG, "Version downgrade detected - showing conflict")
                        installState = InstallState.Conflict(targetPackageName)
                        pendingPersistCallback = null
                        return@launch
                    }

                    // PRE-CHECK: Check for signature conflict BEFORE system dialog
                    val hasConflict = hasSignatureConflict(outputFile, targetPackageName)
                    if (hasConflict) {
                        Log.i(TAG, "Signature conflict detected for $targetPackageName")
                        installState = InstallState.Conflict(targetPackageName)
                        pendingPersistCallback = null
                        return@launch
                    }
                }

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

            } catch (e: Exception) {
                Log.e(TAG, "Install failed", e)
                pendingPersistCallback = null
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
                    // Check if output is a split APK
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
     * Request uninstall of conflicting package.
     * After user uninstalls, handleUninstallComplete will be called.
     */
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

    /**
     * Open installed app
     */
    fun openApp() {
        installedPackageName?.let { pm.launch(it) }
    }

    private fun handleInstallSuccess(packageName: String) {
        installTimeoutJob?.cancel()
        awaitingPackageName = null
        isWaitingForUninstall = false
        installedPackageName = packageName
        installState = InstallState.Installed(packageName)

        // Call persist callback if available
        pendingPersistCallback?.let { callback ->
            viewModelScope.launch {
                try {
                    callback(packageName, InstallType.DEFAULT)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to persist app data", e)
                }
            }
            pendingPersistCallback = null
        }
    }

    private fun handleInstallError(message: String) {
        installTimeoutJob?.cancel()
        awaitingPackageName = null
        pendingPersistCallback = null
        installState = InstallState.Error(message)
    }

    private fun handleUninstallComplete() {
        viewModelScope.launch {
            delay(500) // Wait for system dialog to close
            isWaitingForUninstall = false
            installState = InstallState.Ready
        }
    }

    companion object {
        private const val TAG = "MorpheInstallViewModel"
        private const val INSTALL_TIMEOUT_MS = 240_000L
    }
}
