package app.revanced.manager.domain.installer

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.SystemClock
import app.revanced.manager.IRootSystemService
import app.revanced.manager.service.ManagerRootService
import app.revanced.manager.util.PM
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.time.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration

class RootInstaller(
    private val app: Application,
    private val pm: PM
) : ServiceConnection {
    private var remoteFS = CompletableDeferred<FileSystemManager>()
    @Volatile
    private var cachedHasRoot: Boolean? = null
    @Volatile
    private var lastRootCheck = 0L

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        val ipc = IRootSystemService.Stub.asInterface(service)
        val binder = ipc.fileSystemService

        remoteFS.complete(FileSystemManager.getRemote(binder))
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        remoteFS = CompletableDeferred()
    }

    private suspend fun awaitRemoteFS(): FileSystemManager {
        if (remoteFS.isActive) {
            withContext(Dispatchers.Main) {
                val intent = Intent(app, ManagerRootService::class.java)
                RootService.bind(intent, this@RootInstaller)
            }
        }

        return withTimeoutOrNull(Duration.ofSeconds(20L)) {
            remoteFS.await()
        } ?: throw RootServiceException()
    }

    private suspend fun getShell() = with(CompletableDeferred<Shell>()) {
        Shell.getShell(::complete)

        await()
    }

    suspend fun execute(vararg commands: String) = getShell().newJob().add(*commands).exec()

    fun hasRootAccess(): Boolean {
        Shell.isAppGrantedRoot()?.let { granted ->
            if (granted) cachedHasRoot = true
            return granted
        }

        cachedHasRoot?.let { cached ->
            if (cached) return true
            if (SystemClock.elapsedRealtime() - lastRootCheck < ROOT_CHECK_INTERVAL_MS) return false
        }

        synchronized(this) {
            Shell.isAppGrantedRoot()?.let { granted ->
                if (granted) cachedHasRoot = true
                return granted
            }

            cachedHasRoot?.let { cached ->
                if (cached) return true
                if (SystemClock.elapsedRealtime() - lastRootCheck < ROOT_CHECK_INTERVAL_MS) return false
            }

            val probeResult = runCatching { Shell.cmd("id").exec() }.getOrNull()
            lastRootCheck = SystemClock.elapsedRealtime()

            val granted = Shell.isAppGrantedRoot() == true || probeResult?.hasRootUid() == true
            cachedHasRoot = granted

            return granted
        }
    }

    fun isDeviceRooted() = System.getenv("PATH")?.split(":")?.any { path ->
        File(path, "su").canExecute()
    } ?: false

    suspend fun isAppInstalled(packageName: String) =
        awaitRemoteFS().getFile("$modulesPath/$packageName-revanced").exists()

    suspend fun isAppMounted(packageName: String) = withContext(Dispatchers.IO) {
        pm.getPackageInfo(packageName)?.applicationInfo?.sourceDir?.let {
            execute("mount | grep \"$it\"").isSuccess
        } ?: false
    }

    suspend fun mount(packageName: String) {
        if (isAppMounted(packageName)) return

        withContext(Dispatchers.IO) {
            val stockAPK = pm.getPackageInfo(packageName)?.applicationInfo?.sourceDir
                ?: throw Exception("Failed to load application info")
            val patchedAPK = "$modulesPath/$packageName-revanced/$packageName.apk"

            execute("mount -o bind \"$patchedAPK\" \"$stockAPK\"").assertSuccess("Failed to mount APK")
        }
    }

    suspend fun unmount(packageName: String) {
        if (!isAppMounted(packageName)) return

        withContext(Dispatchers.IO) {
            val stockAPK = pm.getPackageInfo(packageName)?.applicationInfo?.sourceDir
                ?: throw Exception("Failed to load application info")

            execute("umount -l \"$stockAPK\"").assertSuccess("Failed to unmount APK")
        }
    }

    suspend fun install(
        patchedAPK: File,
        stockAPK: File?,
        packageName: String,
        version: String,
        label: String
    ) = withContext(Dispatchers.IO) {
        val remoteFS = awaitRemoteFS()
        val assets = app.assets
        val modulePath = "$modulesPath/$packageName-revanced"

        unmount(packageName)

        stockAPK?.let { stockApp ->
            pm.getPackageInfo(packageName)?.let { packageInfo ->
                // TODO: get user id programmatically
                if (pm.getVersionCode(packageInfo) <= pm.getVersionCode(
                        pm.getPackageInfo(patchedAPK)
                            ?: error("Failed to get package info for patched app")
                    )
                )
                    execute("pm uninstall -k --user 0 $packageName").assertSuccess("Failed to uninstall stock app")
            }

            execute("pm install \"${stockApp.absolutePath}\"").assertSuccess("Failed to install stock app")
        }

        remoteFS.getFile(modulePath).mkdir()

        listOf(
            "service.sh",
            "module.prop",
        ).forEach { file ->
            assets.open("root/$file").use { inputStream ->
                remoteFS.getFile("$modulePath/$file").newOutputStream()
                    .use { outputStream ->
                        val content = String(inputStream.readBytes())
                            .replace("__PKG_NAME__", packageName)
                            .replace("__VERSION__", version)
                            .replace("__LABEL__", label)
                            .toByteArray()

                        outputStream.write(content)
                    }
            }
        }

        "$modulePath/$packageName.apk".let { apkPath ->

            remoteFS.getFile(patchedAPK.absolutePath)
                .also { if (!it.exists()) throw Exception("File doesn't exist") }
                .newInputStream().use { inputStream ->
                    remoteFS.getFile(apkPath).newOutputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

            execute(
                "chmod 644 $apkPath",
                "chown system:system $apkPath",
                "chcon u:object_r:apk_data_file:s0 $apkPath",
                "chmod +x $modulePath/service.sh"
            ).assertSuccess("Failed to set file permissions")
        }
    }

    suspend fun uninstall(packageName: String) {
        val remoteFS = awaitRemoteFS()
        if (isAppMounted(packageName))
            unmount(packageName)

        val moduleDir = remoteFS.getFile("$modulesPath/$packageName-revanced")
        if (!moduleDir.exists()) return

        moduleDir.deleteRecursively().also { deleted ->
            if (!deleted) throw Exception("Failed to delete files")
        }
    }

    companion object {
        const val modulesPath = "/data/adb/modules"

        private fun Shell.Result.assertSuccess(errorMessage: String) {
            if (!isSuccess) throw Exception(errorMessage)
        }

        private const val ROOT_CHECK_INTERVAL_MS = 1_000L
    }
}

class RootServiceException : Exception("Root not available")

private fun Shell.Result.hasRootUid() = isSuccess && out.any { line ->
    line.contains("uid=0")
}
