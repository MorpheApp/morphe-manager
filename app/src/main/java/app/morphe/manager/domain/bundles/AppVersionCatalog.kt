/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.bundles

import android.os.Build
import app.morphe.manager.domain.apk.InstalledApkInfo
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.domain.repository.PatchBundleRepository.Companion.DEFAULT_SOURCE_UID
import app.morphe.manager.patcher.patch.PatchBundleInfo
import app.morphe.patcher.patch.AppTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * An [AppTarget] annotated with the bundle it originates from.
 * Used to group versions by bundle in the APK availability dialog.
 */
data class BundledAppTarget(
    val target: AppTarget,
    val bundleUid: Int,
    val bundleName: String,
    /** Allowed build codes for this version, sourced from the patch bundle. Null means no constraint. */
    val buildCodes: Set<Int>? = null,
    /** Whether the source this version comes from is set to use experimental app versions. */
    val experimentalEnabled: Boolean = false
)

/**
 * Whether an APK of [version] is something these targets can be patched at, down to the build
 * code wherever a target names one.
 *
 * An empty list, or a target carrying no version, is what a universal patch produces: nothing
 * is being asked of the APK, so anything passes.
 */
fun List<BundledAppTarget>.patchableAt(version: String, versionCode: Long?): Boolean {
    if (isEmpty() || any { it.target.version == null }) return true
    return any { entry ->
        entry.target.version == version &&
            (entry.buildCodes == null || versionCode == null || versionCode.toInt() in entry.buildCodes)
    }
}

/**
 * The installed APK as a source these targets accept, or null when they do not: the version it
 * carries has to be one the patches name. Asked here rather than at each call site so the
 * installed app cannot be offered by one flow and withheld by another.
 */
fun InstalledApkInfo?.patchableBy(targets: List<BundledAppTarget>): InstalledApkInfo? =
    this?.takeIf { targets.patchableAt(it.version, it.versionCode) }

/**
 * Versions any source marks experimental. The single definition every experimental badge and
 * warning is drawn from, so a version cannot read as stable in one place and not in another.
 */
fun List<BundledAppTarget>.experimentalVersions(): Set<String> =
    filter { it.target.isExperimental }.mapNotNullTo(mutableSetOf()) { it.target.version }

/**
 * The versions worth putting in front of the user, out of everything the sources cover.
 *
 * Experimental versions survive only for the sources switched into experimental mode: leaving
 * that off says they are not wanted at all, not merely that another one is suggested. A source
 * carrying nothing but experimental versions still shows them, because filtering it down to
 * nothing would leave the user with no version to pick.
 */
fun List<BundledAppTarget>.offered(): List<BundledAppTarget> =
    filter { !it.target.isExperimental || it.experimentalEnabled }.ifEmpty { this }

/** Whether this device meets the minimum SDK the version declares. */
fun BundledAppTarget.installableOnDevice(): Boolean =
    target.minSdk.let { it == null || Build.VERSION.SDK_INT >= it }

/**
 * Versions the device can install, out of targets that arrive newest first. Dropping them all
 * would leave the UI with nothing to show, so in that case they are kept.
 */
fun List<BundledAppTarget>.installable(): List<BundledAppTarget> =
    filter { it.installableOnDevice() }.ifEmpty { this }

/**
 * The version to suggest out of these targets: the newest one the device can install, once the
 * versions the user does not want to see are out of the way.
 */
fun List<BundledAppTarget>.recommended(): AppTarget? =
    offered().installable().firstOrNull()?.target

/**
 * Which app versions the enabled patch sources can work with, and which one to suggest.
 *
 * Both the single-app flow and the batch queue send users to download a specific version, so
 * the answer to "which version" is derived once here rather than in each of them.
 */
class AppVersionCatalog(
    patchBundleRepository: PatchBundleRepository,
    prefs: PreferencesManager
) {
    /**
     * Every version each package can be patched at, grouped by source, newest first. This is the
     * full set, which is what an APK already on the device has to be judged against; [offered]
     * narrows it to what a picker should put in front of the user.
     */
    val compatibleVersions: Flow<Map<String, List<BundledAppTarget>>> = combine(
        patchBundleRepository.bundleInfoFlow,
        patchBundleRepository.sources,
        prefs.bundleExperimentalVersionsEnabled.flow
    ) { bundleInfo, sources, experimentalEnabledUids ->
        val enabledSources = sources.filter { it.enabled }
        extract(
            bundleInfo = bundleInfo,
            bundleNames = enabledSources.associate { it.uid to it.displayTitle },
            enabledBundleUids = enabledSources.map { it.uid }.toSet(),
            experimentalEnabledUids = experimentalEnabledUids
        )
    }

    /** The single version to offer per package. */
    val recommendedVersions: Flow<Map<String, AppTarget>> =
        compatibleVersions.map { versionData ->
            buildMap {
                versionData.forEach { (packageName, bundledTargets) ->
                    bundledTargets.recommended()?.let { put(packageName, it) }
                }
            }
        }

    /** One-shot lookup for the entry points that resolve one app at a time. */
    suspend fun recommendedVersion(packageName: String): String? =
        recommendedVersions.first()[packageName]?.version

    private fun extract(
        bundleInfo: Map<Int, PatchBundleInfo>,
        bundleNames: Map<Int, String>,
        enabledBundleUids: Set<Int> = emptySet(),
        experimentalEnabledUids: Set<String> = emptySet(),
    ): Map<String, List<BundledAppTarget>> {
        // packageName → bundleUid → version → AppTarget
        val targetsByPackage = mutableMapOf<String, MutableMap<Int, MutableMap<String, AppTarget>>>()
        // packageName → bundleUid → version → build codes (parallel to targetsByPackage)
        val codesByPackage = mutableMapOf<String, MutableMap<Int, MutableMap<String, Set<Int>>>>()

        bundleInfo.forEach { (bundleUid, info) ->
            if (enabledBundleUids.isNotEmpty() && bundleUid !in enabledBundleUids) return@forEach

            info.patches.forEach { patch ->
                patch.compatiblePackages?.forEach { pkg ->
                    val packageName = pkg.packageName ?: return@forEach
                    val bundleMap = targetsByPackage
                        .getOrPut(packageName) { mutableMapOf() }
                        .getOrPut(bundleUid) { mutableMapOf() }
                    val codesMap = codesByPackage
                        .getOrPut(packageName) { mutableMapOf() }
                        .getOrPut(bundleUid) { mutableMapOf() }

                    pkg.versions?.forEach { version ->
                        val isExperimental = pkg.experimentalVersions?.contains(version) == true
                        // If a version appears in multiple patches of the same bundle, prefer stable
                        if (version !in bundleMap || !isExperimental) {
                            bundleMap[version] = AppTarget(
                                version = version,
                                isExperimental = isExperimental,
                                description = pkg.versionDescriptions?.get(version),
                                minSdk = pkg.versionMinSdks?.get(version),
                            )
                            pkg.versionCodes?.get(version)?.takeIf { it.isNotEmpty() }?.let {
                                codesMap[version] = it.toSet()
                            }
                        }
                    }
                }
            }
        }

        // Flatten: bundles ordered by display name, versions newest→oldest within each bundle
        return targetsByPackage
            .mapValues { (packageName, byBundle) ->
                byBundle.entries
                    .sortedWith(compareBy({ it.key != DEFAULT_SOURCE_UID }, { bundleNames[it.key] ?: "" }))
                    .flatMap { (uid, versionMap) ->
                        val codesForBundle = codesByPackage[packageName]?.get(uid)
                        versionMap.values
                            .sortedDescending()
                            .map { target ->
                                BundledAppTarget(
                                    target = target,
                                    bundleUid = uid,
                                    bundleName = bundleNames[uid] ?: "Bundle $uid",
                                    buildCodes = target.version?.let { codesForBundle?.get(it) },
                                    experimentalEnabled = uid.toString() in experimentalEnabledUids
                                )
                            }
                    }
            }
            // A package whose patches declare no versions has nothing to offer, and every
            // consumer below assumes a non-empty list
            .filterValues { it.isNotEmpty() }
    }
}
