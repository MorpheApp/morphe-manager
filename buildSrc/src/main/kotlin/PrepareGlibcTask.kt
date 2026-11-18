/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.tukaani.xz.XZInputStream
import java.io.File

/**
 * Takes the GNU C library a JVM built for Linux runs on from packages of it, Debian packages or
 * pacman ones, for a runtime that Android's own C library cannot load.
 *
 * Android lets an app execute only the files it ships as native libraries, so the dynamic loader
 * goes into [nativeLibraries] under a library name, from where the app starts the JVM with it. The
 * loader maps the JVM and the rest of the C library from the app's storage, which Android allows,
 * so those go into [runtimeFiles], which joins the runtime, together with the copyright notices of
 * the packages.
 */
@CacheableTask
abstract class PrepareGlibcTask : DefaultTask() {
    /** The packages to take the files from, by URL, each pinned by its SHA-256. */
    @get:Input
    abstract val packages: MapProperty<String, String>

    /** The directory of the packages the libraries are in, such as lib/arm-linux-gnueabihf. */
    @get:Input
    abstract val libraryDirectory: Property<String>

    /** The libraries the runtime links against, by file name. */
    @get:Input
    abstract val libraries: ListProperty<String>

    /** The dynamic loader among the files of [libraryDirectory], such as ld-linux-armhf.so.3. */
    @get:Input
    abstract val loader: Property<String>

    /** The ABI the app ships the loader for. */
    @get:Input
    abstract val abi: Property<String>

    /** The name the loader ships under, a library name, as the app ships nothing else as native code. */
    @get:Input
    abstract val loaderLibraryName: Property<String>

    /** Where downloaded packages are kept between builds. */
    @get:Internal
    abstract val downloadCache: DirectoryProperty

    /** The libraries under glibc/ and the notices under legal/glibc/, laid out as the runtime has them. */
    @get:OutputDirectory
    abstract val runtimeFiles: DirectoryProperty

    /** The loader laid out as native libraries of the app, in a directory per ABI. */
    @get:OutputDirectory
    abstract val nativeLibraries: DirectoryProperty

    @TaskAction
    fun prepare() {
        val runtime = runtimeFiles.get().asFile.apply {
            deleteRecursively()
            mkdirs()
        }
        val native = nativeLibraries.get().asFile.apply {
            deleteRecursively()
            mkdirs()
        }

        val files = packages.get().map { (url, sha256) ->
            val archive = downloadPinned(url, sha256, downloadCache.get().asFile, logger)
            val entries = packageFiles(archive)
            // The notice a Debian package has, which the licenses of the libraries ask to go with them
            entries.keys.firstOrNull { it.startsWith("usr/share/doc/") && it.endsWith("/copyright") }?.let { notice ->
                runtime.resolve("legal/glibc/${notice.split('/')[3]}/copyright").apply {
                    parentFile.mkdirs()
                    writeBytes(entries.resolve(notice))
                }
            }
            entries
        }

        fun library(name: String): ByteArray {
            val path = "${libraryDirectory.get()}/$name"
            return files.firstOrNull { it.containsKey(path) }?.resolve(path)
                ?: throw GradleException("No package has $path")
        }

        libraries.get().forEach { name ->
            runtime.resolve("glibc/$name").apply {
                parentFile.mkdirs()
                writeBytes(library(name))
            }
        }
        native.resolve("${abi.get()}/${loaderLibraryName.get()}").apply {
            parentFile.mkdirs()
            writeBytes(library(loader.get()))
        }
    }

    // A Debian package is an ar archive around a tar of its files, a pacman one is that tar itself
    private fun packageFiles(archive: File): Map<String, TarEntry> {
        val isDeb = archive.inputStream().use { String(it.readNBytes(AR_MAGIC.length), Charsets.US_ASCII) } == AR_MAGIC
        val tar = if (isDeb) {
            readArMembers(archive)["data.tar.xz"] ?: throw GradleException("$archive holds no data.tar.xz")
        } else {
            archive.readBytes()
        }
        return XZInputStream(tar.inputStream()).use(::readTarEntries)
    }

    private companion object {
        const val AR_MAGIC = "!<arch>\n"
    }
}
