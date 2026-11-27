/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.viewmodel

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.Log
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.domain.apk.InstalledApkInfo
import app.morphe.manager.domain.apk.LocalApkSources
import app.morphe.manager.domain.apk.SavedApkInfo
import app.morphe.manager.domain.apk.apkSignatureCheckable
import app.morphe.manager.domain.repository.OriginalApkRepository
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.patcher.split.SplitApkInspector
import app.morphe.manager.patcher.split.SplitApkPreparer
import app.morphe.manager.ui.model.SelectedApp
import app.morphe.manager.ui.screen.shared.readableLabel
import app.morphe.manager.util.AppDataResolver
import app.morphe.manager.util.LOG_TAG
import app.morphe.manager.util.PM
import app.morphe.manager.util.copyApkInto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** An installed app entry shown in the universal-patch app picker. */
data class InstalledAppPickerItem(
    val packageName: String,
    val label: String,
    val packageInfo: PackageInfo,
    val isSystemApp: Boolean,
    val info: InstalledApkInfo
)

/** Result of attempting to load a local APK file. */
sealed interface ApkLoadResult {
    /**
     * File was read and parsed successfully, with the app's icon and label where it carries them.
     *
     * @param certificates Certificate fingerprints of the file, null where the sources declare none
     *   to check them against.
     */
    data class Success(
        val app: SelectedApp.Local,
        val icon: Drawable?,
        val label: String?,
        val certificates: Set<String>? = null
    ) : ApkLoadResult
    /** File could not be read - provider returned null stream or zero bytes. */
    data object Unreadable : ApkLoadResult
    /** File was read but is not a valid APK/split archive. */
    data object NotAnApk : ApkLoadResult
    /** File is a patched build, which is never patched again. */
    data object AlreadyPatched : ApkLoadResult
    /** An unexpected IO or system exception occurred while copying or parsing. */
    data object IoError : ApkLoadResult
}

/**
 * Gets the APK of a single-app run ready from wherever it comes: a picked file, the saved
 * original, or an app installed on the device. Only reads and copies files, so which dialog
 * follows is left to [PatchFlow].
 *
 * Files of the run go to [Filesystem.uiTempDir] rather than the cache directory, which Android
 * may clear even mid-patch.
 */
class PatchFlowApks(
    private val app: Application,
    private val pm: PM,
    private val filesystem: Filesystem,
    private val appDataResolver: AppDataResolver,
    private val localApkSources: LocalApkSources,
    private val originalApkRepository: OriginalApkRepository,
    private val patchBundleRepository: PatchBundleRepository
) {
    /**
     * Copies the file at [uri] into the run and reads it as an app. Supports both single APK and
     * split APK archives (`apkm`, `apks`, `xapk`).
     */
    suspend fun load(uri: Uri): ApkLoadResult = withContext(Dispatchers.IO) {
        try {
            val tempFile = uri.copyApkInto(filesystem.uiTempDir, "temp_apk", app.contentResolver)
                ?: return@withContext ApkLoadResult.Unreadable

            // A split archive is read through its base module, deleted after the call, so the icon
            // and the label are read in it
            val result = SplitApkInspector.withRepresentativeApk(
                source = tempFile,
                workspace = filesystem.uiTempDir
            ) { apk ->
                val packageInfo = pm.getPackageInfo(apk)
                when {
                    packageInfo == null -> ApkLoadResult.NotAnApk
                    localApkSources.isPatchedApk(apk, packageInfo.packageName) -> ApkLoadResult.AlreadyPatched
                    else -> ApkLoadResult.Success(
                        SelectedApp.Local(
                            packageName = packageInfo.packageName,
                            version = packageInfo.versionName ?: "unknown",
                            versionCode = pm.getVersionCode(packageInfo),
                            file = tempFile,
                            temporary = true
                        ),
                        icon = appDataResolver.detachedArchiveIcon(packageInfo),
                        label = packageInfo.readableLabel(app.packageManager),
                        // Read while the module is unpacked, which the signature check would otherwise repeat
                        certificates = if (
                            apkSignatureCheckable(Build.VERSION.SDK_INT, declaredSignatures(packageInfo.packageName))
                        ) pm.getApkFileSignatureHashes(apk) else null
                    )
                }
            }

            if (result == ApkLoadResult.NotAnApk) {
                Log.w(LOG_TAG, "Picked file ${tempFile.name} could not be parsed as an APK")
            }
            if (result !is ApkLoadResult.Success) tempFile.delete()
            result
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to load APK", e)
            ApkLoadResult.IoError
        }
    }

    /**
     * The APK [info] describes of the installed [packageName], copied into a file of the run's
     * own. Throws when it has gone from the device.
     */
    fun copyInstalled(packageName: String, info: InstalledApkInfo) = SelectedApp.Local(
        packageName = packageName,
        version = info.version,
        versionCode = info.versionCode,
        file = SplitApkPreparer.copyInstalledApp(packageName, info.apkPath, info.splitPaths, filesystem.uiTempDir),
        temporary = true,
        fromInstalledDevice = true
    )

    /**
     * The original APK saved for [packageName], marked as used, or null when its file is gone.
     * The file is the saved one itself, never deleted with the run.
     */
    suspend fun useSaved(packageName: String, info: SavedApkInfo): SelectedApp.Local? {
        val file = File(info.filePath)
        if (!file.exists()) return null
        originalApkRepository.markUsed(packageName)
        return SelectedApp.Local(
            packageName = packageName,
            version = info.version,
            versionCode = info.versionCode,
            file = file,
            temporary = false
        )
    }

    /** The apps on the device that can be patched, by label, for the universal-patch picker. */
    suspend fun installedApps(): List<InstalledAppPickerItem> = withContext(Dispatchers.IO) {
        val candidates = pm.getInstalledPackages()
            .mapNotNull { pkgInfo ->
                if (pkgInfo.packageName == app.packageName) return@mapNotNull null
                val appInfo = pkgInfo.applicationInfo ?: return@mapNotNull null
                val sourceDir = appInfo.sourceDir ?: return@mapNotNull null
                if (!File(sourceDir).exists()) return@mapNotNull null
                val version = pkgInfo.versionName?.takeUnless { it.isBlank() }
                    ?: return@mapNotNull null
                val label = with(pm) { pkgInfo.label() }
                val splitPaths = appInfo.splitSourceDirs
                    ?.filter { File(it).exists() }
                    .orEmpty()
                InstalledAppPickerItem(
                    packageName = pkgInfo.packageName,
                    label = label,
                    packageInfo = pkgInfo,
                    isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    info = InstalledApkInfo(
                        version = version,
                        versionCode = pm.getVersionCode(pkgInfo),
                        apkPath = sourceDir,
                        splitPaths = splitPaths
                    )
                )
            }
        // A patched build is no app to patch, it is the app's own install
        val patched = localApkSources.patchedInstalls(candidates.map { it.packageInfo })
        candidates
            .filterNot { it.packageName in patched }
            .sortedBy { it.label.lowercase() }
    }

    /** Certificate fingerprints of [file], read through its base module where it is a split archive. */
    suspend fun certificates(file: File): Set<String> = withContext(Dispatchers.IO) {
        SplitApkInspector.withRepresentativeApk(
            source = file,
            workspace = filesystem.uiTempDir,
            block = pm::getApkFileSignatureHashes
        )
    }

    private fun declaredSignatures(packageName: String) =
        patchBundleRepository.appMetadata.value[packageName]?.signatures
}
