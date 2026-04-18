package app.morphe.manager.patcher.split

import android.content.res.Resources
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import app.morphe.manager.patcher.logger.LogLevel
import app.morphe.manager.patcher.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.Locale
import java.util.zip.ZipFile

/**
 * Prepares split APK bundles (APKS/APKM/XAPK and plain ZIPs with embedded APKs) for patching
 * by extracting and merging all constituent modules into a single monolithic APK.
 */
object SplitApkPreparer {
    // Recognized split archive container extensions
    private val SUPPORTED_EXTENSIONS = setOf("apks", "apkm", "xapk")
    private const val SKIPPED_STEP_PREFIX = "[skipped]"

    // All known ABI identifiers as they appear in split module names, pre-computed once
    private val KNOWN_ABIS = setOf("armeabi", "armeabi-v7a", "arm64-v8a", "x86", "x86_64")
    private val KNOWN_ABI_TOKENS = KNOWN_ABIS.flatMap { abi ->
        val normalized = abi.lowercase(Locale.ROOT)
        setOf(normalized, normalized.replace('-', '_'), normalized.replace('_', '-'))
    }.toSet()
    private val DENSITY_QUALIFIERS =
        setOf("ldpi", "mdpi", "tvdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")

    /** Returns `true` if [file] is a split APK bundle. */
    fun isSplitArchive(file: File?): Boolean {
        if (file == null || !file.exists()) return false
        val extension = file.extension.lowercase(Locale.ROOT)
        if (extension in SUPPORTED_EXTENSIONS) return true
        return hasEmbeddedApkEntries(file)
    }

    /**
     * If [source] is a split APK bundle, extracts all modules into [workspace], merges them into
     * a single APK and returns a [PreparationResult] pointing to the merged file.
     * If [source] is already a plain APK, returns a [PreparationResult] wrapping it unchanged.
     *
     * The caller is responsible for invoking [PreparationResult.cleanup] when the result is no
     * longer needed to remove temporary files.
     */
    suspend fun prepareIfNeeded(
        source: File,
        workspace: File,
        logger: Logger = DefaultLogger,
        skipUnneededSplits: Boolean = false,
        onProgress: ((String) -> Unit)? = null,
        onSubSteps: ((List<String>) -> Unit)? = null,
        sortMergedApkEntries: Boolean = false
    ): PreparationResult {
        if (!isSplitArchive(source)) {
            return PreparationResult(source, merged = false)
        }

        workspace.mkdirs()
        val workingDir = File(workspace, "split-${System.currentTimeMillis()}")
        val modulesDir = workingDir.resolve("modules").also { it.mkdirs() }
        val mergedApk = workingDir.resolve("${source.nameWithoutExtension}-merged.apk")

        return try {
            val sourceSize = source.length()
            logger.info("Preparing split APK bundle from ${source.name} (size=${sourceSize} bytes)")
            val entries = extractSplitEntries(source, modulesDir, onProgress)
            logger.info("Found ${entries.size} split modules: ${entries.joinToString { it.name }}")
            logger.info("Module sizes: ${entries.joinToString { "${it.name}=${it.file.length()} bytes" }}")
            val mergeOrder = Merger.listMergeOrder(modulesDir.toPath())
            val supportedTokens = supportedAbiTokens()
            val skippedModules = buildSet {
                if (skipUnneededSplits) {
                    addAll(mergeOrder.filter { shouldSkipModule(it, supportedTokens) })
                    val localeTokens = deviceLocaleTokens()
                    val densityQualifier = deviceDensityQualifier()
                    addAll(
                        mergeOrder.filter {
                            shouldSkipModuleForDevice(
                                moduleName = it,
                                localeTokens = localeTokens,
                                densityQualifier = densityQualifier
                            )
                        }
                    )
                }
            }
            onSubSteps?.invoke(buildSplitSubSteps(mergeOrder, skippedModules))

            Merger.merge(
                apkDir = modulesDir.toPath(),
                outputApk = mergedApk,
                skipModules = skippedModules,
                onProgress = onProgress,
                sortApkEntries = sortMergedApkEntries
            )

            onProgress?.invoke("Finalizing merged APK")

            logger.info(
                "Split APK merged to ${mergedApk.absolutePath} " +
                        "(modules=${entries.size}, mergedSize=${mergedApk.length()} bytes)"
            )
            PreparationResult(
                file = mergedApk,
                merged = true
            ) {
                workingDir.deleteRecursively()
            }
        } catch (error: Throwable) {
            workingDir.deleteRecursively()
            throw error
        }
    }

    // Returns true if the ZIP file at [file] contains at least one embedded .apk entry
    private fun hasEmbeddedApkEntries(file: File): Boolean =
        runCatching {
            ZipFile(file).use { zip ->
                zip.entries().asSequence().any { entry ->
                    !entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)
                }
            }
        }.getOrDefault(false)

    private data class ExtractedModule(val name: String, val file: File)

    private fun buildSplitSubSteps(
        mergeOrder: List<String>,
        skippedModules: Set<String>
    ): List<String> {
        val steps = mutableListOf<String>()
        steps.add("Extracting split APKs")
        val skippedLookup = skippedModules
            .map { it.lowercase(Locale.ROOT) }
            .toSet()
        val (skipped, remaining) = mergeOrder.partition {
            skippedLookup.contains(it.lowercase(Locale.ROOT))
        }
        skipped.forEach { steps.add("$SKIPPED_STEP_PREFIX Merging $it") }
        remaining.forEach { steps.add("Merging $it") }
        steps.add("Writing merged APK")
        steps.add("Finalizing merged APK")
        return steps
    }

    // Returns the set of name tokens for the device's primary ABI (the first entry in
    // Build.SUPPORTED_ABIS, which is always the most preferred one).
    // Tokens cover both dash and underscore forms so they match any split module naming variant
    private fun supportedAbiTokens(): Set<String> =
        buildAbiTokens(Build.SUPPORTED_ABIS.first())
            .map { it.lowercase(Locale.ROOT) }
            .toSet()

    // Produces all name variants for a single ABI string: normalized, dash form, underscore form.
    private fun buildAbiTokens(abi: String): Set<String> {
        val normalized = abi.lowercase(Locale.ROOT)
        return setOf(
            normalized,
            normalized.replace('-', '_'),
            normalized.replace('_', '-')
        )
    }

    // Returns true if [moduleName] is a native-library split for an ABI that is NOT in [supportedTokens].
    // Modules that don't look like ABI splits at all are kept (return false)
    private fun shouldSkipModule(
        moduleName: String,
        supportedTokens: Set<String>
    ): Boolean {
        val lower = moduleName.lowercase(Locale.ROOT)
        if (KNOWN_ABI_TOKENS.none { lower.contains(it) }) return false
        return supportedTokens.none { lower.contains(it) }
    }

    // Returns true if [moduleName] is a locale or density config split that does not match the
    // current device. ABI splits are intentionally excluded here - they are handled separately
    // by [shouldSkipModule]
    private fun shouldSkipModuleForDevice(
        moduleName: String,
        localeTokens: Set<String>,
        densityQualifier: String
    ): Boolean {
        val qualifiers = splitConfigQualifiers(moduleName)
        if (qualifiers.isEmpty()) return false
        if (isAbiSplit(moduleName)) return false

        for (qualifier in qualifiers) {
            if (isDensityQualifier(qualifier)) {
                if (qualifier != densityQualifier) return true
                continue
            }
            val localeQualifier = parseLocaleQualifier(qualifier) ?: continue
            if (!matchesLocaleQualifier(localeQualifier, localeTokens)) {
                return true
            }
        }
        return false
    }

    private fun isAbiSplit(moduleName: String): Boolean {
        val lower = moduleName.lowercase(Locale.ROOT)
        return KNOWN_ABI_TOKENS.any { lower.contains(it) }
    }

    // Extracts the qualifier tokens from a config split module name
    private fun splitConfigQualifiers(moduleName: String): List<String> {
        val normalized = moduleName.lowercase(Locale.ROOT).removeSuffix(".apk")
        val splitIndex = normalized.indexOf("split_config.")
        val configIndex = normalized.indexOf("config.")
        val startIndex = when {
            splitIndex != -1 -> splitIndex + "split_config.".length
            configIndex != -1 -> configIndex + "config.".length
            else -> return emptyList()
        }
        val tail = normalized.substring(startIndex)
        return tail.split('.').filter { it.isNotBlank() }
    }

    private fun isDensityQualifier(token: String): Boolean = token in DENSITY_QUALIFIERS

    private data class LocaleQualifier(val language: String, val region: String?)

    private fun parseLocaleQualifier(rawToken: String): LocaleQualifier? {
        val token = rawToken.replace('-', '_')
        val parts = token.split('_').filter { it.isNotBlank() }
        if (parts.isEmpty()) return null
        val language = parts[0]
        if (language.length !in 2..3 || !language.all { it.isLetter() }) return null
        val region = parts.getOrNull(1)
            ?.removePrefix("r")
            ?.takeIf { it.length in 2..3 && it.all { ch -> ch.isLetterOrDigit() } }
        return LocaleQualifier(language.lowercase(Locale.ROOT), region?.lowercase(Locale.ROOT))
    }

    private fun matchesLocaleQualifier(
        qualifier: LocaleQualifier,
        localeTokens: Set<String>
    ): Boolean {
        val language = qualifier.language
        val region = qualifier.region
        return if (region == null) {
            localeTokens.contains(language)
        } else {
            localeTokens.contains("${language}_r$region") ||
                    localeTokens.contains("${language}_$region") ||
                    localeTokens.contains("${language}-$region")
        }
    }

    // Returns all locale tokens for the system's active locale list, covering language,
    // language+region, and language+script variants. Uses Resources.getSystem() to read the
    // actual system locale regardless of any in-app language override
    private fun deviceLocaleTokens(): Set<String> {
        val list = Resources.getSystem().configuration.locales
        val locales = (0 until list.size()).map { index -> list[index] }
        return locales.flatMap { locale ->
            buildLocaleTokens(locale)
        }.map { it.lowercase(Locale.ROOT) }.toSet()
    }

    private fun buildLocaleTokens(locale: Locale): Set<String> {
        val tokens = LinkedHashSet<String>()
        val language = locale.language.lowercase(Locale.ROOT)
        if (language.isBlank()) return tokens
        tokens.add(language)
        val region = locale.country.lowercase(Locale.ROOT)
        if (region.isNotBlank()) {
            tokens.add("${language}_r$region")
            tokens.add("${language}_$region")
            tokens.add("${language}-$region")
        }
        val script = locale.script.lowercase(Locale.ROOT)
        if (script.isNotBlank()) {
            tokens.add("${language}_$script")
            tokens.add("${language}-$script")
        }
        return tokens
    }

    // Maps the system's screen density to the closest standard density qualifier string.
    // Uses Resources.getSystem() to avoid being affected by any in-app configuration override
    private fun deviceDensityQualifier(): String {
        val density = Resources.getSystem().displayMetrics.densityDpi
        return when {
            density <= DisplayMetrics.DENSITY_LOW -> "ldpi"
            density <= DisplayMetrics.DENSITY_MEDIUM -> "mdpi"
            density <= DisplayMetrics.DENSITY_TV -> "tvdpi"
            density <= DisplayMetrics.DENSITY_HIGH -> "hdpi"
            density <= DisplayMetrics.DENSITY_XHIGH -> "xhdpi"
            density <= DisplayMetrics.DENSITY_XXHIGH -> "xxhdpi"
            else -> "xxxhdpi"
        }
    }

    private suspend fun extractSplitEntries(
        source: File,
        targetDir: File,
        onProgress: ((String) -> Unit)? = null
    ): List<ExtractedModule> =
        runInterruptible(Dispatchers.IO) {
            val extracted = mutableListOf<ExtractedModule>()
            ZipFile(source).use { zip ->
                val apkEntries = zip.entries().asSequence()
                    .filterNot { it.isDirectory }
                    .filter { it.name.endsWith(".apk", ignoreCase = true) }
                    .toList()

                if (apkEntries.isEmpty()) {
                    throw IOException("Split archive does not contain any APK entries.")
                }

                onProgress?.invoke("Extracting split APKs")
                apkEntries.forEach { entry ->
                    val entryName = entry.name.substringAfterLast('/')
                    val destination = targetDir.resolve(entryName)
                    destination.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        Files.newOutputStream(destination.toPath()).use { output ->
                            input.copyTo(output)
                        }
                    }
                    extracted += ExtractedModule(destination.name, destination)
                }
            }
            extracted
        }

    /** Result of [prepareIfNeeded]. */
    data class PreparationResult(
        val file: File,
        val merged: Boolean,
        val cleanup: () -> Unit = {}
    )

    private object DefaultLogger : Logger() {
        override fun log(level: LogLevel, message: String) {
            Log.d("SplitApkPreparer", "[${level.name}] $message")
        }
    }
}
