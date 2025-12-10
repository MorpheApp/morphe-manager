package app.revanced.manager.domain.installer

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
import app.universal.revanced.manager.R
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

class ShizukuInstaller(private val app: Application) {

    init {
        val isSui = Sui.init(app.packageName)
        if (!isSui) {
            runCatching { ShizukuProvider.requestBinderForNonProviderProcess(app) }
        }
    }

    data class InstallResult(val status: Int, val message: String?)

    fun availability(target: InstallerManager.InstallTarget): InstallerManager.Availability {
        if (Shizuku.isPreV11()) {
            return InstallerManager.Availability(false, R.string.installer_status_shizuku_unsupported)
        }
        val binderReady = runCatching { Shizuku.pingBinder() }.getOrElse { false }
        if (!binderReady) {
            return InstallerManager.Availability(false, R.string.installer_status_shizuku_not_running)
        }
        val permissionGranted = runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrElse { false }
        if (!permissionGranted) {
            return InstallerManager.Availability(false, R.string.installer_status_shizuku_permission)
        }
        return InstallerManager.Availability(true)
    }

    fun isInstalled(): Boolean {
        if (Sui.isSui()) return true
        return runCatching {
            app.packageManager.getPackageInfo(PACKAGE_NAME, 0)
        }.isSuccess
    }

    fun launchApp(): Boolean {
        val intent = app.packageManager.getLaunchIntentForPackage(PACKAGE_NAME)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(intent)
        return true
    }

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
            userId,
            app
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

    private fun currentUserId(): Int = Process.myUid() / 100000

    class InstallerOperationException(val status: Int, override val message: String?) : Exception(message)

    companion object {
        private const val SHELL_PACKAGE = "com.android.shell"
        private const val BASE_APK_NAME = "base.apk"
        internal const val PACKAGE_NAME = "moe.shizuku.privileged.api"
    }
}

private object PackageInstallerCompat {
    private const val INSTALL_REPLACE_EXISTING = 0x00000002
    private const val INSTALL_ALLOW_TEST = 0x00000004

    fun createPackageInstaller(
        remote: IPackageInstaller,
        installerPackageName: String,
        installerAttributionTag: String?,
        userId: Int,
        app: Application
    ): PackageInstaller {
        return try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    PackageInstaller::class.java
                        .getDeclaredConstructor(
                            IPackageInstaller::class.java,
                            String::class.java,
                            String::class.java,
                            Int::class.javaPrimitiveType
                        )
                        .apply { isAccessible = true }
                        .newInstance(remote, installerPackageName, installerAttributionTag, userId)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    PackageInstaller::class.java
                        .getDeclaredConstructor(
                            IPackageInstaller::class.java,
                            String::class.java,
                            Int::class.javaPrimitiveType
                        )
                        .apply { isAccessible = true }
                        .newInstance(remote, installerPackageName, userId)
                }
                else -> {
                    PackageInstaller::class.java
                        .getDeclaredConstructor(
                            android.content.Context::class.java,
                            PackageManager::class.java,
                            IPackageInstaller::class.java,
                            String::class.java,
                            Int::class.javaPrimitiveType
                        )
                        .apply { isAccessible = true }
                        .newInstance(app, app.packageManager, remote, installerPackageName, userId)
                }
            }
        } catch (error: ReflectiveOperationException) {
            throw RuntimeException(error)
        }
    }

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

    fun applyFlags(params: PackageInstaller.SessionParams) {
        runCatching {
            val field = PackageInstaller.SessionParams::class.java.getDeclaredField("installFlags")
            field.isAccessible = true
            val current = field.getInt(params)
            field.setInt(params, current or INSTALL_REPLACE_EXISTING or INSTALL_ALLOW_TEST)
        }
    }
}

private object IntentSenderCompat {
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
