package app.morphe.manager.domain.installer

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.SystemClock
import app.morphe.manager.IRootSystemService
import app.morphe.manager.service.ManagerRootService
import app.morphe.manager.util.PM
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.time.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
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
            val patchedAPK = resolvePatchedApkPath(packageName)

            // Set SELinux context, bind-mount, and restart the app atomically
            execute(
                "chcon u:object_r:apk_data_file:s0 \"$patchedAPK\"; " +
                        "mount -o bind \"$patchedAPK\" \"$stockAPK\"; " +
                        "am force-stop \"$packageName\""
            ).assertSuccess("Failed to mount APK")
        }
    }

    suspend fun unmount(packageName: String) {
        if (!isAppMounted(packageName)) return

        withContext(Dispatchers.IO) {
            val stockAPK = pm.getPackageInfo(packageName)?.applicationInfo?.sourceDir
                ?: throw Exception("Failed to load application info")

            execute("umount -l \"$stockAPK\"").assertSuccess("Failed to unmount APK")

            // Force-stop the app so it restarts clean without the unmounted patched APK.
            execute("am force-stop \"$packageName\"")
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

        // Use new path for new installations
        val modulePath = "$MODULES_PATH/$packageName-morphe"

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

        val moduleDir = remoteFS.getFile(modulePath)
        if (!moduleDir.exists() && !moduleDir.mkdirs()) {
            throw IOException("Failed to create module directory: $modulePath")
        }

        listOf(
            "service.sh",
            "module.prop",
        ).forEach { file ->
            assets.open("root/$file").use { inputStream ->
                remoteFS.getFile("$modulePath/$file").newOutputStream()
                    .use { outputStream ->
                        val content = String(inputStream.readBytes())
                            .replace("\r\n", "\n")
                            .replace("\r", "\n")
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

        // Force-stop the app so it restarts with the newly mounted patched APK.
        execute("am force-stop \"$packageName\"")
    }

    suspend fun uninstall(packageName: String) {
        val remoteFS = awaitRemoteFS()
        if (isAppMounted(packageName))
            unmount(packageName)

        val moduleDir = remoteFS.getFile("$MODULES_PATH/$packageName-morphe")

        if (!moduleDir.exists()) return

        moduleDir.deleteRecursively().also { deleted ->
            if (!deleted) throw Exception("Failed to delete files")
        }
    }

    /**
     * Resolve the path of the patched APK stored in the Morphe module directory.
     */
    private suspend fun resolvePatchedApkPath(packageName: String): String {
        val remoteFS = awaitRemoteFS()
        val moduleApk = "$MODULES_PATH/$packageName-morphe/$packageName.apk"
        if (remoteFS.getFile(moduleApk).exists()) return moduleApk

        throw Exception("Patched APK not found for mount")
    }

    companion object {
        const val MODULES_PATH = "/data/adb/modules"

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
