/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.zip.ZipFile

/**
 * Lays out the assets the embedded JVM runs from: a runtime per ABI, named after it, and the
 * patcher host it runs. The app reads them from the jvm directory of its assets.
 */
@CacheableTask
abstract class EmbeddedJvmAssetsTask : DefaultTask() {
    /** The archives [PrepareEmbeddedJvmTask] packs, each named after its ABI. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val runtimes: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val hostJar: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun layOut() {
        val assets = outputDirectory.get().dir(ASSETS_DIR).asFile.apply {
            deleteRecursively()
            mkdirs()
        }

        runtimes.forEach { it.copyTo(assets.resolve(it.name)) }

        // Repacked like the runtimes, which takes it to less than half of what a jar deflates to
        ZipFile(hostJar.singleFile).use { jar ->
            writeXzArchive(
                assets.resolve(HOST_ARCHIVE),
                jar.entries().asSequence().filter { !it.isDirectory }.map { entry ->
                    entry.name to { jar.getInputStream(entry).use { it.readBytes() } }
                }
            )
        }
    }

    private companion object {
        const val ASSETS_DIR = "jvm"
        const val HOST_ARCHIVE = "patcher-host.jar.xz"
    }
}
