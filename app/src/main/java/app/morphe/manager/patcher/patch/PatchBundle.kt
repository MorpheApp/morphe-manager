package app.morphe.manager.patcher.patch

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
                name = readManifestAttribute("name"),
                version = readManifestAttribute("version"),
                description = readManifestAttribute("description"),
                source = readManifestAttribute("source"),
                author = readManifestAttribute("author"),
                contact = readManifestAttribute("contact"),
                website = readManifestAttribute("website"),
                license = readManifestAttribute("license")
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
        val license: String?
    )

    object Loader {
        private fun loadBundle(bundle: PatchBundle): Collection<Patch<*>> {
            validateDexEntries(bundle.patchesJar)
            val patchFiles = runCatching {
                loadPatchesFromDex(setOf(File(bundle.patchesJar))).byPatchesFile
            }.getOrElse { error ->
                throw IllegalStateException("Patch bundle is corrupted or incomplete", error)
            }
            val entry = patchFiles.entries.singleOrNull()
                ?: throw IllegalStateException("Unexpected patch bundle load result for ${bundle.patchesJar}")

            return entry.value
        }

        private fun metadataFor(bundle: PatchBundle) = loadBundle(bundle).map(::PatchInfo)

        fun metadata(bundles: Iterable<PatchBundle>) =
            bundles.associateWith(::metadataFor)

        fun metadata(bundle: PatchBundle) = metadataFor(bundle)

        fun patches(bundles: Iterable<PatchBundle>, packageName: String) =
            bundles.associateWith { bundle ->
                loadBundle(bundle).filter { patch ->
                    val compatiblePackages = patch.compatiblePackages
                        ?: return@filter true // Universal patch

                    compatiblePackages.any { (name, _) -> name == packageName }
                }.toSet()
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
