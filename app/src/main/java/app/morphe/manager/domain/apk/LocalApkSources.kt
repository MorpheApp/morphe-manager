/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.apk

import android.util.Log
import app.morphe.manager.domain.repository.InstalledAppRepository
import app.morphe.manager.domain.repository.OriginalApkRepository
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.util.AppDataResolver
import app.morphe.manager.util.AppDataSource
import app.morphe.manager.util.PM
import app.morphe.manager.util.tag
import java.io.File

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
 * The APKs already on the device that an app could be patched from: the original Morphe kept
 * from a previous run, and the app as the system has it installed.
 */
class LocalApkSources(
    private val originalApkRepository: OriginalApkRepository,
    private val installedAppRepository: InstalledAppRepository,
    private val patchBundleRepository: PatchBundleRepository,
    private val appDataResolver: AppDataResolver,
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
     * Decided in priority order, most reliable first: a mounted install, the saved original's
     * own certificate, the certificates the bundle declares, Morphe's own records, and finally
     * who installed the package.
     */
    private suspend fun patchState(packageName: String, installedVersion: String?): InstalledPatchState {
        // Checked first because the certificates below describe the stock app while the file
        // that "Use installed APK" would copy is the patched one
        if (pm.hasSourceApkSignatureMismatch(packageName)) return InstalledPatchState.Patched

        val savedHashes = originalApkRepository.get(packageName)
            ?.let { File(it.filePath) }
            ?.takeIf { it.exists() }
            ?.let { pm.getApkFileSignatureHashes(it) }
            .orEmpty()
        val referenceHashes = savedHashes.ifEmpty {
            patchBundleRepository.appMetadata.value[packageName]?.signatures.orEmpty()
        }

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
