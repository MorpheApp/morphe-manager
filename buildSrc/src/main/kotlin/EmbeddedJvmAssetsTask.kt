/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.zip.ZipFile

/**
 * Lays out the assets the embedded JVM runs from: a runtime per ABI, named after it, and the
 * patcher host it runs. The app reads them from the jvm directory of its assets.
 *
 * The images of the runtimes hold the same classes but for a few, so only the one of [baseAbi]
 * ships whole. Every other ABI gets the delta from it to its own image, see [imageDelta], which
 * takes a few hundred kilobytes in place of an image of several megabytes.
 */
@CacheableTask
abstract class EmbeddedJvmAssetsTask : DefaultTask() {
    /** The archives [PrepareEmbeddedJvmTask] packs, each named after its ABI. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val runtimes: ConfigurableFileCollection

    /** The images [PrepareEmbeddedJvmTask] links, each named after its ABI. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val images: ConfigurableFileCollection

    /** The ABI whose image ships whole. */
    @get:Input
    abstract val baseAbi: Property<String>

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

        val imagesByAbi = images.associateBy { it.name.substringBefore('.') }
        val base = imagesByAbi[baseAbi.get()]?.readBytes()
            ?: throw GradleException("No image for the base ABI ${baseAbi.get()}")
        writeXzFile(assets.resolve(IMAGE_ASSET), base)
        imagesByAbi.forEach { (abi, image) ->
            val bytes = image.readBytes()
            if (!bytes.contentEquals(base)) writeXzFile(assets.resolve("$abi$DELTA_SUFFIX"), imageDelta(base, bytes))
        }

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
        const val IMAGE_ASSET = "modules.xz"
        const val DELTA_SUFFIX = ".modules.delta.xz"
    }
}
