/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.util

import android.os.Build
import app.morphe.manager.domain.apk.ApkFileStamp
import app.morphe.manager.domain.apk.apkFileStampOrNull
import app.morphe.patcher.resource.CpuArchitecture
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Which ABIs an APK ships native libraries for, and which of them a run keeps when unused
 * libraries are stripped. The stripping itself is left to the patcher, which drops the rest
 * while it writes the output.
 */
object NativeLibs {
    // Screens listing saved APKs ask for the same files every time they open, and walking the
    // central directory of an APK hundreds of megabytes large is what makes them slow to fill.
    // Keyed by path, so a file rewritten in place replaces its entry rather than adding one
    private val abiCache = ConcurrentHashMap<String, Pair<ApkFileStamp, List<String>>>()

    private fun extractAbiFromEntry(name: String): String? {
        if (!name.startsWith("lib/")) return null
        val secondSlash = name.indexOf('/', startIndex = 4)
        if (secondSlash == -1) return null
        return name.substring(4, secondSlash)
    }

    /** The ABIs [apkFile] ships native libraries for, read once until the file changes. */
    fun extractAbisFromApk(apkFile: File): List<String> {
        val stamp = apkFile.apkFileStampOrNull() ?: return emptyList()
        abiCache[stamp.path]?.takeIf { it.first == stamp }?.let { return it.second }

        return runCatching {
            ZipFile(apkFile).use { zip ->
                zip.entries().asSequence()
                    .map { it.name }
                    .mapNotNull(::extractAbiFromEntry)
                    .distinct()
                    .toList()
            }
        }.onSuccess { abis ->
            // Only the revision that was read, so a file rewritten meanwhile is read again
            if (apkFile.apkFileStampOrNull() == stamp) abiCache[stamp.path] = stamp to abis
        }.getOrDefault(emptyList())
    }

    /**
     * The same answer as [extractAbisFromApk] for an APK that arrives on [stream], which is how
     * a module of a split archive is reached when it has no file of its own to open.
     *
     * Streaming has no central directory to consult, so the walk ends with the run of lib/
     * entries every build tool writes them as, rather than inflating the whole APK behind it.
     * An APK that turns out unreadable is answered by whatever it yielded up to that point.
     */
    fun extractAbisFromStream(stream: InputStream): List<String> {
        val abis = LinkedHashSet<String>()
        var insideLibs = false

        runCatching {
            ZipInputStream(stream.buffered()).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    val abi = extractAbiFromEntry(entry.name)
                    if (abi == null) {
                        if (insideLibs) break
                    } else {
                        insideLibs = true
                        abis += abi
                    }
                }
            }
        }

        return abis.toList()
    }

    /**
     * The one ABI a run keeps out of [abisInApk], which is the first of [supportedAbis] the APK
     * has anything for. Null when it carries nothing the device runs.
     *
     * Public because it is also the answer to which architecture the output is built for, the
     * one ApkArchitectureResolver reports to patches that declare availability against it.
     */
    fun preferredAbi(abisInApk: Set<String>, supportedAbis: List<String>): String? =
        supportedAbis.firstOrNull { it in abisInApk }

    /**
     * The architectures the patcher keeps in [apkFile] when unused native libraries are stripped.
     * Empty, which keeps every library, when the APK carries nothing the device runs or an ABI
     * the patcher has no name for, rather than leaving the output without native code.
     */
    fun keptArchitectures(
        apkFile: File,
        supportedAbis: List<String> = Build.SUPPORTED_ABIS.filter { it.isNotBlank() }
    ): Set<CpuArchitecture> =
        setOfNotNull(
            preferredAbi(extractAbisFromApk(apkFile).toSet(), supportedAbis)
                ?.let(CpuArchitecture::valueOfOrNull)
        )
}
