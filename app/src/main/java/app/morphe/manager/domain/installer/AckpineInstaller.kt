package app.morphe.manager.domain.installer

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import app.morphe.manager.R
import kotlinx.coroutines.delay
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import rikka.sui.Sui
import ru.solrudev.ackpine.installer.InstallFailure
import ru.solrudev.ackpine.installer.PackageInstaller
import ru.solrudev.ackpine.installer.parameters.InstallParameters
import ru.solrudev.ackpine.installer.parameters.InstallerType
import ru.solrudev.ackpine.installer.parameters.PackageSource
import ru.solrudev.ackpine.session.await
import ru.solrudev.ackpine.session.Session
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
     * On Pixel devices, Play Protect may kill the installation session while running its JIT scan.
     * This method automatically retries up to [MAX_DEAD_SESSION_RETRIES] times when that
     * happens. On subsequent attempts Play Protect uses its cached scan result and no longer
     * interrupts the session.
     *
     * @return null on success, or a typed [InstallFailure] the caller can pattern-match on.
     * @throws InstallCancelledException when the user dismisses the system install dialog.
     * @throws PlayProtectDeadSessionException after exhausting all retries due to Play Protect
     *   killing the session every time (extremely unlikely in practice).
     */
    suspend fun installInternal(apkFile: File): InstallFailure? {
        require(apkFile.exists()) { "APK file does not exist: ${apkFile.path}" }
        Log.d(TAG, "installInternal: ${apkFile.name} (${apkFile.length()} bytes)")

        var attempt = 0
        while (true) {
            attempt++
            val session = packageInstaller.createSession(
                InstallParameters.Builder(Uri.fromFile(apkFile))
                    .setInstallerType(InstallerType.SESSION_BASED)
                    .setConfirmation(Confirmation.IMMEDIATE)
                    .setName(apkFile.name)
                    // On API 33+, marking the APK as a local file disables "restricted settings"
                    // which reduces Play Protect's JIT-scan interference on Pixel devices.
                    // Silently ignored on API < 33.
                    .setPackageSource(PackageSource.LocalFile)
                    .build()
            )
            return try {
                val result = session.await()
                val failure = extractFailure(result)
                if (failure != null) {
                    if (failure.isDeadSession() && attempt <= MAX_DEAD_SESSION_RETRIES) {
                        Log.w(
                            TAG,
                            "installInternal attempt $attempt/$MAX_DEAD_SESSION_RETRIES: " +
                                    "dead session (likely Play Protect JIT scan), retrying…"
                        )
                        // Cancel the dead session explicitly so Ackpine removes it from its
                        // database before we create the next one. Without this, the old session
                        // persists alongside the new one and both fight for the system's single
                        // confirmation dialog slot - causing "finished by user" on every other
                        // attempt.
                        runCatching { session.cancel() }
                        delay(DEAD_SESSION_RETRY_DELAY_MS)
                        continue
                    }
                    Log.w(TAG, "installInternal failed: ${failure.javaClass.simpleName} - ${failure.message}")
                } else {
                    if (attempt > 1) {
                        Log.i(TAG, "installInternal succeeded on attempt $attempt: ${apkFile.name}")
                    } else {
                        Log.i(TAG, "installInternal succeeded: ${apkFile.name}")
                    }
                }
                failure
            } catch (_: CancellationException) {
                throw InstallCancelledException()
            } catch (e: Exception) {
                Log.w(TAG, "installInternal exception: ${e.message}", e)
                throw e
            }
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

        /**
         * How many times to retry after a Play Protect dead-session kill before giving up.
         * In practice the 2nd attempt almost always succeeds because Play Protect caches
         * its scan result and no longer kills the session.
         */
        private const val MAX_DEAD_SESSION_RETRIES = 3

        /** Delay between dead-session retries to let PackageManager clean up the old session. */
        private const val DEAD_SESSION_RETRY_DELAY_MS = 500L
    }
}

/**
 * Returns true when this [InstallFailure] represents a "session is dead" error caused by
 * Play Protect killing the PackageInstaller session during its JIT scan on Pixel devices.
 *
 * Play Protect sends `STATUS_FAILURE_INVALID` with a message that contains "is dead" when it
 * abandons the session to take control of the installation flow itself. The check is intentionally
 * lenient (case-insensitive substring) to survive minor wording changes across Android versions.
 */
fun InstallFailure.isDeadSession(): Boolean =
    this is InstallFailure.Generic &&
            message?.contains("is dead", ignoreCase = true) == true

/** Thrown when the user dismissed the system install dialog. */
class InstallCancelledException : Exception("Installation cancelled by user")

/** Thrown when the user dismissed the system uninstall dialog. */
class UninstallCancelledException : Exception("Uninstall cancelled by user")

/** Thrown when Ackpine reports a non-abort uninstall failure. */
class UninstallFailedException(reason: String) : Exception(reason)

/**
 * Thrown when Play Protect killed every install session and all retries were exhausted.
 * This is extremely unlikely in normal usage - after 1-2 retries Play Protect uses its
 * cached scan result and stops killing sessions.
 */
class PlayProtectDeadSessionException(attempts: Int) : Exception(
    "Play Protect killed the install session $attempts time(s). " +
            "Try disabling Play Protect temporarily or use Shizuku."
)
