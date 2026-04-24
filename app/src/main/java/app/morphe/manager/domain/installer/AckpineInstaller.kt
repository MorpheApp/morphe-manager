package app.morphe.manager.domain.installer

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import app.morphe.manager.R
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import rikka.sui.Sui
import ru.solrudev.ackpine.installer.InstallFailure
import ru.solrudev.ackpine.installer.PackageInstaller
import ru.solrudev.ackpine.installer.parameters.InstallParameters
import ru.solrudev.ackpine.installer.parameters.InstallerType
import ru.solrudev.ackpine.installer.parameters.PackageSource
import ru.solrudev.ackpine.session.Session
import ru.solrudev.ackpine.session.await
import ru.solrudev.ackpine.session.parameters.Confirmation
import ru.solrudev.ackpine.shizuku.ShizukuPlugin
import ru.solrudev.ackpine.uninstaller.PackageUninstaller
import ru.solrudev.ackpine.uninstaller.parameters.UninstallParameters
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "Morphe AckpineInstaller"

/**
 * Wraps Ackpine for internal (PackageInstaller API) and Shizuku installs.
 * Root/mount installs are still handled by [RootInstaller].
 */
class AckpineInstaller(private val app: Application) {

    private val packageInstaller: PackageInstaller = PackageInstaller.getInstance(app)
    private val packageUninstaller: PackageUninstaller = PackageUninstaller.getInstance(app)

    init {
        val isSui = Sui.init(app.packageName)
        if (!isSui) {
            runCatching { ShizukuProvider.requestBinderForNonProviderProcess(app) }
        }
    }

    /**
     * Installs an APK using the standard Android PackageInstaller API via Ackpine.
     * Suspends until the user confirms or cancels the system dialog.
     *
     * @return null on success, or a typed [InstallFailure] the caller can pattern-match on.
     * @throws InstallCancelledException when the user dismisses the system install dialog.
     */
    suspend fun installInternal(apkFile: File): InstallFailure? {
        require(apkFile.exists()) { "APK file does not exist: ${apkFile.path}" }
        Log.d(TAG, "installInternal: ${apkFile.name} (${apkFile.length()} bytes)")
        val session = packageInstaller.createSession(
            InstallParameters.Builder(Uri.fromFile(apkFile))
                .setInstallerType(InstallerType.SESSION_BASED)
                .setConfirmation(Confirmation.IMMEDIATE)
                .setName(apkFile.name)
                // PackageSource.LocalFile disables "restricted settings" enforcement on API 33+
                .setPackageSource(PackageSource.LocalFile)
                .build()
        )
        return try {
            extractFailure(session.await()).also { failure ->
                if (failure != null) {
                    Log.w(TAG, "installInternal failed: ${failure.javaClass.simpleName} - ${failure.message}")
                } else {
                    Log.i(TAG, "installInternal succeeded: ${apkFile.name}")
                }
            }
        } catch (_: CancellationException) {
            throw InstallCancelledException()
        } catch (e: Exception) {
            Log.w(TAG, "installInternal exception: ${e.message}", e)
            throw e
        }
    }

    /**
     * Installs an APK silently via Shizuku/Sui using Ackpine's ShizukuPlugin.
     *
     * @return null on success, or a typed [InstallFailure] the caller can pattern-match on.
     * @throws InstallCancelledException when aborted.
     */
    suspend fun installShizuku(apkFile: File): InstallFailure? {
        require(apkFile.exists()) { "APK file does not exist: ${apkFile.path}" }
        Log.d(TAG, "installShizuku: ${apkFile.name} (${apkFile.length()} bytes)")
        val session = packageInstaller.createSession(
            InstallParameters.Builder(Uri.fromFile(apkFile))
                .setInstallerType(InstallerType.SESSION_BASED)
                .setConfirmation(Confirmation.IMMEDIATE)
                .setName(apkFile.name)
                .registerPlugin(
                    ShizukuPlugin::class.java,
                    ShizukuPlugin.InstallParameters.Builder()
                        .setReplaceExisting(true)
                        .build()
                )
                .build()
        )
        return try {
            extractFailure(session.await()).also { failure ->
                if (failure != null) {
                    Log.w(TAG, "installShizuku failed: ${failure.javaClass.simpleName} - ${failure.message}")
                } else {
                    Log.i(TAG, "installShizuku succeeded: ${apkFile.name}")
                }
            }
        } catch (e: CancellationException) {
            throw InstallCancelledException().initCause(e)
        } catch (e: Exception) {
            Log.w(TAG, "installShizuku exception: ${e.message}", e)
            throw e
        }
    }

    /**
     * Uninstalls a package via Ackpine. Shows the system confirmation dialog.
     * Suspends until the user confirms or cancels.
     *
     * @throws UninstallCancelledException when the user dismisses the dialog.
     * @throws UninstallFailedException on any other failure.
     */
    suspend fun uninstall(packageName: String) {
        val session = packageUninstaller.createSession(
            UninstallParameters.Builder(packageName)
                .setConfirmation(Confirmation.IMMEDIATE)
                .build()
        )
        try {
            when (val result = session.await()) {
                is Session.State.Succeeded -> return
                is Session.State.Failed -> {
                    throw UninstallFailedException(result.failure.message ?: result.failure.javaClass.simpleName)
                }
            }
        } catch (e: CancellationException) {
            throw UninstallCancelledException().initCause(e)
        }
    }

    private fun extractFailure(result: Session.State.Completed<InstallFailure>): InstallFailure? =
        when (result) {
            is Session.State.Succeeded -> null
            is Session.State.Failed -> result.failure
        }

    fun isShizukuInstalled(): Boolean {
        if (Sui.isSui()) return true
        return runCatching {
            app.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        }.isSuccess
    }

    fun shizukuAvailability(@Suppress("UNUSED_PARAMETER") target: InstallerManager.InstallTarget): InstallerManager.Availability {
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

    fun launchShizukuApp(): Boolean {
        val intent = app.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(intent)
        return true
    }

    companion object {
        internal const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    }
}

/** Thrown when the user dismissed the system install dialog. */
class InstallCancelledException : Exception("Installation cancelled by user")

/** Thrown when the user dismissed the system uninstall dialog. */
class UninstallCancelledException : Exception("Uninstall cancelled by user")

/** Thrown when Ackpine reports a non-abort uninstall failure. */
class UninstallFailedException(reason: String) : Exception(reason)
