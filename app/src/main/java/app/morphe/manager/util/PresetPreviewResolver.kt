package app.morphe.manager.util

import java.io.File
import java.util.zip.ZipFile

/**
 * Resolves optional per-value preview images for patch option presets.
 *
 * Convention, no patcher model change: a bundle ships zero or more files at
 * `previews/<package>/<optionKey>/<value>.(png|jpg|jpeg|webp)`, either next to
 * its bundle file or inside the bundle archive itself. The package segment
 * exists because preset values repeat across apps (same icon pack name for
 * YouTube and YT Music). Lookup is exact match on the stored option value
 * with path separators stripped. Absent files mean text-only rows, exactly
 * as today.
 *
 * Pure JVM, no Android dependencies, so this stays unit testable.
 */
object PresetPreviewResolver {

    private val IMAGE_EXTENSIONS = listOf("png", "jpg", "jpeg", "webp")
    private val VIDEO_EXTENSIONS = listOf("mp4", "webm")
    private val EXTENSIONS = IMAGE_EXTENSIONS + VIDEO_EXTENSIONS

    fun isVideoPreview(file: File): Boolean =
        file.extension.lowercase() in VIDEO_EXTENSIONS

    /**
     * @param bundleLocation the bundle file (.mpp/.jar) or the directory holding it.
     * @param packageName target app package, e.g. com.google.android.youtube.
     * @param optionKey patch option key, e.g. "appIcon".
     * @param value stored preset value, e.g. "mmt".
     * @param cacheDir scratch dir for archive extraction. Files are stable per
     *   (bundle, option, value); callers may evict the whole folder on bundle update.
     * @return image file to render, or null when the bundle ships no preview.
     */
    fun resolve(
        bundleLocation: File,
        packageName: String,
        optionKey: String,
        value: String,
        cacheDir: File,
    ): File? {
        val safeValue = value.replace('/', '_').replace('\\', '_')
        val safePackage = packageName.replace('/', '_').replace('\\', '_')
        if (safeValue.isBlank() || safePackage.isBlank()) return null
        val dir = if (bundleLocation.isDirectory) bundleLocation else bundleLocation.parentFile ?: return null

        for (ext in EXTENSIONS) {
            dir.resolve("previews/$safePackage/$optionKey/$safeValue.$ext").takeIf { it.isFile }?.let { return it }
        }

        if (bundleLocation.isFile) {
            for (ext in EXTENSIONS) {
                extractEntry(bundleLocation, "previews/$safePackage/$optionKey/$safeValue.$ext", cacheDir)?.let { return it }
            }
        }
        return null
    }

    private fun extractEntry(bundleFile: File, entryName: String, cacheDir: File): File? {
        val safeName = entryName.replace("..", "_")
        return try {
            ZipFile(bundleFile).use { zip ->
                val entry = zip.getEntry(safeName) ?: return null
                val out = cacheDir.resolve("preset-previews/$safeName")
                if (out.isFile && out.length() == entry.size) return out
                out.parentFile?.mkdirs()
                zip.getInputStream(entry).use { src -> out.outputStream().use { src.copyTo(it) } }
                out
            }
        } catch (e: Exception) {
            null
        }
    }
}
