/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.installer

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.patcher.split.SplitApkInspector
import app.morphe.manager.patcher.split.SplitApkPreparer
import app.morphe.manager.util.PLAY_STORE_INSTALLER_PACKAGE
import app.morphe.manager.util.PM
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.time.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Duration
import java.util.zip.ZipFile
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "Morphe RootInstaller"

class RootInstaller(
    private val app: Application,
    private val pm: PM,
    private val prefs: PreferencesManager
) {
    @Volatile
    private var cachedHasRoot: Boolean? = null
    @Volatile
    private var lastRootCheck = 0L
    @Volatile
    private var cachedIsMagisk: Boolean? = null
    @Volatile
    private var cachedNoMountBin: String? = null
    @Volatile
    private var cachedNoMountReason: String? = null
    @Volatile
    private var lastNoMountCheck = 0L

    private val _mountChanges = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val moduleWriteMutex = Mutex()

    /** Package names whose mount or redirect was put in place or lifted, which no package broadcast reports. */
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
        knownRootAccess()?.let { return it }

        synchronized(this) {
            // Another caller may have probed while this one waited for the lock
            knownRootAccess()?.let { return it }

            val probeResult = runCatching { Shell.cmd("id").exec() }.getOrNull()
            lastRootCheck = SystemClock.elapsedRealtime()

            val granted = Shell.isAppGrantedRoot() == true || probeResult?.hasRootUid() == true
            cachedHasRoot = granted

            return granted
        }
    }

    /**
     * Root access as known without probing: what libsu learned, else a cached answer that is
     * still fresh. Null when only a probe can tell.
     */
    private fun knownRootAccess(): Boolean? {
        Shell.isAppGrantedRoot()?.let { granted ->
            if (granted) cachedHasRoot = true
            return granted
        }
        return when (cachedHasRoot) {
            true -> true
            false if SystemClock.elapsedRealtime() - lastRootCheck < ROOT_CHECK_INTERVAL_MS -> false
            else -> null
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

    /**
     * Whether NoMount can redirect the patched APK in the kernel instead of mounting it, which no
     * mount table shows and no root manager unmounts from an app.
     */
    suspend fun isNoMountAvailable() = noMountBin() != null

    /**
     * The NoMount CLI, when its driver is loaded and speaks the protocol of the UID scoped rules.
     * Rechecked now and then, as the module can be installed or removed while Morphe runs.
     */
    private suspend fun noMountBin(): String? {
        if (Shell.isAppGrantedRoot() != true) return null
        if (isMagisk()) {
            cachedNoMountReason = "NoMount does not run on Magisk"
            return null
        }
        val now = SystemClock.elapsedRealtime()
        if (lastNoMountCheck != 0L && now - lastNoMountCheck < NO_MOUNT_CHECK_INTERVAL_MS) return cachedNoMountBin

        // The CLI prints the driver's protocol and build count, such as "22 (605)", and fails
        // without the driver
        val probe = execute(
            $$"""
                for nm in $${NO_MOUNT_BINS.joinToString(" ")}; do
                    if [ -x "$nm" ] && version=$("$nm" version 2>/dev/null); then echo "$nm $version"; break; fi;
                done
            """.trimIndent()
        ).out.firstOrNull()?.trim().orEmpty()
        val bin = probe.substringBefore(' ').takeIf { it.isNotEmpty() }
        val protocol = probe.substringAfter(' ', "").substringBefore(' ').toIntOrNull() ?: 0

        cachedNoMountBin = bin?.takeIf { protocol >= NO_MOUNT_MIN_PROTOCOL }
        // A driver too old for the UID scoped rules is told apart, as it looks installed and working
        cachedNoMountReason = if (bin != null && cachedNoMountBin == null) {
            "NoMount driver protocol $protocol is outdated ($NO_MOUNT_MIN_VERSION or newer required)"
                .also { Log.w(TAG, "$it, found at $bin") }
        } else null
        lastNoMountCheck = now
        return cachedNoMountBin
    }

    suspend fun isAppMounted(packageName: String) = withContext(Dispatchers.IO) {
        val stockPath = pm.getPackageInfo(packageName)?.applicationInfo?.sourceDir?.shellQuote()
            ?: return@withContext false

        val redirected = noMountBin()?.let { nm ->
            execute("${nm.shellQuote()} rule list 2>/dev/null | grep -F $stockPath").isSuccess
        } == true
        if (redirected || execute("mount | grep -F $stockPath").isSuccess) return@withContext true

        // A redirect Morphe did not set up, such as a VFS rule of another provider, shows in no
        // table, but root reads the patched APK through the stock path. A stock APK of the very
        // same size is not a case worth telling apart
        val patchedPath = "$MODULES_PATH/${moduleId(packageName)}/$packageName.apk".shellQuote()
        execute(
            "[ -f $patchedPath ] && [ \"$(stat -c %s $stockPath)\" = \"$(stat -c %s $patchedPath)\" ]"
        ).isSuccess
    }

    suspend fun mount(packageName: String) {
        withContext(Dispatchers.IO) {
            verifyModuleStockCopies(packageName)
            val stockAPK = pm.getPackageInfo(packageName)?.applicationInfo?.sourceDir
                ?: throw Exception("Failed to load application info")
            val patchedAPK = resolvePatchedApkPath(packageName)
            val stockPath = stockAPK.shellQuote()
            val patchedPath = patchedAPK.shellQuote()
            val forceStop = "am force-stop ${packageName.shellQuote()}"

            // The redirect leaves system_server on the stock APK, so a framework restart scans that
            // one and needs no watcher. Whatever a bind mount left behind is lifted first, and only
            // the rule decides whether the redirect took
            val noMountOnly = prefs.noMountOnly.get()
            val marker = "$MODULES_PATH/${moduleId(packageName)}/$NO_MOUNT_MARKER".shellQuote()
            execute("printf %s ${noMountMarker(noMountOnly)} > $marker")
                .assertSuccess("Failed to save the NoMount only setting")
            val nm = noMountBin()?.shellQuote()
            val redirected = nm?.let {
                execute(
                    "chcon u:object_r:apk_data_file:s0 $patchedPath; " +
                            unmountBindCommands(stockPath) + "; " +
                            frameworkWatcherCommand(packageName, "stop") + "; " +
                            noMountRemoveCommand(nm, stockPath) + "; " +
                            "$nm rule add $NO_MOUNT_SCOPE $stockPath $patchedPath"
                ).also { result ->
                    if (!result.isSuccess) Log.w(TAG, "NoMount redirect failed: ${result.failureDetail()}")
                }.isSuccess
            } == true

            if (redirected) {
                execute(
                    moduleStatusCommand(packageName, "Redirected the patched APK in Morphe with NoMount") + "; " +
                            forceStop + "; " +
                            detachCommand(packageName)
                )
            } else if (noMountOnly) {
                // Whatever a bind mount left behind is lifted, so the app is left on the stock APK
                val reason = if (nm == null) (cachedNoMountReason ?: "NoMount is unavailable") else "NoMount redirect failed"
                execute(
                    unmountBindCommands(stockPath) + "; " +
                            frameworkWatcherCommand(packageName, "stop") + "; " +
                            moduleStatusCommand(packageName, "Not mounted in Morphe: $reason and bind mounts are off") + "; " +
                            forceStop
                )
                error("$reason and bind mounts are off")
            } else {
                // Set SELinux context and bind-mount in the root and zygote namespaces
                val mounted = execute(
                    "chcon u:object_r:apk_data_file:s0 $patchedPath; " +
                            unmountBindCommands(stockPath) + "; " +
                            "mount -o bind $patchedPath $stockPath; " +
                            mountInZygoteNamespacesCommand(patchedPath, stockPath)
                )
                // The commands above report only the last one, so the mount is judged by what the
                // stock path holds afterward
                if (!execute(isMountedOverCommand(patchedPath, stockPath)).isSuccess) {
                    throw mounted.failure("Failed to mount APK")
                }
                // Replace any other APK running processes still hold, and restart the app so its
                // next process inherits the patched APK view
                execute(
                    replaceOtherMountsCommand(patchedAPK, stockPath) + "; " +
                            frameworkWatcherCommand(packageName, "watch") + "; " +
                            moduleStatusCommand(packageName, "Mounted the patched APK in Morphe") + "; " +
                            forceStop + "; " +
                            detachCommand(packageName)
                )
            }

            _mountChanges.emit(packageName)
        }
    }

    suspend fun unmount(packageName: String) {
        withContext(Dispatchers.IO) {
            val stockAPK = pm.getPackageInfo(packageName)?.applicationInfo?.sourceDir
                ?: return@withContext
            val stockPath = stockAPK.shellQuote()
            val removeRedirect = noMountBin()?.let { nm -> noMountRemoveCommand(nm.shellQuote(), stockPath) + "; " }.orEmpty()

            // Force-stop the app so it restarts clean without the unmounted patched APK.
            execute(
                removeRedirect +
                        unmountBindCommands(stockPath) + "; " +
                        frameworkWatcherCommand(packageName, "stop") + "; " +
                        moduleStatusCommand(packageName, "Unmounted in Morphe until the next boot") + "; " +
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
     *
     * [stockAPK], an APK or a split archive, repairs missing, mismatched or unverifiable stock
     * while preserving app data. Boot backups are created only from fully verified snapshots of
     * the installed original APKs as seen by the root shell.
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
        moduleWriteMutex.withLock {
            installVerifiedModule(patchedAPK, stockAPK, packageName, version, label, onStage, onStaged)
        }
    }

    private suspend fun installVerifiedModule(
        patchedAPK: File,
        stockAPK: File?,
        packageName: String,
        version: String,
        label: String,
        onStage: (MountStage) -> Unit,
        onStaged: suspend () -> Unit
    ) {
        require(isValidPackageName(packageName)) { "Invalid package name: $packageName" }

        // Use new path for new installations
        val moduleId = moduleId(packageName)
        val modulePath = "$MODULES_PATH/$moduleId"

        unmount(packageName)
        removeOtherRootInstalls(packageName)

        val originalStockInfo = stockAPK?.let { stockApp ->
            val stockInfo = readStockInfo(stockApp) ?: throw StockApkIntegrityException()
            if (stockInfo.packageName != packageName || stockInfo.versionName != version) {
                error("The original APK does not match $packageName v$version")
            }
            stockInfo
        }
        val originalSigners = stockAPK?.let { stockApp ->
            SplitApkInspector.withRepresentativeApk(stockApp, app.cacheDir) { apk ->
                verifiedStockApkSigners(apk, Build.VERSION.SDK_INT)
            }.takeIf { it.isNotEmpty() } ?: throw StockApkIntegrityException()
        }
        val originalRelease = originalStockInfo?.let { info ->
            StockApkIdentity(info.packageName, pm.getVersionCode(info), info.versionName, null)
        }
        val snapshots = Files.createTempDirectory(app.cacheDir.toPath(), "root-stock-").toFile()
        var stagedTargets = emptySet<String>()
        var committed = false
        try {
            onStage(MountStage.COPYING)
            var installedStockInfo: PackageInfo? = null
            val stockCopies = ensureVerifiedInstalledStock(
                canRestore = stockAPK != null,
                capture = {
                    installedStockInfo = pm.getPackageInfo(packageName)
                    val info = installedStockInfo
                    val installedRelease = info?.let {
                        StockApkIdentity(it.packageName, pm.getVersionCode(it), it.versionName, null)
                    }
                    if (!canCaptureInstalledStockRelease(installedRelease, originalRelease)) null
                    else captureVerifiedStockApks(packageName, version, info, originalSigners, modulePath, snapshots)
                },
                restore = {
                    // A stale mounted or merged view is not promoted into a boot backup merely
                    // because PackageManager's recorded version still matches the patched APK.
                    onStage(MountStage.RESTORING_STOCK)
                    installStockApp(checkNotNull(stockAPK), packageName) { it.isBuildOf(checkNotNull(originalStockInfo)) }
                    onStage(MountStage.COPYING)
                }
            )
            val installedStockPath = checkNotNull(installedStockInfo?.applicationInfo?.sourceDir)
            val stockMountPaths = collectStockMountPaths(packageName, installedStockPath)
            if (stockMountPaths.isEmpty()) throw StockApkIntegrityException()
            val stockHashes = stockCopies.mapValues { (_, copy) -> copy.sha256 }
            val stockSplitPaths = stockCopies.keys.drop(1).toSet()

            val placeholders = mapOf(
                "__PKG_NAME__" to packageName,
                "__MANAGER_PKG__" to app.packageName,
                "__MODULE_ID__" to moduleId,
                "__VERSION__" to version,
                "__LABEL__" to label
            )
            val moduleFiles = MODULE_FILES.associateWith { file ->
                // Version and label come from the APK, and the scripts run as root at every boot
                val escape: (String) -> String =
                    if (file in MODULE_SCRIPTS) { value -> value.shellQuote() } else { value -> value.singleLine() }
                app.assets.open("root/$file").use { String(it.readBytes()) }
                    .replace("\r\n", "\n")
                    .replace("\r", "\n")
                    .let { text -> placeholders.entries.fold(text) { acc, (key, value) -> acc.replace(key, escape(value)) } }
            }
            val stockPathsFile = mapOf(STOCK_PATHS_FILE to stockMountPaths.joinToString("\n", postfix = "\n"))
            val noMountFile = NO_MOUNT_MARKER to noMountMarker(prefs.noMountOnly.get())
            val textFiles = (moduleFiles + stockPathsFile + noMountFile).mapKeys { (name, _) -> "$modulePath/$name" }
            val patchedModuleApk = "$modulePath/$packageName.apk"
            val integrityPath = "$modulePath/$STOCK_INTEGRITY_FILE"
            stagedTargets = (textFiles.keys + stockCopies.keys + patchedModuleApk + integrityPath).toSet()
            stageModuleFiles(modulePath, textFiles)

            stockCopies.forEach { (target, copy) -> stageCopy(copy.file.absolutePath, target, "Verified stock copy is missing") }
            stageCopy(patchedAPK.absolutePath, patchedModuleApk, "File doesn't exist")

            val apkPaths = listOf(patchedModuleApk) + stockCopies.keys
            setStagedFilePermissions(
                apkPaths = apkPaths,
                scriptPaths = MODULE_SCRIPTS.map { "$modulePath/$it" }
            )
            stageModuleFiles(modulePath, mapOf(
                integrityPath to stockApkHashManifest(stockHashes.mapKeys { (path, _) -> File(path).name })
            ))
            val commitFiles = (textFiles.keys + apkPaths + integrityPath).associateWith(::staged)
            // Before persistence, and again with the moves: callbacks can take time and root
            // namespaces may expose a different copy than the one the app just verified.
            verifyStagedStockHashes(stockHashes)
            onStaged()
            val saved = execute(verifiedStockCommitCommand(commitFiles, stockHashes))
            if (!saved.isSuccess) {
                Log.w(TAG, "Stock module commit failed: ${saved.failureDetail()}")
                throw StockApkIntegrityException()
            }
            committed = true

            // Splits of the version staged before would not install beside this one.
            val staleSplits = moduleStockSplits(packageName) - stockSplitPaths
            if (staleSplits.isNotEmpty()) execute("rm -f ${staleSplits.joinToString(" ") { it.shellQuote() }}")
        } finally {
            snapshots.deleteRecursively()
            if (!committed && stagedTargets.isNotEmpty()) {
                // Only this writer's inactive staging files are reclaimed; never the previous
                // module, installed app, retained originals, or app data.
                withContext(NonCancellable) {
                    execute("rm -f ${stagedTargets.joinToString(" ") { staged(it).shellQuote() }}")
                }
            }
        }
    }

    /** The split APKs of the stock app the module of [packageName] keeps beside its stock APK. */
    private suspend fun moduleStockSplits(packageName: String): List<String> {
        val prefix = "$MODULES_PATH/${moduleId(packageName)}/$packageName$STOCK_SPLIT_INFIX"
        return execute($$"""for f in $${prefix.shellQuote()}*.apk; do [ -f "$f" ] && echo "$f"; done""")
            .out.filter { it.isNotBlank() }
    }

    /** The package info only once every original APK verifies as this device checks it. */
    suspend fun readStockInfo(file: File): PackageInfo? = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val verified = isVerifiedStockArchive(file, Build.VERSION.SDK_INT, app.cacheDir)
        currentCoroutineContext().ensureActive()
        if (!verified) return@withContext null
        SplitApkInspector.withRepresentativeApk(file, app.cacheDir) { apk -> pm.getPackageInfo(apk) }
    }

    private data class StockCopy(val file: File, val sha256: String)

    /**
     * Reads what the root shell actually sees after unmount, into app-owned snapshots. A stale
     * patched overlay or a merged original must never be used as the source of a boot backup.
     */
    private suspend fun captureVerifiedStockApks(
        packageName: String,
        version: String,
        info: PackageInfo?,
        originalSigners: Set<String>?,
        modulePath: String,
        directory: File
    ): Map<String, StockCopy>? {
        val stock = info?.takeIf { it.packageName == packageName && it.versionName == version } ?: return null
        val source = stock.applicationInfo?.sourceDir?.takeIf { it.isNotBlank() } ?: return null
        val recorded = pm.getPackageInfo(
            packageName, PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        )?.takeIf { it.isBuildOf(stock) } ?: return null
        val expectedSigners = recorded.currentSignerHashes()
        if (expectedSigners.isEmpty() || originalSigners != null && originalSigners != expectedSigners) return null

        val splitNames = stock.splitNames.orEmpty().asList()
        val splitPaths = stock.applicationInfo?.splitSourceDirs.orEmpty().asList()
        // A same-version update can still have changed its paths or split set between queries.
        if (recorded.applicationInfo?.sourceDir != source ||
            recorded.splitNames.orEmpty().asList() != splitNames ||
            recorded.applicationInfo?.splitSourceDirs.orEmpty().asList() != splitPaths
        ) return null
        val sourceSplits = installedStockSourceSplits(source, splitNames, splitPaths) ?: return null
        val sources = sourceSplits.mapValues { (path, splitName) ->
            if (splitName == null) "$modulePath/$packageName-stock.apk"
            else "$modulePath/$packageName$STOCK_SPLIT_INFIX${File(path).name}"
        }
        val copies = linkedMapOf<String, StockCopy>()
        for ((index, entry) in sources.entries.withIndex()) {
            currentCoroutineContext().ensureActive()
            // Precreating the file in the app preserves its owner and SELinux label while root
            // fills it. Merely reading sourceDir in the app can inspect a different namespace.
            val copy = File.createTempFile("stock-$index-", ".apk", directory)
            val copied = execute("cat ${entry.key.shellQuote()} > ${copy.absolutePath.shellQuote()}")
            if (!copied.isSuccess) return null
            val verified = verifiedStockSnapshot(copy, Build.VERSION.SDK_INT)
            currentCoroutineContext().ensureActive()
            if (verified == null) return null
            if (verified.signers != expectedSigners) return null
            val expectedSplit = sourceSplits.getValue(entry.key)
            // versionName may be a resource reference. Android resolves it using this verified
            // private APK's resources; no certificate metadata from the platform is trusted.
            val identity = if (expectedSplit == null) {
                val metadata = pm.getPackageInfo(copy) ?: return null
                resolvedStockBaseIdentity(
                    verified.identity, metadata.packageName, pm.getVersionCode(metadata), metadata.versionName
                ) ?: return null
            } else verified.identity
            if (!matchesInstalledStockIdentity(
                    identity, packageName, pm.getVersionCode(stock), version, expectedSplit
                )) return null
            copies[entry.value] = StockCopy(copy, verified.sha256)
        }
        return copies
    }

    /** The currently installed signers come from binder, never the APK at its mounted path. */
    @Suppress("DEPRECATION")
    private fun PackageInfo.currentSignerHashes(): Set<String> {
        val current = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            signingInfo?.apkContentsSigners ?: signatures
        } else signatures
        return current.orEmpty().mapTo(mutableSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }
    }

    private suspend fun verifyStagedStockHashes(hashes: Map<String, String>) {
        val checks = hashes.entries.joinToString("\n", postfix = "\n") { (path, hash) -> "$hash  ${staged(path)}" }
        if (!execute("printf '%s' ${checks.shellQuote()} | sha256sum -c").isSuccess) {
            throw StockApkIntegrityException()
        }
    }

    /** Old modules without verified backups must be restaged before a new mount can expose them. */
    private suspend fun verifyModuleStockCopies(packageName: String) {
        val modulePath = "$MODULES_PATH/${moduleId(packageName)}"
        val helper = "$modulePath/stock-integrity.sh".shellQuote()
        val verified = execute(
            "( module_dir=${modulePath.shellQuote()}; " +
                    "package_name=${packageName.shellQuote()}; " +
                    "log=${"$modulePath/log.txt".shellQuote()}; " +
                    "[ -r $helper ] && . $helper && verify_stock_apks )"
        )
        if (!verified.isSuccess) {
            error("The stock backup is not verified. Reinstall the original app and restage its Morphe module.")
        }
    }

    /**
     * Installs [apkFile] with root, recording Google Play Store as its install source when [asPlayStore].
     * Kept to the user Morphe runs in, see [appUserId], as root would otherwise install for every user.
     */
    suspend fun installPackage(apkFile: File, asPlayStore: Boolean) = withContext(Dispatchers.IO) {
        if (!apkFile.exists()) throw Exception("File doesn't exist")

        val source = if (asPlayStore) "-i ${PLAY_STORE_INSTALLER_PACKAGE.shellQuote()} " else ""
        execute(
            withSystemReadableCopies(INSTALL_TEMP_DIR, listOf(apkFile.absolutePath)) { (apk) ->
                "pm install -t --user $appUserId $source-r $apk"
            }
        ).assertSuccess("Failed to install APK")
    }

    /** Uninstalls [packageName] for the user Morphe runs in, the one [installPackage] installs for. */
    suspend fun uninstallPackage(packageName: String) = withContext(Dispatchers.IO) {
        execute(
            "pm uninstall --user $appUserId ${packageName.shellQuote()}"
        ).assertSuccess("Failed to uninstall app")
    }

    suspend fun uninstall(packageName: String) {
        if (isAppMounted(packageName))
            unmount(packageName)

        // A NoMount only module redirects the hidden system copy to its stock APK until the next
        // boot, and the rule would point at nothing once the module is gone
        val modulePath = "$MODULES_PATH/${moduleId(packageName)}"
        val removeStockRedirects = noMountBin()?.let { nm ->
            noMountRemoveRedirectsToCommand(nm.shellQuote(), "$modulePath/$packageName-stock.apk") + "; "
        }.orEmpty()
        execute(removeStockRedirects + reattachCommand(packageName) + "; rm -rf ${modulePath.shellQuote()}")
            .assertSuccess("Failed to delete files")
    }

    /**
     * Starts ("watch") or stops ("stop") the module's watcher for a framework restart, see service.sh,
     * in init's mount namespace where the framework starts. An older module's script has no watcher
     * and would mount the app all over again instead, so it is left alone.
     */
    private fun frameworkWatcherCommand(packageName: String, action: String): String {
        val script = "$MODULES_PATH/${moduleId(packageName)}/service.sh".shellQuote()
        return "if grep -q watch_framework_restarts $script; then " +
                "(nsenter -t 1 -m -- sh $script $action </dev/null >/dev/null 2>&1 &); fi"
    }

    /** Sets the description root managers show for the module, so it does not read as mounted once unmounted. */
    private fun moduleStatusCommand(packageName: String, status: String): String {
        val prop = "$MODULES_PATH/${moduleId(packageName)}/module.prop".shellQuote()
        return "sed -i ${"s|^description=.*|description=$status|".shellQuote()} $prop 2>/dev/null"
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


    /**
     * Installs the stock app from [stockApp], see [installStockApks]. A split archive is installed
     * from its own modules: the merged APK patching builds from one keeps the base module's signing
     * block over contents it no longer describes, so PackageManager rejects it.
     */
    private suspend fun installStockApp(stockApp: File, packageName: String, settled: (PackageInfo) -> Boolean) {
        val extractDir = app.cacheDir.resolve("stock-splits-$packageName")

        try {
            val modules = if (SplitApkPreparer.isSplitArchive(stockApp)) {
                withContext(Dispatchers.IO) {
                    extractDir.deleteRecursively()
                    extractDir.mkdirs()
                    ZipFile(stockApp).use { zip ->
                        SplitApkPreparer.splitModuleEntries(zip).map { entry ->
                            extractDir.resolve(entry.name).also { module ->
                                zip.getInputStream(entry).use { input -> module.outputStream().use { input.copyTo(it) } }
                            }
                        }.toList()
                    }
                }.ifEmpty { error("Split archive does not contain any APK entries") }
            } else {
                listOf(stockApp)
            }
            installStockApks(modules.map { it.absolutePath }, packageName, settled)
        } finally {
            extractDir.deleteRecursively()
        }
    }

    /**
     * Installs [apkPaths], the APKs of the stock app, in one session as a store does, and waits for
     * PackageManager to hold a build [settled] accepts. They are read as root, so they can be files
     * only root reaches, as the copies a module keeps are.
     *
     * @throws StockAppInstallException If no such build settles.
     */
    private suspend fun installStockApks(apkPaths: List<String>, packageName: String, settled: (PackageInfo) -> Boolean) {
        // A module that fails to write abandons the session, so nothing half installed is left
        val result = execute(
            withSystemReadableCopies("/data/local/tmp/morphe-stock-$packageName", apkPaths) { copies ->
                val writes = apkPaths.zip(copies).joinToString(" && ") { (path, copy) ->
                    val name = File(path).name.shellQuote()
                    $$"""pm install-write -S "$(stat -c %s $$copy)" "$session" $$name $$copy"""
                }
                $$"""session="$(pm install-create -r -d --user $$appUserId | sed -n 's/.*\[\([0-9]*\)\].*/\1/p')" && [ -n "$session" ] && { $$writes && pm install-commit "$session" || { pm install-abandon "$session"; false; }; }"""
            }
        )

        val settledInTime = withTimeoutOrNull(STOCK_INSTALL_SETTLE_TIMEOUT) {
            while (pm.getPackageInfo(packageName)?.let(settled) != true) delay(STOCK_INSTALL_SETTLE_POLL)
        } != null
        if (!settledInTime) {
            if (!result.isSuccess) throw StockAppInstallException(result.failureDetail())
            throw StockAppInstallException("Stock app install did not settle")
        }
    }

    /**
     * A command that runs [install] on copies of [apkPaths] in [tempDir] and removes them whatever it
     * returns. PackageManager opens the paths as system_server, which SELinux keeps out of the app's
     * private files under root solutions other than Magisk, while it may read /data/local/tmp.
     *
     * @param install Builds the single-line install command from the quoted paths of the copies.
     */
    private fun withSystemReadableCopies(
        tempDir: String,
        apkPaths: List<String>,
        install: (copies: List<String>) -> String
    ): String {
        val dir = tempDir.shellQuote()
        val copies = apkPaths.map { "$tempDir/${File(it).name}".shellQuote() }

        // The subshell keeps exit from closing the shared root shell, which would leave the
        // commands queued after this one reading nothing back
        return $$"""
            (
                rm -rf $$dir;
                mkdir -p $$dir &&
                cp $${apkPaths.joinToString(" ") { it.shellQuote() }} $$dir/ &&
                chmod 644 $$dir/* &&
                $${install(copies)};
                result=$?;
                rm -rf $$dir;
                exit $result
            )
        """.trimIndent()
    }

    private fun PackageInfo.isBuildOf(other: PackageInfo) =
        pm.getVersionCode(this) == pm.getVersionCode(other) && versionName == other.versionName

    /**
     * Whether the module of [packageName] still holds the patched APK it mounts and the stock APK it
     * was staged from, which is all [restoreFromModule] needs.
     */
    suspend fun hasModuleCopies(packageName: String) = withContext(Dispatchers.IO) {
        val modulePath = "$MODULES_PATH/${moduleId(packageName)}"
        hasRootAccess() && execute(
            "test -f ${"$modulePath/$packageName.apk".shellQuote()} && " +
                    "test -f ${"$modulePath/$packageName-stock.apk".shellQuote()}"
        ).isSuccess
    }

    /**
     * Puts a mount another version replaced, usually a store update, back from the copies its
     * module keeps, which outlast any Morphe saved: the stock APK it was staged from is installed
     * again with its splits and the module staged anew over it, as the reinstalled app has moved.
     * [version] is the one the module mounts. The module is mounted again by [mount].
     */
    suspend fun restoreFromModule(
        packageName: String,
        version: String,
        onStage: (MountStage) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val modulePath = "$MODULES_PATH/${moduleId(packageName)}"
        verifyModuleStockCopies(packageName)

        onStage(MountStage.RESTORING_STOCK)
        installStockApks(
            listOf("$modulePath/$packageName-stock.apk") + moduleStockSplits(packageName),
            packageName
        ) { it.versionName == version }

        val label = pm.getPackageInfo(packageName)?.let { with(pm) { it.label() } } ?: packageName
        install(File("$modulePath/$packageName.apk"), null, packageName, version, label, onStage)
    }

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

    // Succeeds when the root namespace sees the source file at the target path, which a bind mount
    // of it there shows as the same device and inode
    private fun isMountedOverCommand(sourcePath: String, targetPath: String) =
        "[ \"$(stat -Lc %d:%i $targetPath)\" = \"$(stat -Lc %d:%i $sourcePath)\" ]"

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
                $${liftLayersOfPidCommand(targetPath)};
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

    // A rule is only deleted under the UID scope it was added with, so the unscoped one other root
    // installs add for the same path goes too
    private fun noMountRemoveCommand(nm: String, stockPath: String) =
        "$nm rule del $NO_MOUNT_SCOPE $stockPath 2>/dev/null; $nm rule del $stockPath 2>/dev/null; true"

    // Removes the unscoped rules redirecting to the real path, which are listed as "virtual -> real"
    private fun noMountRemoveRedirectsToCommand(nm: String, realPath: String) =
        $$"""
            $$nm rule list 2>/dev/null | grep -F $${" -> $realPath".shellQuote()} | sed 's| -> .*||' |
                while IFS= read -r path; do $$nm rule del "$path"; done; true
        """.trimIndent()

    // A namespace can hold the patched APK twice, once propagated from the root namespace and
    // once mounted into it directly, and a single umount only lifts the top one. Morphe's own
    // namespace was copied from zygote's, so it is cleared too or the APK it reads back for
    // patching stays the patched one
    private fun unmountBindCommands(targetPath: String) =
        $$"""
            for pid in $(pidof zygote64) $(pidof zygote) $${Process.myPid()}; do
                $${liftLayersOfPidCommand(targetPath)};
            done;
            $${liftLayersCommand(targetPath, "/proc/self/mountinfo", "umount -l $targetPath")};
            true
        """.trimIndent()

    /**
     * Runs [umount] while [mountTable] lists the quoted [targetPath], one layer per run. Bounded, as
     * a root that hides mounts can report each umount as done and hold the shell there forever.
     */
    private fun liftLayersCommand(targetPath: String, mountTable: String, umount: String) =
        "for _ in 1 2 3 4 5 6 7 8 9 10; do " +
                "grep -qF \" \"$targetPath\" \" $mountTable && $umount 2>/dev/null || break; done"

    /** [liftLayersCommand] in the namespace of the process the shell holds in `$pid`. */
    private fun liftLayersOfPidCommand(targetPath: String) = liftLayersCommand(
        targetPath,
        mountTable = $$"\"/proc/$pid/mountinfo\"",
        umount = $$"nsenter -t \"$pid\" -m -- umount -l $$targetPath"
    )

    /**
     * Hides the app from Play Store updates, which would replace the stock APK under the mount, when
     * zygisk-detach is installed. The marker records that Morphe detached it, not the user.
     */
    private fun detachCommand(packageName: String): String {
        val marker = "$MODULES_PATH/${moduleId(packageName)}/$DETACHED_MARKER".shellQuote()
        return $$"""
            if [ -x $$DETACH_BIN ] && out=$($$DETACH_BIN detach $${packageName.shellQuote()} 2>/dev/null); then
                case "$out" in *"already detached"*) ;; *) touch $$marker ;; esac;
            fi; true
        """.trimIndent()
    }

    /** Gives the app back to Play Store updates, unless the user detached it before Morphe did. */
    private fun reattachCommand(packageName: String): String {
        val marker = "$MODULES_PATH/${moduleId(packageName)}/$DETACHED_MARKER".shellQuote()
        return "if [ -f $marker ] && [ -x $DETACH_BIN ]; then " +
                "$DETACH_BIN reattach ${packageName.shellQuote()} >/dev/null 2>&1; fi"
    }

    companion object {
        const val MODULES_PATH = "/data/adb/modules"

        /**
         * The module an app is mounted by, which is both the directory holding it and the id it
         * declares. Root implementations look a module up by its id and expect to find it at
         * that path, so the two are the same string or the module cannot be managed at all.
         */
        fun moduleId(packageName: String) = "$packageName-morphe"

        private fun Shell.Result.assertSuccess(errorMessage: String) {
            if (!isSuccess) throw failure(errorMessage)
        }

        /** [errorMessage] with what the shell printed, if anything. */
        private fun Shell.Result.failure(errorMessage: String): Exception {
            val detail = failureDetail()
            return Exception(if (detail.isBlank()) errorMessage else "$errorMessage: $detail")
        }

        private fun Shell.Result.failureDetail() = (err + out).joinToString("\n").trim()

        // Two or more segments, each starting with a letter, as Android requires of a package name
        private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

        internal fun isValidPackageName(packageName: String) = PACKAGE_NAME.matches(packageName)

        private fun String.shellQuote() = "'${replace("'", "'\"'\"'")}'"

        private fun String.singleLine() = replace(Regex("[\r\n]+"), " ")

        private fun staged(path: String) = "$path.tmp"

        private const val ROOT_CHECK_INTERVAL_MS = 1_000L
        private const val NO_MOUNT_CHECK_INTERVAL_MS = 5_000L
        private val STOCK_INSTALL_SETTLE_TIMEOUT = Duration.ofSeconds(30L)
        private val STOCK_INSTALL_SETTLE_POLL = 1_000.milliseconds

        /** Where [installPackage] copies the APK to, a directory system_server is allowed to read. */
        private const val INSTALL_TEMP_DIR = "/data/local/tmp/morphe-install"

        /** The scripts the root manager runs, which have to be executable. */
        private val MODULE_SCRIPTS = listOf("service.sh", "post-fs-data.sh")
        private val MODULE_FILES = MODULE_SCRIPTS + listOf("module.prop", "stock-integrity.sh")
        private const val STOCK_INTEGRITY_FILE = "stock-apk-sha256.txt"
        private const val STOCK_PATHS_FILE = "stock-paths.txt"

        // Between the package name and the split's own file name, so no split reads as the stock APK
        private const val STOCK_SPLIT_INFIX = "-stock-"
        /** Holds "1" when the module may only redirect with NoMount and never falls back to a bind mount. */
        private const val NO_MOUNT_MARKER = "nomount-only"

        private fun noMountMarker(noMountOnly: Boolean) = if (noMountOnly) "1" else "0"

        /** Where NoMount puts its CLI, linked next to the root manager's own tools. */
        private val NO_MOUNT_BINS = listOf(
            "/data/adb/modules/nomount/bin/nm",
            "/data/adb/ksu/bin/nm",
            "/data/adb/ap/bin/nm"
        )

        /** The driver protocol that brought UID scoped rules. */
        private const val NO_MOUNT_MIN_PROTOCOL = 22

        /** The NoMount release that brought [NO_MOUNT_MIN_PROTOCOL]. */
        private const val NO_MOUNT_MIN_VERSION = "2.2.0"

        // Every UID but system_server's, so PackageManager keeps seeing the stock APK it verifies
        private const val NO_MOUNT_SCOPE = "--uid-range all --uid-excluded 1000-1000"

        /** Where zygisk-detach puts its CLI. */
        private const val DETACH_BIN = "$MODULES_PATH/zygisk-detach/detach"
        /** Present when Morphe, not the user, detached the app with zygisk-detach. */
        private const val DETACHED_MARKER = "detached"
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
