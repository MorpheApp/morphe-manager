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

/**
 * Takes the GNU C library a JVM built for Linux runs on from pacman packages of it, for a runtime
 * that Android's own C library cannot load.
 *
 * Android lets an app execute only the files it ships as native libraries, so the dynamic loader
 * goes into [nativeLibraries] under a library name, from where the app starts the JVM with it. The
 * loader maps the JVM and the rest of the C library from the app's storage, which Android allows,
 * so those go into [runtimeFiles], which joins the runtime, together with the copyright notices of
 * the packages and the compiled locales of [localeFiles].
 */
@CacheableTask
abstract class PrepareGlibcTask : DefaultTask() {
    /** The packages and archives of compiled locales to take the files from, by URL, each pinned by its SHA-256. */
    @get:Input
    abstract val packages: MapProperty<String, String>

    /** The directory of the packages the libraries are in, such as data/data/com.termux/files/usr/glibc/lib. */
    @get:Input
    abstract val libraryDirectory: Property<String>

    /** The libraries the runtime links against, by file name. */
    @get:Input
    abstract val libraries: ListProperty<String>

    /**
     * Files of compiled locales as an archive of [packages] lays them out, such as C.UTF-8/LC_CTYPE,
     * which go under glibc/locale/, where the app points glibc to them.
     */
    @get:Input
    abstract val localeFiles: ListProperty<String>

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

    /** The libraries and locales under glibc/ and the notices under legal/glibc/, laid out as the runtime has them. */
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
            val entries = XZInputStream(archive.inputStream().buffered()).use(::readTarEntries)
            // The notices of the package, which the licenses of the libraries ask to go with them
            entries.keys.forEach { path ->
                val (name, file) = NOTICE.find(path)?.destructured ?: return@forEach
                // Some link to the texts of the licenses termux ships apart, which the app's own licenses hold
                val notice = runCatching { entries.resolve(path) }.getOrNull() ?: return@forEach
                runtime.resolve("legal/glibc/$name/$file").apply {
                    parentFile.mkdirs()
                    writeBytes(notice)
                }
            }
            entries
        }

        fun file(path: String): ByteArray = files.firstOrNull { it.containsKey(path) }?.resolve(path)
            ?: throw GradleException("No package has $path")

        fun library(name: String) = file("${libraryDirectory.get()}/$name")

        libraries.get().forEach { name ->
            runtime.resolve("glibc/$name").apply {
                parentFile.mkdirs()
                writeBytes(library(name))
            }
        }
        localeFiles.get().forEach { path ->
            runtime.resolve("glibc/locale/$path").apply {
                parentFile.mkdirs()
                writeBytes(file(path))
            }
        }
        native.resolve("${abi.get()}/${loaderLibraryName.get()}").apply {
            parentFile.mkdirs()
            writeBytes(library(loader.get()))
        }
    }

    private companion object {
        // Such as .../share/doc/glibc/copyright, under the name of the package
        val NOTICE = Regex("share/doc/([^/]+)/(copyright[^/]*)$")
    }
}
