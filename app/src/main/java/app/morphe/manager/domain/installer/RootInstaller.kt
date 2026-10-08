/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/domain/installer/RootInstaller.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.domain.installer

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import app.morphe.manager.IRootSystemService
import app.morphe.manager.service.ManagerRootService
import app.morphe.manager.util.PLAY_STORE_INSTALLER_PACKAGE
import app.morphe.manager.util.PM
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.time.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds

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

    // A job keeps no output unless it is given lists to fill, and callers read both streams
    suspend fun execute(vararg commands: String) =
        getShell().newJob().add(*commands).to(ArrayList(), ArrayList()).exec()

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
            execute("mount | grep -F ${it.shellQuote()}").isSuccess
        } ?: false
    }

    suspend fun mount(packageName: String) {
        withContext(Dispatchers.IO) {
            val stockAPK = pm.getPackageInfo(packageName)?.applicationInfo?.sourceDir
                ?: throw Exception("Failed to load application info")
            val patchedAPK = resolvePatchedApkPath(packageName)
            val stockPath = stockAPK.shellQuote()
            val patchedPath = patchedAPK.shellQuote()

            // Set SELinux context, bind-mount in the root and zygote namespaces, and restart
            // the app so its next process inherits the patched APK view.
            execute(
                "chcon u:object_r:apk_data_file:s0 $patchedPath; " +
                        unmountBindCommands(stockPath) + "; " +
                        "mount -o bind $patchedPath $stockPath; " +
                        mountInZygoteNamespacesCommand(patchedPath, stockPath) + "; " +
                        "am force-stop ${packageName.shellQuote()}"
            ).assertSuccess("Failed to mount APK")
        }
    }

    suspend fun unmount(packageName: String) {
        withContext(Dispatchers.IO) {
            val stockAPK = pm.getPackageInfo(packageName)?.applicationInfo?.sourceDir
                ?: return@withContext
            val stockPath = stockAPK.shellQuote()

            // Force-stop the app so it restarts clean without the unmounted patched APK.
            execute(
                unmountBindCommands(stockPath) + "; " +
                        "am force-stop ${packageName.shellQuote()}"
            ).assertSuccess("Failed to unmount APK")
        }
    }

    suspend fun install(
        patchedAPK: File,
        stockAPK: File?,
        packageName: String,
        version: String,
        label: String
    ) = withContext(Dispatchers.IO) {
        require(isValidPackageName(packageName)) { "Invalid package name: $packageName" }

        val remoteFS = awaitRemoteFS()
        val assets = app.assets

        // Use new path for new installations
        val moduleId = moduleId(packageName)
        val modulePath = "$MODULES_PATH/$moduleId"

        unmount(packageName)

        var installedStockInfo = pm.getPackageInfo(packageName)
        var stockSourceFile: File? = null

        stockAPK?.let { stockApp ->
            val stockInfo = pm.getPackageInfo(stockApp)
                ?: error("Failed to get package info for stock app")
            if (stockInfo.packageName != packageName) {
                error("Stock APK package (${stockInfo.packageName}) does not match $packageName")
            }

            val installedInfo = pm.getPackageInfo(packageName)
            val stockAlreadyInstalled = installedInfo != null &&
                    pm.getVersionCode(installedInfo) == pm.getVersionCode(stockInfo) &&
                    installedInfo.versionName == stockInfo.versionName

            if (!stockAlreadyInstalled) {
                val result = installStockApp(stockApp, packageName)
                val stockInstalled = waitForInstalledStock(packageName, stockInfo)
                if (!stockInstalled) {
                    if (!result.isSuccess) throw StockAppInstallException(result.failureDetail())
                    throw StockAppInstallException("Stock app install did not settle")
                }
            }

            installedStockInfo = pm.getPackageInfo(packageName)
            stockSourceFile = stockApp
        }

        val moduleDir = remoteFS.getFile(modulePath)
        if (!moduleDir.exists() && !moduleDir.mkdirs()) {
            throw IOException("Failed to create module directory: $modulePath")
        }

        listOf(
            "service.sh",
            "post-fs-data.sh",
            "module.prop",
        ).forEach { file ->
            assets.open("root/$file").use { inputStream ->
                remoteFS.getFile("$modulePath/$file").newOutputStream()
                    .use { outputStream ->
                        val content = String(inputStream.readBytes())
                            .replace("\r\n", "\n")
                            .replace("\r", "\n")
                            .replace("__PKG_NAME__", packageName)
                            .replace("__MODULE_ID__", moduleId)
                            .replace("__VERSION__", version)
                            .replace("__LABEL__", label)
                            .toByteArray()

                        outputStream.write(content)
                    }
            }
        }

        val installedStockPath = installedStockInfo?.applicationInfo?.sourceDir
        val stockMountPaths = collectStockMountPaths(packageName, installedStockPath)
        val stockModuleApk = "$modulePath/$packageName-stock.apk"
        val stockSourcePath = stockSourceFile?.absolutePath ?: installedStockPath
        val stockModuleApkWritten = !stockSourcePath.isNullOrBlank() && stockMountPaths.isNotEmpty()
        if (stockModuleApkWritten) {
            copyIntoPlace(stockSourcePath, stockModuleApk, "Stock APK doesn't exist")

            remoteFS.getFile("$modulePath/stock-paths.txt").newOutputStream().use { outputStream ->
                outputStream.write(stockMountPaths.joinToString("\n", postfix = "\n").toByteArray())
            }
        }

        "$modulePath/$packageName.apk".let { apkPath ->
            copyIntoPlace(patchedAPK.absolutePath, apkPath, "File doesn't exist")

            setModuleFilePermissions(
                modulePath = modulePath,
                patchedApkPath = apkPath,
                stockApkPath = stockModuleApk.takeIf { stockModuleApkWritten }
            )
        }
    }

    suspend fun installAsPlayStore(apkFile: File) = withContext(Dispatchers.IO) {
        if (!apkFile.exists()) throw Exception("File doesn't exist")

        execute(
            "pm install -t -i ${PLAY_STORE_INSTALLER_PACKAGE.shellQuote()} -r ${apkFile.absolutePath.shellQuote()}"
        ).assertSuccess("Failed to install APK as Play Store")
    }

    suspend fun uninstallPackage(packageName: String) = withContext(Dispatchers.IO) {
        execute(
            "pm uninstall --user 0 ${packageName.shellQuote()}"
        ).assertSuccess("Failed to uninstall app")
    }

    suspend fun uninstall(packageName: String) {
        val remoteFS = awaitRemoteFS()
        if (isAppMounted(packageName))
            unmount(packageName)

        val moduleDir = remoteFS.getFile("$MODULES_PATH/${moduleId(packageName)}")

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
        val moduleApk = "$MODULES_PATH/${moduleId(packageName)}/$packageName.apk"
        if (remoteFS.getFile(moduleApk).exists()) return moduleApk

        throw Exception("Patched APK not found for mount")
    }

    /**
     * Copies [sourcePath] to [targetPath] through a staging file in the same directory and
     * moves it over the target once every byte is there. The module's APK is bind mounted
     * over the app at boot, so a copy cut short by a full disk or a power loss must never
     * be left under the name the mount script looks for.
     *
     * The root shell copies it rather than the file service, which keeps writing after its
     * stream is closed and so cannot tell when the staging file is complete.
     */
    private suspend fun copyIntoPlace(sourcePath: String, targetPath: String, missingMessage: String) {
        val source = sourcePath.shellQuote()
        val staging = "$targetPath.tmp".shellQuote()

        if (!execute("test -f $source").isSuccess) throw Exception(missingMessage)

        execute(
            "cp $source $staging && mv -f $staging ${targetPath.shellQuote()} || { rm -f $staging; false; }"
        ).assertSuccess("Failed to copy the APK into place at $targetPath")
    }

    private suspend fun installStockApp(stockApp: File, packageName: String): Shell.Result {
        val tempPath = "/data/local/tmp/morphe-stock-$packageName.apk"
        val tempPathQuoted = tempPath.shellQuote()

        // The subshell keeps exit from closing the shared root shell, which would leave the
        // commands queued after this one reading nothing back
        return execute(
            $$"""
                (
                    rm -f $$tempPathQuoted;
                    cp $${stockApp.absolutePath.shellQuote()} $$tempPathQuoted &&
                    chmod 644 $$tempPathQuoted &&
                    pm install -r -d $$tempPathQuoted;
                    result=$?;
                    rm -f $$tempPathQuoted;
                    exit $result
                )
            """.trimIndent()
        )
    }

    private suspend fun waitForInstalledStock(packageName: String, stockInfo: PackageInfo): Boolean =
        withTimeoutOrNull(STOCK_INSTALL_SETTLE_TIMEOUT) {
            while (true) {
                val installedInfo = pm.getPackageInfo(packageName)
                if (installedInfo != null &&
                    pm.getVersionCode(installedInfo) == pm.getVersionCode(stockInfo) &&
                    installedInfo.versionName == stockInfo.versionName
                ) {
                    return@withTimeoutOrNull true
                }
                delay(STOCK_INSTALL_SETTLE_POLL)
            }
        } == true

    private suspend fun collectStockMountPaths(
        packageName: String,
        installedStockPath: String?
    ): List<String> {
        val hiddenSystemPath = execute("dumpsys package ${packageName.shellQuote()} 2>/dev/null")
            .out
            .hiddenSystemPackagePath(packageName)

        return listOfNotNull(
            installedStockPath?.takeIf { it.isNotBlank() },
            hiddenSystemPath
        ).distinct()
    }

    private suspend fun setModuleFilePermissions(
        modulePath: String,
        patchedApkPath: String,
        stockApkPath: String?
    ) {
        var lastResult: Shell.Result? = null
        val applied = withTimeoutOrNull(MODULE_PERMISSION_SETTLE_TIMEOUT) {
            while (true) {
                val modulePathQuoted = modulePath.shellQuote()
                val patchedApkPathQuoted = patchedApkPath.shellQuote()
                val stockApkPathQuoted = stockApkPath?.shellQuote()
                val commands = buildList {
                    add("test -f $patchedApkPathQuoted && test -f $modulePathQuoted/service.sh && test -f $modulePathQuoted/post-fs-data.sh")
                    add("chmod 644 $patchedApkPathQuoted")
                    add("chown system:system $patchedApkPathQuoted")
                    add("chcon u:object_r:apk_data_file:s0 $patchedApkPathQuoted")
                    stockApkPathQuoted?.let { path ->
                        add("test -f $path")
                        add("chmod 644 $path")
                        add("chown system:system $path")
                        add("chcon u:object_r:apk_data_file:s0 $path")
                    }
                    add("chmod +x $modulePathQuoted/service.sh")
                    add("chmod +x $modulePathQuoted/post-fs-data.sh")
                }
                val result = execute(*commands.toTypedArray())
                if (result.isSuccess) return@withTimeoutOrNull true

                lastResult = result
                delay(MODULE_PERMISSION_RETRY)
            }
        } == true

        if (!applied) {
            lastResult?.assertSuccess("Failed to set file permissions")
                ?: throw Exception("Failed to set file permissions")
        }
    }

    // The toybox nsenter reads every option up to the command as its own and rejects
    // mount's -o, so the command is set apart with --
    private fun mountInZygoteNamespacesCommand(sourcePath: String, targetPath: String) =
        $$"""
            for zpid in $(pidof zygote64) $(pidof zygote); do
                nsenter -t "$zpid" -m -- mount -o bind $$sourcePath $$targetPath 2>/dev/null || true;
            done
        """.trimIndent()

    // A namespace can hold the patched APK twice, once propagated from the root namespace and
    // once mounted into it directly, and a single umount only lifts the top one. Morphe's own
    // namespace was copied from zygote's, so it is cleared too or the APK it reads back for
    // patching stays the patched one
    private fun unmountBindCommands(targetPath: String) =
        $$"""
            for pid in $(pidof zygote64) $(pidof zygote) $${Process.myPid()}; do
                while grep -qF " "$$targetPath" " "/proc/$pid/mountinfo" &&
                    nsenter -t "$pid" -m -- umount -l $$targetPath 2>/dev/null; do :; done;
            done;
            while grep -qF " "$$targetPath" " /proc/self/mountinfo &&
                umount -l $$targetPath 2>/dev/null; do :; done;
            true
        """.trimIndent()

    companion object {
        const val MODULES_PATH = "/data/adb/modules"

        /**
         * The module an app is mounted by, which is both the directory holding it and the id it
         * declares. Root implementations look a module up by its id and expect to find it at
         * that path, so the two are the same string or the module cannot be managed at all.
         */
        fun moduleId(packageName: String) = "$packageName-morphe"

        private fun Shell.Result.assertSuccess(errorMessage: String) {
            if (!isSuccess) {
                val detail = failureDetail()
                throw Exception(if (detail.isBlank()) errorMessage else "$errorMessage: $detail")
            }
        }

        private fun Shell.Result.failureDetail() = (err + out).joinToString("\n").trim()

        // Two or more segments, each starting with a letter, as Android requires of a package name
        private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

        internal fun isValidPackageName(packageName: String) = PACKAGE_NAME.matches(packageName)

        private fun String.shellQuote() = "'${replace("'", "'\"'\"'")}'"

        private const val ROOT_CHECK_INTERVAL_MS = 1_000L
        private val STOCK_INSTALL_SETTLE_TIMEOUT = Duration.ofSeconds(30L)
        private val STOCK_INSTALL_SETTLE_POLL = 1_000.milliseconds
        private val MODULE_PERMISSION_SETTLE_TIMEOUT = Duration.ofSeconds(10L)
        private val MODULE_PERMISSION_RETRY = 500.milliseconds
    }
}

class RootServiceException : Exception("Root not available")

class StockAppInstallException(detail: String) : Exception(
    if (detail.isBlank()) "Failed to install stock app" else "Failed to install stock app: $detail"
)

private fun Shell.Result.hasRootUid() = isSuccess && out.any { line ->
    line.contains("uid=0")
}

private fun List<String>.hiddenSystemPackagePath(packageName: String): String? {
    var inHiddenSection = false
    var inPackage = false

    for (rawLine in this) {
        val line = rawLine.trim()
        if (line.contains("Hidden system package")) {
            inHiddenSection = true
            inPackage = false
            continue
        }
        if (!inHiddenSection) continue

        if (line.startsWith("Package [")) {
            inPackage = line.startsWith("Package [$packageName]")
            continue
        }
        if (!inPackage) continue

        if (line.startsWith("resourcePath=") || line.startsWith("codePath=")) {
            return line.substringAfter('=').trim().takeIf { it.isNotBlank() }
        }
    }

    return null
}
