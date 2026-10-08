/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/patcher/split/SplitApkPreparer.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.patcher.split

import android.content.res.Resources
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import app.morphe.manager.patcher.logger.LogLevel
import app.morphe.manager.patcher.logger.Logger
import app.morphe.manager.patcher.util.Abi
import app.morphe.manager.patcher.util.NativeLibs
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Files
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

sealed class SplitPreparationEvent {
    data object Extracting : SplitPreparationEvent()
    data class Merging(val apkName: String) : SplitPreparationEvent()
    data object Writing : SplitPreparationEvent()
    data object Finalizing : SplitPreparationEvent()

    /** The name this event crosses the patcher process boundary under, read back by [fromWire]. */
    val wireType: String
        get() = when (this) {
            Extracting -> "Extracting"
            is Merging -> "Merging"
            Writing -> "Writing"
            Finalizing -> "Finalizing"
        }

    companion object {
        /** The event sent as [wireType] and, for [Merging], the module name, or null if unknown. */
        fun fromWire(type: String?, apkName: String?): SplitPreparationEvent? = when (type) {
            "Extracting" -> Extracting
            "Merging" -> Merging(apkName.orEmpty())
            "Writing" -> Writing
            "Finalizing" -> Finalizing
            else -> null
        }
    }
}

/**
 * Prepares split APK bundles (APKS/APKM/XAPK and plain ZIPs with embedded APKs) for patching
 * by extracting and merging all constituent modules into a single monolithic APK.
 */
object SplitApkPreparer {
    /**
     * Packs [baseApk] and its [splitApks] into an APKS archive at [output], each under its own
     * file name so [prepareIfNeeded] can tell the base from the splits. APKs are compressed
     * already, so they are stored rather than deflated, which needs each CRC up front.
     */
    fun writeApksArchive(baseApk: String, splitApks: List<String>, output: File) {
        output.parentFile?.mkdirs()
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            (listOf(baseApk) + splitApks).map(::File).forEach { file ->
                val crc = CRC32()
                val buffer = ByteArray(65536)
                FileInputStream(file).use { input ->
                    var read: Int
                    while (input.read(buffer).also { read = it } >= 0) crc.update(buffer, 0, read)
                }
                zip.putNextEntry(
                    ZipEntry(file.name).apply {
                        method = ZipEntry.STORED
                        size = file.length()
                        compressedSize = file.length()
                        this.crc = crc.value
                    }
                )
                FileInputStream(file).use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    // Recognized split archive container extensions
    private val SUPPORTED_EXTENSIONS = setOf("apks", "apkm", "xapk")

    // Ordered highest → lowest, matching Android's density fallback direction
    private val DENSITY_ORDER = listOf("xxxhdpi", "xxhdpi", "xhdpi", "hdpi", "tvdpi", "mdpi", "ldpi")

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
        onEvent: ((SplitPreparationEvent) -> Unit)? = null
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
            val entries = extractSplitEntries(source, modulesDir, skipUnneededSplits, logger, onEvent)
            logger.info("Extracted ${entries.size} split modules: ${entries.joinToString { it.name }}")
            logger.info("Module sizes: ${entries.joinToString { "${it.name}=${it.file.length()} bytes" }}")
            Merger.merge(
                apkDir = modulesDir.toPath(),
                outputApk = mergedApk,
                onEvent = onEvent
            )

            onEvent?.invoke(SplitPreparationEvent.Finalizing)

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

    /**
     * ABIs [file] ships native libraries for, named as they would appear under lib/ once merged.
     *
     * Answered from the module names where the archive has ABI splits, which keeps it a question
     * of reading the archive's index rather than unpacking every split in it. A bundle built for
     * a single ABI ships no such split, so there the modules themselves have to be opened.
     */
    fun splitArchiveAbis(file: File): List<String> =
        runCatching {
            ZipFile(file).use { zip ->
                val modules = splitModuleEntries(zip).toList()

                modules.mapNotNull { Abi.namedIn(it.name) }.distinct().ifEmpty {
                    modules
                        .flatMap { module ->
                            zip.getInputStream(module).use(NativeLibs::extractAbisFromStream)
                        }
                        .distinct()
                }
            }
        }.getOrDefault(emptyList())

    // Split module entries are always at the root of the archive (no path separator).
    // Nested .apk files (e.g. res/raw/) are embedded resources, not split modules.
    internal fun splitModuleEntries(zip: ZipFile): Sequence<ZipEntry> =
        zip.entries().asSequence().filter { entry ->
            !entry.isDirectory &&
                    !entry.name.contains('/') &&
                    entry.name.endsWith(".apk", ignoreCase = true)
        }

    private fun hasEmbeddedApkEntries(file: File): Boolean =
        runCatching {
            ZipFile(file).use { zip -> splitModuleEntries(zip).any() }
        }.getOrDefault(false)

    private data class ExtractedModule(val name: String, val file: File)

    // Returns the set of name tokens for the device's primary ABI (the first entry in
    // Build.SUPPORTED_ABIS, which is always the most preferred one)
    private fun supportedAbiTokens(): Set<String> = Abi.tokensOf(Build.SUPPORTED_ABIS.first())

    // Returns true if [moduleName] is a native-library split for an ABI that is NOT in [supportedTokens].
    // Modules that don't look like ABI splits at all are kept (return false)
    private fun shouldSkipModule(
        moduleName: String,
        supportedTokens: Set<String>
    ): Boolean {
        if (!isAbiSplit(moduleName)) return false
        val lower = moduleName.lowercase(Locale.ROOT)
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

    private fun isAbiSplit(moduleName: String): Boolean = Abi.namedIn(moduleName) != null

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

    private fun isDensityQualifier(token: String): Boolean = token in DENSITY_ORDER

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

    // Returns the best density qualifier available in [moduleNames] for [deviceQualifier].
    // If the exact bucket is absent, falls back to the nearest lower density,
    // then to the nearest higher one as a last resort
    private fun resolveEffectiveDensityQualifier(
        moduleNames: List<String>,
        deviceQualifier: String
    ): String {
        val available = moduleNames
            .flatMap { splitConfigQualifiers(it) }
            .filter { isDensityQualifier(it) }
            .toSet()
        if (available.isEmpty() || deviceQualifier in available) return deviceQualifier
        val deviceIndex = DENSITY_ORDER.indexOf(deviceQualifier)
        for (i in deviceIndex + 1 until DENSITY_ORDER.size) {
            val candidate = DENSITY_ORDER[i]
            if (candidate in available) return candidate
        }
        for (i in deviceIndex - 1 downTo 0) {
            val candidate = DENSITY_ORDER[i]
            if (candidate in available) return candidate
        }
        return deviceQualifier
    }

    // Decided from the archive's index, so the unneeded splits are never unpacked. A name is only
    // a hint: an .xapk names its base after the package, which can read like a config split, so
    // a module whose manifest declares no split is kept whatever its name says
    private fun unneededModules(zip: ZipFile, entries: List<ZipEntry>): Set<String> {
        val names = entries.map { it.name }
        val supportedTokens = supportedAbiTokens()
        val localeTokens = deviceLocaleTokens()
        val effectiveDensity = resolveEffectiveDensityQualifier(names, deviceDensityQualifier())
        return entries
            .filter { entry ->
                val unneeded = shouldSkipModule(entry.name, supportedTokens) ||
                        shouldSkipModuleForDevice(
                            moduleName = entry.name,
                            localeTokens = localeTokens,
                            densityQualifier = effectiveDensity
                        )
                unneeded && !isBaseModule(zip, entry)
            }
            .mapTo(LinkedHashSet()) { it.name }
    }

    /**
     * Whether the manifest of the module in [entry] declares no split. The module is streamed
     * rather than extracted, which stops early as build tools write the manifest first.
     */
    internal fun isBaseModule(zip: ZipFile, entry: ZipEntry): Boolean =
        runCatching {
            ZipInputStream(zip.getInputStream(entry)).use { module ->
                generateSequence { module.nextEntry }
                    .firstOrNull { it.name == "AndroidManifest.xml" }
                    ?.let { !AndroidManifestBlock.load(module).isSplit }
            }
        }.getOrNull() == true

    private suspend fun extractSplitEntries(
        source: File,
        targetDir: File,
        skipUnneededSplits: Boolean,
        logger: Logger,
        onEvent: ((SplitPreparationEvent) -> Unit)? = null
    ): List<ExtractedModule> =
        runInterruptible(Dispatchers.IO) {
            val extracted = mutableListOf<ExtractedModule>()
            ZipFile(source).use { zip ->
                val allEntries = splitModuleEntries(zip).toList()

                if (allEntries.isEmpty()) {
                    throw IOException("Split archive does not contain any APK entries.")
                }

                val skipped = if (skipUnneededSplits) unneededModules(zip, allEntries) else emptySet()
                if (skipped.isNotEmpty()) {
                    logger.info("Skipping ${skipped.size} unneeded split modules: ${skipped.joinToString()}")
                }
                val apkEntries = allEntries.filterNot { it.name in skipped }

                onEvent?.invoke(SplitPreparationEvent.Extracting)
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
