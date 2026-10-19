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
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * The JVM the app ships in its assets for patching, unpacked to storage the first time a run
 * needs it, since the JVM can only be loaded from files.
 *
 * The runtime is unpacked once per runtime the app ships. The patcher host is part of the app's
 * own code and is unpacked once per install of the app.
 */
class JvmInstallation(private val app: Application) {
    /**
     * The ABI of the runtime this device gets, null where the app ships none for it. A 32 bit
     * process could never hold a heap this large anyway.
     */
    val abi: String? =
        if (Process.is64Bit()) Build.SUPPORTED_64_BIT_ABIS.firstOrNull { it in BuildConfig.EMBEDDED_JVM_ABIS }
        else null

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

        ZipInputStream(app.assets.open("$ASSETS_DIR/$abi.zip").buffered()).use { zip ->
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

        if (!staging.renameTo(home)) throw IOException("Could not move the runtime into $home")
        Log.i(tag, "Unpacked the patcher JVM for $abi")
    }

    private fun unpackHost(hostJar: File) {
        val staging = File(hostJar.path + STAGING_SUFFIX)
        app.assets.open("$ASSETS_DIR/$HOST_JAR").use { input ->
            staging.outputStream().use { input.copyTo(it) }
        }
        if (!staging.renameTo(hostJar)) throw IOException("Could not move the patcher host into $hostJar")
    }

    class Installed(val home: File, val hostJar: File) {
        val libjvm get() = home.resolve("lib/server/libjvm.so")

        /** The libraries of the runtime that others of it link against, dependencies first. */
        val preload get() = PRELOAD.map { home.resolve("lib/$it").path }
    }

    private companion object {
        const val ROOT_DIR = "jvm"
        const val ASSETS_DIR = "jvm"
        const val HOST_JAR = "patcher-host.jar"
        const val STAGING_SUFFIX = ".staging"

        // The app's linker namespace does not search the runtime's directory, so a library naming
        // one of these would not find it unless it is already loaded
        val PRELOAD = listOf("libjava.so", "libnet.so", "libnio.so", "libzip.so")
    }
}
