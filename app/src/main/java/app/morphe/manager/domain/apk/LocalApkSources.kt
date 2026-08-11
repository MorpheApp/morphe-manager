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
import app.morphe.manager.domain.repository.InstalledAppRepository
import app.morphe.manager.domain.repository.OriginalApkRepository
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.util.AOSP_INSTALLER_PACKAGE
import app.morphe.manager.util.AOSP_INSTALLER_PACKAGE_LEGACY
import app.morphe.manager.util.AppDataResolver
import app.morphe.manager.util.AppDataSource
import app.morphe.manager.util.PLAY_STORE_INSTALLER_PACKAGE
import app.morphe.manager.util.PM
import app.morphe.manager.util.tag
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
 * patched build is no longer accounted for. Only a confirmed patched install with nothing
 * retained has nothing to clean up, since the record then describes the app on the device.
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
    private val pm: PM
) {
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
     * Whether the app is installed and, if it is a single unpatched APK, its info.
     *
     * The info is withheld when the installed app looks patched, because copying it would
     * feed a patched build back into the patcher. When the certificate cannot be read the
     * info is returned with [InstalledApkInfo.patchStateUnknown] so the caller can say the
     * check did not happen rather than imply it passed.
     */
    suspend fun installed(packageName: String): Pair<Boolean, InstalledApkInfo?> = try {
        val pkgInfo = pm.getPackageInfo(packageName)

        if (pkgInfo == null) {
            false to null
        } else when (val patchState = patchState(packageName, pkgInfo.versionName)) {
            InstalledPatchState.Patched -> true to null

            else -> {
                val appInfo = pkgInfo.applicationInfo
                val sourceDir = appInfo?.sourceDir?.takeIf { File(it).exists() }
                val version = pkgInfo.versionName?.takeUnless { it.isBlank() }

                if (sourceDir == null || version == null) {
                    true to null
                } else {
                    true to InstalledApkInfo(
                        version = version,
                        versionCode = pm.getVersionCode(pkgInfo),
                        apkPath = sourceDir,
                        splitPaths = appInfo.splitSourceDirs?.filter { File(it).exists() }.orEmpty(),
                        patchStateUnknown = patchState == InstalledPatchState.Unknown
                    )
                }
            }
        }
    } catch (e: Exception) {
        Log.e(tag, "Failed to load installed app info", e)
        false to null
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
     * Certificate reads go through [PM]'s archive cache, so repeated calls for an unchanged app
     * cost a package manager query rather than a full archive verification.
     */
    suspend fun trackedAppSnapshot(app: InstalledApp): TrackedAppSnapshot = withContext(Dispatchers.IO) {
        val savedPatched = validatedPatchedApk(app)
        val savedPatchedApk: File? = savedPatched?.first
        val savedPatchedInfo: PackageInfo? = savedPatched?.second

        val installedPackageInfo = pm.getPackageInfo(app.currentPackageName)
            ?: return@withContext TrackedAppSnapshot(null, savedPatchedApk, savedPatchedInfo, null)

        // A mounted install keeps the stock certificate in PackageManager while sourceDir points
        // at the patched APK, so this signal must win over all certificate comparisons below.
        if (pm.hasSourceApkSignatureMismatch(app.currentPackageName)) {
            return@withContext TrackedAppSnapshot(
                installedPackageInfo,
                savedPatchedApk,
                savedPatchedInfo,
                InstalledPatchState.Patched
            )
        }

        val installer = pm.getInstallerPackageName(app.currentPackageName)

        TrackedAppSnapshot(
            installedPackageInfo = installedPackageInfo,
            savedPatchedApk = savedPatchedApk,
            savedPatchedApkInfo = savedPatchedInfo,
            patchState = resolveTrackedPatchState(
                installedHashes = pm.getInstalledSignatureHashes(app.currentPackageName),
                savedPatchedHashes = savedPatchedInfo?.let(pm::signatureHashes).orEmpty(),
                originalHashes = referenceSignatureHashes(app.originalPackageName),
                installedByPatchManager = pm.isPatchManagerInstaller(installer),
                installerAttributionMatches = installerMatchesRecord(app.installType, installer),
                installedAfterPatching = installedAfterPatching(app, installedPackageInfo)
            )
        )
    }

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

            // Shizuku installs leave no attribution behind, so anything else replaced the package
            InstallType.SHIZUKU, InstallType.SAVED -> installer == null
        }

    /**
     * Whether the installation on the device is newer than the patch record tracking it.
     * Morphe's own reinstalls keep the original record, so this is only trusted next to an
     * installer that Morphe would not have set.
     */
    private fun installedAfterPatching(app: InstalledApp, installedPackageInfo: PackageInfo): Boolean {
        val patchedAt = app.patchedAt ?: return false
        return installedPackageInfo.firstInstallTime > patchedAt + INSTALL_TIME_TOLERANCE_MS
    }

    /**
     * Searches both current and legacy storage paths, but only accepts the artifact the record
     * describes. A renamed app must never fall back to an APK whose embedded id is the original
     * package, and the expected file name is not on its own proof of what the file contains.
     */
    private fun validatedPatchedApk(app: InstalledApp): Pair<File, PackageInfo>? =
        listOf(
            filesystem.getPatchedAppFile(app.currentPackageName, app.version),
            filesystem.getPatchedAppFile(app.originalPackageName, app.version)
        ).distinctBy { it.absolutePath }.firstNotNullOfOrNull { file ->
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
     * Decided in priority order, most reliable first: a mounted install, the saved original's
     * own certificate, the certificates the bundle declares, Morphe's own records, and finally
     * who installed the package.
     */
    private suspend fun patchState(packageName: String, installedVersion: String?): InstalledPatchState {
        // Checked first because the certificates below describe the stock app while the file
        // that "Use installed APK" would copy is the patched one
        if (pm.hasSourceApkSignatureMismatch(packageName)) return InstalledPatchState.Patched

        val referenceHashes = referenceSignatureHashes(packageName)

        if (referenceHashes.isNotEmpty()) {
            val installedHashes = pm.getInstalledSignatureHashes(packageName)
            if (installedHashes.isNotEmpty()) {
                return if (installedHashes.none { it in referenceHashes }) {
                    InstalledPatchState.Patched
                } else {
                    InstalledPatchState.NotPatched
                }
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
