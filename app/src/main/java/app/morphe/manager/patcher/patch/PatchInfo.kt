package app.morphe.manager.patcher.patch

import androidx.compose.runtime.Immutable
import app.morphe.patcher.patch.*
import kotlinx.collections.immutable.*
import kotlin.reflect.KType
import app.morphe.patcher.patch.ColorOption as PatchColorOption
import app.morphe.patcher.patch.FilePathOption as PatchFilePathOption
import app.morphe.patcher.patch.FilesOption as PatchFilesOption
import app.morphe.patcher.patch.FolderOption as PatchFolderOption
import app.morphe.patcher.patch.ImageOption as PatchImageOption
import app.morphe.patcher.patch.Option as PatchOption

data class PatchInfo(
    /** Key for selections and options, unique within one app's list: [displayName], suffixed on collision. */
    val name: String,
    val description: String?,
    val include: Boolean,
    val compatiblePackages: ImmutableList<CompatiblePackage>?,
    val options: ImmutableList<Option<*>>?,
    val availabilityResolver: AvailabilityResolver? = null,
    /** The name as declared by the bundle, for anything the user reads. */
    val displayName: String = name,
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
                    versionCodes = compatibility.targets
                        .mapNotNull { target ->
                            val v = target.version ?: return@mapNotNull null
                            val codes = target.buildCodesOrNull()?.toImmutableSet() ?: return@mapNotNull null
                            v to codes
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
        options = patch.options.map { (_, option) -> Option(option) }.ifEmpty { null }?.toImmutableList(),
        availabilityResolver = patch.availability,
    )

    /**
     * Whether this patch should be selected by default for the given install target.
     *
     * When the patch ships an [availabilityResolver], it is the authoritative source:
     * REQUIRED and ENABLED count as selected, UNAVAILABLE and DISABLED as unselected.
     * Patches without a resolver fall back to [include].
     */
    fun defaultSelected(installerType: InstallerType, apkArchitecture: ApkArchitecture): Boolean {
        val resolver = availabilityResolver ?: return include
        return when (resolver.resolve(installerType, apkArchitecture)) {
            PatchAvailability.REQUIRED, PatchAvailability.ENABLED -> true
            PatchAvailability.UNAVAILABLE, PatchAvailability.DISABLED -> false
        }
    }

    /**
     * Whether the user can toggle this patch for the given install target, and in which direction
     * it is locked when they cannot.
     */
    fun lockState(installerType: InstallerType, apkArchitecture: ApkArchitecture): PatchLockState {
        val resolver = availabilityResolver ?: return PatchLockState.NONE
        return when (resolver.resolve(installerType, apkArchitecture)) {
            PatchAvailability.REQUIRED    -> PatchLockState.LOCKED_ON
            PatchAvailability.UNAVAILABLE -> PatchLockState.LOCKED_OFF
            PatchAvailability.ENABLED,
            PatchAvailability.DISABLED    -> PatchLockState.NONE
        }
    }

    /** Universal patches declare no compatible packages and therefore apply to any app. */
    val isUniversal get() = compatiblePackages.isNullOrEmpty()

    fun compatibleWith(packageName: String) =
        compatiblePackages == null ||
                compatiblePackages.any { it.packageName == null || it.packageName == packageName }

    fun supports(packageName: String, versionName: String?, versionCode: Long? = null): Boolean {
        val packages = compatiblePackages ?: return true // Universal patch

        return packages.any { pkg ->
            // Universal patch (null packageName) supports everything
            if (pkg.packageName == null) return@any true
            if (pkg.packageName != packageName) return@any false
            if (pkg.versions == null) return@any true
            if (versionName == null || versionName !in pkg.versions) return@any false
            val allowedCodes = pkg.versionCodes?.get(versionName) ?: return@any true
            versionCode == null || versionCode.toInt() in allowedCodes
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

/** Compatible package names, sorted, or empty for a universal patch. */
private fun PatchInfo.compatibilityKey() =
    compatiblePackages?.mapNotNull { it.packageName }?.sorted()?.joinToString().orEmpty()

/**
 * Selection keys for [patches], in the same order, keeping same-named ones apart so they cannot
 * share one entry. Scoped to a single app's list, because that is how selections are stored.
 */
fun uniqueNames(patches: List<PatchInfo>): List<String> {
    val keys = patches.mapTo(mutableListOf()) { it.name }
    val duplicates = patches.withIndex().groupBy { it.value.name }.filterValues { it.size > 1 }
    if (duplicates.isEmpty()) return keys

    val taken = patches.mapTo(mutableSetOf()) { it.name }
    duplicates.forEach { (name, group) ->
        // The most specific patch keeps the plain name, so already stored keys stay valid
        group.sortedByDescending { it.value.compatibilityKey() }.drop(1).forEach { (index, _) ->
            var occurrence = 1
            var key = name
            while (!taken.add(key)) key = "$name (${++occurrence})"
            keys[index] = key
        }
    }
    return keys
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
    /** Per-version allowed version codes (union of all declared ABI codes). Null means no constraint. */
    val versionCodes: ImmutableMap<String, ImmutableSet<Int>>? = null,
)

/** Returns the union of all ABI-specific version codes, or null if none are declared. */
fun AppTarget.buildCodesOrNull(): Set<Int>? = versionCodes?.values?.toSet()?.ifEmpty { null }

/**
 * Semantic UI hint produced by a typed patcher [PatchOption] subclass
 * (e.g. [PatchFolderOption], [PatchFilePathOption]). Null when the underlying
 * option is a plain untyped [PatchOption].
 */
enum class ExplicitOptionKind { Folder, FilePath, Files, Image, Color }

/** Recommended pixel dimensions for an [ExplicitOptionKind.Image] option. */
data class ImageSize(val width: Int, val height: Int)

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
    /** Non-null when the patch declared a typed option (FolderOption, FilePathOption, etc.). */
    val explicitKind: ExplicitOptionKind? = null,
    /** File extensions filter for FilePath, Files, or Image options. Null when unrestricted. */
    val allowedExtensions: ImmutableList<String>? = null,
    /** Recommended image dimensions declared by an Image option. */
    val recommendedSize: ImageSize? = null,
) {
    @Suppress("DEPRECATION")
    constructor(option: PatchOption<T>) : this(
        title = option.title ?: option.key,
        key = option.key,
        description = option.description.orEmpty(),
        required = option.required,
        type = option.type,
        default = option.default,
        presets = option.values,
        validator = { option.validator(option, it) },
        explicitKind = extractExplicitKind(option),
        allowedExtensions = extractAllowedExtensions(option)?.toImmutableList(),
        recommendedSize = extractRecommendedSize(option),
    )
}

private fun extractExplicitKind(option: PatchOption<*>): ExplicitOptionKind? = when (option) {
    is PatchFolderOption   -> ExplicitOptionKind.Folder
    is PatchFilePathOption -> ExplicitOptionKind.FilePath
    is PatchFilesOption    -> ExplicitOptionKind.Files
    is PatchImageOption    -> ExplicitOptionKind.Image
    is PatchColorOption    -> ExplicitOptionKind.Color
    else -> null
}

private fun extractAllowedExtensions(option: PatchOption<*>): List<String>? = when (option) {
    is PatchFilePathOption -> option.allowedExtensions
    is PatchFilesOption    -> option.allowedExtensions
    is PatchImageOption    -> option.allowedExtensions
    else -> null
}

private fun extractRecommendedSize(option: PatchOption<*>): ImageSize? = when (option) {
    is PatchImageOption -> option.recommendedSize?.let { ImageSize(it.width, it.height) }
    else -> null
}
