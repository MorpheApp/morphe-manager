/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.installer

import android.annotation.SuppressLint
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.util.Log
import app.morphe.manager.ManagerApplication
import app.morphe.manager.R
import app.morphe.manager.notification.PatchingNotifications
import app.morphe.manager.util.APK_MIMETYPE
import app.morphe.manager.util.PLAY_STORE_INSTALLER_PACKAGE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

private const val TAG = "Morphe SessionInstaller"
private const val ACTION_INSTALL_STATUS = "app.morphe.manager.INSTALL_STATUS"
private const val ACTION_UNINSTALL_STATUS = "app.morphe.manager.UNINSTALL_STATUS"
private const val EXTRA_SESSION_ID = "session_id"
private const val EXTRA_UNINSTALL_REQUEST_ID = "uninstall_request_id"

// How often a session awaiting its status is checked for, and how long its broadcast may lag behind
private val SESSION_WATCH_POLL = 2.seconds
private val SESSION_GONE_GRACE = 5.seconds

/**
 * PackageInstaller-based installer.
 *
 * [installInternal] suspends until the system confirms or the user cancels.
 * On some devices a system component may kill the session before the user confirms,
 * in which case [SessionDeadException] is thrown so the caller can fall back to [launchIntentInstall].
 *
 * Shizuku silent installation is handled via [ShizukuInstaller].
 */
class SessionInstaller(private val app: Application) {

    private val shizuku = ShizukuEnvironment(app)
    private val shizukuInstaller = ShizukuInstaller(app)
    private val uninstallRequestIds = AtomicInteger()

    /** Set once the platform has refused a silent session, so the next install stops asking. */
    @Volatile
    private var silentUpdatesRefused = false

    /**
     * Installs an APK using the PackageInstaller session API.
     * Suspends until the system confirms or the user cancels.
     *
     * Android 12+ can apply an update of an app this manager installed without asking the user.
     * OEM builds that refuse one destroy the session instead of falling back, so the install is
     * repeated with the dialog and silent updates are dropped for the rest of the process.
     *
     * @throws InstallCancelledException if the user dismissed the dialog, install was aborted, or
     * the session ended without reporting how.
     * @throws SessionDeadException if the session was killed before completion.
     */
    suspend fun installInternal(apkFile: File): InstallResult {
        requireApkPresent(apkFile)
        val silentUpdate = !silentUpdatesRefused
        Log.d(TAG, "installInternal: ${apkFile.name} (${apkFile.length()} bytes), silentUpdate=$silentUpdate")
        return try {
            awaitSession(apkFile, silentUpdate)
        } finally {
            // However it ended, a confirmation nobody answered must not outlive the session
            ManagerApplication.onReturnToForeground = null
            PatchingNotifications.cancelInstallConfirmation(app)
        }
    }

    private suspend fun awaitSession(apkFile: File, silentUpdate: Boolean): InstallResult {
        if (!silentUpdate) return commitSession(apkFile, requireUserAction = true)

        return try {
            commitSession(apkFile, requireUserAction = false)
        } catch (e: SilentInstallRefusedException) {
            silentUpdatesRefused = true
            Log.w(TAG, "Silent update refused by the platform (${e.message}), asking the user instead")
            commitSession(apkFile, requireUserAction = true)
        }
    }

    @SuppressLint("RequestInstallPackagesPolicy")
    private suspend fun commitSession(apkFile: File, requireUserAction: Boolean): InstallResult {
        // With the dialog asked for up front, an abort can only be the user dismissing it
        var userActionShown = requireUserAction
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply {
            setOriginatingUid(Process.myUid())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                setRequestUpdateOwnership(true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                setPackageSource(PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(
                    if (requireUserAction) {
                        PackageInstaller.SessionParams.USER_ACTION_REQUIRED
                    } else {
                        PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
                    }
                )
            }
        }

        val sessionId = installer.createSession(params)
        Log.d(TAG, "Created session $sessionId for ${apkFile.name}")

        try {
            // The copy and fsync of a multi-hundred-megabyte archive must not run on the caller's thread
            withContext(Dispatchers.IO) {
                installer.openSession(sessionId).use { session ->
                    session.openWrite("base.apk", 0, apkFile.length()).use { out ->
                        apkFile.inputStream().use { it.copyTo(out) }
                        session.fsync(out)
                    }
                }
            }
        } catch (e: Throwable) {
            // Cancellation too: it surfaces once the copy is done, and a session left behind holds
            // the whole APK until the system expires it
            runCatching { installer.abandonSession(sessionId) }
            throw e
        }

        // Some systems end a session without its status broadcast, which would leave the install
        // waiting for good. Once the session is gone, the caller checks what was installed instead
        return coroutineScope {
            val watchdog = launch {
                awaitSessionGone(installer, sessionId)
                delay(SESSION_GONE_GRACE)
                Log.w(TAG, "Session $sessionId ended without a status")
                throw InstallCancelledException()
            }
            try {
                suspendCancellableCoroutine { cont ->
                    var registered: BroadcastReceiver? = null
                    try {
                        installer.openSession(sessionId).use { session ->
                            val pi = statusPendingIntent(ACTION_INSTALL_STATUS, EXTRA_SESSION_ID, sessionId)

                            val receiver = object : BroadcastReceiver() {
                                override fun onReceive(context: Context, intent: Intent) {
                                    if (intent.getIntExtra(EXTRA_SESSION_ID, -1) != sessionId) return

                                    val status = intent.getIntExtra(
                                        PackageInstaller.EXTRA_STATUS,
                                        PackageInstaller.STATUS_FAILURE
                                    )
                                    val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                                    Log.d(TAG, "Session $sessionId status=$status message=$message")

                                    when (status) {
                                        PackageInstaller.STATUS_SUCCESS -> {
                                            app.unregisterReceiver(this)
                                            cont.resume(InstallResult.Success)
                                        }

                                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                                            // The session may be killed by a system component after this
                                            // broadcast, so the receiver is kept alive intentionally.
                                            userActionShown = true
                                            launchUserConfirmation(intent)
                                        }

                                        PackageInstaller.STATUS_FAILURE_ABORTED -> {
                                            app.unregisterReceiver(this)
                                            cont.resumeWithException(
                                                if (userActionShown) {
                                                    InstallCancelledException()
                                                } else {
                                                    SilentInstallRefusedException(message)
                                                }
                                            )
                                        }

                                        PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                                            app.unregisterReceiver(this)
                                            cont.resume(InstallResult.Conflict(message))
                                        }

                                        else -> {
                                            app.unregisterReceiver(this)
                                            if (message?.contains("dead", ignoreCase = true) == true ||
                                                message?.contains("abandoned", ignoreCase = true) == true
                                            ) {
                                                cont.resumeWithException(SessionDeadException(message))
                                            } else {
                                                cont.resume(InstallResult.Failure(message))
                                            }
                                        }
                                    }
                                }
                            }

                            registerReceiverCompat(receiver, IntentFilter(ACTION_INSTALL_STATUS))
                            registered = receiver

                            cont.invokeOnCancellation {
                                runCatching { app.unregisterReceiver(receiver) }
                                runCatching { installer.abandonSession(sessionId) }
                            }

                            session.commit(pi.intentSender)
                        }
                    } catch (e: Exception) {
                        registered?.let { runCatching { app.unregisterReceiver(it) } }
                        runCatching { installer.abandonSession(sessionId) }
                        throw e
                    }
                }
            } finally {
                watchdog.cancel()
            }
        }
    }

    private suspend fun awaitSessionGone(installer: PackageInstaller, sessionId: Int) {
        while (withContext(Dispatchers.IO) { installer.getSessionInfo(sessionId) } != null) {
            delay(SESSION_WATCH_POLL)
        }
    }

    /**
     * Whether Android would update [packageName] without its dialog. Worth asking only before an
     * install the user did not start, since nobody is there to answer one.
     */
    suspend fun canUpdateSilently(packageName: String): Boolean =
        withContext(Dispatchers.IO) { isSilentUpdateTarget(packageName) }

    private fun isSilentUpdateTarget(packageName: String): Boolean {
        // Guarded here rather than in the rule: the reads below need Android 12 themselves
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false

        val installed = runCatching {
            app.packageManager.getPackageInfo(packageName, 0)
        }.getOrNull()
        val source = installed?.let {
            runCatching { app.packageManager.getInstallSourceInfo(packageName) }.getOrNull()
        }

        return allowsSilentUpdate(
            sdkInt = Build.VERSION.SDK_INT,
            installedTargetSdk = installed?.applicationInfo?.targetSdkVersion,
            installerPackageName = source?.installingPackageName,
            updateOwnerPackageName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                source?.updateOwnerPackageName
            } else {
                null
            },
            selfPackageName = app.packageName
        )
    }

    /**
     * Launches [Intent.ACTION_INSTALL_PACKAGE] for the given [apkFile].
     * Carries the manager's own update, and the fallback for when [installInternal] throws
     * [SessionDeadException].
     * The caller is responsible for monitoring completion via package broadcasts.
     */
    fun launchIntentInstall(apkFile: File) {
        launchPackageInstall(apkFile, app.packageName)
    }

    /**
     * Launches the system package installer while asking Android to record Google Play Store
     * as the installer package. This mirrors KingInstaller's non-root install behavior.
     */
    fun launchPlayStoreInstall(apkFile: File) {
        launchPackageInstall(apkFile, PLAY_STORE_INSTALLER_PACKAGE)
    }

    /**
     * Fires the legacy [Intent.ACTION_INSTALL_PACKAGE] flow attributing the install to
     * [installerPackageName]. Used as the fallback path when session-based installs are
     * unavailable. All install flows are user-initiated from patcher/installer UI, which
     * satisfies the REQUEST_INSTALL_PACKAGES policy.
     */
    @SuppressLint("RequestInstallPackagesPolicy")
    @Suppress("DEPRECATION")
    private fun launchPackageInstall(apkFile: File, installerPackageName: String) {
        requireApkPresent(apkFile)
        Log.d(TAG, "launchPackageInstall: ${apkFile.name}, installer=$installerPackageName")
        val uri = InstallerFileProvider.getUriForFile(app, apkFile)
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, APK_MIMETYPE)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_NEW_TASK
            )
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, false)
            putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, installerPackageName)
        }
        app.startActivity(intent)
    }

    /**
     * Silent install via Shizuku/Sui. Suspends until the install completes.
     *
     * @throws InstallCancelledException if the installation was aborted or the coroutine was canceled.
     */
    suspend fun installShizuku(apkFile: File, expectedPackage: String): InstallResult {
        requireApkPresent(apkFile)
        Log.d(TAG, "installShizuku: ${apkFile.name} (${apkFile.length()} bytes)")
        return installShizukuWithInstallerPackage(apkFile, expectedPackage, null)
    }

    suspend fun installShizukuAsPlayStore(apkFile: File, expectedPackage: String): InstallResult {
        requireApkPresent(apkFile)
        Log.d(TAG, "installShizukuAsPlayStore: ${apkFile.name} (${apkFile.length()} bytes)")
        return installShizukuWithInstallerPackage(apkFile, expectedPackage, PLAY_STORE_INSTALLER_PACKAGE)
    }

    private suspend fun installShizukuWithInstallerPackage(
        apkFile: File,
        expectedPackage: String,
        installerPackageName: String?
    ): InstallResult {
        return try {
            val result = shizukuInstaller.install(apkFile, expectedPackage, installerPackageName)
            if (result.status == PackageInstaller.STATUS_SUCCESS) {
                InstallResult.Success
            } else {
                InstallResult.Failure(result.message)
            }
        } catch (e: ShizukuInstaller.InstallerOperationException) {
            when (e.status) {
                PackageInstaller.STATUS_FAILURE_CONFLICT -> InstallResult.Conflict(e.message)
                PackageInstaller.STATUS_FAILURE_ABORTED -> throw InstallCancelledException()
                else -> InstallResult.Failure(e.message)
            }
        } catch (_: TimeoutCancellationException) {
            InstallResult.Failure("Timed out waiting for Shizuku install result")
        }
    }

    /**
     * Silent uninstall via Shizuku/Sui. Suspends until uninstall completes.
     *
     * @throws UninstallCancelledException if uninstall was aborted or the coroutine was canceled.
     */
    suspend fun uninstallShizuku(packageName: String): UninstallResult {
        Log.d(TAG, "uninstallShizuku: $packageName")
        return try {
            val result = shizukuInstaller.uninstall(packageName)
            if (result.status == PackageInstaller.STATUS_SUCCESS) {
                UninstallResult.Success
            } else {
                UninstallResult.Failure(result.message)
            }
        } catch (e: ShizukuInstaller.InstallerOperationException) {
            when (e.status) {
                PackageInstaller.STATUS_FAILURE_ABORTED -> throw UninstallCancelledException()
                else -> UninstallResult.Failure(e.message)
            }
        } catch (_: TimeoutCancellationException) {
            UninstallResult.Failure("Timed out waiting for Shizuku uninstall result")
        }
    }

    /**
     * Launches the system uninstall UI for [packageName] and suspends until the user confirms
     * or dismisses.
     *
     * @throws UninstallCancelledException if the user dismissed the uninstall dialog.
     */
    suspend fun uninstall(packageName: String) = suspendCancellableCoroutine { cont ->
        val installer = app.packageManager.packageInstaller
        val requestId = uninstallRequestIds.incrementAndGet()
        val pi = statusPendingIntent(ACTION_UNINSTALL_STATUS, EXTRA_UNINSTALL_REQUEST_ID, requestId)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getIntExtra(EXTRA_UNINSTALL_REQUEST_ID, -1) != requestId) return

                val status = intent.getIntExtra(
                    PackageInstaller.EXTRA_STATUS,
                    PackageInstaller.STATUS_FAILURE
                )
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.d(TAG, "Uninstall $packageName status=$status message=$message")

                when (status) {
                    PackageInstaller.STATUS_SUCCESS -> {
                        runCatching { app.unregisterReceiver(this) }
                        cont.resume(Unit)
                    }

                    PackageInstaller.STATUS_PENDING_USER_ACTION -> launchUserConfirmation(intent)

                    PackageInstaller.STATUS_FAILURE_ABORTED -> {
                        runCatching { app.unregisterReceiver(this) }
                        cont.resumeWithException(UninstallCancelledException())
                    }

                    else -> {
                        runCatching { app.unregisterReceiver(this) }
                        cont.resumeWithException(Exception(message ?: app.getString(R.string.installer_hint_generic)))
                    }
                }
            }
        }

        registerReceiverCompat(receiver, IntentFilter(ACTION_UNINSTALL_STATUS))

        cont.invokeOnCancellation {
            runCatching { app.unregisterReceiver(receiver) }
        }

        try {
            installer.uninstall(packageName, pi.intentSender)
        } catch (e: Exception) {
            runCatching { app.unregisterReceiver(receiver) }
            throw e
        }
    }

    /**
     * Returns the actual package name of the installed Shizuku provider, or null if not installed.
     * Works in stealth mode where the package name differs from the canonical one.
     */
    fun shizukuPackageName(): String? = shizuku.providerPackageName()

    /** Returns true if Shizuku, Shizuku+ or Sui is installed on the device. */
    fun isShizukuInstalled(): Boolean = shizuku.isInstalled()

    /** Returns the current [InstallerManager.Availability] of Shizuku. */
    fun shizukuAvailability(): InstallerManager.Availability = shizukuStatus().availability

    fun shizukuStatus(): ShizukuStatus {
        val flavor = shizuku.flavor()
        // Resolved once and passed on: settling the flavor costs a round trip to the server.
        val packageName = shizuku.providerPackageName(flavor)
        val installed = shizuku.isInstalled()
        val supported = installed && !runCatching { Shizuku.isPreV11() }.getOrDefault(true)
        val running = supported && runCatching { Shizuku.pingBinder() }.getOrElse { false }
        val permissionGranted = running && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrElse { false }

        val availability = when {
            !installed -> InstallerManager.Availability(false, R.string.installer_status_shizuku_not_installed)
            !supported -> InstallerManager.Availability(false, R.string.installer_status_shizuku_unsupported)
            !running -> InstallerManager.Availability(false, R.string.installer_status_shizuku_not_running)
            !permissionGranted -> InstallerManager.Availability(false, R.string.installer_status_shizuku_permission)
            else -> InstallerManager.Availability(true)
        }

        return ShizukuStatus(
            installed = installed,
            supported = supported,
            running = running,
            permissionGranted = permissionGranted,
            flavor = flavor,
            packageName = packageName,
            availability = availability
        )
    }

    /**
     * Dispatches Shizuku's permission prompt for this app. Returns false when Shizuku is
     * missing, unsupported, not running, permission is already granted, or the IPC call failed.
     *
     * The prompt result is observed by callers via [shizukuStatus], so no
     * [Shizuku.OnRequestPermissionResultListener] is registered here.
     */
    fun requestShizukuPermission(): Boolean {
        val status = shizukuStatus()
        val canAsk = status.installed && status.supported && status.running && !status.permissionGranted
        return canAsk && runCatching {
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            true
        }.onFailure { error ->
            Log.w(TAG, "Failed to request Shizuku permission", error)
        }.getOrDefault(false)
    }

    /** Launches the Shizuku app. Returns false if it is not installed. */
    fun launchShizukuApp(): Boolean = shizuku.launchManager()

    /**
     * Rejects an APK that is gone by the time the install starts. Downloads staged in a
     * temporary directory can be cleaned up while an install dialog is still on screen, and
     * every entry point here is reached from UI that must report that instead of dying on it.
     */
    private fun requireApkPresent(apkFile: File) {
        if (!apkFile.exists()) throw MissingApkException(apkFile.path)
    }

    /** Builds the [PendingIntent] the system reports progress to, tagged to tell concurrent sessions apart. */
    private fun statusPendingIntent(action: String, requestExtra: String, requestId: Int): PendingIntent {
        val broadcastIntent = Intent(action).apply {
            `package` = app.packageName
            putExtra(requestExtra, requestId)
        }
        @Suppress("WrongConstant")
        return PendingIntent.getBroadcast(
            app,
            requestId,
            broadcastIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    /** Shows the system dialog carried by a [PackageInstaller.STATUS_PENDING_USER_ACTION] broadcast. */
    private fun launchUserConfirmation(statusIntent: Intent) {
        @Suppress("DEPRECATION", "UnsafeIntentLaunch")
        val confirmIntent = if (Build.VERSION.SDK_INT >= 33) {
            statusIntent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            statusIntent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
        // Safe to launch: intent originates from the system PackageInstaller.
        @Suppress("UnsafeIntentLaunch")
        confirmIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return

        // Android drops an activity started from the background, which is where an install that
        // began on its own usually ends up, so the dialog is handed over as a notification
        if (ManagerApplication.isInForeground) {
            app.startActivity(confirmIntent)
        } else {
            Log.w(TAG, "Confirmation needed while in the background, offering it as a notification")
            notifyPendingConfirmation(confirmIntent)
        }
    }

    /** Posts [confirmIntent] so a tap opens the install dialog the session is waiting on. */
    private fun notifyPendingConfirmation(confirmIntent: Intent) {
        PatchingNotifications.showInstallConfirmation(app, confirmIntent)

        // Tapping the notification is one way back to the dialog; opening the manager is the
        // other, and a session waiting on it must not look like an install that stalled
        ManagerApplication.onReturnToForeground = {
            PatchingNotifications.cancelInstallConfirmation(app)
            runCatching { app.startActivity(confirmIntent) }
        }
    }

    /** Registers [receiver] with [filter], applying [Context.RECEIVER_NOT_EXPORTED] on API 33+. */
    private fun registerReceiverCompat(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= 33) {
            @Suppress("WrongConstant")
            app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            app.registerReceiver(receiver, filter)
        }
    }

    data class ShizukuStatus(
        val installed: Boolean,
        val supported: Boolean,
        val running: Boolean,
        val permissionGranted: Boolean,
        val flavor: ShizukuEnvironment.Flavor,
        val packageName: String?,
        val availability: InstallerManager.Availability
    )

    companion object {
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 9162
    }
}

/**
 * The conditions Android puts on updating a package without its dialog. [installedTargetSdk] is
 * null when nothing is installed under that name, and [updateOwnerPackageName] when nothing has
 * claimed the update or the platform is too old to track it.
 *
 * Kept free of Android APIs so the order between installer and update owner can be tested.
 */
internal fun allowsSilentUpdate(
    sdkInt: Int,
    installedTargetSdk: Int?,
    installerPackageName: String?,
    updateOwnerPackageName: String?,
    selfPackageName: String
): Boolean {
    // The waiver arrived in Android 12, and only for apps built against it
    if (sdkInt < Build.VERSION_CODES.S) return false
    if (installedTargetSdk == null || installedTargetSdk < Build.VERSION_CODES.S) return false

    // An update owner, once claimed, outranks whoever installed the app
    return updateOwnerPackageName?.let { it == selfPackageName }
        ?: (installerPackageName == selfPackageName)
}

sealed class InstallResult {
    data object Success : InstallResult()
    data class Conflict(val message: String?) : InstallResult()
    data class Failure(val message: String?) : InstallResult()
}

sealed class UninstallResult {
    data object Success : UninstallResult()
    data class Failure(val message: String?) : UninstallResult()
}

/** Thrown when the user dismissed the installation dialog or the installation was aborted. */
class InstallCancelledException : Exception("Installation cancelled")

/** Thrown when the APK handed to an installer no longer exists. */
class MissingApkException(path: String) : Exception("APK does not exist: $path")

/**
 * Thrown when a session that asked for no user action was destroyed without one being shown,
 * which is how OEM builds such as HyperOS turn down a silent update.
 */
private class SilentInstallRefusedException(message: String?) : Exception(message)

/** Thrown when the PackageInstaller session was killed before completion. */
class SessionDeadException(message: String?) : Exception(message)

/** Thrown when the user dismissed the uninstallation dialog. */
class UninstallCancelledException : Exception("Uninstall cancelled by user")
