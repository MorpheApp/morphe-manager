package app.morphe.manager.patcher.patch

import app.morphe.manager.util.PatchSelection

/**
 * A base class for storing [PatchBundle] metadata.
 */
sealed class PatchBundleInfo {
    /**
     * The name of the bundle.
     */
    abstract val name: String

    /**
     * The version of the bundle.
     */
    abstract val version: String?

    /**
     * The unique ID of the bundle.
     */
    abstract val uid: Int

    /**
     * The state indicating whether the bundle is enabled or disabled.
     */
    abstract val enabled: Boolean

    /**
     * The patch list.
     */
    abstract val patches: List<PatchInfo>

    /**
     * Information about a bundle and all the patches it contains.
     *
     * @see [PatchBundleInfo]
     */
    data class Global(
        override val name: String,
        override val version: String?,
        override val uid: Int,
        override val enabled: Boolean,
        override val patches: List<PatchInfo>
    ) : PatchBundleInfo() {
        /**
         * Create a [PatchBundleInfo.Scoped] that only contains information about patches that are relevant for a specific [packageName].
         */
        fun forPackage(packageName: String, version: String?): Scoped {
            val relevantPatches = patches.filter { it.compatibleWith(packageName) }
            val compatible = mutableListOf<PatchInfo>()
            val incompatible = mutableListOf<PatchInfo>()
            val universal = mutableListOf<PatchInfo>()

            // Accumulate all per-package metadata in a single pass
            var isVersionExperimental = false
            var appIconColor: Int? = null
            var apkFileType: app.morphe.patcher.patch.ApkFileType? = null
            var displayName: String? = null
            val signaturesAcc = mutableSetOf<String>()

            relevantPatches.forEach { patch ->
                // Categorise into compatible / incompatible / universal
                val targetList = when {
                    patch.compatiblePackages == null -> universal
                    patch.supports(packageName, version) -> compatible
                    else -> incompatible
                }
                targetList.add(patch)

                // Collect metadata (first non-null wins for scalar fields)
                if (version != null && !isVersionExperimental) {
                    isVersionExperimental = patch.isExperimental(packageName, version)
                }
                if (appIconColor == null) appIconColor = patch.appIconColorFor(packageName)
                if (apkFileType == null) apkFileType = patch.apkFileTypeFor(packageName)
                if (displayName == null) displayName = patch.displayNameFor(packageName)
                patch.signaturesFor(packageName)?.let { signaturesAcc.addAll(it) }
            }

            return Scoped(
                name,
                this.version,
                uid,
                enabled,
                relevantPatches,
                compatible,
                incompatible,
                universal,
                isVersionExperimental = isVersionExperimental,
                appIconColor = appIconColor,
                apkFileType = apkFileType,
                displayName = displayName,
                signatures = signaturesAcc.takeIf { it.isNotEmpty() },
            )
        }
    }

    /**
     * Contains information about a bundle that is relevant for a specific package name.
     *
     * @param compatible Patches that are compatible with the specified package name and version.
     * @param incompatible Patches that are compatible with the specified package name but not version.
     * @param universal Patches that are compatible with all packages.
     * @param isVersionExperimental Whether the selected app version is marked as experimental in this bundle.
     * @param appIconColor The 0xAARRGGBB app icon background color declared in the bundle, or null.
     * @param apkFileType The preferred/required APK file type declared in the bundle, or null.
     * @param displayName The app display name declared in the patch bundle (e.g. "YouTube"), or null.
     * @param signatures Valid SHA-256 signing fingerprints of the original app, or null if not declared.
     * @see [PatchBundleInfo.Global.forPackage]
     * @see [PatchBundleInfo]
     */
    data class Scoped(
        override val name: String,
        override val version: String?,
        override val uid: Int,
        override val enabled: Boolean,
        override val patches: List<PatchInfo>,
        val compatible: List<PatchInfo>,
        val incompatible: List<PatchInfo>,
        val universal: List<PatchInfo>,
        val isVersionExperimental: Boolean = false,
        val appIconColor: Int? = null,
        val apkFileType: app.morphe.patcher.patch.ApkFileType? = null,
        val displayName: String? = null,
        val signatures: Set<String>? = null,
    ) : PatchBundleInfo() {
        fun patchSequence(allowIncompatible: Boolean) = if (allowIncompatible) {
            patches.asSequence()
        } else {
            sequence {
                yieldAll(compatible)
                yieldAll(universal)
            }
        }
    }

    companion object Extensions {
        inline fun Iterable<Scoped>.toPatchSelection(
            allowIncompatible: Boolean,
            condition: (Int, PatchInfo) -> Boolean
        ): PatchSelection = this.associate { bundle ->
            val patches =
                bundle.patchSequence(allowIncompatible)
                    .mapNotNullTo(mutableSetOf()) { patch ->
                        patch.name.takeIf {
                            condition(
                                bundle.uid,
                                patch
                            )
                        }
                    }

            bundle.uid to patches
        }

        /**
         * Algorithm for determining whether all required options have been set.
         */
        inline fun Iterable<Scoped>.requiredOptionsSet(
            allowIncompatible: Boolean,
            crossinline isSelected: (Scoped, PatchInfo) -> Boolean,
            crossinline optionsForPatch: (Scoped, PatchInfo) -> Map<String, Any?>?
        ) = all bundle@{ bundle ->
            bundle
                .patchSequence(allowIncompatible)
                .filter { isSelected(bundle, it) }
                .all patch@{
                    if (it.options.isNullOrEmpty()) return@patch true
                    val opts by lazy { optionsForPatch(bundle, it).orEmpty() }

                    it.options.all option@{ option ->
                        if (!option.required || option.default != null) return@option true

                        option.key in opts
                    }
                }
        }
    }
}
