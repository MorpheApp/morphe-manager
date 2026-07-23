package app.morphe.manager.patcher.patch

import android.os.Build
import android.os.Parcelable
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.loadPatchesFromDex
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.io.File
import java.io.IOException
import java.util.jar.JarFile

@Parcelize
data class PatchBundle(val patchesJar: String) : Parcelable {
    /**
     * The [java.util.jar.Manifest] of [patchesJar].
     */
    @IgnoredOnParcel
    private val manifest by lazy {
        try {
            JarFile(patchesJar).use { it.manifest }
        } catch (_: IOException) {
            null
        }
    }

    @IgnoredOnParcel
    val manifestAttributes by lazy {
        if (manifest != null)
            ManifestAttributes(
                name = readManifestAttribute("Name"),
                version = readManifestAttribute("Version"),
                description = readManifestAttribute("Description"),
                source = readManifestAttribute("Source"),
                author = readManifestAttribute("Author"),
                contact = readManifestAttribute("Contact"),
                website = readManifestAttribute("Website"),
                license = readManifestAttribute("License"),
                patcherVersion = readManifestAttribute("Patcher-Version"),
                addOnBundles = readManifestAttribute("Add-On-Bundle")
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() },
            ) else
            null
    }

    private fun readManifestAttribute(name: String) = manifest?.mainAttributes?.getValue(name)
        ?.takeIf { it.isNotBlank() } // If empty, set it to null instead.

    data class ManifestAttributes(
        val name: String?,
        val version: String?,
        val description: String?,
        val source: String?,
        val author: String?,
        val contact: String?,
        val website: String?,
        val license: String?,
        val patcherVersion: String?,
        /** Names of parent bundles from the `Add-On-Bundle` manifest attribute (comma-separated). */
        val addOnBundles: List<String>? = null,
    )

    object Loader {
        /**
         * Load patches declared by [bundle]. If [bundle] declares `Add-On-Bundle`, matching
         * parents from [availableBundles] are added to the class loader (add-on only; fork
         * bundles stay isolated). Returned patches are from [bundle] itself only.
         */
        private fun loadBundle(
            bundle: PatchBundle,
            availableBundles: Iterable<PatchBundle> = emptyList()
        ): Collection<Patch<*>> {
            validateDexEntries(bundle.patchesJar)
            val jarFile = File(bundle.patchesJar)
            val parentJars = resolveParentBundleJars(bundle, availableBundles)
            val allJars = buildSet {
                add(jarFile)
                parentJars.forEach { add(it) }
            }
            val patchFiles = runCatching {
                loadPatchesFromDex(
                    allJars,
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
                        null
                    } else {
                        // Must pass in any directory that exists.
                        // Directory is ignored with Android 8.0+, but is required
                        // for Android 8.0 otherwise NPE occurs.
                        jarFile.parentFile
                    }
                ).byPatchesFile
            }.getOrElse { error ->
                throw IllegalStateException("Patch bundle is corrupted or incomplete", error)
            }
            return patchFiles[jarFile]
                ?: throw IllegalStateException("Unexpected patch bundle load result for ${bundle.patchesJar}")
        }

        private fun resolveParentBundleJars(
            bundle: PatchBundle,
            availableBundles: Iterable<PatchBundle>
        ): List<File> {
            val parentNames = bundle.manifestAttributes?.addOnBundles.orEmpty()
            if (parentNames.isEmpty()) return emptyList()
            return availableBundles.mapNotNull { other ->
                if (other === bundle) return@mapNotNull null
                val otherName = other.manifestAttributes?.name ?: return@mapNotNull null
                if (otherName in parentNames) File(other.patchesJar) else null
            }
        }

        private fun metadataFor(
            bundle: PatchBundle,
            availableBundles: Iterable<PatchBundle> = emptyList()
        ) = loadBundle(bundle, availableBundles).map(::PatchInfo)

        fun metadata(bundles: Iterable<PatchBundle>): Map<PatchBundle, Collection<PatchInfo>> {
            val list = bundles.toList()
            return list.associateWith { bundle -> metadataFor(bundle, list) }
        }

        fun metadata(
            bundle: PatchBundle,
            availableBundles: Iterable<PatchBundle> = emptyList()
        ) = metadataFor(bundle, availableBundles)

        fun patches(bundles: Iterable<PatchBundle>, packageName: String): Map<PatchBundle, Set<Patch<*>>> {
            val list = bundles.toList()
            return loadGrouped(list).mapValues { (_, patches) ->
                patches.filter { patch ->
                    val compatibility = patch.compatibility
                        ?: return@filter true // Universal patch

                    compatibility.any { compat ->
                        compat.packageName == packageName ||
                                compat.packageName == null // Universal compatibility entry
                    }
                }.toSet()
            }
        }

        /**
         * Splits [bundles] into class-loading groups and loads each group through its own
         * DexClassLoader. Add-on bundles (declaring `Add-On-Bundle`) are unioned with their
         * declared parents so shared classes resolve to one Class → one static instance across
         * the group. Fork bundles without add-on declarations stay in their own singleton
         * groups (isolated), preserving current behavior for third-party forks that ship
         * divergent copies of shared classes.
         */
        private fun loadGrouped(bundles: List<PatchBundle>): Map<PatchBundle, Collection<Patch<*>>> {
            if (bundles.isEmpty()) return emptyMap()
            val groups = buildClassLoaderGroups(bundles)
            val result = mutableMapOf<PatchBundle, Collection<Patch<*>>>()
            groups.forEach { group ->
                if (group.size == 1) {
                    val only = group.single()
                    result[only] = loadBundle(only)
                } else {
                    result.putAll(loadSharedGroup(group))
                }
            }
            return result
        }

        /** Union-Find: merge each add-on with the declared parents present in [bundles]. */
        private fun buildClassLoaderGroups(bundles: List<PatchBundle>): List<List<PatchBundle>> {
            val nameToBundle = bundles.mapNotNull { b ->
                b.manifestAttributes?.name?.let { it to b }
            }.toMap()
            val parent = bundles.associateWithTo(mutableMapOf()) { it }
            fun find(x: PatchBundle): PatchBundle {
                var r = x
                while (parent.getValue(r) !== r) r = parent.getValue(r)
                return r
            }
            fun union(a: PatchBundle, b: PatchBundle) {
                val ra = find(a); val rb = find(b)
                if (ra !== rb) parent[ra] = rb
            }
            bundles.forEach { bundle ->
                bundle.manifestAttributes?.addOnBundles?.forEach { parentName ->
                    nameToBundle[parentName]?.let { union(bundle, it) }
                }
            }
            return bundles.groupBy(::find).values.toList()
        }

        private fun loadSharedGroup(group: List<PatchBundle>): Map<PatchBundle, Collection<Patch<*>>> {
            group.forEach { validateDexEntries(it.patchesJar) }
            val jarByBundle = group.associateWith { File(it.patchesJar) }
            val allJars = jarByBundle.values.toSet()
            val result = runCatching {
                loadPatchesFromDex(
                    allJars,
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) null
                    else jarByBundle.values.first().parentFile
                ).byPatchesFile
            }.getOrElse { error ->
                throw IllegalStateException("Patch bundle is corrupted or incomplete", error)
            }
            return group.associateWith { result[jarByBundle[it]!!] ?: emptyList() }
        }

        private fun validateDexEntries(jarPath: String) {
            JarFile(jarPath).use { jar ->
                val dexEntries = jar.entries().toList().filter { entry ->
                    val name = entry.name.lowercase()
                    name.endsWith(".dex")
                }
                if (dexEntries.isEmpty()) {
                    throw IllegalStateException("Patch bundle is missing dex entries")
                }
                val hasEmptyDex = dexEntries.any { it.size <= 0L }
                if (hasEmptyDex) {
                    throw IllegalStateException("Patch bundle contains empty dex entries")
                }
            }
        }
    }
}
