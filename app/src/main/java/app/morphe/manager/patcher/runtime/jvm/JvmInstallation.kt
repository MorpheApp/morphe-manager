/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.runtime.jvm

import android.app.Application
import android.os.Build
import android.os.Process
import android.util.Log
import app.morphe.manager.BuildConfig
import app.morphe.manager.patcher.runtime.JvmUnavailableException
import app.morphe.manager.util.tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tukaani.xz.XZInputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The JVM the app ships in its assets for patching, unpacked to storage the first time a run
 * needs it, since the JVM can only be loaded from files.
 *
 * The runtime is unpacked once per runtime the app ships. The patcher host is part of the app's
 * own code and is unpacked once per install of the app. Both ship compressed with xz, which
 * takes a few seconds to undo on a phone. The image of the runtime, its lib/modules, ships once
 * for all ABIs, see [unpackImage].
 */
class JvmInstallation(private val app: Application) {
    /**
     * The ABI of the runtime this device gets, null where the app ships none for it. Matched to
     * the process rather than the device, as a 64 bit device can run the app as a 32 bit one.
     */
    val abi: String? =
        (if (Process.is64Bit()) Build.SUPPORTED_64_BIT_ABIS else Build.SUPPORTED_32_BIT_ABIS)
            .firstOrNull { it in BuildConfig.EMBEDDED_JVM_ABIS }

    val isAvailable get() = abi != null

    private val root = app.noBackupFilesDir.resolve(ROOT_DIR)
    private val lock = Mutex()

    /** The runtime and host ready to start, unpacked first if they are not yet. */
    suspend fun install(): Installed = lock.withLock {
        withContext(Dispatchers.IO) {
            val abi = abi ?: throw JvmUnavailableException(
                "No patcher JVM for ${Build.SUPPORTED_ABIS.joinToString()}"
            )

            try {
                val home = root.resolve("runtime-$abi-${BuildConfig.EMBEDDED_JVM_ID}")
                if (!home.isDirectory) unpackRuntime(abi, home)

                val hostJar = root.resolve("host-${BuildConfig.VERSION_CODE}-${BuildConfig.BUILD_ID}.jar")
                if (!hostJar.isFile) unpackHost(hostJar)

                // Whatever an earlier version of the app unpacked is of no use to this one
                root.listFiles()?.filter { it != home && it != hostJar }?.forEach { it.deleteRecursively() }

                Installed(home, hostJar)
            } catch (e: IOException) {
                throw JvmUnavailableException("Could not unpack the patcher JVM", e)
            }
        }
    }

    // Unpacked beside the destination and renamed into place, so a half unpacked runtime left by
    // a process that died midway is never taken for a complete one
    private fun unpackRuntime(abi: String, home: File) {
        val staging = File(home.path + STAGING_SUFFIX).apply { deleteRecursively() }

        ZipInputStream(XZInputStream(app.assets.open("$ASSETS_DIR/$abi.zip.xz").buffered())).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                val target = staging.resolve(entry.name)
                if (!target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) {
                    throw IOException("Runtime entry ${entry.name} points outside of it")
                }

                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                }
            }
        }

        unpackImage(abi, staging.resolve(IMAGE_PATH))

        if (!staging.renameTo(home)) throw IOException("Could not move the runtime into $home")
        Log.i(tag, "Unpacked the patcher JVM for $abi")
    }

    // The app ships one image for all ABIs, and for each whose image differs the delta to it
    private fun unpackImage(abi: String, image: File) {
        image.parentFile?.mkdirs()
        val delta = "$abi$DELTA_SUFFIX"
        if (app.assets.list(ASSETS_DIR)?.contains(delta) != true) {
            unpackXz(IMAGE_ASSET, image)
            return
        }

        val base = File(image.path + BASE_SUFFIX)
        try {
            unpackXz(IMAGE_ASSET, base)
            RandomAccessFile(base, "r").use { baseImage ->
                XZInputStream(app.assets.open("$ASSETS_DIR/$delta").buffered()).use { input ->
                    image.outputStream().buffered().use { JvmImageDelta.apply(baseImage, input, it) }
                }
            }
        } finally {
            base.delete()
        }
    }

    private fun unpackXz(asset: String, target: File) {
        XZInputStream(app.assets.open("$ASSETS_DIR/$asset").buffered()).use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
    }

    // The Android frameworks ARSCLib reads as resources are left out of the host by the build, as
    // the APK carries the very same files for patching inside the app. They are taken from it here.
    private fun unpackHost(hostJar: File) {
        val staging = File(hostJar.path + STAGING_SUFFIX)
        ZipOutputStream(staging.outputStream().buffered()).use { jar ->
            ZipInputStream(XZInputStream(app.assets.open("$ASSETS_DIR/$HOST_ARCHIVE").buffered())).use { host ->
                generateSequence { host.nextEntry }.filter { !it.isDirectory }.forEach { entry ->
                    jar.writeStored(entry.name, host.readBytes())
                }
            }

            ZipFile(app.applicationInfo.sourceDir).use { apk ->
                val frameworks = apk.entries().asSequence()
                    .filter { !it.isDirectory && it.name.startsWith(FRAMEWORKS_DIR) }
                    .toList()
                if (frameworks.isEmpty()) throw IOException("The app ships no Android frameworks for the patcher host")
                frameworks.forEach { entry -> jar.writeStored(entry.name, apk.getInputStream(entry).use { it.readBytes() }) }
            }
        }
        if (!staging.renameTo(hostJar)) throw IOException("Could not move the patcher host into $hostJar")
    }

    // Stored like the host ships, so the JVM reads its classes without inflating them
    private fun ZipOutputStream.writeStored(name: String, content: ByteArray) {
        putNextEntry(ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = content.size.toLong()
            compressedSize = content.size.toLong()
            crc = CRC32().apply { update(content) }.value
        })
        write(content)
        closeEntry()
    }

    class Installed(val home: File, val hostJar: File) {
        /** The VM the runtime ships, the server one where there is one, the client one otherwise. */
        val libjvm
            get() = VMS.map { home.resolve("lib/$it/libjvm.so") }.firstOrNull { it.isFile }
                ?: throw JvmUnavailableException("The patcher JVM ships no VM")

        /** The libraries of the runtime that others of it link against, dependencies first. */
        val preload get() = PRELOAD.map { home.resolve("lib/$it").path }
    }

    private companion object {
        const val ROOT_DIR = "jvm"
        const val ASSETS_DIR = "jvm"
        const val HOST_ARCHIVE = "patcher-host.jar.xz"
        const val STAGING_SUFFIX = ".staging"
        const val IMAGE_ASSET = "modules.xz"
        const val DELTA_SUFFIX = ".modules.delta.xz"
        const val IMAGE_PATH = "lib/modules"
        const val BASE_SUFFIX = ".base"
        const val FRAMEWORKS_DIR = "frameworks/"

        val VMS = listOf("server", "client")

        // The app's linker namespace does not search the runtime's directory, so a library naming
        // one of these would not find it unless it is already loaded
        val PRELOAD = listOf("libjava.so", "libnet.so", "libnio.so", "libzip.so")
    }
}
