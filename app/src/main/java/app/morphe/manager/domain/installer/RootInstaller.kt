/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.installer

import android.app.Application
import android.content.pm.PackageInfo
import android.os.Process
import android.os.SystemClock
import android.util.Log
import app.morphe.manager.util.PLAY_STORE_INSTALLER_PACKAGE
import app.morphe.manager.util.PM
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.time.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "Morphe RootInstaller"

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

    private val _mountChanges = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Package names whose bind mount was put in place or lifted, which no package broadcast reports. */
    val mountChanges = _mountChanges.asSharedFlow()

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

            // Set SELinux context, bind-mount in the root and zygote namespaces, replace any other
            // APK running processes still hold, and restart the app so its next process inherits
            // the patched APK view.
            execute(
                "chcon u:object_r:apk_data_file:s0 $patchedPath; " +
                        unmountBindCommands(stockPath) + "; " +
                        "mount -o bind $patchedPath $stockPath; " +
                        mountInZygoteNamespacesCommand(patchedPath, stockPath) + "; " +
                        replaceOtherMountsCommand(patchedAPK, stockPath) + "; " +
                        "am force-stop ${packageName.shellQuote()}"
            ).assertSuccess("Failed to mount APK")
            _mountChanges.emit(packageName)
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
            _mountChanges.emit(packageName)
        }
    }

    /**
     * Writes the module that mounts [patchedAPK] over [packageName] at every boot.
     *
     * The module is staged beside the files it replaces and only switched in after [onStaged].
     * A process killed during the slow copies leaves the previous module in place, still
     * matching what was recorded about it.
     */
    suspend fun install(
        patchedAPK: File,
        stockAPK: File?,
        packageName: String,
        version: String,
        label: String,
        onStage: (MountStage) -> Unit = {},
        onStaged: suspend () -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        require(isValidPackageName(packageName)) { "Invalid package name: $packageName" }

        // Use new path for new installations
        val moduleId = moduleId(packageName)
        val modulePath = "$MODULES_PATH/$moduleId"

        unmount(packageName)
        removeOtherRootInstalls(packageName)

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
        val textFiles = (moduleFiles + stockPathsFile).mapKeys { (name, _) -> "$modulePath/$name" }
        stageModuleFiles(modulePath, textFiles)

        val patchedModuleApk = "$modulePath/$packageName.apk"
        if (stockModuleApkWritten) {
            stageCopy(stockSourcePath, stockModuleApk, "Stock APK doesn't exist")
        }
        stageCopy(patchedAPK.absolutePath, patchedModuleApk, "File doesn't exist")

        val apkPaths = listOfNotNull(patchedModuleApk, stockModuleApk.takeIf { stockModuleApkWritten })
        setStagedFilePermissions(
            apkPaths = apkPaths,
            scriptPaths = MODULE_SCRIPTS.map { "$modulePath/$it" }
        )

        onStaged()
        commitStaged(textFiles.keys + apkPaths)
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
     * Copies [sourcePath] to the staging file of [targetPath] in the same directory. The module's
     * APK is bind mounted over the app at boot, so a copy cut short by a full disk or a power loss
     * must never be left under the name the mount script looks for.
     */
    private suspend fun stageCopy(sourcePath: String, targetPath: String, missingMessage: String) {
        val source = sourcePath.shellQuote()
        val staging = staged(targetPath).shellQuote()

        if (!execute("test -f $source").isSuccess) throw Exception(missingMessage)

        execute("cp $source $staging || { rm -f $staging; false; }")
            .assertSuccess("Failed to copy the APK to $targetPath")
    }

    /** Moves the staged files of [targetPaths] over them in a single call, so the switch is near instant. */
    private suspend fun commitStaged(targetPaths: Collection<String>) {
        execute(targetPaths.joinToString(" && ") { "mv -f ${staged(it).shellQuote()} ${it.shellQuote()}" })
            .assertSuccess("Failed to put the module in place")
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

    /** Stages each of [files], keyed by the path it ends up at, in [modulePath], which is created first. */
    private suspend fun stageModuleFiles(modulePath: String, files: Map<String, String>) {
        val commands = listOf("mkdir -p ${modulePath.shellQuote()}") + files.map { (path, content) ->
            "printf '%s' ${content.shellQuote()} > ${staged(path).shellQuote()}"
        }
        execute(commands.joinToString(" && ")).assertSuccess("Failed to write the module files")
    }

    // A move keeps the owner, mode and SELinux context, so the staged files get them up front
    private suspend fun setStagedFilePermissions(apkPaths: List<String>, scriptPaths: List<String>) {
        val commands = apkPaths.map { staged(it).shellQuote() }.flatMap { path ->
            listOf(
                "chmod 644 $path",
                "chown system:system $path",
                "chcon u:object_r:apk_data_file:s0 $path"
            )
        } + scriptPaths.map { "chmod +x ${staged(it).shellQuote()}" }
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

    // Processes started before the mount, such as System UI, keep what their namespace held, and an
    // APK another root install left there makes them look up the app's resources in another version.
    // A previous patched APK counts too, it shows up marked deleted once a new one replaced it.
    // Morphe's own namespace is skipped so the APK it reads back for patching stays the stock one.
    // Hundreds of processes share a few namespaces, and forking for each of them takes seconds.
    // The candidates are found in single passes instead, and one per namespace is entered
    private fun replaceOtherMountsCommand(patchedAPK: String, targetPath: String): String {
        val sourcePath = patchedAPK.shellQuote()
        // Mount tables name a source by its path within the data partition
        val mountSource = patchedAPK.removePrefix("/data")

        return $$"""
            own_ns=$(stat -Lc %i /proc/$${Process.myPid()}/ns/mnt);
            for pid in $(grep -F " "$$targetPath" " /proc/[0-9]*/mountinfo 2>/dev/null |
                grep -vF " "$${mountSource.shellQuote()}" "$$targetPath" " |
                sed 's|/mountinfo:.*|/ns/mnt|' | sort -u | xargs stat -Lc '%i %n' 2>/dev/null |
                grep -v "^$own_ns " | sort -u -k1,1 | cut -d/ -f3); do
                while grep -qF " "$$targetPath" " "/proc/$pid/mountinfo" &&
                    nsenter -t "$pid" -m -- umount -l $$targetPath 2>/dev/null; do :; done;
                nsenter -t "$pid" -m -- mount -o bind $$sourcePath $$targetPath 2>/dev/null || true;
            done
        """.trimIndent()
    }

    /**
     * Removes what other managers' root installs of the app leave behind. They mount their own APK
     * over the same stock path at boot, racing the module, so only one of them can be kept.
     */
    private suspend fun removeOtherRootInstalls(packageName: String) {
        val paths = listOf(
            // ReVanced Manager before its rewrite, with a boot script outside the modules
            "/data/adb/service.d/$packageName.sh",
            "/data/adb/revanced/$packageName",
            // ReVanced Manager
            "$MODULES_PATH/$packageName-ReVanced",
            // Universal ReVanced Manager, and Morphe before its modules were renamed
            "$MODULES_PATH/$packageName-revanced"
        )
        val removed = execute(
            paths.joinToString("; ") { path ->
                "if [ -e ${path.shellQuote()} ]; then rm -rf ${path.shellQuote()} && echo ${path.shellQuote()}; fi"
            } + "; true"
        ).out
        removed.forEach { Log.i(TAG, "Removed another root install: $it") }
    }

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

        private fun staged(path: String) = "$path.tmp"

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
