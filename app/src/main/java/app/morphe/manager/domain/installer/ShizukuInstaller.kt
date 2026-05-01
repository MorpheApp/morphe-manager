package app.morphe.manager.domain.installer

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.content.IntentSender
import android.content.pm.IPackageInstaller
import android.content.pm.IPackageInstallerSession
import android.content.pm.IPackageManager
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.os.RemoteException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.ShizukuProvider
import rikka.shizuku.SystemServiceHelper
import rikka.sui.Sui
import java.io.File
import java.io.IOException
import java.lang.reflect.Constructor

/**
 * Performs silent APK installation via the Shizuku/Sui privileged service.
 *
 * Communicates directly with the system [IPackageInstaller] through Shizuku binder IPC,
 * bypassing the user confirmation dialog required by the standard PackageInstaller API.
 */
class ShizukuInstaller(private val app: Application) {

    init {
        val isSui = Sui.init(app.packageName)
        if (!isSui) {
            runCatching { ShizukuProvider.requestBinderForNonProviderProcess(app) }
        }
    }

    /** Result of a silent installation attempt. */
    data class InstallResult(val status: Int, val message: String?)

    /**
     * Silently installs [sourceFile] via Shizuku/Sui. Suspends until the installation completes.
     *
     * @param sourceFile The APK file to install.
     * @param expectedPackage The expected package name of the APK, used to configure the session.
     * @throws InstallerOperationException if the installation fails or is aborted.
     */
    @SuppressLint("RequestInstallPackagesPolicy")
    suspend fun install(sourceFile: File, expectedPackage: String): InstallResult = withContext(Dispatchers.IO) {
        val packageInstaller = obtainPackageInstaller()
        val isRoot = runCatching { Shizuku.getUid() }.getOrDefault(-1) == 0
        val installerPackageName = if (isRoot) app.packageName else SHELL_PACKAGE
        val installerAttributionTag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) app.attributionTag else null
        val userId = if (isRoot) currentUserId() else 0

        val packageInstallerWrapper = PackageInstallerCompat.createPackageInstaller(
            packageInstaller,
            installerPackageName,
            installerAttributionTag,
            userId
        )
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            runCatching { setAppPackageName(expectedPackage) }
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                setRequestUpdateOwnership(true)
            }
        }
        PackageInstallerCompat.applyFlags(params)
        val sessionId = packageInstallerWrapper.createSession(params)
        val sessionBinder = IPackageInstallerSession.Stub.asInterface(
            ShizukuBinderWrapper(packageInstaller.openSession(sessionId).asBinder())
        )
        val session = PackageInstallerCompat.createSession(sessionBinder)

        try {
            sourceFile.inputStream().use { input ->
                session.openWrite(BASE_APK_NAME, 0, sourceFile.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }

            val resultDeferred = CompletableDeferred<InstallResult>()
            val intentSender = IntentSenderCompat.create { intent ->
                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                resultDeferred.complete(InstallResult(status, message))
            }

            session.commit(intentSender)
            val result = resultDeferred.await()
            if (result.status != PackageInstaller.STATUS_SUCCESS) {
                throw InstallerOperationException(result.status, result.message)
            }
            result
        } finally {
            runCatching { session.close() }
        }
    }

    /** Resolves [IPackageInstaller] via the Shizuku binder IPC. */
    private fun obtainPackageInstaller(): IPackageInstaller {
        val binder = SystemServiceHelper.getSystemService("package")
            ?: throw IOException("Package service unavailable")
        try {
            val manager = IPackageManager.Stub.asInterface(ShizukuBinderWrapper(binder))
            val installer = manager.packageInstaller
            return IPackageInstaller.Stub.asInterface(ShizukuBinderWrapper(installer.asBinder()))
        } catch (error: RemoteException) {
            throw IOException(error)
        }
    }

    /** Returns the user ID derived from the current process UID. */
    private fun currentUserId(): Int = Process.myUid() / 100000

    /** Thrown when the installation fails or is aborted by the system. */
    class InstallerOperationException(val status: Int, override val message: String?) : Exception(message)

    companion object {
        private const val SHELL_PACKAGE = "com.android.shell"
        private const val BASE_APK_NAME = "base.apk"
        internal const val PACKAGE_NAME = "moe.shizuku.privileged.api"
    }
}

/**
 * Reflection-based compatibility helpers for constructing [PackageInstaller] and
 * [PackageInstaller.Session] instances from raw Shizuku binder objects.
 *
 * Required because the public API does not expose constructors that accept an [IPackageInstaller]
 * directly - these are internal to the system and accessed via reflection.
 */
private object PackageInstallerCompat {
    private const val INSTALL_REPLACE_EXISTING = 0x00000002

    /**
     * Constructs a [PackageInstaller] wrapping the given [remote] binder.
     * Uses the appropriate constructor signature for the running API level.
     */
    fun createPackageInstaller(
        remote: IPackageInstaller,
        installerPackageName: String,
        installerAttributionTag: String?,
        userId: Int
    ): PackageInstaller {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PackageInstaller::class.java
                    .getDeclaredConstructor(
                        IPackageInstaller::class.java,
                        String::class.java,
                        String::class.java,
                        Int::class.javaPrimitiveType
                    )
                    .apply { isAccessible = true }
                    .newInstance(remote, installerPackageName, installerAttributionTag, userId)
            } else {
                PackageInstaller::class.java
                    .getDeclaredConstructor(
                        IPackageInstaller::class.java,
                        String::class.java,
                        Int::class.javaPrimitiveType
                    )
                    .apply { isAccessible = true }
                    .newInstance(remote, installerPackageName, userId)
            }
        } catch (error: ReflectiveOperationException) {
            throw RuntimeException(error)
        }
    }

    /** Constructs a [PackageInstaller.Session] wrapping the given [remote] session binder. */
    fun createSession(remote: IPackageInstallerSession): PackageInstaller.Session {
        return try {
            PackageInstaller.Session::class.java
                .getDeclaredConstructor(IPackageInstallerSession::class.java)
                .apply { isAccessible = true }
                .newInstance(remote)
        } catch (error: ReflectiveOperationException) {
            throw RuntimeException(error)
        }
    }

    /**
     * Sets [INSTALL_REPLACE_EXISTING] on the internal installFlags field of
     * [PackageInstaller.SessionParams] via reflection. Required for Shizuku installs to correctly
     * replace an existing package - without this flag the system may return
     * [PackageInstaller.STATUS_FAILURE_CONFLICT] even when signatures match.
     */
    @SuppressLint("DiscouragedPrivateApi")
    @Suppress("SoonBlockedPrivateApi", "PrivateApi")
    fun applyFlags(params: PackageInstaller.SessionParams) {
        runCatching {
            val field = PackageInstaller.SessionParams::class.java.getDeclaredField("installFlags")
            field.isAccessible = true
            field.setInt(params, field.getInt(params) or INSTALL_REPLACE_EXISTING)
        }
    }

}

/**
 * Creates an [IntentSender] backed by a custom [android.content.IIntentSender] stub.
 *
 * Required because Shizuku sessions deliver their result intent through an [IntentSender],
 * but the standard [android.app.PendingIntent]-based approach is unavailable in the Shizuku context.
 */
private object IntentSenderCompat {
    /**
     * Returns an [IntentSender] that invokes [callback] with the delivered [Intent].
     */
    fun create(callback: (Intent) -> Unit): IntentSender {
        val binder = object : android.content.IIntentSender.Stub() {
            override fun send(
                code: Int,
                intent: Intent?,
                resolvedType: String?,
                finishedReceiver: android.content.IIntentReceiver?,
                requiredPermission: String?,
                options: Bundle?
            ): Int {
                intent?.let(callback)
                return 0
            }

            override fun send(
                code: Int,
                intent: Intent?,
                resolvedType: String?,
                whitelistToken: IBinder?,
                finishedReceiver: android.content.IIntentReceiver?,
                requiredPermission: String?,
                options: Bundle?
            ) {
                intent?.let(callback)
            }
        }
        return try {
            val ctor: Constructor<IntentSender> = IntentSender::class.java.getDeclaredConstructor(android.content.IIntentSender::class.java)
            ctor.isAccessible = true
            ctor.newInstance(binder)
        } catch (error: ReflectiveOperationException) {
            throw RuntimeException(error)
        }
    }
}
