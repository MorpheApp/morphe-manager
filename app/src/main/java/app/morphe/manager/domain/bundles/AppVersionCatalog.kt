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
 * Versions any source marks experimental. The single definition every experimental badge and
 * warning is drawn from, so a version cannot read as stable in one place and not in another.
 */
fun List<BundledAppTarget>.experimentalVersions(): Set<String> =
    filter { it.target.isExperimental }.mapNotNullTo(mutableSetOf()) { it.target.version }

/**
 * What one source offers for an app: the version it stands behind, and the version the manager
 * will actually patch at.
 *
 * The experimental toggle only moves the latter. A source does not recommend a version it marks
 * experimental, so a list that badged one as recommended would put words in its mouth.
 */
data class BundleRecommendation(
    /** Null when a source carries nothing but experimental versions for the app. */
    val declared: AppTarget?,
    val effective: AppTarget
)

/**
 * What the enabled sources say about one app's versions: which to suggest, and which come
 * with the caveat that they are experimental.
 */
data class AppVersionHints(
    val recommendedVersion: String?,
    val experimentalVersions: Set<String>
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
    val recommendedVersionsByBundle: Flow<Map<String, Map<Int, BundleRecommendation>>> = combine(
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
                    val appTargets = targets.map { it.target }
                    BundleRecommendation(
                        declared = declared(appTargets),
                        effective = pick(
                            targets = appTargets,
                            preferExperimental = experimentalPackagesByBundle[bundleUid]
                                ?.contains(packageName) == true
                        )
                    )
                }
        }
    }

    /**
     * Versions the device can install, out of [targets], which arrive newest first. Dropping
     * them all would leave the UI with nothing to show, so in that case they are kept.
     */
    private fun installable(targets: List<AppTarget>): List<AppTarget> {
        val deviceSdk = Build.VERSION.SDK_INT
        return targets
            .filter { it.minSdk == null || deviceSdk >= it.minSdk!! }
            .ifEmpty { targets }
    }

    /**
     * The version a source stands behind. Null when it carries none the device can install
     * outside the experimental ones, which a source does not recommend by definition.
     */
    private fun declared(targets: List<AppTarget>): AppTarget? =
        installable(targets).firstOrNull { !it.isExperimental }

    /** Picks the one version to offer out of [targets], which arrive newest first. */
    private fun pick(targets: List<AppTarget>, preferExperimental: Boolean): AppTarget {
        val candidates = installable(targets)

        return if (preferExperimental) {
            candidates.firstOrNull { it.isExperimental } ?: candidates.first()
        } else {
            candidates.firstOrNull { !it.isExperimental } ?: candidates.first()
        }
    }

    /**
     * Everything the batch planner needs to say about an app's versions, resolved in one pass.
     *
     * The maps behind it are derived from every patch of every source, so a planner that asked
     * per app would rebuild the whole catalog for each one.
     */
    suspend fun hints(): Map<String, AppVersionHints> {
        val recommended = recommendedVersions.first()
        val compatible = compatibleVersions.first()

        return compatible.mapValues { (packageName, targets) ->
            AppVersionHints(
                recommendedVersion = recommended[packageName]?.version,
                experimentalVersions = targets.experimentalVersions()
            )
        }
    }

    /** One-shot lookup for a single app, for the entry points that resolve one at a time. */
    suspend fun hints(packageName: String): AppVersionHints? = hints()[packageName]

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
