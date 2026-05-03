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
import androidx.core.net.toUri
import app.morphe.manager.R
import app.morphe.manager.util.APK_MIMETYPE
import kotlinx.coroutines.suspendCancellableCoroutine
import rikka.shizuku.Shizuku
import rikka.sui.Sui
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "Morphe SessionInstaller"
private const val ACTION_INSTALL_STATUS = "app.morphe.manager.INSTALL_STATUS"
private const val EXTRA_SESSION_ID = "session_id"

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

    private val shizukuInstaller = ShizukuInstaller(app)

    init {
        val isSui = Sui.init(app.packageName)
        if (!isSui) {
            runCatching { rikka.shizuku.ShizukuProvider.requestBinderForNonProviderProcess(app) }
        }
    }

    /**
     * Installs an APK using the PackageInstaller session API.
     * Suspends until the system confirms or the user cancels.
     *
     * @throws InstallCancelledException if the user dismissed the dialog or install was aborted.
     * @throws SessionDeadException if the session was killed before completion.
     */
    @SuppressLint("RequestInstallPackagesPolicy")
    suspend fun installInternal(apkFile: File): InstallResult {
        require(apkFile.exists()) { "APK does not exist: ${apkFile.path}" }
        Log.d(TAG, "installInternal: ${apkFile.name} (${apkFile.length()} bytes)")
        return suspendCancellableCoroutine { cont ->
            val installer = app.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                setOriginatingUid(Process.myUid())
                if (Build.VERSION.SDK_INT >= 33) {
                    setPackageSource(PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE)
                }
                if (Build.VERSION.SDK_INT >= 31) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
            }

            val sessionId = installer.createSession(params)
            Log.d(TAG, "Created session $sessionId for ${apkFile.name}")

            try {
                installer.openSession(sessionId).use { session ->
                    session.openWrite("base.apk", 0, apkFile.length()).use { out ->
                        apkFile.inputStream().use { it.copyTo(out) }
                        session.fsync(out)
                    }

                    val broadcastIntent = Intent(ACTION_INSTALL_STATUS).apply {
                        `package` = app.packageName
                        putExtra(EXTRA_SESSION_ID, sessionId)
                    }
                    val pi = PendingIntent.getBroadcast(
                        app, sessionId, broadcastIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )

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
                                    @Suppress("DEPRECATION", "UnsafeIntentLaunch")
                                    val confirmIntent = if (Build.VERSION.SDK_INT >= 33) {
                                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                                    } else {
                                        intent.getParcelableExtra(Intent.EXTRA_INTENT)
                                    }
                                    // Safe to launch: intent originates from the system PackageInstaller.
                                    @Suppress("UnsafeIntentLaunch")
                                    confirmIntent?.also { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                        ?.let { app.startActivity(it) }
                                }

                                PackageInstaller.STATUS_FAILURE_ABORTED -> {
                                    app.unregisterReceiver(this)
                                    cont.resumeWithException(InstallCancelledException())
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

                    cont.invokeOnCancellation {
                        runCatching { app.unregisterReceiver(receiver) }
                        runCatching { installer.abandonSession(sessionId) }
                    }

                    session.commit(pi.intentSender)
                }
            } catch (e: Exception) {
                runCatching { installer.abandonSession(sessionId) }
                throw e
            }
        }
    }

    /**
     * Launches [Intent.ACTION_INSTALL_PACKAGE] for the given [apkFile].
     * Fallback for when [installInternal] throws [SessionDeadException].
     * The caller is responsible for monitoring completion via package broadcasts.
     */
    @SuppressLint("RequestInstallPackagesPolicy")
    @Suppress("DEPRECATION")
    fun launchIntentInstall(apkFile: File) {
        require(apkFile.exists()) { "APK does not exist: ${apkFile.path}" }
        Log.d(TAG, "launchIntentInstall: ${apkFile.name}")
        val uri = InstallerFileProvider.getUriForFile(app, apkFile)
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, APK_MIMETYPE)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_NEW_TASK
            )
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, false)
            putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, app.packageName)
        }
        app.startActivity(intent)
    }

    /**
     * Silent install via Shizuku/Sui. Suspends until the install completes.
     *
     * @throws InstallCancelledException if the installation was aborted or the coroutine was canceled.
     */
    suspend fun installShizuku(apkFile: File, expectedPackage: String): InstallResult {
        require(apkFile.exists()) { "APK does not exist: ${apkFile.path}" }
        Log.d(TAG, "installShizuku: ${apkFile.name} (${apkFile.length()} bytes)")
        return try {
            val result = shizukuInstaller.install(apkFile, expectedPackage)
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
        }
    }

    /**
     * Launches the system uninstall UI for [packageName] and suspends until the user confirms
     * or dismisses. Throws [UninstallCancelledException] if the user canceled.
     */
    @Suppress("DEPRECATION")
    suspend fun uninstall(packageName: String) = suspendCancellableCoroutine { cont ->
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val pkg = intent.data?.schemeSpecificPart ?: return
                if (pkg != packageName) return
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
                app.unregisterReceiver(this)
                cont.resume(Unit)
            }
        }

        registerReceiverCompat(
            receiver,
            IntentFilter(Intent.ACTION_PACKAGE_REMOVED).apply { addDataScheme("package") }
        )

        cont.invokeOnCancellation {
            runCatching { app.unregisterReceiver(receiver) }
        }

        app.startActivity(buildUninstallIntent(packageName))
    }

    /** Returns true if Shizuku or Sui is installed on the device. */
    fun isShizukuInstalled(): Boolean {
        if (Sui.isSui()) return true
        return runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                app.packageManager.getPackageInfo(
                    ShizukuInstaller.PACKAGE_NAME,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                app.packageManager.getPackageInfo(ShizukuInstaller.PACKAGE_NAME, 0)
            }
        }.isSuccess
    }

    /** Returns the current [InstallerManager.Availability] of Shizuku for the given [target]. */
    fun shizukuAvailability(
        @Suppress("UNUSED_PARAMETER") target: InstallerManager.InstallTarget
    ): InstallerManager.Availability {
        if (Shizuku.isPreV11()) {
            return InstallerManager.Availability(false, R.string.installer_status_shizuku_unsupported)
        }
        val binderReady = runCatching { Shizuku.pingBinder() }.getOrElse { false }
        if (!binderReady) {
            return InstallerManager.Availability(false, R.string.installer_status_shizuku_not_running)
        }
        val permissionGranted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrElse { false }
        if (!permissionGranted) {
            return InstallerManager.Availability(false, R.string.installer_status_shizuku_permission)
        }
        return InstallerManager.Availability(true)
    }

    /** Launches the Shizuku app. Returns false if it is not installed. */
    fun launchShizukuApp(): Boolean {
        val intent = app.packageManager.getLaunchIntentForPackage(ShizukuInstaller.PACKAGE_NAME)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(intent)
        return true
    }

    /** Registers [receiver] with [filter], applying [Context.RECEIVER_NOT_EXPORTED] on API 33+. */
    private fun registerReceiverCompat(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= 33) {
            app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            app.registerReceiver(receiver, filter)
        }
    }

    /** Builds an [Intent.ACTION_UNINSTALL_PACKAGE] intent for [packageName]. */
    @Suppress("DEPRECATION")
    private fun buildUninstallIntent(packageName: String) =
        Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
            data = "package:$packageName".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}

sealed class InstallResult {
    data object Success : InstallResult()
    data class Conflict(val message: String?) : InstallResult()
    data class Failure(val message: String?) : InstallResult()
}

/** Thrown when the user dismissed the installation dialog or the installation was aborted. */
class InstallCancelledException : Exception("Installation cancelled")

/** Thrown when the PackageInstaller session was killed before completion. */
class SessionDeadException(message: String?) : Exception(message)

/** Thrown when the user dismissed the uninstallation dialog. */
class UninstallCancelledException : Exception("Uninstall cancelled by user")
