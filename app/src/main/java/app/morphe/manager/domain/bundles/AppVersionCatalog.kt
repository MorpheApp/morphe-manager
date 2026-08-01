/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.bundles

import android.os.Build
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.domain.repository.PatchBundleRepository.Companion.DEFAULT_SOURCE_UID
import app.morphe.manager.patcher.patch.PatchBundleInfo
import app.morphe.patcher.patch.AppTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * An [AppTarget] annotated with the bundle it originates from.
 * Used to group versions by bundle in the APK availability dialog.
 */
data class BundledAppTarget(
    val target: AppTarget,
    val bundleUid: Int,
    val bundleName: String,
    /** Allowed build codes for this version, sourced from the patch bundle. Null means no constraint. */
    val buildCodes: Set<Int>? = null
)

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
    /** Every version each package can be patched at, grouped by source, newest first. */
    val compatibleVersions: Flow<Map<String, List<BundledAppTarget>>> =
        patchBundleRepository.bundleInfoFlow
            .combine(patchBundleRepository.sources) { bundleInfo, sources ->
                val enabledSources = sources.filter { it.enabled }
                extract(
                    bundleInfo = bundleInfo,
                    bundleNames = enabledSources.associate { it.uid to it.displayTitle },
                    enabledBundleUids = enabledSources.map { it.uid }.toSet()
                )
            }

    /** The single version to offer per package, honoring the experimental toggle per source. */
    val recommendedVersions: Flow<Map<String, AppTarget>> = combine(
        compatibleVersions,
        prefs.bundleExperimentalVersionsEnabled.flow,
        patchBundleRepository.bundleInfoFlow,
        patchBundleRepository.sources
    ) { versionData, experimentalEnabledUids, bundleInfo, sources ->
        val enabledUids = sources.filter { it.enabled }.map { it.uid }.toSet()
        // Packages for which at least one enabled bundle has experimental toggle on
        val experimentalEnabledPackages = bundleInfo
            .filterKeys { it in enabledUids && it.toString() in experimentalEnabledUids }
            .values
            .flatMap { it.patches }
            .flatMap { it.compatiblePackages.orEmpty() }
            .mapNotNull { it.packageName }
            .toSet()

        versionData.mapValues { (packageName, bundledTargets) ->
            pick(
                targets = bundledTargets.map { it.target },
                preferExperimental = packageName in experimentalEnabledPackages
            )
        }
    }

    /**
     * The same choice made per source, so the version list can badge each section
     * independently. Sources differ in which versions they carry and whether experimental
     * ones are enabled for them.
     */
    val recommendedVersionsByBundle: Flow<Map<String, Map<Int, AppTarget>>> = combine(
        compatibleVersions,
        prefs.bundleExperimentalVersionsEnabled.flow,
        patchBundleRepository.bundleInfoFlow,
        patchBundleRepository.sources
    ) { versionData, experimentalEnabledUids, bundleInfo, sources ->
        val enabledUids = sources.filter { it.enabled }.map { it.uid }.toSet()
        // Per-bundle set of packages that have experimental mode enabled
        val experimentalPackagesByBundle: Map<Int, Set<String>> = bundleInfo
            .filterKeys { it in enabledUids && it.toString() in experimentalEnabledUids }
            .mapValues { (_, info) ->
                info.patches
                    .flatMap { it.compatiblePackages.orEmpty() }
                    .mapNotNull { it.packageName }
                    .toSet()
            }

        versionData.mapValues { (packageName, bundledTargets) ->
            bundledTargets
                .groupBy { it.bundleUid }
                .mapValues { (bundleUid, targets) ->
                    pick(
                        targets = targets.map { it.target },
                        preferExperimental = experimentalPackagesByBundle[bundleUid]
                            ?.contains(packageName) == true
                    )
                }
        }
    }

    /**
     * Picks the one version to offer out of [targets], which arrive newest first.
     *
     * Versions the device cannot install are dropped, unless that would leave nothing, in
     * which case the newest is offered anyway so the UI has something to show.
     */
    private fun pick(targets: List<AppTarget>, preferExperimental: Boolean): AppTarget {
        val deviceSdk = Build.VERSION.SDK_INT
        val installable = targets.filter { it.minSdk == null || deviceSdk >= it.minSdk!! }
        val candidates = installable.ifEmpty { targets }

        return if (preferExperimental) {
            candidates.firstOrNull { it.isExperimental } ?: candidates.first()
        } else {
            candidates.firstOrNull { !it.isExperimental } ?: candidates.first()
        }
    }

    /** One-shot lookup for callers that are not observing, such as the batch planner. */
    suspend fun recommendedVersion(packageName: String): String? =
        recommendedVersions.first()[packageName]?.version

    private fun extract(
        bundleInfo: Map<Int, PatchBundleInfo>,
        bundleNames: Map<Int, String>,
        enabledBundleUids: Set<Int> = emptySet(),
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
                                    buildCodes = target.version?.let { codesForBundle?.get(it) }
                                )
                            }
                    }
            }
            // A package whose patches declare no versions has nothing to offer, and every
            // consumer below assumes a non-empty list
            .filterValues { it.isNotEmpty() }
    }
}
