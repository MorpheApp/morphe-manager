package app.revanced.manager.patcher.split

import android.util.Log
import app.revanced.manager.patcher.logger.LogLevel
import app.revanced.manager.patcher.logger.Logger
import app.revanced.manager.patcher.util.NativeLibStripper
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.Locale
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SplitApkPreparer {
    private val SUPPORTED_EXTENSIONS = setOf("apks", "apkm", "xapk")

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

            val module = Merger.merge(modulesDir.toPath())
            module.use {
                withContext(Dispatchers.IO) {
                    it.writeApk(mergedApk)
                }
            }

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

    private suspend fun extractSplitEntries(source: File, targetDir: File): List<ExtractedModule> =
        withContext(Dispatchers.IO) {
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
                    val destination = targetDir.resolve(entry.name.substringAfterLast('/'))
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
