/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/util/ExportNameFormatter.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.util

import android.content.pm.PackageInfo
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.patcher.patch.PatchSourceRef

/**
 * What an APK handed out of the manager is named by.
 *
 * @param sources The sources the APK was patched from, none for an APK as the app ships.
 * @param isClone Whether the build is a copy of the app the user asked for, see
 *   [app.morphe.manager.ui.model.producesClone].
 */
data class ApkExportData(
    val appName: String?,
    val packageName: String,
    val appVersion: String?,
    val sources: List<PatchSourceRef> = emptyList(),
    val isClone: Boolean = false
)

/**
 * Export data of the APK a run [built], named by that APK rather than by the app the run started
 * from, since a patch may give it a name, version or package of its own.
 *
 * @param fallbackVersion The version the run started from, for an APK that names none.
 */
fun PM.exportData(
    built: PackageInfo,
    sources: List<PatchSourceRef>,
    isClone: Boolean,
    fallbackVersion: String?
) = ApkExportData(
    appName = runCatching { built.label() }.getOrNull(),
    packageName = built.packageName,
    appVersion = built.versionName?.takeUnless { it.isBlank() } ?: fallbackVersion,
    sources = sources,
    isClone = isClone
)

/**
 * The name the build kept for this record goes by, which is the one the run gave it: the sources
 * are named as they were when it was patched, however they have been renamed or updated since.
 */
fun InstalledApp.exportFileName(appName: String? = appLabel): String = ExportNameFormatter.format(
    ApkExportData(
        appName = appName,
        packageName = currentPackageName,
        appVersion = version,
        sources = selectionPayload?.bundles.orEmpty().mapNotNull { bundle ->
            bundle.bundleName?.let { PatchSourceRef(it, bundle.bundleVersion) }
        },
        isClone = isClone
    )
)

object ExportNameFormatter {
    /** Sources named in the file name, the rest only counted, so it stays within filesystem limits. */
    private const val NAMED_SOURCES = 2

    private const val CLONE_MARK = "clone"

    /**
     * The name an APK is saved under, such as `YouTube-v20.1.0-Morphe_Patches-v1.2.0.apk`.
     * Every source names the version it patched with, as builds from different sources of the
     * same app are otherwise told apart by nothing. A clone usually keeps the app's own name,
     * so it is marked as one to keep it from landing on the file of the app it was built from.
     */
    fun format(data: ApkExportData): String {
        // An app name made only of symbols would leave nothing before the version
        val app = data.appName?.let { FilenameUtils.sanitize(it, keepScripts = true) }?.takeUnless { it.isEmpty() }
            ?: FilenameUtils.sanitize(data.packageName)
        return listOfNotNull(
            app,
            CLONE_MARK.takeIf { data.isClone },
            formatVersion(data.appVersion) ?: "unknown",
            formatSources(data.sources)
        ).joinToString("-", postfix = ".apk")
    }

    private fun formatSources(sources: List<PatchSourceRef>): String? {
        val named = sources
            .mapNotNull { source ->
                FilenameUtils.sanitize(source.name, keepScripts = true).takeUnless { it.isEmpty() }
                    ?.let { name -> listOfNotNull(name, formatVersion(source.version)).joinToString("-") }
            }
            .distinct()
            .takeUnless { it.isEmpty() }
            ?: return null
        val hidden = named.size - NAMED_SOURCES
        return (named.take(NAMED_SOURCES) + listOfNotNull(hidden.takeIf { it > 0 }?.toString()))
            .joinToString("+")
    }

    // Lowercased first, so a "V" prefix goes too
    private fun formatVersion(raw: String?): String? =
        raw?.trim()?.lowercase()?.normalizeVersion()
            ?.let { FilenameUtils.sanitize(it) }
            ?.takeUnless { it.isEmpty() }
            ?.let { "v$it" }
}
