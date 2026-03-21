/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.patch

import androidx.compose.ui.graphics.Color
import app.morphe.manager.util.KnownApps
import app.morphe.patcher.patch.ApkFileType

/**
 * Aggregated metadata about an app as declared in one or more enabled patch bundles.
 * Priority for conflicting values across bundles: first non-null value wins.
 *
 * @param packageName  The app package name.
 * @param displayName  App name declared in the bundle (e.g. "YouTube"). Null if not declared.
 * @param appIconColor 0xAARRGGBB accent color declared in the bundle. Null if not declared.
 * @param apkFileType  Preferred/required APK input format. Null if not declared.
 * @param signatures   Union of all valid SHA-256 signing fingerprints across all bundles.
 *                     Null means no bundle declared signatures → skip verification.
 * @param experimentalVersions Union of all versions marked as experimental across all bundles.
 */
data class BundleAppMetadata(
    val packageName: String,
    val displayName: String?,
    val appIconColor: Int?,
    val apkFileType: ApkFileType?,
    val signatures: Set<String>?,
    val experimentalVersions: Set<String>,
) {
    /** Derived gradient color list for home screen buttons. Null means use fallback. */
    val gradientColors: List<Color>? = appIconColor?.let { rgb ->
        // appIconColor is 0xRRGGBB (alpha=0x00 per Compatibility spec) - force full opacity
        listOf(Color(rgb or (0xFF shl 24)), KnownApps.GRADIENT_MID, KnownApps.GRADIENT_END)
    }

    /** Derived download button color. Null means use fallback. */
    val downloadColor: Color? = appIconColor?.let { rgb ->
        // appIconColor is 0xRRGGBB (alpha=0x00 per Compatibility spec) - force full opacity
        Color(rgb or (0xFF shl 24))
    }

    companion object {
        /**
         * Build a [Map] of packageName → [BundleAppMetadata] from all enabled [PatchBundleInfo.Global].
         * Called whenever bundleInfoFlow emits a new value.
         */
        fun buildFrom(bundleInfoMap: Map<Int, PatchBundleInfo.Global>): Map<String, BundleAppMetadata> {
            // packageName → mutable accumulators
            val allPackageNames = mutableSetOf<String>()
            val displayNames = mutableMapOf<String, String>()
            val iconColors = mutableMapOf<String, Int>()
            val apkFileTypes = mutableMapOf<String, ApkFileType>()
            val signaturesMap = mutableMapOf<String, MutableSet<String>>()
            val experimentalMap = mutableMapOf<String, MutableSet<String>>()

            bundleInfoMap.values
                .filter { it.enabled }
                .flatMap { it.patches }
                .forEach { patch ->
                    patch.compatiblePackages?.forEach { pkg ->
                        // Universal patches (null packageName) have no per-app metadata to aggregate
                        val pkgName = pkg.packageName ?: return@forEach

                        // Always register the package name so patchablePackagesFlow works
                        // even for patches that declare no metadata fields
                        allPackageNames.add(pkgName)

                        if (pkg.displayName != null && pkgName !in displayNames) {
                            displayNames[pkgName] = pkg.displayName
                        }
                        if (pkg.appIconColor != null && pkgName !in iconColors) {
                            iconColors[pkgName] = pkg.appIconColor
                        }
                        if (pkg.apkFileType != null && pkgName !in apkFileTypes) {
                            apkFileTypes[pkgName] = pkg.apkFileType
                        }
                        pkg.signatures?.let {
                            signaturesMap.getOrPut(pkgName) { mutableSetOf() }.addAll(it)
                        }
                        pkg.experimentalVersions?.let {
                            experimentalMap.getOrPut(pkgName) { mutableSetOf() }.addAll(it)
                        }
                    }
                }

            return allPackageNames.associateWith { pkgName ->
                BundleAppMetadata(
                    packageName = pkgName,
                    displayName = displayNames[pkgName] ?: KnownApps.fallbackName(pkgName),
                    appIconColor = iconColors[pkgName] ?: legacyAppIconColor(pkgName),
                    apkFileType = apkFileTypes[pkgName],
                    signatures = signaturesMap[pkgName]?.toSet(),
                    experimentalVersions = experimentalMap[pkgName] ?: emptySet(),
                )
            }
        }

        // TODO: Remove once all active bundles ship Compatibility with appIconColor field.
        //  Transitional fallback for the period between Manager 1.3.0 release and
        //  patch bundles being updated to use the new Compatibility API.
        private fun legacyAppIconColor(packageName: String): Int? = when (packageName) {
            KnownApps.YOUTUBE       -> 0xFF0000  // YouTube red
            KnownApps.YOUTUBE_MUSIC -> 0xFF0000  // YouTube Music red
            KnownApps.REDDIT        -> 0xFF4500  // Reddit orange-red
            KnownApps.X_TWITTER     -> 0x000000  // X black
            else                    -> null
        }
    }
}
