/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.installer

import android.app.Application
import android.content.pm.PackageInfo
import android.os.Process
import android.os.SystemClock
import app.morphe.manager.util.PLAY_STORE_INSTALLER_PACKAGE
import app.morphe.manager.util.PM
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.time.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class RootInstaller(
    private val app: Application,
    private val pm: PM
) {
    @Volatile
    private var cachedHasRoot: Boolean? = null
    @Volatile
    private var lastRootCheck = 0L
    @Volatile
    private var cachedIsMagisk: Boolean? = null

    private suspend fun getShell() = with(CompletableDeferred<Shell>()) {
        Shell.getShell(::complete)

        await()
    }

    // A job keeps no output unless it is given lists to fill, and callers read both streams.
    // It blocks until the shell answers, so it never runs on the caller's thread
    suspend fun execute(vararg commands: String): Shell.Result = withContext(Dispatchers.IO) {
        getShell().newJob().add(*commands).to(ArrayList(), ArrayList()).exec()
    }

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

    /**
     * Whether root comes from Magisk, which never hides module mounts from apps as KernelSU and
     * APatch can. Only asked once root is granted, so it never raises the root prompt itself.
     */
    suspend fun isMagisk(): Boolean {
        cachedIsMagisk?.let { return it }
        return Shell.isAppGrantedRoot() == true &&
                execute("magisk -V").isSuccess.also { cachedIsMagisk = it }
    }

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
        label: String,
        onStage: (MountStage) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        require(isValidPackageName(packageName)) { "Invalid package name: $packageName" }

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

            val installedInfo = installedStockInfo
            val stockAlreadyInstalled = installedInfo != null &&
                    pm.getVersionCode(installedInfo) == pm.getVersionCode(stockInfo) &&
                    installedInfo.versionName == stockInfo.versionName

            if (!stockAlreadyInstalled) {
                onStage(MountStage.RESTORING_STOCK)
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

        onStage(MountStage.COPYING)
        val installedStockPath = installedStockInfo?.applicationInfo?.sourceDir
        val stockMountPaths = collectStockMountPaths(packageName, installedStockPath)
        val stockModuleApk = "$modulePath/$packageName-stock.apk"
        val stockSourcePath = stockSourceFile?.absolutePath ?: installedStockPath
        val stockModuleApkWritten = !stockSourcePath.isNullOrBlank() && stockMountPaths.isNotEmpty()

        val placeholders = mapOf(
            "__PKG_NAME__" to packageName,
            "__MANAGER_PKG__" to app.packageName,
            "__MODULE_ID__" to moduleId,
            "__VERSION__" to version,
            "__LABEL__" to label
        )
        val moduleFiles = MODULE_FILES.associateWith { file ->
            app.assets.open("root/$file").use { String(it.readBytes()) }
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .let { text -> placeholders.entries.fold(text) { acc, (key, value) -> acc.replace(key, value) } }
        }
        val stockPathsFile = if (stockModuleApkWritten) {
            mapOf(STOCK_PATHS_FILE to stockMountPaths.joinToString("\n", postfix = "\n"))
        } else {
            emptyMap()
        }
        writeModuleFiles(modulePath, moduleFiles + stockPathsFile)

        if (stockModuleApkWritten) {
            copyIntoPlace(stockSourcePath, stockModuleApk, "Stock APK doesn't exist")
        }
        val patchedModuleApk = "$modulePath/$packageName.apk"
        copyIntoPlace(patchedAPK.absolutePath, patchedModuleApk, "File doesn't exist")

        setModuleFilePermissions(
            modulePath = modulePath,
            apkPaths = listOfNotNull(patchedModuleApk, stockModuleApk.takeIf { stockModuleApkWritten })
        )
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
        if (isAppMounted(packageName))
            unmount(packageName)

        execute("rm -rf ${"$MODULES_PATH/${moduleId(packageName)}".shellQuote()}")
            .assertSuccess("Failed to delete files")
    }

    /**
     * Resolve the path of the patched APK stored in the Morphe module directory.
     */
    private suspend fun resolvePatchedApkPath(packageName: String): String {
        val moduleApk = "$MODULES_PATH/${moduleId(packageName)}/$packageName.apk"
        if (execute("test -f ${moduleApk.shellQuote()}").isSuccess) return moduleApk

        throw Exception("Patched APK not found for mount")
    }

    /**
     * Copies [sourcePath] to [targetPath] through a staging file in the same directory and
     * moves it over the target once every byte is there. The module's APK is bind mounted
     * over the app at boot, so a copy cut short by a full disk or a power loss must never
     * be left under the name the mount script looks for.
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

    /** Writes each of [files] under its name into [modulePath], which is created first. */
    private suspend fun writeModuleFiles(modulePath: String, files: Map<String, String>) {
        val commands = listOf("mkdir -p ${modulePath.shellQuote()}") + files.map { (name, content) ->
            "printf '%s' ${content.shellQuote()} > ${"$modulePath/$name".shellQuote()}"
        }
        execute(commands.joinToString(" && ")).assertSuccess("Failed to write the module files")
    }

    private suspend fun setModuleFilePermissions(modulePath: String, apkPaths: List<String>) {
        val commands = apkPaths.map { it.shellQuote() }.flatMap { path ->
            listOf(
                "chmod 644 $path",
                "chown system:system $path",
                "chcon u:object_r:apk_data_file:s0 $path"
            )
        } + MODULE_SCRIPTS.map { "chmod +x ${"$modulePath/$it".shellQuote()}" }
        execute(commands.joinToString(" && ")).assertSuccess("Failed to set file permissions")
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

        /** The scripts the root manager runs, which have to be executable. */
        private val MODULE_SCRIPTS = listOf("service.sh", "post-fs-data.sh")
        private val MODULE_FILES = MODULE_SCRIPTS + "module.prop"
        private const val STOCK_PATHS_FILE = "stock-paths.txt"
    }
}

/** The steps of a mount install that take long enough to be named while they run. */
enum class MountStage {
    RESTORING_STOCK,
    COPYING,
    MOUNTING
}

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
