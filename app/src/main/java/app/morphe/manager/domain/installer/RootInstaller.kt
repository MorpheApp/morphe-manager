package app.morphe.manager.domain.installer

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
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
    } == true

    // ============================================================================
    // TEMPORARY MIGRATION CODE - Remove after sufficient adoption period (e.g., 3-6 months)
    // ============================================================================

    /**
     * Migrate module directories from old naming scheme to new one
     * This is a one-time migration for existing users
     *
     * TODO: Remove this code after most users have migrated (recommended: 3-6 months after release)
     */
    private suspend fun migrateModuleIfNeeded(packageName: String) = withContext(Dispatchers.IO) {
        val oldModulePath = "$MODULES_PATH/$packageName-revanced"
        val newModulePath = "$MODULES_PATH/$packageName-morphe"

        val remoteFS = try {
            awaitRemoteFS()
        } catch (_: Exception) {
            return@withContext // Can't migrate without root
        }

        val oldModule = remoteFS.getFile(oldModulePath)
        val newModule = remoteFS.getFile(newModulePath)

        // If old module exists and new doesn't, migrate
        if (oldModule.exists() && !newModule.exists()) {
            try {
                // Unmount if currently mounted
                if (isAppMountedInternal(packageName)) {
                    unmountInternal(packageName)
                }

                // Rename the directory
                execute("mv \"$oldModulePath\" \"$newModulePath\"")
                    .assertSuccess("Failed to migrate module directory")

                // Update module.prop with new path
                val modulePropPath = "$newModulePath/module.prop"
                val modulePropFile = remoteFS.getFile(modulePropPath)

                if (modulePropFile.exists()) {
                    val content = modulePropFile.newInputStream().use {
                        String(it.readBytes())
                    }

                    val updatedContent = content
                        .replace("$packageName-revanced", "$packageName-morphe")
                        .replace("ReVanced", "Morphe") // Update display name if present

                    modulePropFile.newOutputStream().use {
                        it.write(updatedContent.toByteArray())
                    }
                }

                // Update service.sh
                val serviceShPath = "$newModulePath/service.sh"
                val serviceShFile = remoteFS.getFile(serviceShPath)

                if (serviceShFile.exists()) {
                    val content = serviceShFile.newInputStream().use {
                        String(it.readBytes())
                    }

                    val updatedContent = content
                        .replace("$packageName-revanced", "$packageName-morphe")

                    serviceShFile.newOutputStream().use {
                        it.write(updatedContent.toByteArray())
                    }
                }

                Log.d(TAG, "Successfully migrated module: $packageName from -revanced to -morphe")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to migrate module for $packageName", e)
                // Don't throw - allow fallback to old path
            }
        }
    }

    /**
     * Internal mount check that doesn't trigger migration
     * Used during migration process to avoid recursion
     *
     * TODO: Remove this code after migration period
     */
    private suspend fun isAppMountedInternal(packageName: String) = withContext(Dispatchers.IO) {
        pm.getPackageInfo(packageName)?.applicationInfo?.sourceDir?.let {
            execute("mount | grep \"$it\"").isSuccess
        } == true
    }

    /**
     * Internal unmount that doesn't trigger migration
     * Used during migration process to avoid recursion
     *
     * TODO: Remove this code after migration period
     */
    private suspend fun unmountInternal(packageName: String) {
        if (!isAppMountedInternal(packageName)) return

        withContext(Dispatchers.IO) {
            val stockAPK = pm.getPackageInfo(packageName)?.applicationInfo?.sourceDir
                ?: throw Exception("Failed to load application info")

            execute("umount -l \"$stockAPK\"").assertSuccess("Failed to unmount APK")
        }
    }

    // ============================================================================
    // END OF TEMPORARY MIGRATION CODE
    // ============================================================================

    suspend fun isAppMounted(packageName: String) = withContext(Dispatchers.IO) {
        pm.getPackageInfo(packageName)?.applicationInfo?.sourceDir?.let {
            execute("mount | grep \"$it\"").isSuccess
        } == true
    }

    suspend fun mount(packageName: String) {
        if (isAppMounted(packageName)) return

        // TEMPORARY: Try migration
        // TODO: Remove migration call after adoption period
        migrateModuleIfNeeded(packageName)

        withContext(Dispatchers.IO) {
            val stockAPK = pm.getPackageInfo(packageName)?.applicationInfo?.sourceDir
                ?: throw Exception("Failed to load application info")

            val remoteFS = awaitRemoteFS()

            // Try new path first
            var patchedAPK = "$MODULES_PATH/$packageName-morphe/$packageName.apk"

            // TEMPORARY: Fallback to old path if new doesn't exist
            // TODO: Remove this fallback after migration period
            if (!remoteFS.getFile(patchedAPK).exists()) {
                patchedAPK = "$MODULES_PATH/$packageName-revanced/$packageName.apk"
            }

            execute("mount -o bind \"$patchedAPK\" \"$stockAPK\"")
                .assertSuccess("Failed to mount APK")
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

        // Try new path first
        var moduleDir = remoteFS.getFile("$MODULES_PATH/$packageName-morphe")

        // TEMPORARY: Fallback to old path for backward compatibility
        // TODO: Remove this fallback after migration period
        if (!moduleDir.exists()) {
            moduleDir = remoteFS.getFile("$MODULES_PATH/$packageName-revanced")
        }

        if (!moduleDir.exists()) return

        moduleDir.deleteRecursively().also { deleted ->
            if (!deleted) throw Exception("Failed to delete files")
        }
    }

    companion object {
        const val MODULES_PATH = "/data/adb/modules"
        private const val TAG = "RootInstaller"

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
