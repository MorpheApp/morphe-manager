/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.apk

import android.content.pm.PackageInfo
import android.util.Log
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.domain.manager.KeystoreManager
import app.morphe.manager.domain.repository.InstalledAppRepository
import app.morphe.manager.domain.repository.OriginalApkRepository
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// The record is written once the install finished, so only clock skew separates the two stamps
private const val INSTALL_TIME_TOLERANCE_MS = 60_000L

/** Saved APK information for display in APK selection dialog. */
data class SavedApkInfo(
    val fileName: String,
    val filePath: String,
    val version: String,
    val versionCode: Long? = null
)

/** Installed APK information for display in APK selection dialog. */
data class InstalledApkInfo(
    val version: String,
    val versionCode: Long? = null,
    val apkPath: String,
    val splitPaths: List<String> = emptyList(),
    val patchStateUnknown: Boolean = false
) {
    val isSplit: Boolean get() = splitPaths.isNotEmpty()
}

/**
 * What the device itself holds for one app: whether the app is still the one under that name, the
 * archive behind it as a patching source, and the version it reports.
 *
 * [apk] and [version] are both withheld from a patched install, so neither the patcher nor the UI
 * can take Morphe's own output for the app it was built from. [hasStockInstall] outlives them,
 * because a mount install overlays the app rather than replacing it.
 */
data class InstalledAppSource(
    val hasStockInstall: Boolean = false,
    val apk: InstalledApkInfo? = null,
    val version: String? = null
) {
    companion object {
        /** Nothing under that package name, or nothing that could be read. */
        val None = InstalledAppSource()
    }
}

/** Whether an installed app has already been patched. */
enum class InstalledPatchState {
    Patched,
    NotPatched,
    Unknown
}

/**
 * Everything a UI action needs to know about a tracked app, resolved in one pass.
 *
 * [patchState] is null exactly when nothing is installed under the tracked package name, and
 * [savedPatchedApkInfo] is the archive parse behind [savedPatchedApk] so callers can read the
 * label or version without opening the file again.
 */
data class TrackedAppSnapshot(
    val installedPackageInfo: PackageInfo?,
    val savedPatchedApk: File?,
    val savedPatchedApkInfo: PackageInfo?,
    val patchState: InstalledPatchState?
)

/**
 * Weighs the evidence that the installed package still is the build Morphe patched.
 * Kept free of Android APIs so the ordering between the signals can be tested directly.
 */
internal fun resolveTrackedPatchState(
    installedHashes: Set<String>,
    savedPatchedHashes: Set<String>,
    originalHashes: Set<String>,
    managerSigningHashes: Set<String>,
    installedByPatchManager: Boolean,
    installerAttributionMatches: Boolean,
    installedAfterPatching: Boolean
): InstalledPatchState {
    // A mismatch only proves that the installed package is not the reference artifact; it does
    // not identify what replaced it. Only positive matches and trusted installer evidence can
    // confirm either state.
    if (installedHashes.any { it in savedPatchedHashes }) {
        return InstalledPatchState.Patched
    }

    if (installedHashes.any { it in originalHashes }) {
        return InstalledPatchState.NotPatched
    }

    // Patching signs with Morphe's own keystore, so its certificate outlives both archives and
    // still identifies the build after the retained APKs have been deleted
    if (installedHashes.any { it in managerSigningHashes }) {
        return InstalledPatchState.Patched
    }

    if (installedByPatchManager) return InstalledPatchState.Patched

    // The record knows which installer Morphe would have attributed its own install to. A
    // different installer on a package that only appeared after the patch is somebody else's
    // installation, which is the one case certificates cannot describe when nothing was retained.
    if (!installerAttributionMatches && installedAfterPatching) {
        return InstalledPatchState.NotPatched
    }

    return InstalledPatchState.Unknown
}

/**
 * Whether the tracked record can still be removed from the app detail view.
 *
 * The record outlives the build it describes, so cleanup has to stay reachable whenever the
 * patched build is no longer accounted for. The one case with nothing to clean up is a confirmed
 * patched install that retained no APK, since the record then describes the app on the device.
 */
internal fun canRemoveTrackedRecord(
    installType: InstallType,
    patchState: InstalledPatchState?,
    hasSavedApk: Boolean
): Boolean =
    hasSavedApk ||
            installType == InstallType.SAVED ||
            patchState != InstalledPatchState.Patched

/**
 * The APKs already on the device that an app could be patched from: the original Morphe kept
 * from a previous run, and the app as the system has it installed.
 */
class LocalApkSources(
    private val originalApkRepository: OriginalApkRepository,
    private val installedAppRepository: InstalledAppRepository,
    private val patchBundleRepository: PatchBundleRepository,
    private val appDataResolver: AppDataResolver,
    private val filesystem: Filesystem,
    private val keystoreManager: KeystoreManager,
    private val pm: PM
) {
    // Keyed by the tracked package, kept only while the evidence behind it is unchanged
    private val snapshotCache = mutableMapOf<String, CachedSnapshot>()

    private data class CachedSnapshot(val fingerprint: String, val snapshot: TrackedAppSnapshot)

    /** The original APK kept after a previous patch, or null when there is none on disk. */
    suspend fun saved(packageName: String): SavedApkInfo? = try {
        val originalApk = originalApkRepository.get(packageName)
        val file = originalApk?.let { File(it.filePath) }?.takeIf { it.exists() }

        if (file == null) {
            null
        } else {
            // Resolved rather than taken from the record, because the file is the truth
            val resolved = appDataResolver.resolveAppData(
                packageName = packageName,
                preferredSource = AppDataSource.ORIGINAL_APK
            )
            SavedApkInfo(
                fileName = file.name,
                filePath = file.absolutePath,
                version = resolved.version ?: originalApk.version,
                versionCode = resolved.packageInfo?.let { pm.getVersionCode(it) }
            )
        }
    } catch (e: Exception) {
        Log.e(tag, "Failed to load saved APK info", e)
        null
    }

    /**
     * What the device offers for [packageName], resolved in one pass so every flow asks the
     * patch state once.
     *
     * A patched install yields no APK and no version: copying the archive would feed a patched
     * build back into the patcher, and a patch that keeps the package name installs over the app
     * it patched, so the version describes Morphe's own output. When the certificate cannot be
     * read the APK is returned with [InstalledApkInfo.patchStateUnknown] so the caller can say the
     * check did not happen rather than imply it passed.
     */
    suspend fun installed(packageName: String): InstalledAppSource = try {
        val pkgInfo = pm.getPackageInfo(packageName)
        val version = pkgInfo?.versionName?.takeUnless { it.isBlank() }
        // Read here rather than inside the patch state, because it is the one patched install
        // that leaves the app itself in place: the module overlays the archive, nothing else
        val mounted = pkgInfo != null && pm.hasSourceApkSignatureMismatch(packageName)

        if (pkgInfo == null) {
            InstalledAppSource.None
        } else when (val patchState = patchState(packageName, version, mounted)) {
            InstalledPatchState.Patched -> InstalledAppSource(hasStockInstall = mounted)

            else -> {
                val appInfo = pkgInfo.applicationInfo
                val sourceDir = appInfo?.sourceDir?.takeIf { File(it).exists() }
                val apk = if (sourceDir == null || version == null) {
                    null
                } else {
                    InstalledApkInfo(
                        version = version,
                        versionCode = pm.getVersionCode(pkgInfo),
                        apkPath = sourceDir,
                        splitPaths = appInfo.splitSourceDirs?.filter { File(it).exists() }.orEmpty(),
                        patchStateUnknown = patchState == InstalledPatchState.Unknown
                    )
                }

                InstalledAppSource(hasStockInstall = true, apk = apk, version = version)
            }
        }
    } catch (e: Exception) {
        Log.e(tag, "Failed to load installed app info", e)
        InstalledAppSource.None
    }

    /**
     * Identifies whether the package currently occupying a tracked app's package name is still
     * the patched build Morphe recorded.
     *
     * The installed-app row is deliberately not evidence here: it survives an uninstall so the
     * user can retain patch settings. A stock reinstall with the same package and version must
     * therefore be checked against the saved patched APK, the original certificate, bundle
     * certificates, or the installer rather than being accepted merely because the row exists.
     * Null means the package is no longer installed.
     */
    suspend fun trackedPatchState(app: InstalledApp): InstalledPatchState? =
        trackedAppSnapshot(app).patchState

    /**
     * Resolves the saved APK and installed identity together for UI action gating.
     *
     * Repeated calls for an unchanged app are answered from the previous result, so a refresh can
     * ask about every tracked app without paying for the inspection again.
     */
    suspend fun trackedAppSnapshot(app: InstalledApp): TrackedAppSnapshot = withContext(Dispatchers.IO) {
        val installedPackageInfo = pm.getPackageInfo(app.currentPackageName)
        val installer = installedPackageInfo?.let {
            pm.getInstallerPackageName(app.currentPackageName)
        }
        val signingHashes = keystoreManager.signingCertificateHashes()
        val fingerprint = trackedAppFingerprint(app, installedPackageInfo, installer, signingHashes)
        cachedSnapshot(app.currentPackageName, fingerprint)?.let { return@withContext it }

        val snapshot = resolveTrackedAppSnapshot(app, installedPackageInfo, installer, signingHashes)
        cacheSnapshot(app.currentPackageName, fingerprint, snapshot)
        snapshot
    }

    /** Drops the remembered snapshot of [packageName] so the next read inspects the disk again. */
    fun invalidate(packageName: String) {
        synchronized(snapshotCache) { snapshotCache.remove(packageName) }
    }

    private suspend fun resolveTrackedAppSnapshot(
        app: InstalledApp,
        installedPackageInfo: PackageInfo?,
        installer: String?,
        signingHashes: Set<String>
    ): TrackedAppSnapshot {
        val savedPatched = validatedPatchedApk(app)
        val savedPatchedApk: File? = savedPatched?.first
        val savedPatchedInfo: PackageInfo? = savedPatched?.second

        if (installedPackageInfo == null) {
            return TrackedAppSnapshot(null, savedPatchedApk, savedPatchedInfo, null)
        }

        val patchState = resolveTrackedPatchState(
            installedHashes = pm.getInstalledSignatureHashes(app.currentPackageName),
            savedPatchedHashes = savedPatchedApk?.let(pm::getApkFileSignatureHashes).orEmpty(),
            originalHashes = referenceSignatureHashes(app.originalPackageName),
            managerSigningHashes = signingHashes,
            installedByPatchManager = pm.isPatchManagerInstaller(installer),
            installerAttributionMatches = installerMatchesRecord(app.installType, installer),
            installedAfterPatching = installedAfterPatching(app, installedPackageInfo)
        )

        // A mounted install reports the stock certificate while sourceDir points at the patched
        // APK, so it overrides the verdict, and is only read while that verdict is still open
        val mounted = patchState != InstalledPatchState.Patched &&
                pm.hasSourceApkSignatureMismatch(app.currentPackageName)

        return TrackedAppSnapshot(
            installedPackageInfo = installedPackageInfo,
            savedPatchedApk = savedPatchedApk,
            savedPatchedApkInfo = savedPatchedInfo,
            patchState = if (mounted) InstalledPatchState.Patched else patchState
        )
    }

    private fun cachedSnapshot(packageName: String, fingerprint: String): TrackedAppSnapshot? =
        synchronized(snapshotCache) {
            snapshotCache[packageName]?.takeIf { it.fingerprint == fingerprint }?.snapshot
        }

    private fun cacheSnapshot(packageName: String, fingerprint: String, snapshot: TrackedAppSnapshot) {
        synchronized(snapshotCache) {
            snapshotCache[packageName] = CachedSnapshot(fingerprint, snapshot)
        }
    }

    /**
     * Everything that can change what the snapshot resolves to, read with stat calls alone.
     *
     * A mount swaps the file behind `sourceDir` and a repatch rewrites the saved APK in place.
     * Both archives are therefore described by their own size and timestamp, not by the record.
     */
    private suspend fun trackedAppFingerprint(
        app: InstalledApp,
        installedPackageInfo: PackageInfo?,
        installer: String?,
        signingHashes: Set<String>
    ): String {
        val installedApk = installedPackageInfo?.applicationInfo?.sourceDir?.let(::File)
        val originalApk = originalApkRepository.get(app.originalPackageName)
        val originalFile = originalApk?.let { File(it.filePath) }
        val bundleSignatures = patchBundleRepository.appMetadata.value[app.originalPackageName]
            ?.signatures
            .orEmpty()
        return buildString {
            append(app.currentPackageName).append('|')
            append(app.originalPackageName).append('|')
            append(app.version).append('|')
            append(app.installType).append('|')
            append(app.patchedAt).append('|')
            append(installedPackageInfo?.versionName).append('|')
            append(installedPackageInfo?.firstInstallTime).append('|')
            append(installedPackageInfo?.lastUpdateTime).append('|')
            append(installer).append('|')
            append(fileStamp(installedApk)).append('|')
            savedPatchedApkCandidates(app).joinTo(this, ";") { fileStamp(it) }
            append('|').append(originalApk?.version).append('|')
            append(originalFile?.absolutePath).append(':').append(fileStamp(originalFile)).append('|')
            bundleSignatures.sorted().joinTo(this, ",")
            append('|')
            signingHashes.sorted().joinTo(this, ",")
        }
    }

    private fun fileStamp(file: File?): String =
        if (file == null) "-" else "${file.length()}:${file.lastModified()}"

    /**
     * Whether the current installer is the one Morphe itself would have set for this record.
     *
     * Play Store-attributed and custom install modes make installer identity inconclusive, so
     * only the modes that leave a predictable attribution can rule an installation out.
     */
    private fun installerMatchesRecord(installType: InstallType, installer: String?): Boolean =
        when (installType) {
            InstallType.PLAY_STORE,
            InstallType.ROOT_PLAY_STORE,
            InstallType.SHIZUKU_PLAY_STORE -> installer == PLAY_STORE_INSTALLER_PACKAGE

            // A mounted stock app and a user-picked installer can carry any attribution
            InstallType.MOUNT, InstallType.CUSTOM -> true

            // Handing the APK to the system installer can leave either Morphe or the installer
            // itself as the attribution, and the Morphe case was already accepted as proof above
            InstallType.DEFAULT -> installer == null ||
                    installer == AOSP_INSTALLER_PACKAGE ||
                    installer == AOSP_INSTALLER_PACKAGE_LEGACY

            // Shizuku runs as the shell user, so its installs are attributed either to the shell
            // package or to nothing at all, and anything else replaced the package
            InstallType.SHIZUKU, InstallType.SAVED -> installer == null ||
                    installer == SHELL_INSTALLER_PACKAGE
        }

    /**
     * Whether the installation on the device is newer than the patch record tracking it.
     * An install Morphe made itself keeps the original record, so this is only trusted next
     * to an installer that Morphe would not have set.
     */
    private fun installedAfterPatching(app: InstalledApp, installedPackageInfo: PackageInfo): Boolean {
        val patchedAt = app.patchedAt ?: return false
        return installedPackageInfo.firstInstallTime > patchedAt + INSTALL_TIME_TOLERANCE_MS
    }

    /** Current and legacy storage paths a patched build could have been retained at. */
    private fun savedPatchedApkCandidates(app: InstalledApp): List<File> =
        listOf(
            filesystem.getPatchedAppFile(app.currentPackageName, app.version),
            filesystem.getPatchedAppFile(app.originalPackageName, app.version)
        ).distinctBy { it.absolutePath }

    /**
     * Searches both storage paths, but only accepts the artifact the record describes. A renamed
     * app must never fall back to an APK whose embedded id is the original package. The expected
     * file name is not on its own proof of what the file contains.
     */
    private fun validatedPatchedApk(app: InstalledApp): Pair<File, PackageInfo>? =
        savedPatchedApkCandidates(app).firstNotNullOfOrNull { file ->
            pm.readSavedApkInfo(file, app.version, app.currentPackageName)?.let { file to it }
        }

    /** Certificates that identify the stock build: the retained original first, then the bundle. */
    private suspend fun referenceSignatureHashes(packageName: String): Set<String> {
        val savedHashes = originalApkRepository.get(packageName)
            ?.let { File(it.filePath) }
            ?.takeIf { it.exists() }
            ?.let(pm::getApkFileSignatureHashes)
            .orEmpty()

        return savedHashes.ifEmpty {
            patchBundleRepository.appMetadata.value[packageName]?.signatures.orEmpty()
        }
    }

    /**
     * Decided in priority order, most reliable first: a mounted install, Morphe's own signing
     * certificate, the saved original's own certificate, the certificates the bundle declares,
     * Morphe's own records, and finally who installed the package.
     */
    private suspend fun patchState(
        packageName: String,
        installedVersion: String?,
        mounted: Boolean
    ): InstalledPatchState {
        // Checked first because the certificates below describe the stock app while the file
        // that "Use installed APK" would copy is the patched one
        if (mounted) return InstalledPatchState.Patched

        val installedHashes = pm.getInstalledSignatureHashes(packageName)

        // Morphe's signature identifies its own output even when nothing was kept to compare against
        if (installedHashes.any { it in keystoreManager.signingCertificateHashes() }) {
            return InstalledPatchState.Patched
        }

        val referenceHashes = referenceSignatureHashes(packageName)

        if (referenceHashes.isNotEmpty() && installedHashes.isNotEmpty()) {
            return if (installedHashes.none { it in referenceHashes }) {
                InstalledPatchState.Patched
            } else {
                InstalledPatchState.NotPatched
            }
        }

        val tracked = installedAppRepository.get(packageName)
        if (tracked != null && installedVersion == tracked.version) return InstalledPatchState.Patched
        if (pm.isInstalledByPatchManager(packageName)) return InstalledPatchState.Patched

        // A comparison was possible in principle, so an unreadable certificate leaves the
        // state genuinely unknown rather than clean
        return if (referenceHashes.isNotEmpty()) {
            InstalledPatchState.Unknown
        } else {
            InstalledPatchState.NotPatched
        }
    }
}
