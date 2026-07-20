package app.morphe.manager.domain.installer

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.os.IBinder
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

            execute(unmountBindCommands(stockPath)).assertSuccess("Failed to unmount APK")

            // Force-stop the app so it restarts clean without the unmounted patched APK.
            execute("am force-stop ${packageName.shellQuote()}")
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
            remoteFS.getFile(stockSourcePath)
                .also { if (!it.exists()) throw Exception("Stock APK doesn't exist") }
                .newInputStream().use { inputStream ->
                    remoteFS.getFile(stockModuleApk).newOutputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

            remoteFS.getFile("$modulePath/stock-paths.txt").newOutputStream().use { outputStream ->
                outputStream.write(stockMountPaths.joinToString("\n", postfix = "\n").toByteArray())
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

            setModuleFilePermissions(
                modulePath = modulePath,
                patchedApkPath = apkPath,
                stockApkPath = stockModuleApk.takeIf { stockModuleApkWritten }
            )
        }

        // Force-stop the app so it restarts with the newly mounted patched APK.
        execute("am force-stop \"$packageName\"")
    }

    suspend fun installAsPlayStore(apkFile: File) = withContext(Dispatchers.IO) {
        if (!apkFile.exists()) throw Exception("File doesn't exist")

        execute(
            "pm install -t -i ${PLAY_STORE_INSTALLER_PACKAGE.shellQuote()} -r ${apkFile.absolutePath.shellQuote()}"
        ).assertSuccess("Failed to install APK as Play Store")
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

    private suspend fun installStockApp(stockApp: File, packageName: String): Shell.Result {
        val tempPath = "/data/local/tmp/morphe-stock-$packageName.apk"
        val tempPathQuoted = tempPath.shellQuote()

        return execute(
            "rm -f $tempPathQuoted; " +
                    "cp ${stockApp.absolutePath.shellQuote()} $tempPathQuoted && " +
                    "chmod 644 $tempPathQuoted && " +
                    "pm install -r -d $tempPathQuoted; " +
                    "result=\$?; " +
                    "rm -f $tempPathQuoted; " +
                    "exit \$result"
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
                delay(STOCK_INSTALL_SETTLE_POLL_MS)
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
                delay(MODULE_PERMISSION_RETRY_MS)
            }
        } == true

        if (!applied) {
            lastResult?.assertSuccess("Failed to set file permissions")
                ?: throw Exception("Failed to set file permissions")
        }
    }

    private fun mountInZygoteNamespacesCommand(sourcePath: String, targetPath: String) =
        "for zpid in \$(pidof zygote64) \$(pidof zygote); do " +
                "nsenter -t \"\$zpid\" -m mount -o bind $sourcePath $targetPath 2>/dev/null || true; " +
                "done"

    private fun unmountBindCommands(targetPath: String) =
        "for zpid in \$(pidof zygote64) \$(pidof zygote); do " +
                "nsenter -t \"\$zpid\" -m umount -l $targetPath 2>/dev/null || true; " +
                "done; " +
                "umount -l $targetPath 2>/dev/null || true"

    companion object {
        const val MODULES_PATH = "/data/adb/modules"

        private fun Shell.Result.assertSuccess(errorMessage: String) {
            if (!isSuccess) {
                val detail = failureDetail()
                throw Exception(if (detail.isBlank()) errorMessage else "$errorMessage: $detail")
            }
        }

        private fun Shell.Result.failureDetail() = (err + out).joinToString("\n").trim()

        private fun String.shellQuote() = "'${replace("'", "'\"'\"'")}'"

        private const val ROOT_CHECK_INTERVAL_MS = 1_000L
        private val STOCK_INSTALL_SETTLE_TIMEOUT = Duration.ofSeconds(30L)
        private const val STOCK_INSTALL_SETTLE_POLL_MS = 1_000L
        private val MODULE_PERMISSION_SETTLE_TIMEOUT = Duration.ofSeconds(10L)
        private const val MODULE_PERMISSION_RETRY_MS = 500L
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
