/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.patch

import androidx.compose.ui.graphics.Color
import app.morphe.manager.util.KnownApps
import app.morphe.patcher.patch.ApkFileType

/**
 * Color of a bundle's `appIconColor`. The value is 0xRRGGBB with a zero alpha per the
 * Compatibility spec, so full opacity is forced.
 */
fun appIconColorOf(rgb: Int): Color = Color(rgb or (0xFF shl 24))

/** Color of [packageName] as the first of these patches to declare one gives it, if any does. */
fun Sequence<PatchInfo>.appColorFor(packageName: String): Color? =
    firstNotNullOfOrNull { it.appIconColorFor(packageName) }?.let(::appIconColorOf)

/**
 * Aggregated metadata about an app as declared in one or more patch bundles.
 * Priority for conflicting values across bundles: first non-null value wins.
 *
 * @param packageName  The app package name.
 * @param displayName  App name declared in the bundle (e.g. "YouTube"). Null if not declared.
 * @param appIconColor 0xAARRGGBB accent color declared in the bundle. Null if not declared.
 * @param apkFileType  Preferred/required APK input format. Null if not declared.
 * @param signatures   Union of all valid SHA-256 signing fingerprints across all bundles.
 *                     Null means no bundle declared signatures → skip verification.
 */
data class BundleAppMetadata(
    val packageName: String,
    val displayName: String?,
    val appIconColor: Int?,
    val apkFileType: ApkFileType?,
    val signatures: Set<String>?,
) {
    /** Derived gradient color list for home screen buttons. Null means use fallback. */
    val gradientColors: List<Color>? = appIconColor?.let { rgb ->
        listOf(appIconColorOf(rgb), KnownApps.GRADIENT_MID, KnownApps.GRADIENT_END)
    }

    /** Derived download button color. Null means use fallback. */
    val downloadColor: Color? = appIconColor?.let(::appIconColorOf)

    companion object {
        /**
         * Build a [Map] of packageName → [BundleAppMetadata] from every [PatchBundleInfo.Global] given.
         * Which bundles count is the caller's choice, so a disabled one still names its apps.
         */
        fun buildFrom(bundleInfoMap: Map<Int, PatchBundleInfo.Global>): Map<String, BundleAppMetadata> {
            // packageName → mutable accumulators
            val allPackageNames = mutableSetOf<String>()
            val displayNames = mutableMapOf<String, String>()
            val iconColors = mutableMapOf<String, Int>()
            val apkFileTypes = mutableMapOf<String, ApkFileType>()
            val signaturesMap = mutableMapOf<String, MutableSet<String>>()

            bundleInfoMap.values
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
                    }
                }

            return allPackageNames.associateWith { pkgName ->
                BundleAppMetadata(
                    packageName = pkgName,
                    displayName = displayNames[pkgName] ?: KnownApps.fallbackName(pkgName),
                    appIconColor = iconColors[pkgName],
                    apkFileType = apkFileTypes[pkgName],
                    signatures = signaturesMap[pkgName]?.toSet(),
                )
            }
        }
    }
}
