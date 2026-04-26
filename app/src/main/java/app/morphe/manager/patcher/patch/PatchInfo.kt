package app.morphe.manager.patcher.patch

import androidx.compose.runtime.Immutable
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.ApkFileType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlin.reflect.KType
import app.morphe.patcher.patch.Option as PatchOption

data class PatchInfo(
    val name: String,
    val description: String?,
    val include: Boolean,
    val compatiblePackages: ImmutableList<CompatiblePackage>?,
    val options: ImmutableList<Option<*>>?
) {
    @Suppress("DEPRECATION")
    constructor(patch: Patch<*>) : this(
        name = patch.name.orEmpty(),
        description = patch.description,
        include = patch.default,
        compatiblePackages = patch.compatibility
            ?.map { compatibility ->
                CompatiblePackage(
                    packageName = compatibility.packageName, // null = universal patch
                    displayName = compatibility.name,
                    versions = compatibility.targets
                        .mapNotNull { it.version }
                        .toImmutableSet()
                        .takeIf { it.isNotEmpty() },
                    appIconColor = compatibility.appIconColor,
                    apkFileType = compatibility.apkFileType,
                    experimentalVersions = compatibility.targets
                        .filter { it.isExperimental }
                        .mapNotNull { it.version }
                        .toImmutableSet()
                        .takeIf { it.isNotEmpty() },
                    signatures = compatibility.signatures?.toImmutableSet(),
                    versionDescriptions = compatibility.targets
                        .mapNotNull { target ->
                            val v = target.version ?: return@mapNotNull null
                            val d = target.description ?: return@mapNotNull null
                            v to d
                        }
                        .toMap()
                        .toImmutableMap()
                        .takeIf { it.isNotEmpty() },
                    versionMinSdks = compatibility.targets
                        .mapNotNull { target ->
                            val v = target.version ?: return@mapNotNull null
                            val sdk = target.minSdk ?: return@mapNotNull null
                            v to sdk
                        }
                        .toMap()
                        .toImmutableMap()
                        .takeIf { it.isNotEmpty() },
                )
            }
            ?.toImmutableList()
        // Fallback to legacy API if new compatibility is not available
            ?: patch.compatiblePackages?.map { (pkgName, versions) ->
                CompatiblePackage(
                    packageName = pkgName,
                    versions = versions?.toImmutableSet()
                )
            }?.toImmutableList(),
        options = patch.options.map { (_, option) -> Option(option) }.ifEmpty { null }?.toImmutableList()
    )

    fun compatibleWith(packageName: String) =
        compatiblePackages == null ||
                compatiblePackages.any { it.packageName == null || it.packageName == packageName }

    fun supports(packageName: String, versionName: String?): Boolean {
        val packages = compatiblePackages ?: return true // Universal patch

        return packages.any { pkg ->
            // Universal patch (null packageName) supports everything
            if (pkg.packageName == null) return@any true
            if (pkg.packageName != packageName) return@any false
            if (pkg.versions == null) return@any true

            versionName != null && versionName in pkg.versions
        }
    }

    /**
     * Returns true if [versionName] is an experimental target for [packageName].
     */
    fun isExperimental(packageName: String, versionName: String?): Boolean {
        if (versionName == null) return false
        return pkgFor(packageName)?.experimentalVersions?.contains(versionName) == true
    }

    /**
     * Returns the display name for [packageName] declared in the patch bundle, or null if not specified.
     */
    fun displayNameFor(packageName: String): String? = pkgFor(packageName)?.displayName

    /**
     * Returns the SHA-256 signatures for [packageName] declared in the patch bundle, or null if not specified.
     */
    fun signaturesFor(packageName: String): ImmutableSet<String>? = pkgFor(packageName)?.signatures

    /**
     * Returns the preferred [ApkFileType] for [packageName], if specified.
     */
    fun apkFileTypeFor(packageName: String): ApkFileType? = pkgFor(packageName)?.apkFileType

    /**
     * Returns the app icon color for [packageName] as a 0xAARRGGBB int, or null if not specified.
     */
    fun appIconColorFor(packageName: String): Int? = pkgFor(packageName)?.appIconColor

    /** Finds the [CompatiblePackage] entry for [packageName], or null if not found. */
    private fun pkgFor(packageName: String): CompatiblePackage? =
        compatiblePackages?.firstOrNull { it.packageName == packageName }

}

@Immutable
data class CompatiblePackage(
    /** Package name of the target app. **Null means universal patch** - compatible with any package. */
    val packageName: String?,
    val versions: ImmutableSet<String>?,
    /** App display name declared in the patch bundle. Null if not specified. */
    val displayName: String? = null,
    /** 0xAARRGGBB color for the app icon background, or null if not specified. */
    val appIconColor: Int? = null,
    /** Preferred or required APK file type, or null if not specified. */
    val apkFileType: ApkFileType? = null,
    /** Subset of [versions] that are marked as experimental targets. */
    val experimentalVersions: ImmutableSet<String>? = null,
    /** Valid SHA-256 signing certificate fingerprints of the original app APK. Null means no verification. */
    val signatures: ImmutableSet<String>? = null,
    /** Per-version user-facing descriptions. */
    val versionDescriptions: ImmutableMap<String, String>? = null,
    /** Minimum Android SDK version required per app version. */
    val versionMinSdks: ImmutableMap<String, Int>? = null,
)

@Immutable
data class Option<T>(
    val title: String,
    val key: String,
    val description: String,
    val required: Boolean,
    val type: KType,
    val default: T?,
    val presets: Map<String, T?>?,
    val validator: (T?) -> Boolean,
) {
    @Suppress("DEPRECATION")
    constructor(option: PatchOption<T>) : this(
        option.title ?: option.key,
        option.key,
        option.description.orEmpty(),
        option.required,
        option.type,
        option.default,
        option.values,
        { option.validator(option, it) },
    )
}
