package app.revanced.manager.patcher.split

import android.content.res.Resources
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import app.revanced.manager.patcher.logger.LogLevel
import app.revanced.manager.patcher.logger.Logger
import app.revanced.manager.patcher.util.NativeLibStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.Locale
import java.util.zip.ZipFile

object SplitApkPreparer {
    private val SUPPORTED_EXTENSIONS = setOf("apks", "apkm", "xapk")
//    private const val SKIPPED_STEP_PREFIX = "[skipped]"
    private val KNOWN_ABIS = setOf("armeabi", "armeabi-v7a", "arm64-v8a", "x86", "x86_64")
    private val DENSITY_QUALIFIERS =
        setOf("ldpi", "mdpi", "tvdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")

    fun isSplitArchive(file: File?): Boolean {
        if (file == null || !file.exists()) return false
        val extension = file.extension.lowercase(Locale.ROOT)
        if (extension in SUPPORTED_EXTENSIONS) return true
        return hasEmbeddedApkEntries(file)
    }

    suspend fun prepareIfNeeded(
        source: File,
        workspace: File,
        logger: Logger = defaultLogger,
        stripNativeLibs: Boolean = false
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
            val entries = extractSplitEntries(source, modulesDir)
            logger.info("Found ${entries.size} split modules: ${entries.joinToString { it.name }}")
            logger.info("Module sizes: ${entries.joinToString { "${it.name}=${it.file.length()} bytes" }}")
            val mergeOrder = Merger.listMergeOrder(modulesDir.toPath())
            val supportedTokens = supportedAbiTokens()
            val skippedModules = buildSet {
                if (stripNativeLibs) {
                    addAll(mergeOrder.filter { shouldSkipModule(it, supportedTokens) })
                }
                if (false) { // Morphe "skip unused bundles"
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
//            onSubSteps?.invoke(buildSplitSubSteps(mergeOrder, skippedModules, stripNativeLibs))

            Merger.merge(
                apkDir = modulesDir.toPath(),
                outputApk = mergedApk,
                skipModules = skippedModules,
//                onProgress = onProgress
            )

            if (stripNativeLibs) {
                NativeLibStripper.strip(mergedApk)
            }

            persistMergedIfDownloaded(source, mergedApk, logger)

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

    private fun hasEmbeddedApkEntries(file: File): Boolean =
        runCatching {
            ZipFile(file).use { zip ->
                zip.entries().asSequence().any { entry ->
                    !entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)
                }
            }
        }.getOrDefault(false)

    private data class ExtractedModule(val name: String, val file: File)

    private fun supportedAbiTokens(): Set<String> =
        selectPrimaryAbi(Build.SUPPORTED_ABIS.toList())
            ?.let { primary ->
                buildAbiTokens(primary)
                    .map { it.lowercase(Locale.ROOT) }
                    .toSet()
            }
            ?: Build.SUPPORTED_ABIS
                .flatMap { abi -> buildAbiTokens(abi) }
                .map { it.lowercase(Locale.ROOT) }
                .toSet()

    private fun buildAbiTokens(abi: String): Set<String> {
        val normalized = abi.lowercase(Locale.ROOT)
        return setOf(
            normalized,
            normalized.replace('-', '_'),
            normalized.replace('_', '-')
        )
    }

    private fun selectPrimaryAbi(supportedAbis: List<String>): String? =
        supportedAbis.firstOrNull { it.isNotBlank() }

    private fun shouldSkipModule(
        moduleName: String,
        supportedTokens: Set<String>
    ): Boolean {
        val lower = moduleName.lowercase(Locale.ROOT)
        val knownTokens = KNOWN_ABIS.flatMap { buildAbiTokens(it) }.toSet()
        if (knownTokens.none { lower.contains(it) }) return false
        return supportedTokens.none { lower.contains(it) }
    }

    private fun shouldSkipModuleForDevice(
        moduleName: String,
        localeTokens: Set<String>,
        densityQualifier: String?
    ): Boolean {
        val qualifiers = splitConfigQualifiers(moduleName)
        if (qualifiers.isEmpty()) return false
        if (isAbiSplit(moduleName)) return false

        for (qualifier in qualifiers) {
            if (isDensityQualifier(qualifier)) {
                val deviceDensity = densityQualifier ?: continue
                if (qualifier != deviceDensity) return true
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
        val knownTokens = KNOWN_ABIS.flatMap { buildAbiTokens(it) }.toSet()
        return knownTokens.any { lower.contains(it) }
    }

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

    private fun deviceLocaleTokens(): Set<String> {
        val locales = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val list = Resources.getSystem().configuration.locales
            (0 until list.size()).map { index -> list[index] }
        } else {
            listOf(Locale.getDefault())
        }

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

    private fun deviceDensityQualifier(): String? {
        val density = Resources.getSystem().displayMetrics?.densityDpi ?: return null
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

    data class PreparationResult(
        val file: File,
        val merged: Boolean,
        val cleanup: () -> Unit = {}
    )

    private fun persistMergedIfDownloaded(source: File, merged: File, logger: Logger) {
        // Only persist back to the downloads cache when the original input lives in our downloaded-apps dir.
        val downloadsRoot = source.parentFile?.parentFile
        val isDownloadedApp = downloadsRoot?.name?.startsWith("app_downloaded-apps") == true
        if (!isDownloadedApp) return

        runCatching {
            merged.copyTo(source, overwrite = true)
            logger.info("Persisted merged split APK back to downloads cache: ${source.absolutePath}")
        }.onFailure { error ->
            logger.warn("Failed to persist merged split APK to downloads cache: ${error.message}")
        }
    }

    private object defaultLogger : Logger() {
        override fun log(level: LogLevel, message: String) {
            Log.d("SplitApkPreparer", "[${level.name}] $message")
        }
    }
}
